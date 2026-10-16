package com.markreader.webview

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.markreader.file.DocumentType
import com.markreader.file.DocumentTypeResolver
import com.markreader.file.FileTooLargeException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Serves document resources — the Android counterpart of the desktop app's `mdr://` protocol
 * handler, and the reason `readFile` on this host is an ordinary `fetch` rather than a second
 * bridge.
 *
 * Registered on [WebViewAssetLoader]'s default domain under [RESOURCE_PREFIX], so a document's
 * images and its own text both come from `https://appassets.androidplatform.net/__mdr/saf/<token>/…`
 * — the page's own origin. That is what `android-host.js` points `setResourceOrigin` at, and what
 * `'self'` covers in the page's CSP.
 *
 * **It serves nothing outside [SafPathSpace].** The desktop handler originally served any absolute
 * path the page asked for, on the reasoning that a local reader reads local files; that reasoning
 * does not survive rendering untrusted HTML, and it was scoped to the open document's folder.
 * Here the equivalent scope is structural rather than a check: a virtual path names an adopted
 * SAF source or it names nothing, and the app only ever adopts what the user picked.
 *
 * Note on decoding: [WebViewAssetLoader] hands `handle` the result of `Uri.getPath()`, which is
 * **already percent-decoded**, minus the registered prefix. `asLocalResourceUri` encodes each
 * segment on the way out, so decoding here a second time would corrupt any name containing a
 * literal `%`.
 */
internal class DocumentResourceHandler(
    private val space: SafPathSpace,
    private val onMiss: (String) -> Unit = {},
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse {
        val type = contentTypeFor(path)

        // Nothing a provider does may escape into the WebView's thread. A grant revoked between
        // render and fetch, an unmounted volume, a cloud provider dropping mid-read, a document
        // pointing at something enormous — each is one broken reference on the page, and the
        // document around it still has to render.
        val body = try {
            openBody(path, type)
        } catch (ex: FileTooLargeException) {
            onMiss("$path — ${ex.message}")
            return notFound()
        } catch (ex: IOException) {
            onMiss("$path — ${ex.message}")
            return notFound()
        }

        if (body == null) {
            onMiss(path)
            return notFound()
        }
        return WebResourceResponse(type.mimeType, type.encoding, HTTP_OK, "OK", HEADERS, body)
    }

    /**
     * Text is decoded and re-served as UTF-8; everything else is streamed straight through.
     *
     * The decode is the point: it goes through the same `TextCodec` the rest of the app uses, so
     * a document with a byte-order mark or in UTF-16 reads here exactly as it does in the reader,
     * instead of the WebView being told `utf-8` and believing it.
     *
     * Images are not decoded, not buffered, and not size-capped — they are streamed, and a
     * WebView that runs out of memory decoding one fails the image rather than the document.
     */
    private fun openBody(path: String, type: ContentType): InputStream? =
        if (type.encoding == null) {
            space.openStream(path)
        } else {
            space.readText(path)?.byteInputStream()
        }

    /**
     * An explicit 404 rather than `null`.
     *
     * Returning null lets the WebView fall through to the network, which turns a deliberately
     * missing `./assets/absent.png` into a DNS failure seconds later instead of a prompt broken
     * image — and this app has no `INTERNET` permission, so the fall-through can only ever fail.
     */
    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", HTTP_NOT_FOUND, "Not Found",
            HEADERS, ByteArrayInputStream(ByteArray(0)),
        )

    /** A MIME type, and the charset for the text types this handler decodes. */
    private data class ContentType(val mimeType: String, val encoding: String?)

    /**
     * Decode or stream, and what to call it.
     *
     * **The decision is [DocumentTypeResolver]'s, not this file's.** This used to carry its own
     * extension table naming the text formats, which drifted from the resolver's the moment
     * either gained an extension the other lacked. [DocumentTypeResolver.recognize] answers it
     * instead — and it is the nullable one on purpose, because here "unrecognised" means a PNG
     * rather than a README, and must stream rather than decode.
     *
     * The two tables that remain are MIME strings, which is all they ever were.
     */
    private fun contentTypeFor(path: String): ContentType {
        val extension = path.substringAfterLast('.', "").lowercase()
        val recognized = DocumentTypeResolver.recognize(path)
        return if (recognized == null || recognized == DocumentType.Binary) {
            ContentType(MEDIA_MIME_TYPES[extension] ?: "application/octet-stream", null)
        } else {
            ContentType(TEXT_MIME_TYPES[extension] ?: "text/plain", "utf-8")
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404

        /**
         * Text formats whose MIME type is worth stating precisely. Anything else the resolver
         * places is served as `text/plain` — Markdown included, deliberately: the renderer parses
         * it, and `text/markdown` would invite the WebView to do something else with it.
         */
        val TEXT_MIME_TYPES = mapOf(
            "csv" to "text/csv",
            "json" to "application/json",
            "xml" to "application/xml",
            "svg" to "image/svg+xml",
            // No text/html or text/css here on purpose. Both would be served from the renderer's
            // own origin, and declaring them is a security decision to take deliberately rather
            // than inherit from a MIME table. They fall through to text/plain, which under
            // `nosniff` is inert.
        )

        /**
         * Media the resolver has no opinion about — it classifies documents, and none of these is
         * one. They are streamed, not decoded, and not size-capped: a WebView that runs out of
         * memory on an image fails the image rather than the document.
         */
        val MEDIA_MIME_TYPES = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "avif" to "image/avif",
            "bmp" to "image/bmp",
            "ico" to "image/x-icon",
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "mp3" to "audio/mpeg",
            "ogg" to "audio/ogg",
            "wav" to "audio/wav",
            "woff2" to "font/woff2",
            "woff" to "font/woff",
            "ttf" to "font/ttf",
            "otf" to "font/otf",
        )

        /**
         * `nosniff` because the type this handler states is derived from an extension a document
         * chose, and content sniffing is how a mislabelled file becomes something executable.
         * `no-store` because these bytes are a live SAF document: a reload has to see the file as
         * it is now, not as the WebView cached it.
         */
        val HEADERS = mapOf(
            "X-Content-Type-Options" to "nosniff",
            "Cache-Control" to "no-store",
        )
    }
}

/**
 * The [WebViewAssetLoader] path prefix document resources are served under.
 *
 * Shared with `android-host.js`, which appends it to `location.origin` and hands the result to the
 * renderer's `setResourceOrigin`. It keeps the SAF space clear of [ASSETS_PREFIX], which is the
 * renderer's own vendored files.
 */
internal const val RESOURCE_PREFIX = "/__mdr/"

/** Where the vendored renderer is served from. `AssetsPathHandler` maps it onto `assets/`. */
internal const val ASSETS_PREFIX = "/assets/"
