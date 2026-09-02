package com.markreader.ui.reader

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.markreader.webview.ReaderEvents
import com.markreader.webview.RendererSettings
import com.markreader.webview.rememberDocumentHost

/**
 * The editor's preview pane: the document being written, drawn by the shared renderer.
 *
 * This is the end of the app's second Markdown implementation. The preview was a Markwon
 * `TextView` for as long as the reader was one, and it stayed behind when the reader moved into
 * the page — so "preview" and "read" showed the same file two different ways, from two parsers,
 * with two sets of bugs. There is one now.
 *
 * **The page is the reader's**, held for the Activity's life. The editor opens on its Edit tab, so
 * the WebView is not pulled out of the reader by navigating here — only by asking for a preview,
 * which is also the moment the reader stops being on screen. Sharing it costs a `showDocument`
 * each way and saves building a second WebView, which is the whole of what holding one bought.
 *
 * One piece of that sharing is this pane's to put back, because it is state on the page rather
 * than an argument to a render: the **content inset**, which the reader sets to the height of its
 * collapsing chrome and the editor draws nothing over. The callbacks are
 * [com.markreader.webview.rememberDocumentHost]'s to hand back, and the view itself is
 * [DocumentPageView]'s — the pane is shorter than the reader, which is what made the sharing
 * visible.
 *
 * What it deliberately does *not* do is the reader's other half: no scroll reporting, no restore,
 * no outline, no find. A preview is a look at what is being typed.
 */
@Composable
fun WebViewPreview(
    /** Markdown source, straight from the editor's buffer. */
    source: String,
    /**
     * The document being edited, when it has been saved at all. Adopted so that relative images
     * beside it resolve — which the Markwon preview could never do. Null for a new document, whose
     * references have nothing to be relative to yet.
     */
    documentUri: String?,
    fileName: String,
    settings: RendererSettings,
    /** This WebView cannot run the renderer, so there is no preview to draw. */
    onRendererUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The host outlives this composition, so it would otherwise hold the callback the first one
    // happened to pass.
    val unavailable by rememberUpdatedState(onRendererUnavailable)
    val host = rememberDocumentHost {
        ReaderEvents(onRendererUnavailable = { unavailable() })
    }

    val virtualPath = remember(host, documentUri, fileName) {
        host.adoptDocument(documentUri?.let(Uri::parse), fileName)
    }
    DisposableEffect(host, virtualPath) {
        onDispose { host.forget(virtualPath) }
    }

    // `preserveScroll` is deliberately left false: the preview has never restored a position, and
    // the page it is pushed into may have been showing a document scrolled somewhere else.
    LaunchedEffect(host, virtualPath, source) {
        host.showDocument(virtualPath, source)
    }

    LaunchedEffect(host, settings) {
        host.applySettings(settings)
    }

    // The reader pushes the height of its collapsing chrome into the page. Nothing is drawn over
    // the preview, and the two share a page, so a pane that did not put this back would open with
    // a band of empty space at the top of the document.
    LaunchedEffect(host) {
        host.applyContentInset(0f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        DocumentPageView(host = host)
    }
}
