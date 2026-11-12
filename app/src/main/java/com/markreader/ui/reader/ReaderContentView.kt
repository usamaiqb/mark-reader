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

/** The reader's own padding around the text, in dp, on all four sides. */
private const val READER_PADDING_DP = 16

/**
 * Where in the viewport a revealed target lands: one this-many-th of the way down rather than
 * flush against the top edge, so the line before it stays on screen as context.
 */
private const val REVEAL_VIEWPORT_DIVISOR = 3

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
    // Held as State, not Int: the live scroll position changes every frame while
    // scrolling, and taking it as a value would recompose this composable — and
    // re-run the AndroidView update block — on every one of those frames. It is
    // only ever read to restore a position, so it is read without snapshot
    // observation below.
    savedScrollY: State<Int>,
    scrollToOffset: Int?,
    onScrollChange: (scrollY: Int, maxScrollY: Int) -> Unit,
    onScrollExtentChange: (maxScrollY: Int) -> Unit,
    onClearPendingScroll: () -> Unit,
    selectionHighlightColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    textAlignment: TextAlignmentPreference,
    modifier: Modifier = Modifier,
    /**
     * Height of the chrome the reader draws underneath, added to the content's own top
     * padding *inside* the ScrollView. Kept out of [padding] deliberately: [padding] insets
     * the viewport, which resizes the scroll container and moves `maxScrollY`, and that is
     * the feedback loop this avoids.
     */
    contentTopInset: Dp = 0.dp,
    codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    isMonospaced: Boolean = false
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
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.TopCenter
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            factory = { context ->
                buildReaderScrollView(
                    context = context,
                    controller = controller,
                    fontSizeSp = fontSizeSp,
                    lineHeight = lineHeight,
                    textColor = textColor,
                    selectionHighlightColor = selectionHighlightColor,
                    onScrollChange = onScrollChange,
                    onScrollExtentChange = onScrollExtentChange
                )
            },
            // Four steps, each its own function below, and each deciding for itself whether it
            // has anything to do. This pass runs on every recomposition, so "nothing changed" is
            // the common case and all four have to be cheap in it.
            update = { scrollView ->
                val textView = scrollView.getChildAt(0) as? TextView ?: return@AndroidView
                applyTopInset(textView, contentTopInset)
                controller.applyText(textView, text, spanned)
                controller.applyStyle(textView, contentKey, textColor, selectionHighlightColor)
                controller.applyPendingScroll(
                    scrollView = scrollView,
                    contentKey = contentKey,
                    savedScrollY = savedScrollY,
                    scrollToOffset = scrollToOffset,
                    onClearPendingScroll = onClearPendingScroll
                )
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

/** The reader's view tree: one selectable [TextView] in a scroll container that reports itself. */
private fun buildReaderScrollView(
    context: Context,
    controller: ReaderContentController,
    fontSizeSp: Float,
    lineHeight: Float,
    textColor: Int,
    selectionHighlightColor: Int,
    onScrollChange: (scrollY: Int, maxScrollY: Int) -> Unit,
    onScrollExtentChange: (maxScrollY: Int) -> Unit
): ScrollView {
    val paddingPx = (READER_PADDING_DP * context.resources.displayMetrics.density).toInt()
    return FocusStableScrollView(context).apply {
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
            onScrollChange(scrollY, computeMaxScrollY(this))
        }
        // Report scroll extent whenever content lays out so reading
        // progress is available before the first scroll event. This
        // reports the extent only — never a position. It fires on every
        // window-wide layout, including every frame of the chrome's
        // show/hide animation, and reporting a position from here would
        // feed those frames into the chrome's scroll-delta threshold.
        //
        // Held in the controller and detached in the caller's `onRelease`: a
        // global-layout listener is registered against the whole window's
        // ViewTreeObserver, so an anonymous one keeps this ScrollView —
        // and the document attached to it — reachable for as long as the
        // window lives, across every document opened after it.
        val extentListener = ViewTreeObserver.OnGlobalLayoutListener {
            val extent = computeMaxScrollY(this)
            if (extent != controller.lastReportedExtent) {
                controller.lastReportedExtent = extent
                onScrollExtentChange(extent)
            }
        }
        controller.extentListener = extentListener
        viewTreeObserver.addOnGlobalLayoutListener(extentListener)
    }
}

/**
 * Puts the chrome's height into the text's top padding, if it has moved.
 *
 * The chrome overlays the reader, so its height belongs to the content rather than to the
 * viewport. Padding keeps the viewport a constant size — `maxScrollY` never moves, Android never
 * clamps `scrollY`, and the text scrolls up into the space the bar leaves when it collapses.
 */
private fun applyTopInset(textView: TextView, contentTopInset: Dp) {
    val density = textView.context.resources.displayMetrics.density
    val contentTopPx =
        (READER_PADDING_DP * density).toInt() + (contentTopInset.value * density).toInt()
    if (textView.paddingTop != contentTopPx) {
        textView.setPadding(
            textView.paddingLeft,
            contentTopPx,
            textView.paddingRight,
            textView.paddingBottom
        )
    }
}

/**
 * Applies [text], if it is not already on screen.
 *
 * The cheap test first: the very instance already applied is already on screen. Anything else is
 * [ReaderContentController.needsText]'s to decide, because the characters are only half of what
 * can change — the spans over them are the other half, and a search highlight changes nothing
 * else.
 */
private fun ReaderContentController.applyText(
    textView: TextView,
    text: CharSequence,
    spanned: Spanned?
) {
    if (text === lastTextRef) return
    val characters = text.toString()
    val hash = characters.hashCode()
    if (needsText(isStyled = spanned != null, contentHash = hash)) {
        // Unstyled text goes in as a String: no Spannable is allocated to draw
        // text that has no styling.
        textView.text = spanned ?: characters
        tookText(isStyled = spanned != null, contentHash = hash)
    }
    lastTextRef = text
}

/**
 * Applies the typography and the two colours, each only if it has moved.
 *
 * The typography is read back off [contentKey] rather than passed alongside it: the key *is* the
 * set of values that decide the styling, so the comparison and what gets applied cannot disagree.
 */
private fun ReaderContentController.applyStyle(
    textView: TextView,
    contentKey: ContentKey,
    textColor: Int,
    selectionHighlightColor: Int
) {
    if (lastStyleKey != contentKey) {
        applyStyleToTextView(
            textView,
            contentKey.fontSizeSp,
            contentKey.lineHeight,
            contentKey.readingFont,
            contentKey.codeFont,
            contentKey.isMonospaced,
            contentKey.textAlignment,
            textColor,
            selectionHighlightColor
        )
        lastStyleKey = contentKey
    }
    if (lastTextColor != textColor) {
        textView.setTextColor(textColor)
        lastTextColor = textColor
    }
    if (lastSelectionHighlightColor != selectionHighlightColor) {
        textView.highlightColor = selectionHighlightColor
        lastSelectionHighlightColor = selectionHighlightColor
    }
}

/**
 * Either restores the saved reading position or applies a requested one.
 *
 * Each branch claims the key synchronously, before posting, so that a later pass through the
 * update block cannot queue a second, conflicting restore behind the one in flight.
 */
private fun ReaderContentController.applyPendingScroll(
    scrollView: ScrollView,
    contentKey: ContentKey,
    savedScrollY: State<Int>,
    scrollToOffset: Int?,
    onClearPendingScroll: () -> Unit
) {
    if (scrollToOffset == null) {
        if (lastRestoredKey == contentKey) return
        // Restore-only read: observing it from the update block would resubscribe
        // that block to a value that changes on every scroll frame.
        val restoreY = Snapshot.withoutReadObservation { savedScrollY.value }
        if (restoreY > 0) {
            lastRestoredKey = contentKey
            scrollView.post { scrollView.scrollTo(0, restoreY) }
        }
        return
    }
    lastRestoredKey = contentKey
    scrollView.post {
        val y = resolveScrollY(scrollView, scrollToOffset)
        if (y != null) {
            val centeredY = (y - scrollView.height / REVEAL_VIEWPORT_DIVISOR).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, centeredY)
        }
        // Consumed either way, so an unresolvable target does not
        // leave the request pending forever.
        onClearPendingScroll()
    }
}
