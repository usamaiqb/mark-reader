package com.markreader.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LineBackgroundSpan
import android.text.style.LeadingMarginSpan
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.markreader.R
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.ui.markdown.CodeBlockMarkerSpan
import com.markreader.ui.markdown.TableMarkerSpan
import com.markreader.ui.zoom.ZoomableContentLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import io.noties.markwon.ext.tables.TableRowSpan

private enum class SegmentType { Text, Code, Table }
private data class Segment(val start: Int, val end: Int, val type: SegmentType)

private data class ContentKey(
    val textHash: Int,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val readingFont: ReadingFontPreference,
    val codeFont: CodeFontPreference,
    val isSourceCode: Boolean,
    val textAlignment: TextAlignmentPreference
)

@Composable
fun RenderedTextView(
    text: Any,
    textColor: Int,
    padding: PaddingValues,
    savedScrollY: Int,
    scrollToOffset: Int?,
    onScrollChanged: (Int) -> Unit,
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
    var lastRestoredKey by remember { mutableStateOf<ContentKey?>(null) }
    var lastWrapEnabled by remember { mutableStateOf(isWordWrapEnabled) }
    var lastCodeBlockWrapEnabled by remember { mutableStateOf(isCodeBlockWrapEnabled) }
    var pendingAnchorOffset by remember { mutableStateOf<Int?>(null) }
    var lastTextHash by remember { mutableStateOf(0) }
    var lastTextRef by remember { mutableStateOf<Any?>(null) }
    var lastStyleKey by remember {
        mutableStateOf(
            ContentKey(
                textHash = 0,
                fontSizeSp = -1f,
                lineHeight = -1f,
                readingFont = ReadingFontPreference.Merriweather,
                codeFont = CodeFontPreference.JetBrainsMono,
                isSourceCode = false,
                textAlignment = TextAlignmentPreference.Left
            )
        )
    }
    var lastTextColor by remember { mutableStateOf(textColor) }
    var lastWrapEnabledApplied by remember { mutableStateOf(isWordWrapEnabled) }
    var lastSelectionHighlightColor by remember { mutableStateOf(selectionHighlightColor) }
    var splitBoundaries by remember { mutableStateOf<List<Segment>>(emptyList()) }
    val currentHeadings by rememberUpdatedState(headings)
    val currentSplitBoundaries by rememberUpdatedState(splitBoundaries)
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
                val isSplitMode = text is Spanned && (
                    (isWordWrapEnabled && (!isCodeBlockWrapEnabled || hasTables(text))) ||
                        (!isWordWrapEnabled && hasCodeBlocks(text))
                    )
                val useGlobalHorizontalScroll = !isWordWrapEnabled

                val scrollView = ScrollView(context).apply {
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
                        val textView = TextView(context).apply {
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
                        onScrollChanged(scrollY)
                        val list = currentHeadings
                        if (list.isEmpty()) return@setOnScrollChangeListener
                        val firstChild = getChildAt(0) ?: return@setOnScrollChangeListener
                        if (firstChild is LinearLayout) {
                            onActiveHeadingChangedState(
                                findActiveHeadingInSplit(
                                    firstChild, currentSplitBoundaries, list, scrollY
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
                            onActiveHeadingChangedState(findActiveHeadingIndex(layout, list, y))
                        }
                    }
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
                val isSplitMode = text is Spanned && (
                    (isWordWrapEnabled && (!isCodeBlockWrapEnabled || hasTables(text))) ||
                        (!isWordWrapEnabled && hasCodeBlocks(text))
                    )
                val useGlobalHorizontalScroll = !isWordWrapEnabled
                val wrapChanged = lastWrapEnabled != isWordWrapEnabled ||
                    lastCodeBlockWrapEnabled != isCodeBlockWrapEnabled
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
                    pendingAnchorOffset = getAnchorFromView(
                        scrollView, child, currentSplitBoundaries
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

                    lastWrapEnabled = isWordWrapEnabled
                    lastCodeBlockWrapEnabled = isCodeBlockWrapEnabled
                    // Force text to be re-applied; new views are already styled
                    lastTextHash = 0
                    lastTextRef = null
                    lastStyleKey = contentKey
                    lastTextColor = textColor
                    lastWrapEnabledApplied = isWordWrapEnabled
                    lastSelectionHighlightColor = selectionHighlightColor
                }

                // Text / style updates
                val currentChild = scrollView.getChildAt(0) ?: return@AndroidView

                if (currentChild is LinearLayout) {
                    // Split mode
                    val container = currentChild
                    val spanned = text as Spanned
                    val textRefChanged = spanned !== lastTextRef
                    val textHash = if (textRefChanged || lastTextHash == 0) {
                        spanned.toString().hashCode()
                    } else {
                        lastTextHash
                    }
                    if (textRefChanged || textHash != lastTextHash) {
                        val splitCode = !isCodeBlockWrapEnabled || !isWordWrapEnabled
                        val splitTables = isWordWrapEnabled
                        val segments = splitByMarkers(spanned, splitCode, splitTables)
                        splitBoundaries = segments
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
                        lastTextHash = textHash
                        lastTextRef = spanned
                        lastStyleKey = contentKey
                        lastTextColor = textColor
                        lastSelectionHighlightColor = selectionHighlightColor
                    } else if (lastStyleKey != contentKey || lastTextColor != textColor ||
                        lastSelectionHighlightColor != selectionHighlightColor
                    ) {
                        // Rebuild segments to refresh table colors and code spans
                        lastTextHash = 0
                    }
                    if (lastStyleKey != contentKey || lastTextColor != textColor ||
                        lastSelectionHighlightColor != selectionHighlightColor
                    ) {
                        for (i in 0 until container.childCount) {
                            val seg = container.getChildAt(i)
                            val target = when (seg) {
                                is FrameLayout -> seg.getChildAt(0)
                                else -> seg
                            }
                            val tv = if (target is HorizontalScrollView) {
                                target.getChildAt(0) as? TextView
                            } else {
                                target as? TextView
                            }
                            tv?.let {
                                applyStyleToTextView(
                                    it, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode,
                                    textAlignment, textColor, selectionHighlightColor
                                )
                            }
                        }
                        lastStyleKey = contentKey
                        lastTextColor = textColor
                        lastSelectionHighlightColor = selectionHighlightColor
                    }
                } else {
                    // Single-TV mode
                    val textView = when (currentChild) {
                        is HorizontalScrollView -> currentChild.getChildAt(0) as TextView
                        else -> currentChild as TextView
                    }

                    when (text) {
                        is Spanned -> {
                            if (text !== lastTextRef) {
                                textView.text = text
                                lastTextHash = text.toString().hashCode()
                                lastTextRef = text
                            }
                        }
                        else -> {
                            if (text !== lastTextRef) {
                                val textHash = text.hashCode()
                                if (textHash != lastTextHash) {
                                    textView.text = text.toString()
                                    lastTextHash = textHash
                                }
                                lastTextRef = text
                            }
                        }
                    }
                    if (lastStyleKey != contentKey) {
                        applyStyleToTextView(
                            textView, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode,
                            textAlignment, textColor, selectionHighlightColor
                        )
                        lastStyleKey = contentKey
                    }
                    if (lastTextColor != textColor) {
                        textView.setTextColor(textColor)
                        lastTextColor = textColor
                    }
                    if (lastWrapEnabledApplied != isWordWrapEnabled) {
                        textView.setHorizontallyScrolling(!isWordWrapEnabled)
                        textView.isHorizontalScrollBarEnabled = !isWordWrapEnabled
                        lastWrapEnabledApplied = isWordWrapEnabled
                    }
                    if (lastSelectionHighlightColor != selectionHighlightColor) {
                        textView.highlightColor = selectionHighlightColor
                        lastSelectionHighlightColor = selectionHighlightColor
                    }
                }

                // Scroll handling
                if (pendingAnchorOffset != null) {
                    val targetOffset = pendingAnchorOffset
                    pendingAnchorOffset = null
                    scrollView.post {
                        val y = resolveScrollY(
                            scrollView, currentSplitBoundaries, targetOffset ?: return@post
                        )
                        lastRestoredKey = contentKey
                        scrollView.smoothScrollTo(0, y)
                    }
                } else if (scrollToOffset == null && savedScrollY > 0 &&
                    lastRestoredKey != contentKey
                ) {
                    lastRestoredKey = contentKey
                    scrollView.post { scrollView.scrollTo(0, savedScrollY) }
                }

                if (scrollToOffset != null) {
                    scrollView.post {
                        val y = resolveScrollY(
                            scrollView, currentSplitBoundaries, scrollToOffset
                        )
                        lastRestoredKey = contentKey
                        scrollView.smoothScrollTo(0, y)
                        if (zoomLayout.currentScale > 1f) zoomLayout.resetPan()
                        onScrollConsumed()
                    }
                }

                // Heading tracking
                val curChild = scrollView.getChildAt(0)
                if (currentHeadings.isNotEmpty()) {
                    if (curChild is LinearLayout) {
                        onActiveHeadingChangedState(
                            findActiveHeadingInSplit(
                                curChild, currentSplitBoundaries,
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
                                findActiveHeadingIndex(layout, currentHeadings, y)
                            )
                        }
                    }
                }
            }
        )
    }
}

// ---------- Helper functions ----------

private fun findActiveHeadingIndex(
    layout: Layout,
    headings: List<HeadingItem>,
    y: Int
): Int {
    var low = 0
    var high = headings.lastIndex
    var result = -1
    val textLength = layout.text.length
    while (low <= high) {
        val mid = (low + high) ushr 1
        val offset = headings[mid].offset.coerceIn(0, textLength - 1)
        val line = layout.getLineForOffset(offset)
        val top = layout.getLineTop(line)
        if (top <= y) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun findActiveHeadingInSplit(
    container: LinearLayout,
    boundaries: List<Segment>,
    headings: List<HeadingItem>,
    scrollY: Int
): Int {
    if (headings.isEmpty() || boundaries.isEmpty()) return -1
    var result = -1
    val adjustedY = scrollY + container.paddingTop
    for (i in 0 until container.childCount.coerceAtMost(boundaries.size)) {
        val child = container.getChildAt(i)
        val (segStart, segEnd, _) = boundaries[i]
        val tv = extractTextView(child) ?: continue
        val layout = tv.layout ?: continue
        for (h in headings.indices) {
            val offset = headings[h].offset
            if (offset < segStart || offset >= segEnd) continue
            val localOffset = (offset - segStart).coerceIn(0, layout.text.length - 1)
            val line = layout.getLineForOffset(localOffset)
            val top = child.top + layout.getLineTop(line)
            if (top <= adjustedY) {
                result = h
            }
        }
    }
    return result
}

private fun getAnchorFromView(
    scrollView: ScrollView,
    child: android.view.View,
    boundaries: List<Segment>
): Int? {
    val sy = scrollView.scrollY
    when (child) {
        is LinearLayout -> {
            for (i in 0 until child.childCount.coerceAtMost(boundaries.size)) {
                val seg = child.getChildAt(i)
                if (seg.top + seg.height > sy) {
                    val segStart = boundaries[i].start
                    val tv = extractTextView(seg)
                    val layout = tv?.layout
                    return if (layout != null) {
                        val localY = (sy - seg.top).coerceAtLeast(0)
                        val line = layout.getLineForVertical(localY)
                        segStart + layout.getLineStart(line)
                    } else {
                        segStart
                    }
                }
            }
            return boundaries.lastOrNull()?.start
        }
        is HorizontalScrollView -> {
            val tv = child.getChildAt(0) as? TextView
            val layout = tv?.layout ?: return null
            val line = layout.getLineForVertical(sy)
            return layout.getLineStart(line)
        }
        is TextView -> {
            val layout = child.layout ?: return null
            val line = layout.getLineForVertical(sy)
            return layout.getLineStart(line)
        }
        else -> return null
    }
}

private fun resolveScrollY(
    scrollView: ScrollView,
    boundaries: List<Segment>,
    offset: Int
): Int {
    val child = scrollView.getChildAt(0) ?: return 0
    if (child is LinearLayout && boundaries.isNotEmpty()) {
        return scrollToOffsetInSplit(child, boundaries, offset)
    }
    val tv = when (child) {
        is HorizontalScrollView -> child.getChildAt(0) as? TextView
        is TextView -> child
        else -> null
    } ?: return 0
    val layout = tv.layout ?: return 0
    val line = layout.getLineForOffset(offset)
    return layout.getLineTop(line)
}

private fun scrollToOffsetInSplit(
    container: LinearLayout,
    boundaries: List<Segment>,
    offset: Int
): Int {
    for (i in boundaries.indices) {
        val (segStart, segEnd, _) = boundaries[i]
        if (offset < segStart || offset >= segEnd) continue
        if (i >= container.childCount) break
        val child = container.getChildAt(i)
        val tv = extractTextView(child) ?: continue
        val layout = tv.layout ?: continue
        val localOffset = (offset - segStart).coerceIn(
            0,
            layout.text.length.coerceAtLeast(1) - 1
        )
        val line = layout.getLineForOffset(localOffset)
        return child.top + layout.getLineTop(line) + container.paddingTop
    }
    return 0
}

private fun extractTextView(view: android.view.View): TextView? {
    return when (view) {
        is FrameLayout -> view.getChildAt(0)?.let { extractTextView(it) }
        is HorizontalScrollView -> view.getChildAt(0) as? TextView
        is TextView -> view
        else -> null
    }
}

private fun splitByMarkers(
    text: Spanned,
    splitCode: Boolean,
    splitTables: Boolean
): List<Segment> {
    val ranges = mutableListOf<Segment>()
    if (splitCode) {
        for (span in text.getSpans(0, text.length, CodeBlockMarkerSpan::class.java)) {
            ranges.add(Segment(text.getSpanStart(span), text.getSpanEnd(span), SegmentType.Code))
        }
    }
    if (splitTables) {
        for (span in text.getSpans(0, text.length, TableMarkerSpan::class.java)) {
            ranges.add(Segment(text.getSpanStart(span), text.getSpanEnd(span), SegmentType.Table))
        }
    }
    if (ranges.isEmpty()) return listOf(Segment(0, text.length, SegmentType.Text))
    ranges.sortBy { it.start }
    val segments = mutableListOf<Segment>()
    var pos = 0
    for (range in ranges) {
        if (range.start > pos) {
            segments.add(Segment(pos, range.start, SegmentType.Text))
        }
        segments.add(range)
        pos = range.end
    }
    if (pos < text.length) {
        segments.add(Segment(pos, text.length, SegmentType.Text))
    }
    return segments
}

private fun hasCodeBlocks(text: Spanned): Boolean {
    return text.getSpans(0, text.length, CodeBlockMarkerSpan::class.java).isNotEmpty()
}

private fun hasTables(text: Spanned): Boolean {
    return text.getSpans(0, text.length, TableMarkerSpan::class.java).isNotEmpty()
}

private fun buildTableLayout(
    text: Spanned,
    context: Context,
    textColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    selectionHighlightColor: Int,
    density: Float
): android.widget.TableLayout? {
    val tableRowSpans = text.getSpans(0, text.length, TableRowSpan::class.java)
    if (tableRowSpans.isEmpty()) return null

    tableRowSpans.sortBy { text.getSpanStart(it) }

    val cellsField = try {
        TableRowSpan::class.java.getDeclaredField("cells").apply { isAccessible = true }
    } catch (_: Exception) { return null }

    val cellPaddingPx = (8 * density).toInt()
    val borderWidthPx = maxOf(1, density.toInt())
    // Markwon defaults: border = textColor at 75/255 alpha, odd row bg = textColor at 22/255 alpha
    val borderColor = (textColor and 0x00FFFFFF) or (75 shl 24)
    val oddRowBg = (textColor and 0x00FFFFFF) or (22 shl 24)
    val typeface = resolveTypeface(context, false, readingFont, CodeFontPreference.JetBrainsMono)

    val tableLayout = android.widget.TableLayout(context)

    for ((rowIndex, rowSpan) in tableRowSpans.withIndex()) {
        @Suppress("UNCHECKED_CAST")
        val cells = try {
            cellsField.get(rowSpan) as? List<TableRowSpan.Cell> ?: continue
        } catch (_: Exception) { continue }

        val isHeader = rowIndex == 0
        val isOdd = !isHeader && rowIndex % 2 == 1

        val tableRow = android.widget.TableRow(context)

        for ((colIndex, cell) in cells.withIndex()) {
            val cellBg = GradientDrawable().apply {
                setStroke(borderWidthPx, borderColor)
                setColor(if (isOdd) oddRowBg else Color.TRANSPARENT)
            }
            val cellTv = TextView(context).apply {
                this.text = cell.text()
                textSize = fontSizeSp
                setLineSpacing(0f, lineHeight)
                setTextColor(textColor)
                setPadding(cellPaddingPx, cellPaddingPx, cellPaddingPx, cellPaddingPx)
                this.typeface = typeface
                if (isHeader) setTypeface(this.typeface, Typeface.BOLD)
                highlightColor = selectionHighlightColor
                background = cellBg
                gravity = when (cell.alignment()) {
                    TableRowSpan.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
                    TableRowSpan.ALIGN_RIGHT -> Gravity.END
                    else -> Gravity.START
                }
            }
            val params = android.widget.TableRow.LayoutParams(
                android.widget.TableRow.LayoutParams.WRAP_CONTENT,
                android.widget.TableRow.LayoutParams.MATCH_PARENT
            )
            // Overlap adjacent borders so they don't double up
            if (colIndex > 0) params.leftMargin = -borderWidthPx
            tableRow.addView(cellTv, params)
        }

        val rowParams = android.widget.TableLayout.LayoutParams(
            android.widget.TableLayout.LayoutParams.WRAP_CONTENT,
            android.widget.TableLayout.LayoutParams.WRAP_CONTENT
        )
        if (rowIndex > 0) rowParams.topMargin = -borderWidthPx
        tableLayout.addView(tableRow, rowParams)
    }

    return tableLayout
}

private fun stripBackgroundSpans(text: Spanned): Spanned {
    val mutable = SpannableStringBuilder(text)
    val lineSpans = mutable.getSpans(0, mutable.length, LineBackgroundSpan::class.java)
    for (span in lineSpans) {
        mutable.removeSpan(span)
    }
    val marginSpans = mutable.getSpans(0, mutable.length, LeadingMarginSpan::class.java)
    for (span in marginSpans) {
        mutable.removeSpan(span)
    }
    val anySpans = mutable.getSpans(0, mutable.length, Any::class.java)
    for (span in anySpans) {
        val name = span.javaClass.name
        if (name.contains("CodeBlock", ignoreCase = true)) {
            mutable.removeSpan(span)
        }
    }
    return mutable
}

private fun resolveTypeface(
    context: Context,
    isSourceCode: Boolean,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference
): Typeface = if (isSourceCode) {
    when (codeFont) {
        CodeFontPreference.JetBrainsMono -> try {
            ResourcesCompat.getFont(
                context, R.font.jetbrains_mono_regular
            ) ?: Typeface.MONOSPACE
        } catch (_: Exception) { Typeface.MONOSPACE }
        CodeFontPreference.SystemMono -> Typeface.MONOSPACE
    }
} else {
    when (readingFont) {
        ReadingFontPreference.Merriweather -> try {
            ResourcesCompat.getFont(
                context, R.font.merriweather_regular
            ) ?: Typeface.SERIF
        } catch (_: Exception) { Typeface.SERIF }
        ReadingFontPreference.SystemSerif -> Typeface.SERIF
    }
}

private fun createStyledTextView(
    context: Context,
    paddingPx: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int,
    horizontalScroll: Boolean
): TextView {
    return TextView(context).apply {
        textSize = fontSizeSp
        setLineSpacing(0f, lineHeight)
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        setHorizontallyScrolling(horizontalScroll)
        isHorizontalScrollBarEnabled = horizontalScroll
        setTextIsSelectable(true)
        setTextColor(textColor)
        highlightColor = selectionHighlightColor
        typeface = resolveTypeface(context, isSourceCode, readingFont, codeFont)
        if (textAlignment == TextAlignmentPreference.Justified &&
            Build.VERSION.SDK_INT >= 26
        ) {
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            this.textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
        }
    }
}

private fun applyStyleToTextView(
    tv: TextView,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int
) {
    tv.textSize = fontSizeSp
    tv.setLineSpacing(0f, lineHeight)
    tv.setTextColor(textColor)
    tv.highlightColor = selectionHighlightColor
    tv.typeface = resolveTypeface(tv.context, isSourceCode, readingFont, codeFont)
    if (textAlignment == TextAlignmentPreference.Justified &&
        Build.VERSION.SDK_INT >= 26
    ) {
        tv.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
    } else {
        if (Build.VERSION.SDK_INT >= 26) {
            tv.justificationMode = Layout.JUSTIFICATION_MODE_NONE
        }
        tv.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
    }
}
