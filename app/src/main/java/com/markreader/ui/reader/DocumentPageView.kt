package com.markreader.ui.reader

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.markreader.webview.WebViewDocumentHost

/**
 * The host's page, placed in whichever surface is drawing it — the reader, or the editor's
 * preview pane.
 *
 * One WebView is shared between them, so this is where a surface *claims* it: on the way in it is
 * taken off whichever surface had it, given a transparent background, and pointed at this
 * surface's scroll listener. All of that happens in the factory, which runs once per surface,
 * and **deliberately not in `update`**: moving between surfaces animates, so both are composed
 * for a few frames and either may recompose after the other has claimed the page. A claim in
 * `update` would let the surface on its way out take it back.
 *
 * ### Why the WebView goes inside a container instead of being handed to `AndroidView` directly
 *
 * Not ceremony — a container is what stops the outgoing surface resizing the page under the
 * incoming one. Compose's `AndroidViewHolder` keeps a direct reference to the view it was given
 * and lays it out on every pass, `view.layout(0, 0, width, height)`, without checking that the
 * view is still its child. During the same animation, then, the outgoing holder lays the shared
 * WebView out at *its* height after the incoming one has already sized it — and nothing measures
 * it again, so the WebView keeps the previous surface's height.
 *
 * That is invisible between two documents, because two readers are the same size. It is very
 * visible coming back from the editor's preview, which sits below a tab row: the reader was left
 * a tab row's worth of dead space at the bottom of the document. Giving each surface a container
 * of its own means each holder lays out something it exclusively owns, and the empty one it is
 * left holding costs nothing.
 */
@Composable
internal fun DocumentPageView(
    host: WebViewDocumentHost,
    modifier: Modifier = Modifier,
    /**
     * Where this surface wants scroll positions. Defaulted, and the default is not "no listener"
     * but a listener that does nothing — which matters, because the listener belongs to the
     * *WebView* rather than to the surface. A pane that installed none would leave the previous
     * surface's in place, and the editor's preview would report its own scrolling into the
     * reader's view model as if the reader were being read.
     */
    onScroll: (scrollY: Int, maxScrollY: Int) -> Unit = { _, _ -> },
) {
    val scrolled by rememberUpdatedState(onScroll)
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val page = host.webView
                // A View that still has a parent cannot be added to another.
                (page.parent as? ViewGroup)?.removeView(page)
                // Otherwise the surface's own background shows through until first paint, which
                // flashes white in a dark theme.
                page.setBackgroundColor(Color.TRANSPARENT)
                page.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    scrolled(scrollY, page.maxScrollY())
                }
                addView(
                    page,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    )
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
