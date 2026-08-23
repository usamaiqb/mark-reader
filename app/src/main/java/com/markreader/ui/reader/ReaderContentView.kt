package com.markreader.ui.reader

import android.content.Context
import android.graphics.Rect
import android.text.Spanned
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.ui.screens.HeadingItem
import com.markreader.ui.zoom.ZoomableContentLayout

/**
 * A [ScrollView] that never scrolls itself to keep a focused descendant on screen.
 *
 * Code blocks are selectable TextViews, and `setTextIsSelectable(true)` makes them
 * focusable. Android's ScrollView reacts to a focused descendant in two ways, and
 * both fight the user here:
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

@Composable
fun RenderedTextView(
    text: Any,
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
    headings: List<HeadingItem>,
    onActiveHeadingChanged: (Int) -> Unit,
    isWordWrapEnabled: Boolean,
    isCodeBlockWrapEnabled: Boolean,
    selectionHighlightColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    isSourceCode: Boolean = false,
    textAlignment: TextAlignmentPreference,
    codeBlockBackgroundColor: Int
) {
    val contentKey = remember(text, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode, textAlignment) {
        ContentKey(
            textHash = text.hashCode(),
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            readingFont = readingFont,
            codeFont = codeFont,
            isSourceCode = isSourceCode,
            textAlignment = textAlignment
        )
    }
    // Span scans over the whole document — cached per text, never per frame.
    val docHasCodeBlocks = remember(text) { text is Spanned && hasCodeBlocks(text) }
    val docHasTables = remember(text) { text is Spanned && hasTables(text) }
    // Split mode gives code blocks and tables their own horizontally scrollable
    // views. Only worth the cost when the document actually contains one.
    val isSplitMode = remember(
        text, isWordWrapEnabled, isCodeBlockWrapEnabled, docHasCodeBlocks, docHasTables
    ) {
        text is Spanned && (
            (isWordWrapEnabled && ((!isCodeBlockWrapEnabled && docHasCodeBlocks) || docHasTables)) ||
                (!isWordWrapEnabled && docHasCodeBlocks)
            )
    }
    val restoreKey = remember(contentKey, isWordWrapEnabled, isCodeBlockWrapEnabled) {
        RestoreKey(contentKey, isWordWrapEnabled, isCodeBlockWrapEnabled)
    }
    // Reconciler state for the update block below. Plain fields, so a write does
    // not invalidate the composition and is visible to the rest of the same pass;
    // see [ReaderContentController].
    val controller = remember {
        ReaderContentController(
            isWordWrapEnabled = isWordWrapEnabled,
            isCodeBlockWrapEnabled = isCodeBlockWrapEnabled,
            textColor = textColor,
            selectionHighlightColor = selectionHighlightColor,
            codeBlockBackgroundColor = codeBlockBackgroundColor
        )
    }
    val currentHeadings by rememberUpdatedState(headings)
    val onActiveHeadingChangedState by rememberUpdatedState(onActiveHeadingChanged)

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
                val useGlobalHorizontalScroll = !isWordWrapEnabled

                val scrollView = FocusStableScrollView(context).apply {
                    if (isSplitMode) {
                        val container = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        }
                        addView(
                            container,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    } else {
                        val textView = SearchHighlightTextView(context).apply {
                            textSize = fontSizeSp
                            setLineSpacing(0f, lineHeight)
                            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                            setHorizontallyScrolling(!isWordWrapEnabled)
                            isHorizontalScrollBarEnabled = !isWordWrapEnabled
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
                    }
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        onScrollChanged(scrollY, computeMaxScrollY(this))
                        val list = currentHeadings
                        if (list.isEmpty()) return@setOnScrollChangeListener
                        val firstChild = getChildAt(0) ?: return@setOnScrollChangeListener
                        if (firstChild is LinearLayout) {
                            onActiveHeadingChangedState(
                                findActiveHeadingInSplit(
                                    firstChild, controller.splitBoundaries, list, scrollY
                                )
                            )
                        } else {
                            val tv = if (firstChild is HorizontalScrollView) {
                                firstChild.getChildAt(0) as? TextView
                            } else {
                                firstChild as? TextView
                            } ?: return@setOnScrollChangeListener
                            val layout = tv.layout ?: return@setOnScrollChangeListener
                            if (layout.text.isEmpty()) return@setOnScrollChangeListener
                            val y = scrollY + tv.paddingTop
                            onActiveHeadingChangedState(
                                findActiveHeadingIndex(layout.lineMetrics(), list, y)
                            )
                        }
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
                val rootView = if (useGlobalHorizontalScroll) {
                    HorizontalScrollView(context).apply {
                        isHorizontalScrollBarEnabled = true
                        addView(
                            scrollView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                } else {
                    scrollView
                }
                val zoomLayout = ZoomableContentLayout(context).apply {
                    addView(
                        rootView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                zoomLayout
            },
            update = { zoomLayout ->
                val rootView = zoomLayout.getChildAt(0)
                val scrollView = when (rootView) {
                    is HorizontalScrollView -> rootView.getChildAt(0) as ScrollView
                    else -> rootView as ScrollView
                }
                val child = scrollView.getChildAt(0) ?: return@AndroidView
                val useGlobalHorizontalScroll = !isWordWrapEnabled
                val wrapChanged = controller.lastWrapEnabled != isWordWrapEnabled ||
                    controller.lastCodeBlockWrapEnabled != isCodeBlockWrapEnabled
                val currentIsSplit = child is LinearLayout
                val needsRestructure = wrapChanged || (isSplitMode != currentIsSplit)
                val density = scrollView.context.resources.displayMetrics.density
                val paddingPx = (16 * density).toInt()

                if (needsRestructure) {
                    val hasGlobalHorizontalScroll = rootView is HorizontalScrollView
                    if (wrapChanged && useGlobalHorizontalScroll != hasGlobalHorizontalScroll) {
                        val parent = scrollView.parent as? ViewGroup
                        parent?.removeView(scrollView)
                        zoomLayout.removeAllViews()
                        if (useGlobalHorizontalScroll) {
                            val hsv = HorizontalScrollView(scrollView.context).apply {
                                isHorizontalScrollBarEnabled = true
                                addView(
                                    scrollView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                            zoomLayout.addView(
                                hsv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        } else {
                            zoomLayout.addView(
                                scrollView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                    // Save anchor from current view structure
                    controller.pendingAnchorOffset = getAnchorFromView(
                        scrollView, child, controller.splitBoundaries
                    )
                    scrollView.removeAllViews()

                    if (isSplitMode) {
                        val container = LinearLayout(scrollView.context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        }
                        scrollView.addView(
                            container,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    } else if (isWordWrapEnabled) {
                        val tv = createStyledTextView(
                            scrollView.context, paddingPx, fontSizeSp, lineHeight,
                            readingFont, codeFont, isSourceCode, textAlignment, textColor, selectionHighlightColor,
                            horizontalScroll = false
                        )
                        scrollView.addView(
                            tv,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    } else {
                        val tv = createStyledTextView(
                            scrollView.context, paddingPx, fontSizeSp, lineHeight,
                            readingFont, codeFont, isSourceCode, textAlignment, textColor, selectionHighlightColor,
                            horizontalScroll = true
                        )
                        scrollView.addView(
                            tv,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }

                    controller.lastWrapEnabled = isWordWrapEnabled
                    controller.lastCodeBlockWrapEnabled = isCodeBlockWrapEnabled
                    // Force text to be re-applied; new views are already styled
                    controller.lastTextHash = 0
                    controller.lastTextRef = null
                    controller.lastStyleKey = contentKey
                    controller.lastTextColor = textColor
                    controller.lastWrapEnabledApplied = isWordWrapEnabled
                    controller.lastSelectionHighlightColor = selectionHighlightColor
                    controller.lastCodeBlockBackgroundColor = codeBlockBackgroundColor
                }

                // The chrome overlays the reader, so its height belongs to the content
                // rather than to the viewport. Putting it in `paddingTop` keeps the
                // viewport a constant size — `maxScrollY` never moves, Android never
                // clamps `scrollY`, and the text scrolls up into the space the bar leaves
                // when it collapses. `paddingTop` specifically, because the heading math
                // further down already reads `tv.paddingTop`, so it stays consistent for
                // free rather than needing a second term threaded through it.
                val contentTopPx = paddingPx + (contentTopInset.value * density).toInt()
                val paddedChild = scrollView.getChildAt(0)?.let {
                    if (it is HorizontalScrollView) it.getChildAt(0) else it
                }
                if (paddedChild != null && paddedChild.paddingTop != contentTopPx) {
                    paddedChild.setPadding(
                        paddedChild.paddingLeft,
                        contentTopPx,
                        paddedChild.paddingRight,
                        paddedChild.paddingBottom
                    )
                }

                // Text / style updates
                val currentChild = scrollView.getChildAt(0) ?: return@AndroidView

                if (currentChild is LinearLayout) {
                    // Split mode
                    val container = currentChild
                    val spanned = text as Spanned
                    val textRefChanged = spanned !== controller.lastTextRef
                    val textHash = if (textRefChanged || controller.lastTextHash == 0) {
                        spanned.toString().hashCode()
                    } else {
                        controller.lastTextHash
                    }
                    val structuralChange = textHash != controller.lastTextHash
                    if (structuralChange) {
                        // Underlying text changed — full rebuild of segments
                        val splitCode = !isCodeBlockWrapEnabled || !isWordWrapEnabled
                        val splitTables = isWordWrapEnabled
                        val segments = splitByMarkers(spanned, splitCode, splitTables)
                        controller.splitBoundaries = segments
                        container.removeAllViews()
                        for ((start, end, type) in segments) {
                            val isCode = type == SegmentType.Code
                            val isTable = type == SegmentType.Table
                            val segText = spanned.subSequence(start, end) as Spanned
                            val segmentContent = if (isCode) {
                                stripBackgroundSpans(segText)
                            } else {
                                segText
                            }
                            if (isTable && !useGlobalHorizontalScroll) {
                                val tableView = buildTableLayout(
                                    segmentContent, container.context, textColor,
                                    fontSizeSp, lineHeight, readingFont,
                                    selectionHighlightColor, density
                                )
                                val contentView = if (tableView != null) {
                                    HorizontalScrollView(container.context).apply {
                                        isHorizontalScrollBarEnabled = true
                                        addView(
                                            tableView,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                        )
                                    }
                                } else {
                                    // Fallback: plain text
                                    val tv = createStyledTextView(
                                        container.context, 0, fontSizeSp, lineHeight,
                                        readingFont, codeFont, isSourceCode, textAlignment,
                                        textColor, selectionHighlightColor,
                                        horizontalScroll = false
                                    )
                                    tv.text = segmentContent
                                    tv
                                }
                                container.addView(
                                    contentView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                )
                            } else {
                                val needsCodeHScroll =
                                    !useGlobalHorizontalScroll && isCode
                                val tv = createStyledTextView(
                                    container.context, 0, fontSizeSp, lineHeight,
                                    readingFont, codeFont,
                                    isSourceCode = isSourceCode || isCode,
                                    textAlignment = textAlignment,
                                    textColor = textColor,
                                    selectionHighlightColor = selectionHighlightColor,
                                    horizontalScroll = needsCodeHScroll
                                )
                                tv.text = segmentContent
                                val contentView = if (needsCodeHScroll) {
                                    HorizontalScrollView(container.context).apply {
                                        isHorizontalScrollBarEnabled = true
                                        addView(
                                            tv,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                        )
                                    }
                                } else {
                                    tv
                                }
                                if (isCode) {
                                    val block = FrameLayout(container.context).apply {
                                        setBackgroundColor(codeBlockBackgroundColor)
                                        addView(
                                            contentView,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                        )
                                    }
                                    container.addView(
                                        block,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                    )
                                } else {
                                    container.addView(
                                        contentView,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                    )
                                }
                            }
                        }
                        controller.lastTextHash = textHash
                        controller.lastTextRef = spanned
                        controller.lastStyleKey = contentKey
                        controller.lastTextColor = textColor
                        controller.lastSelectionHighlightColor = selectionHighlightColor
                        controller.lastCodeBlockBackgroundColor = codeBlockBackgroundColor
                    } else if (textRefChanged) {
                        // Same underlying text, different spans (search highlights) —
                        // update existing TextViews in-place without rebuilding views
                        val boundaries = controller.splitBoundaries
                        for (i in 0 until container.childCount.coerceAtMost(boundaries.size)) {
                            val (start, end, type) = boundaries[i]
                            val segText = spanned.subSequence(start, end) as Spanned
                            val segmentContent = if (type == SegmentType.Code) {
                                stripBackgroundSpans(segText)
                            } else {
                                segText
                            }
                            if (type == SegmentType.Table && !useGlobalHorizontalScroll) {
                                // Tables need a full rebuild — cells aren't simple TextViews
                                val tableView = buildTableLayout(
                                    segmentContent, container.context, textColor,
                                    fontSizeSp, lineHeight, readingFont,
                                    selectionHighlightColor, density
                                )
                                if (tableView != null) {
                                    val hsv = container.getChildAt(i)
                                    if (hsv is HorizontalScrollView) {
                                        hsv.removeAllViews()
                                        hsv.addView(
                                            tableView,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                        )
                                    }
                                }
                            } else {
                                val tv = extractTextView(container.getChildAt(i))
                                tv?.text = segmentContent
                            }
                        }
                        controller.lastTextRef = spanned
                    }
                    if (controller.lastStyleKey != contentKey ||
                        controller.lastTextColor != textColor ||
                        controller.lastSelectionHighlightColor != selectionHighlightColor ||
                        controller.lastCodeBlockBackgroundColor != codeBlockBackgroundColor
                    ) {
                        // Restyle in place. This must never fall back to a segment
                        // rebuild: textColor comes from an animated Color, so a
                        // rebuild here would tear down and recreate the whole view
                        // tree on alternating frames of that animation — which also
                        // cancels any touch gesture in flight.
                        for (i in 0 until container.childCount) {
                            val seg = container.getChildAt(i)
                            val target = when (seg) {
                                is FrameLayout -> {
                                    // Code block wrapper — carries the block tint.
                                    seg.setBackgroundColor(codeBlockBackgroundColor)
                                    seg.getChildAt(0)
                                }
                                else -> seg
                            }
                            val inner = if (target is HorizontalScrollView) {
                                target.getChildAt(0)
                            } else {
                                target
                            }
                            when (inner) {
                                is TableLayout -> restyleTableLayout(
                                    inner, textColor, fontSizeSp, lineHeight, readingFont,
                                    selectionHighlightColor, density
                                )
                                is TextView -> applyStyleToTextView(
                                    inner, fontSizeSp, lineHeight, readingFont, codeFont,
                                    isSourceCode, textAlignment, textColor,
                                    selectionHighlightColor
                                )
                            }
                        }
                        controller.lastStyleKey = contentKey
                        controller.lastTextColor = textColor
                        controller.lastSelectionHighlightColor = selectionHighlightColor
                        controller.lastCodeBlockBackgroundColor = codeBlockBackgroundColor
                    }
                } else {
                    // Single-TV mode
                    val textView = when (currentChild) {
                        is HorizontalScrollView -> currentChild.getChildAt(0) as TextView
                        else -> currentChild as TextView
                    }

                    when (text) {
                        is Spanned -> {
                            if (text !== controller.lastTextRef) {
                                textView.text = text
                                controller.lastTextHash = text.toString().hashCode()
                                controller.lastTextRef = text
                            }
                        }
                        else -> {
                            if (text !== controller.lastTextRef) {
                                val textHash = text.hashCode()
                                if (textHash != controller.lastTextHash) {
                                    textView.text = text.toString()
                                    controller.lastTextHash = textHash
                                }
                                controller.lastTextRef = text
                            }
                        }
                    }
                    if (controller.lastStyleKey != contentKey) {
                        applyStyleToTextView(
                            textView, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode,
                            textAlignment, textColor, selectionHighlightColor
                        )
                        controller.lastStyleKey = contentKey
                    }
                    if (controller.lastTextColor != textColor) {
                        textView.setTextColor(textColor)
                        controller.lastTextColor = textColor
                    }
                    if (controller.lastWrapEnabledApplied != isWordWrapEnabled) {
                        textView.setHorizontallyScrolling(!isWordWrapEnabled)
                        textView.isHorizontalScrollBarEnabled = !isWordWrapEnabled
                        controller.lastWrapEnabledApplied = isWordWrapEnabled
                    }
                    if (controller.lastSelectionHighlightColor != selectionHighlightColor) {
                        textView.highlightColor = selectionHighlightColor
                        controller.lastSelectionHighlightColor = selectionHighlightColor
                    }
                }

                // Scroll handling. Every branch claims restoreKey synchronously,
                // before posting, so that a later pass through this block cannot
                // queue a second, conflicting restore behind the one in flight.
                val targetOffset = controller.pendingAnchorOffset
                if (targetOffset != null) {
                    controller.pendingAnchorOffset = null
                    controller.lastRestoredKey = restoreKey
                    scrollView.post {
                        val y = resolveScrollY(
                            scrollView, controller.splitBoundaries, targetOffset
                        )
                        // Unresolvable anchor — hold position rather than jumping.
                        if (y != null) scrollView.smoothScrollTo(0, y)
                    }
                } else if (scrollToOffset == null && controller.lastRestoredKey != restoreKey) {
                    // Restore-only read: observing it here would resubscribe this
                    // block to a value that changes on every scroll frame.
                    val restoreY = Snapshot.withoutReadObservation { savedScrollY.value }
                    if (restoreY > 0) {
                        controller.lastRestoredKey = restoreKey
                        scrollView.post { scrollView.scrollTo(0, restoreY) }
                    }
                }

                if (scrollToOffset != null) {
                    controller.lastRestoredKey = restoreKey
                    scrollView.post {
                        val y = resolveScrollY(
                            scrollView, controller.splitBoundaries, scrollToOffset
                        )
                        if (y != null) {
                            val centeredY = (y - scrollView.height / 3).coerceAtLeast(0)
                            scrollView.smoothScrollTo(0, centeredY)
                            if (zoomLayout.currentScale > 1f) zoomLayout.resetPan()
                        }
                        // Consumed either way, so an unresolvable target does not
                        // leave the request pending forever.
                        onScrollConsumed()
                    }
                }

                // Heading tracking
                val curChild = scrollView.getChildAt(0)
                if (currentHeadings.isNotEmpty()) {
                    if (curChild is LinearLayout) {
                        onActiveHeadingChangedState(
                            findActiveHeadingInSplit(
                                curChild, controller.splitBoundaries,
                                currentHeadings, scrollView.scrollY
                            )
                        )
                    } else {
                        val tv = when (curChild) {
                            is HorizontalScrollView -> curChild.getChildAt(0) as? TextView
                            is TextView -> curChild
                            else -> null
                        }
                        val layout = tv?.layout
                        if (layout != null && layout.text.isNotEmpty()) {
                            val y = scrollView.scrollY + (tv?.paddingTop ?: 0)
                            onActiveHeadingChangedState(
                                findActiveHeadingIndex(layout.lineMetrics(), currentHeadings, y)
                            )
                        }
                    }
                }
            },
            onRelease = { zoomLayout ->
                controller.extentListener?.let { listener ->
                    val rootView = zoomLayout.getChildAt(0)
                    val scrollView = when (rootView) {
                        is HorizontalScrollView -> rootView.getChildAt(0) as? ScrollView
                        else -> rootView as? ScrollView
                    }
                    scrollView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
                    controller.extentListener = null
                }
            }
        )
    }
}
