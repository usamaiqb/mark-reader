package com.markreader.ui.reader

import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.markreader.webview.RendererSettings
import com.markreader.webview.WebViewDocumentHost
import com.markreader.webview.WebViewHeading
import com.markreader.webview.WebViewToc

/**
 * The reader surface for [com.markreader.format.ReaderContent.Markdown]: the shared renderer,
 * in a WebView.
 *
 * Where [RenderedTextView] draws text the app has already styled, this hands source across to a
 * renderer that does the styling itself — the same one the desktop app runs. So there is no
 * Markwon here, no spans, and none of the split-mode machinery; there is a page, and a document
 * pushed into it.
 *
 * **Deliberately no chrome work.** The WebView goes exactly where the `ScrollView` was, with the
 * same constant top inset, and scroll position is reported out so the progress indicator and the
 * resume position keep working. Making the chrome *collapse* over it — nested scroll, which a
 * WebView makes materially harder than the native container that design was written for — is
 * planned separately.
 *
 * The host owns a WebView, which outlives its Activity if nobody says otherwise, so it is
 * destroyed on dispose and the document is dropped from the path space with it.
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
    modifier: Modifier = Modifier,
    /**
     * Chrome drawn over the reader. Pushed into the page as document padding rather than applied
     * here, for the same reason [RenderedTextView] puts it inside its `ScrollView`: the chrome
     * collapses on scroll, and an inset applied from out here would leave a dead band behind
     * when it goes.
     */
    contentTopInset: Dp = 0.dp,
    searchQuery: String = "",
    onSearchResult: (query: String, count: Int, index: Int) -> Unit = { _, _, _ -> },
    onSearchNavigationReady: (((Boolean) -> Unit)?) -> Unit = {},
    onTocChange: (WebViewToc) -> Unit = {},
    onHeadingNavigationReady: (((WebViewHeading) -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Plain fields rather than snapshot state, for the reason [ReaderContentController] gives at
    // length: this is written from a WebView callback and read from another, and a snapshot write
    // from either would invalidate the composition on every document that renders.
    val restore = remember { ScrollRestore() }
    val unavailable by rememberUpdatedState(onRendererUnavailable)
    val searchResult by rememberUpdatedState(onSearchResult)
    val navigationReady by rememberUpdatedState(onSearchNavigationReady)
    val tocChanged by rememberUpdatedState(onTocChange)
    val headingNavigationReady by rememberUpdatedState(onHeadingNavigationReady)
    val host = remember {
        WebViewDocumentHost(
            context,
            scope,
            onDocumentRendered = { restore.run() },
            onRendererUnavailable = { unavailable(it) },
        )
    }
    restore.webView = host.webView
    restore.target = { savedScrollY.value }

    DisposableEffect(host) {
        navigationReady(host.findNext)
        host.toc.onChanged = { tocChanged(it) }
        headingNavigationReady(host.toc::reveal)
        onDispose {
            host.toc.onChanged = {}
            headingNavigationReady(null)
            tocChanged(WebViewToc())
            navigationReady(null)
            host.destroy()
        }
    }

    // Adopting is what makes a virtual path resolvable, so it happens before the document is
    // pushed — and is undone when the reader leaves, or a closed document stays reachable.
    val virtualPath = remember(host, documentUri, fileName) {
        host.adoptDocument(Uri.parse(documentUri), fileName)
    }
    DisposableEffect(host, virtualPath) {
        onDispose { host.forget(virtualPath) }
    }

    LaunchedEffect(host, virtualPath, source) {
        restore.arm()
        host.showDocument(virtualPath, source)
    }

    LaunchedEffect(host, searchQuery) {
        host.setSearchQuery(searchQuery) { query, count, index -> searchResult(query, count, index) }
    }

    LaunchedEffect(host, settings) {
        host.applySettings(settings)
    }

    LaunchedEffect(host, contentTopInset) {
        host.applyContentInset(contentTopInset.value)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                host.webView.apply {
                    // The host is remembered across a recomposition that rebuilds this
                    // AndroidView, and a View that still has a parent cannot be added to another.
                    (parent as? ViewGroup)?.removeView(this)
                    // Otherwise the reader's own background shows through until first paint,
                    // which flashes white in a dark theme.
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        onScroll(scrollY, maxScrollY())
                    }
                }
            }
        )
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

/**
 * How far this document can scroll.
 *
 * `computeVerticalScrollRange` is protected, so the content height comes from the public
 * `contentHeight`, which is CSS pixels at scale 1 — the only scale there is, since the host
 * disables zoom.
 */
private fun WebView.maxScrollY(): Int {
    val contentPx = (contentHeight * resources.displayMetrics.density).toInt()
    return (contentPx - height).coerceAtLeast(0)
}
