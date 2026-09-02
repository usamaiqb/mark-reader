package com.markreader.ui.reader

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.markreader.webview.HeadingItem
import com.markreader.webview.PageCommand
import com.markreader.webview.ReaderEvents
import com.markreader.webview.RendererSettings
import com.markreader.webview.rememberDocumentHost
import kotlinx.coroutines.flow.Flow

/**
 * The reader surface for [com.markreader.format.ReaderContent.Markdown]: the shared renderer,
 * in a WebView.
 *
 * Where [RenderedTextView] draws a document's source as text, this hands that source across to a
 * renderer that styles it — the same one the desktop app runs. So there are no spans here and
 * nothing to reconcile; there is a page, and a document pushed into it.
 *
 * **Deliberately no chrome work.** The WebView goes exactly where the `ScrollView` was, with the
 * same constant top inset, and scroll position is reported out so the progress indicator and the
 * resume position keep working. Making the chrome *collapse* over it — nested scroll, which a
 * WebView makes materially harder than the native container that design was written for — is
 * planned separately.
 *
 * Search and the table of contents cross here in both directions, because both belong to the
 * rendering and the rendering is in the page: requests go in through [commands], and the page
 * reports its outline, the heading being read and its match count back out. Compose draws all
 * three — the WebView draws no chrome of its own.
 *
 * The page underneath is usually the Activity's, shared with the editor's preview pane — see
 * [rememberDocumentHost]. What this reader owns either way is its *document*: adopted on the way
 * in, dropped from the path space on the way out.
 */
@Composable
fun WebViewReader(
    /** Markdown source. The page renders it; nothing on this side does. */
    source: String,
    /** The document's own URI, adopted into the host's path space so relative images resolve. */
    documentUri: String,
    fileName: String,
    settings: RendererSettings,
    padding: PaddingValues,
    savedScrollY: State<Int>,
    onScroll: (scrollY: Int, maxScrollY: Int) -> Unit,
    /**
     * This WebView cannot run the renderer, so the caller should draw the document some other
     * way. Carries the WebView version when known.
     */
    onRendererUnavailable: (webViewVersion: String?) -> Unit,
    /**
     * Search and outline requests, from the chrome that draws them.
     *
     * A flow rather than parameters because these are requests, not state: "next match" has no
     * value to hold. Collected for as long as this reader is composed, which is also as long as
     * the page it drives exists.
     */
    commands: Flow<PageCommand>,
    /** The document's outline, as the page rendered it. */
    onOutlineChange: (List<HeadingItem>) -> Unit,
    /** The heading being read, by slug, or null in a document with no headings. */
    onActiveHeadingChange: (slug: String?) -> Unit,
    /** Which match the page's find is on, and how many there are. An index of -1 means none. */
    onFindStateChange: (index: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Chrome drawn over the reader. Pushed into the page as document padding rather than applied
     * here, for the same reason [RenderedTextView] puts it inside its `ScrollView`: the chrome
     * collapses on scroll, and an inset applied from out here would leave a dead band behind
     * when it goes.
     */
    contentTopInset: Dp = 0.dp,
) {
    // Plain fields rather than snapshot state, for the reason [ReaderContentController] gives at
    // length: this is written from a WebView callback and read from another, and a snapshot write
    // from either would invalidate the composition on every document that renders.
    val restore = remember { ScrollRestore() }
    // The host outlives this composition, so it would otherwise hold whichever callbacks the
    // first composition happened to pass.
    val unavailable by rememberUpdatedState(onRendererUnavailable)
    val outlineChanged by rememberUpdatedState(onOutlineChange)
    val activeHeadingChanged by rememberUpdatedState(onActiveHeadingChange)
    val findStateChanged by rememberUpdatedState(onFindStateChange)

    // Attached rather than constructed with. A held host is shared with every document that comes
    // after this one, so what this reader hands over has to come back when it leaves: a report
    // arriving late would otherwise reach a disposed composition, and through it a ViewModel that
    // has moved on to another document. [rememberDocumentHost] guards the way out.
    val host = rememberDocumentHost {
        ReaderEvents(
            onDocumentRendered = { restore.run() },
            onRendererUnavailable = { unavailable(it) },
            onOutlineChanged = { outlineChanged(it) },
            onActiveHeadingChanged = { activeHeadingChanged(it) },
            onFindStateChanged = { index, total -> findStateChanged(index, total) },
        )
    }

    restore.webView = host.webView
    restore.target = { savedScrollY.value }

    // Adopting is what makes a virtual path resolvable, so it happens before the document is
    // pushed — and is undone when the reader leaves, or a closed document stays reachable.
    val virtualPath = remember(host, documentUri, fileName) {
        host.paths.adoptDocument(Uri.parse(documentUri), fileName)
    }
    DisposableEffect(host, virtualPath) {
        onDispose { host.paths.forget(virtualPath) }
    }

    LaunchedEffect(host, virtualPath, source) {
        restore.arm()
        host.showDocument(virtualPath, source)
    }

    LaunchedEffect(host, settings) {
        host.applySettings(settings)
    }

    LaunchedEffect(host, contentTopInset) {
        host.applyContentInset(contentTopInset.value)
    }

    LaunchedEffect(host, commands) {
        commands.collect { command -> host.send(command) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        DocumentPageView(host = host, onScroll = onScroll)
    }
}

/**
 * A scroll position waiting for a document to exist.
 *
 * Restoring before the page reports pixels scrolls a document of zero height, which lands at the
 * top and reads as the feature simply not working — so the request is armed when a document is
 * pushed and spent on the render that follows, once.
 */
private class ScrollRestore {
    var webView: WebView? = null
    var target: () -> Int = { 0 }
    private var armed = false

    fun arm() {
        armed = true
    }

    fun run() {
        if (!armed) return
        armed = false
        val view = webView ?: return
        val y = target()
        if (y > 0) {
            view.post { view.scrollTo(0, y) }
        }
    }
}
