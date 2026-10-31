package com.markreader.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * One [WebViewDocumentHost], held for as long as the Activity that owns it.
 *
 * Before this, every document open built a WebView and destroyed it on the way out — so opening a
 * document from the file list cost the same fixed page-load as a cold start, and nothing was ever
 * reused. Measured on an emulator: ~140 ms of it in a warm process, ~280 ms in a cold one, on
 * every open. Held, the second document onwards pays neither.
 *
 * **Activity-scoped, deliberately, and not the application.** A WebView built with the application
 * context is the version of this that leaks nothing and breaks text selection: the selection
 * `ActionMode` and its menu resolve against the context the view was built with, and selection is
 * the criterion the rendering decision was allowed to fail on. Tying it to the Activity keeps a
 * real themed context, keeps the lifetime honest — [destroy] in `onDestroy`, one WebView per
 * Activity — and still covers every reuse there is to have, because the app is a single Activity
 * and navigating between documents never leaves it.
 *
 * A configuration change therefore builds a new one. That is correct rather than unfortunate: the
 * old Activity's WebView holds the old resources, and reusing it across a theme change is how a
 * reader ends up rendering in yesterday's palette.
 *
 * ### Why the warm-up is posted rather than eager
 *
 * Constructing the first WebView in a process loads the Chromium provider — 215 ms of main-thread
 * work on the emulator, 9 ms once warm. Doing that *in* `onCreate` would spend it before the first
 * frame and delay the Home screen for everyone who opened the app to read their file list rather
 * than a document. [warmUp] posts instead, so the provider loads in the gap after the first frame,
 * while the reader is still deciding what to draw and the file is still being read.
 *
 * A reader that gets there first is not made to wait for the post: [acquire] builds the host on
 * the spot. The warm-up is a head start, never a precondition.
 */
class ReaderHostHolder(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    /**
     * Bumped when the held host is replaced, so a composition reading it re-acquires.
     *
     * Snapshot state because it is read from Compose, and it only ever changes on the death of a
     * render process — which is rare, and is the one case where a held WebView is worse than a
     * per-document one unless something notices.
     */
    var generation by mutableIntStateOf(0)
        private set

    private var host: WebViewDocumentHost? = null
    private var destroyed = false

    /** The held host, built now if the warm-up has not got to it yet. */
    fun acquire(): WebViewDocumentHost = host ?: create()

    /**
     * Starts the page loading before anything asks for it.
     *
     * Safe to call more than once and safe to never call — it is an optimisation, and [acquire] is
     * the contract.
     */
    fun warmUp() {
        if (destroyed || host != null) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            if (!destroyed && host == null) {
                create()
            }
        }
    }

    /** Releases the WebView. The owner's `onDestroy`, and nowhere else. */
    fun destroy() {
        destroyed = true
        host?.destroy()
        host = null
    }

    private fun create(): WebViewDocumentHost {
        val created = WebViewDocumentHost(context, scope)
        created.onDied = {
            // The host destroyed itself on the way in here. Drop it and count a generation, so the
            // reader on screen re-acquires and gets a live page instead of a dead one.
            if (host === created) {
                host = null
                generation++
            }
        }
        host = created
        return created
    }
}

/**
 * The held host, for the reader to find.
 *
 * Null where nothing provided one — a preview, a test, a screen composed outside the Activity's
 * content. `WebViewReader` builds and owns a host of its own in that case, so the composable stays
 * usable on its own; it just does not get the reuse.
 *
 * A local rather than a parameter, which is the exception detekt is warning about below and is
 * deliberate: this is one Activity-scoped service with one leaf consumer, three navigation layers
 * down. Threading it through `MarkReaderApp`, the nav graph and `ViewerScreen` would put a WebView
 * in the signature of three things that have no opinion about one, and the null default is what
 * keeps `WebViewReader` composable on its own. The same shape as `LocalContext`, for the same
 * reason.
 */
@Suppress("CompositionLocalAllowlist")
val LocalReaderHostHolder = staticCompositionLocalOf<ReaderHostHolder?> { null }
