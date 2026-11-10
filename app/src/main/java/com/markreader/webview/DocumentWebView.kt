package com.markreader.webview

import android.content.Context
import android.webkit.WebView

/**
 * The page, with its scroll geometry readable.
 *
 * A [WebView] keeps the numbers that describe its own scrolling behind `protected`, so anything
 * outside has to approximate them. This exists to stop approximating one of them.
 *
 * **It deliberately does not take over scrolling.** An earlier version of the reader did — Compose
 * captured the drag and drove the page with `scrollBy`, which is `readium/kotlin-toolkit`'s
 * approach and makes the reader a first-class nested-scroll participant. It does not survive
 * contact with a Markdown document. Readium can own the gesture because it owns *both* axes; this
 * reader cannot, because the horizontal scrollers are wide tables and code blocks **inside** the
 * document, which Compose has no way to see. `Modifier.scrollable` measures touch slop on its own
 * axis alone, so a swipe measured at 91% horizontal was claimed as a vertical scroll the moment its
 * wobble crossed twenty pixels, and the table under the finger did not move. There is no parameter
 * that weighs the other axis.
 */
internal class DocumentWebView(context: Context) : WebView(context) {

    /**
     * How far this document can scroll.
     *
     * The real number, out of the two `protected` methods a [WebView] computes it from. It was
     * approximated as `contentHeight * density`, which is a CSS-pixel content height scaled by hand
     * and drifts from what the view will actually let you scroll — visible as a reading percentage
     * that never quite reaches 100.
     */
    val maxScrollY: Int
        get() = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
}
