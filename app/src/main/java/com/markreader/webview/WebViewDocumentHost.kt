package com.markreader.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Android implementation of the shared renderer's host contract.
 *
 * The desktop app and this class are the two hosts of one document renderer, which lives in
 * `markreader-desktop` under `src/renderer`, and is vendored here as
 * `assets/renderer/` — see that directory's `MANIFEST.md`. The contract between them is written
 * down in the desktop repo's `src/renderer/host.ts`; the Android side of it is four members,
 * installed as `window.markreader` by `assets/renderer/android-host.js`.
 *
 * The seam is a [WebViewAssetLoader] origin rather than `loadDataWithBaseURL`, and that is the
 * decision everything else follows from. Because the page is served from
 * `https://appassets.androidplatform.net`, document resources can be served from the *same*
 * origin under [RESOURCE_PREFIX] — so a document's text and a document's images travel one seam,
 * `readFile` is a plain `fetch` instead of a second bridge, and [SafPathSpace] is the only thing
 * that has to know a path means a SAF document.
 *
 * **What crosses into Kotlin is seven methods and nothing else.** No file reading, no settings, no
 * window control. Four are lifecycle and navigation — `ready`, `rendered`, `openPath`,
 * `openExternal` — and three report what only the page can know, because it is the side holding
 * the rendering: the document's outline, which heading is being read, and how many matches a
 * search has. Compose draws its table of contents and its search bar from those three.
 *
 * `openPath` deliberately does not read the file on the page's side even though `readFile` could,
 * because SAF permission, the encoding decision, the recents list and the resume position all
 * live out here — a document the page opened by itself would be invisible to all four.
 *
 * ### The security half
 *
 * Option 3 introduces script execution over untrusted document content, which the Markwon reader
 * does not have. The desktop side of that is in the vendored modules — DOMPurify before every
 * `innerHTML`, and a resource handler scoped to the open document. The parts only a host can
 * state are here and in the page:
 *
 * - **The CSP** is a meta tag in `android-host.html`. `default-src 'none'`, no `'unsafe-inline'`
 *   or `'unsafe-eval'` in `script-src`, and no `https:` in `img-src` — the app declares no
 *   `INTERNET` permission and keeps it that way, so remote images do not load on Android though
 *   they do on desktop. An accepted cross-host difference; a document that can fetch a remote URL
 *   can exfiltrate through it.
 * - **The `openExternal` allowlist** is [openExternalUrl], the last of three checks of the same
 *   thing and the only one outside the WebView.
 * - **Navigation** goes nowhere. The page renders documents with script; it never navigates to
 *   one, so [WebViewClient.shouldOverrideUrlLoading] refuses every URL and routes the allowlisted
 *   ones out of the app.
 *
 * ### Using it
 *
 * Construct with an Activity context, adopt what the user picked, then open a path:
 *
 * ```kotlin
 * val host = WebViewDocumentHost(activity, lifecycleScope, onDocumentRendered = { … })
 * host.openDocument(host.adoptDocument(uri, displayName))
 * ```
 *
 * The page starts loading on construction, so it is warming while the document is still being
 * read. [destroy] must be called when the owner goes away; the WebView outlives its Activity
 * otherwise. Placing it in Compose chrome, and the scroll behaviour that goes with it, is
 * deliberately not here.
 */
class WebViewDocumentHost(
    private val context: Context,
    private val scope: CoroutineScope,
    /** A document is on screen. Fires for links followed inside a document as well as for opens. */
    private val onDocumentRendered: (virtualPath: String) -> Unit = {},
    /** A document could not be read. [cause] is null when the path resolved to nothing. */
    private val onDocumentFailed: (virtualPath: String, cause: Throwable?) -> Unit = { _, _ -> },
    /**
     * The page never reported ready, so this WebView cannot run the renderer at all. Carries the
     * WebView's version name when it is known, for a message worth reading.
     */
    private val onRendererUnavailable: (webViewVersion: String?) -> Unit = {},
    /** The document's outline, as the page rendered it. Re-reported on every render. */
    private val onOutlineChanged: (outline: List<HeadingItem>) -> Unit = {},
    /** The heading being read, by slug, or null in a document with no headings. */
    private val onActiveHeadingChanged: (slug: String?) -> Unit = {},
    /**
     * The result of the last find: which match is current, and how many there are. [index] is -1
     * when there are none.
     */
    private val onFindStateChanged: (index: Int, total: Int) -> Unit = { _, _ -> },
) {

    private val space = SafPathSpace(context.contentResolver)

    private val assetLoader = WebViewAssetLoader.Builder()
        // The vendored renderer: its modules, its stylesheets, and the host page itself.
        .addPathHandler(ASSETS_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
        // Document resources, out of the virtual path space.
        .addPathHandler(RESOURCE_PREFIX, DocumentResourceHandler(space) { miss ->
            Log.i(TAG, "no document resource at $miss")
        })
        .build()

    /**
     * Completed by the page's `ready()`, which it calls only after the `DocumentView` exists.
     *
     * Everything sent into the page awaits this. A document pushed before the view has been built
     * would be dropped in silence, which is a miserable thing to diagnose on a device.
     */
    private val pageReady = CompletableDeferred<Unit>()

    private var destroyed = false

    val webView: WebView = createWebView()

    init {
        webView.loadUrl(PAGE_URL)

        // Watch for a renderer that never starts.
        //
        // Behavioural, deliberately, rather than a version comparison. `ready()` fires only after
        // the whole vendored module graph has resolved and executed, so its absence is the one
        // signal covering every cause at once — a WebView too old for the syntax the renderer is
        // written in, an asset that failed to load, a CSP that refused a module. A hardcoded
        // minimum Chromium version would be a guess about *why*, and it would go stale the moment
        // the shared renderer adopts newer syntax. It will: the floor moved to optional chaining
        // without anyone noticing.
        //
        // The version is read only to make the message specific, never to decide anything.
        scope.launch(Dispatchers.Main) {
            val ready = withTimeoutOrNull(READY_TIMEOUT_MS) { pageReady.await() }
            if (ready == null && !destroyed) {
                val version = runCatching {
                    WebViewCompat.getCurrentWebViewPackage(context)?.versionName
                }.getOrNull()
                Log.e(TAG, "the renderer never reported ready; WebView is $version")
                onRendererUnavailable(version)
            }
        }
    }

    // ------------------------------------------------------------------ the path space

    /**
     * Takes a single document — from a file manager, or the share sheet — into the space and
     * returns its complete virtual path. Its relative references will 404, because a single-document
     * grant is all the app has permission to read.
     *
     * Single documents are all this host adopts, because they are all the reader opens. Folders
     * are already `SafPathSpace`'s to adopt, and a delegation for them belongs here on the day
     * something calls it — a document naming a sibling is `"$root/$relativePath"`, the same
     * arithmetic the renderer does.
     *
     * The URI permission is the picker's to take; this does not.
     */
    fun adoptDocument(documentUri: Uri, displayName: String): String =
        space.adoptDocument(documentUri, displayName)

    /** Drops a source, so a closed document stops being reachable from the page. */
    fun forget(virtualPath: String) = space.forget(virtualPath)

    // -------------------------------------------------------------------- the document

    /**
     * Reads [virtualPath] and renders it.
     *
     * @param rootPath the folder root, when the document came from an adopted tree. Root-relative
     *   links (`/README.md`) resolve against it.
     * @param preserveScroll holds the reading position, for re-reading the document already shown.
     */
    fun openDocument(
        virtualPath: String,
        rootPath: String? = null,
        preserveScroll: Boolean = false,
    ) {
        scope.launch(Dispatchers.Main) {
            val read = withContext(Dispatchers.IO) {
                runCatching { space.readText(virtualPath) }
            }
            val text = read.getOrNull()
            if (text == null) {
                onDocumentFailed(virtualPath, read.exceptionOrNull())
                return@launch
            }

            pageReady.await()
            call(
                "showDocument",
                JSONObject()
                    .put("path", virtualPath)
                    .put("text", text)
                    .put("rootPath", rootPath ?: JSONObject.NULL)
                    .put("preserveScroll", preserveScroll),
            )
        }
    }

    /**
     * Renders [text] as the document at [virtualPath], without reading anything.
     *
     * The counterpart to [openDocument], for the caller that already has the text — which the
     * reader does: it read and decoded the bytes itself, because the encoding, the recents entry
     * and the resume position all hang off that read. Going through [openDocument] would decode
     * the same file a second time, up to `MAX_FILE_BYTES` of it, and leave two copies of the
     * document that could disagree.
     *
     * [virtualPath] still has to name something adopted, because that is what the document's own
     * relative images resolve against.
     */
    fun showDocument(
        virtualPath: String,
        text: String,
        rootPath: String? = null,
        preserveScroll: Boolean = false,
    ) {
        scope.launch(Dispatchers.Main) {
            pageReady.await()
            call(
                "showDocument",
                JSONObject()
                    .put("path", virtualPath)
                    .put("text", text)
                    .put("rootPath", rootPath ?: JSONObject.NULL)
                    .put("preserveScroll", preserveScroll),
            )
        }
    }

    /**
     * Room to leave at the top of the document for chrome drawn over the WebView, in CSS pixels
     * (which are dp here, since the host disables zoom).
     *
     * Deliberately *not* an inset applied to the WebView from outside. The chrome collapses as
     * the reader scrolls, and an outer inset would leave a dead band where it used to be; page
     * padding scrolls away with the content, which is what the native reader gets by putting the
     * same inset inside its `ScrollView`.
     */
    fun applyContentInset(topDp: Float) {
        scope.launch(Dispatchers.Main) {
            pageReady.await()
            call("setContentInset", JSONObject().put("top", topDp))
        }
    }

    // --------------------------------------------------------------- search and outline

    /**
     * Asks the page for something about search or the outline.
     *
     * One method rather than four, because these are the requests that cannot be expressed as
     * state — see [PageCommand]. Nothing comes back from any of them directly: the page owns the
     * match set, since matches are DOM ranges over a rendering only it has, so what it has to say
     * arrives through [onFindStateChanged] and [onOutlineChanged] instead. Those also fire again
     * whenever the document is re-rendered underneath a live query.
     *
     * A [PageCommand.Reveal] naming nothing is ignored by the page, which is what acting on a
     * stale outline looks like.
     */
    fun send(command: PageCommand) {
        scope.launch(Dispatchers.Main) {
            pageReady.await()
            when (command) {
                is PageCommand.Find -> call("runFind", JSONObject().put("query", command.query))
                is PageCommand.StepFind -> call("stepFind", JSONObject().put("delta", command.delta))
                PageCommand.CloseFind -> call("closeFind")
                is PageCommand.Reveal -> call("revealSlug", JSONObject().put("slug", command.slug))
            }
        }
    }

    /** Applies reader settings. Safe before the first document; the page re-renders if it needs to. */
    fun applySettings(settings: RendererSettings) {
        scope.launch(Dispatchers.Main) {
            pageReady.await()
            call("applyConfig", settings.toJson())
        }
    }

    fun destroy() {
        destroyed = true
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
    }

    /**
     * Calls into the page.
     *
     * `JSONObject.quote` produces a quoted, escaped JavaScript string literal, so the payload
     * crosses as a *string* that the page then parses. Interpolating the JSON as an expression
     * would make every document's text a script fragment in this line.
     */
    private fun call(method: String, payload: JSONObject? = null) {
        if (destroyed) {
            return
        }
        val argument = payload?.let { JSONObject.quote(it.toString()) } ?: ""
        webView.evaluateJavascript("window.__mdrPage.$method($argument)", null)
    }

    // -------------------------------------------------------------------- the WebView

    @SuppressLint("SetJavaScriptEnabled") // The point of option 3; see the class comment.
    private fun createWebView(): WebView {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // What the three device checks owed before the first option-3 release are run with.
            WebView.setWebContentsDebuggingEnabled(true)
        }

        return WebView(context).apply {
            // On, and only here. ExportManager keeps it false for PDF export, over HTML it built
            // itself from commonmark; these two WebViews want opposite settings.
            settings.javaScriptEnabled = true

            // Everything local is reachable through the asset loader or not at all. The two
            // file-URL settings are no-ops from API 30 but minSdk is 24.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false

            // The page stores nothing. Reading state is the app's, in DataStore.
            settings.domStorageEnabled = false
            settings.setGeolocationEnabled(false)
            settings.mediaPlaybackRequiresUserGesture = true

            // Pinch zoom was removed from the reader deliberately; the WebView follows.
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false

            // `textZoom` is deliberately left alone: it is where the platform's font-size
            // accessibility setting lands, and pinning it to 100 would discard that.

            // `blockNetworkLoads` is deliberately not set. It would be belt over the braces of
            // having no INTERNET permission at all, and it is one more thing that could turn the
            // asset-loader origin into a blank page on some WebView build.

            webViewClient = HostPageClient()
            webChromeClient = HostPageChromeClient()
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
        }
    }

    private inner class HostPageClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

        /**
         * Nothing navigates. The page renders documents into itself, so any URL arriving here is
         * either a link `document.ts` did not claim or something a document tried on its own —
         * neither of which should replace the reader. Allowlisted schemes leave the app; the rest
         * are dropped.
         */
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            openExternalUrl(context, request.url.toString())
            return true
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Sub-resource failures are ordinary — a missing image a document references, or a
            // source map nobody shipped. A main-frame failure means no reader at all.
            if (request.isForMainFrame) {
                Log.e(TAG, "host page failed to load: ${error.errorCode}")
            }
        }

        /**
         * Without this the renderer process dying takes the app process with it. A reader holding
         * a WebView for the length of a session has to survive it instead.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "the render process died (crashed=${detail.didCrash()})")
            destroy()
            return true
        }
    }

    /**
     * The page's error surface.
     *
     * Uncaught exceptions, rejected promises, CSP violations and a module that failed to load all
     * arrive as console messages, so forwarding them is cheaper and catches strictly more than
     * hooking `window.onerror` and `console.error` inside the page — including failures that
     * happen before any of our own script has run.
     */
    private inner class HostPageChromeClient : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            val text = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
            when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, text)
                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, text)
                else -> Log.i(TAG, text)
            }
            return true
        }
    }

    // --------------------------------------------------------------------- the bridge
    //
    // Injected as `window.__mdrHost`; `android-host.js` builds `window.markreader` on top of it.
    // Every method here runs on a WebView JavaScript thread, never the main one, which is why
    // each one hops before it touches anything.

    private inner class Bridge {

        @JavascriptInterface
        fun ready() = fromHostPage {
            // The one lifecycle milestone this host has, and the only positive signal that the
            // whole vendored module graph resolved and executed. Its absence, with no console
            // error alongside it, is what a module that failed to load looks like.
            Log.i(TAG, "host page ready")
            pageReady.complete(Unit)
        }

        @JavascriptInterface
        fun rendered(virtualPath: String) = fromHostPage { onDocumentRendered(virtualPath) }

        @JavascriptInterface
        fun openPath(virtualPath: String) = fromHostPage { openDocument(virtualPath) }

        @JavascriptInterface
        fun openExternal(url: String) = fromHostPage { openExternalUrl(context, url) }

        /**
         * The document's outline, as JSON — the one report here that is a list rather than a
         * value, and the only reason this bridge parses anything.
         *
         * A malformed entry is skipped rather than failing the whole outline: a table of contents
         * missing one row is still navigation, and there is no user-facing way to say more.
         */
        @JavascriptInterface
        fun outline(json: String) = fromHostPage { onOutlineChanged(parseOutline(json)) }

        /** Empty means the document has no headings at all; see `android-host.js`. */
        @JavascriptInterface
        fun activeHeading(slug: String) = fromHostPage {
            onActiveHeadingChanged(slug.takeIf { it.isNotEmpty() })
        }

        @JavascriptInterface
        fun findState(index: Int, total: Int) = fromHostPage {
            onFindStateChanged(index, total)
        }

        /**
         * Hops to the main thread, and refuses to act on anything but the host page.
         *
         * `addJavascriptInterface` exposes the bridge to whatever document is loaded, not to one
         * origin — so the check is what keeps the guarantee that nothing else ever is. It is
         * belt-and-braces given that [shouldOverrideUrlLoading] refuses every navigation, and it
         * is the cheap half of the pair. (`WebViewCompat.addWebMessageListener` scopes by origin
         * natively, but it needs WebView 88, and one of the device checks owed is a deliberately
         * old WebView.)
         */
        private inline fun fromHostPage(crossinline action: () -> Unit) {
            scope.launch(Dispatchers.Main) {
                if (webView.url?.startsWith(PAGE_URL) == true) {
                    action()
                }
            }
        }
    }

    private companion object {

        /**
         * The outline, out of the array `android-host.js` stringifies.
         *
         * Only the three fields the table of contents draws are read. The page also reports the
         * source line each heading came from, which nothing here has a use for yet — the resume
         * position is planned to lean on the slug, not the line.
         */
        fun parseOutline(json: String): List<HeadingItem> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { headingAt(array, it) }
        }

        /** Null for an entry the table of contents could not act on, which is one with no slug. */
        private fun headingAt(array: JSONArray, index: Int): HeadingItem? {
            val entry = array.optJSONObject(index) ?: return null
            val slug = entry.optString("slug")
            if (slug.isEmpty()) {
                return null
            }
            return HeadingItem(
                text = entry.optString("text"),
                level = entry.optInt("level", 1),
                slug = slug,
            )
        }

        const val TAG = "MarkReaderWebView"

        /** [WebViewAssetLoader]'s default domain. Its `https` origin is the page's own. */
        const val ORIGIN = "https://appassets.androidplatform.net"

        const val PAGE_URL = "$ORIGIN${ASSETS_PREFIX}renderer/android-host.html"

        const val BRIDGE_NAME = "__mdrHost"

        /**
         * How long the page gets to report ready before the renderer is called unusable.
         *
         * Generous on purpose. The spike measured ~1.15 s of fixed cost on a cold process start,
         * and that was not the slowest device anyone owns — a false positive here would drop a
         * working reader into raw text, which is far worse than waiting.
         */
        const val READY_TIMEOUT_MS = 10_000L
    }
}
