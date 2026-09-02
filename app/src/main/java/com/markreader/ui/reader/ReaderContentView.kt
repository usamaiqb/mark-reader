package com.markreader.ui.reader

import android.content.Context
import android.graphics.Rect
import android.text.Spanned
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.format.ReaderContent

/**
 * A [ScrollView] that never scrolls itself to keep a focused descendant on screen.
 *
 * The text is selectable, and `setTextIsSelectable(true)` makes it focusable.
 * Android's ScrollView reacts to a focused descendant in two ways, and both
 * fight the user here:
 *
 *  - `requestChildFocus()` scrolls the focused child into view.
 *  - `onSizeChanged()` re-scrolls to keep it on screen whenever the viewport
 *    height changes — and the chrome show/hide animation changes that height on
 *    every frame.
 *
 * The second one is the damaging one: the automatic `doScrollY` lands in the
 * middle of the user's drag, and the scroll deltas it emits feed straight back
 * into the chrome's show/hide threshold, so the bar re-hides itself as it is
 * appearing.
 *
 * Both paths route through [computeScrollDeltaToGetChildRectOnScreen], but so
 * does `requestChildRectangleOnScreen` — which is what scrolls when a selection
 * handle is dragged past the edge of the viewport, and is worth keeping. So the
 * suppression is scoped to the two callers that misbehave rather than applied to
 * the method outright.
 */
private class FocusStableScrollView(context: Context) : ScrollView(context) {
    private var suppressFocusScroll = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        suppressFocusScroll = true
        try {
            super.onSizeChanged(w, h, oldw, oldh)
        } finally {
            suppressFocusScroll = false
        }
    }

    override fun requestChildFocus(child: android.view.View?, focused: android.view.View?) {
        suppressFocusScroll = true
        try {
            super.requestChildFocus(child, focused)
        } finally {
            suppressFocusScroll = false
        }
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int =
        if (suppressFocusScroll) 0 else super.computeScrollDeltaToGetChildRectOnScreen(rect)
}

/**
 * The app's own text surface: one selectable [TextView] in a [ScrollView].
 *
 * **What draws raw mode, and nothing else now.** It used to have a second structure — a container
 * of per-segment views, so that code blocks and tables got their own horizontally scrollable
 * panes — and that structure was driven by marker spans the Markwon renderer left behind. Both
 * Markdown surfaces render in the page today, no producer of those markers is left, and the split
 * container went with them. What arrives here is a document's source: unstyled, or carrying the
 * search highlights the view model painted onto it.
 */
@Composable
fun RenderedTextView(
    content: ReaderContent,
    textColor: Int,
    padding: PaddingValues,
    /**
     * Height of the chrome the reader draws underneath, added to the content's own top
     * padding *inside* the ScrollView. Kept out of [padding] deliberately: [padding] insets
     * the viewport, which resizes the scroll container and moves `maxScrollY`, and that is
     * the feedback loop this avoids.
     */
    contentTopInset: Dp = 0.dp,
    // Held as State, not Int: the live scroll position changes every frame while
    // scrolling, and taking it as a value would recompose this composable — and
    // re-run the AndroidView update block — on every one of those frames. It is
    // only ever read to restore a position, so it is read without snapshot
    // observation below.
    savedScrollY: State<Int>,
    scrollToOffset: Int?,
    onScrollChanged: (scrollY: Int, maxScrollY: Int) -> Unit,
    onScrollExtentChanged: (maxScrollY: Int) -> Unit,
    onScrollConsumed: () -> Unit,
    selectionHighlightColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    isMonospaced: Boolean = false,
    textAlignment: TextAlignmentPreference
) {
    // This view draws text and nothing else. Callers route the other two elsewhere —
    // [ReaderContent.Markdown] to [WebViewReader], and [ReaderContent.Table] to a native table
    // once one is written — so neither arm should be reachable. They are spelled out rather than
    // hidden behind an `else` so that a fourth shape still cannot be added without deciding what
    // happens here, and they draw nothing rather than throwing: a mis-routed document is a blank
    // reader, not a crashed one.
    val text: CharSequence = when (content) {
        is ReaderContent.Spans -> content.text
        is ReaderContent.Markdown, is ReaderContent.Table -> ""
    }
    val contentKey = remember(text, fontSizeSp, lineHeight, readingFont, codeFont, isMonospaced, textAlignment) {
        ContentKey(
            textHash = text.hashCode(),
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            readingFont = readingFont,
            codeFont = codeFont,
            isMonospaced = isMonospaced,
            textAlignment = textAlignment
        )
    }
    // Search highlights arrive as spans over a string that has not changed, so styled and
    // unstyled text are applied by different tests below. Narrowed here, at the one place that
    // difference matters.
    val spanned = text as? Spanned
    // Reconciler state for the update block below. Plain fields, so a write does
    // not invalidate the composition and is visible to the rest of the same pass;
    // see [ReaderContentController].
    val controller = remember {
        ReaderContentController(
            textColor = textColor,
            selectionHighlightColor = selectionHighlightColor
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.TopCenter
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val paddingPx = (16 * density).toInt()

                val scrollView = FocusStableScrollView(context).apply {
                    val textView = SearchHighlightTextView(context).apply {
                        textSize = fontSizeSp
                        setLineSpacing(0f, lineHeight)
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        setTextIsSelectable(true)
                        setTextColor(textColor)
                        highlightColor = selectionHighlightColor
                    }
                    addView(
                        textView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        onScrollChanged(scrollY, computeMaxScrollY(this))
                    }
                    // Report scroll extent whenever content lays out so reading
                    // progress is available before the first scroll event. This
                    // reports the extent only — never a position. It fires on every
                    // window-wide layout, including every frame of the chrome's
                    // show/hide animation, and reporting a position from here would
                    // feed those frames into the chrome's scroll-delta threshold.
                    //
                    // Held in the controller and detached in `onRelease` below: a
                    // global-layout listener is registered against the whole window's
                    // ViewTreeObserver, so an anonymous one keeps this ScrollView —
                    // and the document attached to it — reachable for as long as the
                    // window lives, across every document opened after it.
                    val extentListener = ViewTreeObserver.OnGlobalLayoutListener {
                        val extent = computeMaxScrollY(this)
                        if (extent != controller.lastReportedExtent) {
                            controller.lastReportedExtent = extent
                            onScrollExtentChanged(extent)
                        }
                    }
                    controller.extentListener = extentListener
                    viewTreeObserver.addOnGlobalLayoutListener(extentListener)
                }
                scrollView
            },
            update = { scrollView ->
                val textView = scrollView.getChildAt(0) as? TextView ?: return@AndroidView
                val density = scrollView.context.resources.displayMetrics.density
                val paddingPx = (16 * density).toInt()

                // The chrome overlays the reader, so its height belongs to the content
                // rather than to the viewport. Putting it in `paddingTop` keeps the
                // viewport a constant size — `maxScrollY` never moves, Android never
                // clamps `scrollY`, and the text scrolls up into the space the bar leaves
                // when it collapses.
                val contentTopPx = paddingPx + (contentTopInset.value * density).toInt()
                if (textView.paddingTop != contentTopPx) {
                    textView.setPadding(
                        textView.paddingLeft,
                        contentTopPx,
                        textView.paddingRight,
                        textView.paddingBottom
                    )
                }

                if (spanned != null) {
                    // Spans can change while the string does not — a search highlight
                    // re-render is exactly that — so identity is the only safe test.
                    if (text !== controller.lastTextRef) {
                        textView.text = text
                        controller.lastTextHash = text.toString().hashCode()
                        controller.lastTextRef = text
                    }
                } else if (text !== controller.lastTextRef) {
                    // Nothing but characters, so equal content is the same render — and
                    // no Spannable is allocated to draw it.
                    val textHash = text.hashCode()
                    if (textHash != controller.lastTextHash) {
                        textView.text = text.toString()
                        controller.lastTextHash = textHash
                    }
                    controller.lastTextRef = text
                }
                if (controller.lastStyleKey != contentKey) {
                    applyStyleToTextView(
                        textView, fontSizeSp, lineHeight, readingFont, codeFont, isMonospaced,
                        textAlignment, textColor, selectionHighlightColor
                    )
                    controller.lastStyleKey = contentKey
                }
                if (controller.lastTextColor != textColor) {
                    textView.setTextColor(textColor)
                    controller.lastTextColor = textColor
                }
                if (controller.lastSelectionHighlightColor != selectionHighlightColor) {
                    textView.highlightColor = selectionHighlightColor
                    controller.lastSelectionHighlightColor = selectionHighlightColor
                }

                // Scroll handling. Each branch claims the key synchronously, before
                // posting, so that a later pass through this block cannot queue a
                // second, conflicting restore behind the one in flight.
                if (scrollToOffset == null && controller.lastRestoredKey != contentKey) {
                    // Restore-only read: observing it here would resubscribe this
                    // block to a value that changes on every scroll frame.
                    val restoreY = Snapshot.withoutReadObservation { savedScrollY.value }
                    if (restoreY > 0) {
                        controller.lastRestoredKey = contentKey
                        scrollView.post { scrollView.scrollTo(0, restoreY) }
                    }
                }

                if (scrollToOffset != null) {
                    controller.lastRestoredKey = contentKey
                    scrollView.post {
                        val y = resolveScrollY(scrollView, scrollToOffset)
                        if (y != null) {
                            val centeredY = (y - scrollView.height / 3).coerceAtLeast(0)
                            scrollView.smoothScrollTo(0, centeredY)
                        }
                        // Consumed either way, so an unresolvable target does not
                        // leave the request pending forever.
                        onScrollConsumed()
                    }
                }
            },
            onRelease = { scrollView ->
                controller.extentListener?.let { listener ->
                    scrollView.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
                    controller.extentListener = null
                }
            }
        )
    }
}
