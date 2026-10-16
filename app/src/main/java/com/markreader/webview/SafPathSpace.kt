package com.markreader.webview

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.markreader.file.DocumentSource
import com.markreader.file.TextCodec
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The virtual path space the shared renderer sees — and the only place that knows a path in it
 * means a Storage Access Framework document.
 *
 * The renderer resolves `./assets/badge.svg` by string arithmetic on the open document's path:
 * `resolvePath(dirname(documentPath), href)`. That works on a filesystem path and is hopeless on
 * a `content://` tree URI, whose entire path lives percent-encoded inside one segment — there is
 * no `dirname` of it, and no path arithmetic available in the first place.
 *
 * So the host never shows the renderer a content URI. It hands over a virtual path:
 *
 * ```
 * /saf/<token>/kitchen-sink.md          <- what this class emits
 * /saf/<token>/assets/badge.svg         <- what the renderer derives, unaided
 * ```
 *
 * `<token>` names one adopted source. The renderer's arithmetic works untouched, and the
 * contract it works to is written down in the desktop repo's `src/renderer/host.ts`: a path
 * crossing that interface is opaque, `/` separates segments, `<dir>/<segment>` names the child,
 * and whatever the host emits it can take back. Nothing under `src/renderer` parses meaning out
 * of one, which `test/unit/opaque-paths.test.ts` over there enforces.
 *
 * Turning a derived path back into a document is a **per-segment walk** over
 * [DocumentsContract.buildChildDocumentsUriUsingTree], matching `DISPLAY_NAME`. Appending to the
 * tree's document id is one query rather than one per segment, but it only holds for
 * `com.android.externalstorage.documents` and breaks on Drive, Dropbox and anything else that
 * does not encode a filesystem path in its ids. The walk is the portable form, and its cost is
 * paid once per path because the results are cached.
 *
 * Two kinds of source live in here, because the app opens both. A picked *tree* resolves its
 * whole subtree, so a document's sibling images load. A single document arriving from a file
 * manager or the share sheet is all the app has permission to read — so only its own name
 * resolves, and its relative references 404. That degrades exactly as the contract intends: a
 * broken image rather than a thrown error.
 *
 * **Everything here is touched from more than one thread.** The WebView calls in on its own
 * thread for every image on the page while the reader coroutine is still resolving the document,
 * which is why every cache is concurrent.
 */
class SafPathSpace(private val resolver: ContentResolver) {

    private val sources = ConcurrentHashMap<String, Source>()

    /**
     * Takes a picked tree into the space and returns its virtual root. The caller names a
     * document inside it the way the renderer would: `"$root/$relativePath"`.
     *
     * Does not take the URI permission — that is the picker's caller's job, and a tree may also
     * be one persisted from an earlier run.
     */
    fun adoptTree(treeUri: Uri): String = register(Tree(resolver, treeUri))

    /**
     * Takes a single document into the space and returns its virtual path, complete.
     *
     * [displayName] is what the reader already knows the file as; it becomes the last segment,
     * so its extension is what decides how the document is parsed and served. Path separators in
     * it are collapsed, since a display name is provider-supplied and need not be a bare name.
     */
    fun adoptDocument(documentUri: Uri, displayName: String): String {
        val name = displayName.replace('\\', '/').substringAfterLast('/').ifEmpty { UNNAMED }
        return "${register(SingleDocument(documentUri, name))}/$name"
    }

    /** Drops a source, so a closed document's grant stops being reachable from the page. */
    fun forget(virtualPath: String) {
        parseVirtualPath(virtualPath)?.let { sources.remove(it.token) }
    }

    /** The content URI a virtual path names, or null if it names nothing this space holds. */
    fun resolve(virtualPath: String): Uri? {
        val parsed = parseVirtualPath(virtualPath) ?: return null
        return sources[parsed.token]?.documentUri(parsed.relativePath)
    }

    /** Bytes for a virtual path. The caller closes the stream. */
    fun openStream(virtualPath: String): InputStream? {
        val uri = resolve(virtualPath) ?: return null
        return runCatching { resolver.openInputStream(uri) }.getOrNull()
    }

    /**
     * A virtual path decoded as text, through the same [TextCodec] the rest of the app uses — so
     * byte-order marks and UTF-16 are handled here exactly as they are in the reader, and the
     * size cap applies.
     *
     * The encoding [TextCodec] recovers is dropped on purpose: these bytes are on their way to a
     * WebView that will be told `utf-8`, and nothing on this path ever writes back.
     *
     * Throws [com.markreader.file.FileTooLargeException] rather than truncating, and returns null
     * when the path resolves to nothing.
     */
    fun readText(virtualPath: String): String? =
        openStream(virtualPath)?.use { TextCodec.decode(DocumentSource.readBounded(it)).text }

    private fun register(source: Source): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(TOKEN_LENGTH)
        sources[token] = source
        return "/$PREFIX_SEGMENT/$token"
    }

    private interface Source {
        /** @param relativePath already split-safe: no empty segments, no `.`, no `..`. */
        fun documentUri(relativePath: String): Uri?
    }

    /** One picked tree, resolved by walking it. */
    private class Tree(
        private val resolver: ContentResolver,
        private val treeUri: Uri,
    ) : Source {

        private val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

        /** Relative path -> document id, for paths that resolved. */
        private val resolved = ConcurrentHashMap<String, String>()

        /**
         * Relative paths that did not resolve. A separate set rather than a sentinel in
         * [resolved], because [ConcurrentHashMap] holds no null values — and a miss is worth
         * remembering: `./assets/missing.png` would otherwise re-walk the tree on every render.
         */
        private val missing = ConcurrentHashMap.newKeySet<String>()

        /** Parent document id -> its children as display name -> document id. */
        private val children = ConcurrentHashMap<String, Map<String, String>>()

        override fun documentUri(relativePath: String): Uri? =
            documentId(relativePath)?.let {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, it)
            }

        private fun documentId(relativePath: String): String? {
            if (relativePath.isEmpty()) return rootDocumentId
            if (relativePath in missing) return null
            resolved[relativePath]?.let { return it }

            var current = rootDocumentId
            for (segment in relativePath.split('/')) {
                val match = childrenOf(current)[segment]
                if (match == null) {
                    missing.add(relativePath)
                    return null
                }
                current = match
            }
            resolved[relativePath] = current
            return current
        }

        private fun childrenOf(parentDocumentId: String): Map<String, String> =
            children.getOrPut(parentDocumentId) { queryChildren(parentDocumentId) }

        private fun queryChildren(parentDocumentId: String): Map<String, String> {
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val byName = LinkedHashMap<String, String>()
            // A provider can fail a query on any node — a revoked grant, an unmounted volume, a
            // cloud provider offline. That is a miss for the path being walked, not a crash.
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val id = cursor.getString(1)
                        if (name != null && id != null) {
                            // Duplicate display names happen; first wins, as a walk would.
                            byName.putIfAbsent(name, id)
                        }
                    }
                }
            }
            return byName
        }
    }

    /** One document, opened without a tree around it. Nothing but itself resolves. */
    private class SingleDocument(
        private val documentUri: Uri,
        private val name: String,
    ) : Source {
        override fun documentUri(relativePath: String): Uri? =
            documentUri.takeIf { relativePath == name }
    }

    private companion object {
        /**
         * Random rather than sequential. Tokens travel in URLs inside the page, and a document is
         * untrusted content: with `t0`, `t1` a document opened from one source could name a
         * resource in another. The page's CSP means it could not read what came back — no script
         * of its own runs — but a guess that cannot succeed is cheaper than one that is merely
         * contained.
         */
        const val TOKEN_LENGTH = 12

        const val UNNAMED = "document"
    }
}

/** The first segment of every virtual path. Shared with `android-host.js` only by convention. */
private const val PREFIX_SEGMENT = "saf"

/** A virtual path split into the source it names and the path within it. */
internal data class VirtualPath(val token: String, val relativePath: String)

/**
 * Parses `/saf/<token>/…` — the one form this space recognises.
 *
 * Everything else is null, which is the point of a virtual space: a document that links to
 * `/etc/passwd`, or to `/saf` with no token, gets nothing. Tolerant about shape and strict about
 * meaning, because both a host and an untrusted document reach this:
 *
 * - `\` is accepted as a separator, per the contract's first guarantee.
 * - Empty segments and `.` are dropped, so `/saf/t/a//./b` is `a/b`.
 * - `..` is refused outright, even though `resolvePath` collapses it on the renderer's side
 *   before a path ever comes back. Trusting that is trusting a document.
 *
 * A top-level function so it is testable without a `ContentResolver`.
 */
internal fun parseVirtualPath(virtualPath: String): VirtualPath? {
    val segments = virtualPath
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." }

    if (segments.size < 2 || segments[0] != PREFIX_SEGMENT) return null
    if (segments.any { it == ".." }) return null

    return VirtualPath(segments[1], segments.drop(2).joinToString("/"))
}
