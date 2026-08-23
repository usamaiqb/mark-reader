package com.markreader.ui.reader

import android.text.Layout
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.markreader.ui.screens.HeadingItem

/**
 * The part of a laid-out [Layout] the anchor math reads.
 *
 * The math is separated from the views behind this interface so it can be
 * exercised without inflating anything: the "unresolvable anchor" cases below
 * all hinge on a layout being absent, which is awkward to stage with real views
 * and easy to get wrong (returning 0 instead of null once sent the viewer to the
 * top of the document).
 */
internal interface LineMetrics {
    val textLength: Int
    fun getLineForOffset(offset: Int): Int
    fun getLineForVertical(y: Int): Int
    fun getLineTop(line: Int): Int
    fun getLineStart(line: Int): Int
}

private class LayoutLineMetrics(private val layout: Layout) : LineMetrics {
    override val textLength: Int get() = layout.text.length
    override fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset)
    override fun getLineForVertical(y: Int): Int = layout.getLineForVertical(y)
    override fun getLineTop(line: Int): Int = layout.getLineTop(line)
    override fun getLineStart(line: Int): Int = layout.getLineStart(line)
}

internal fun Layout.lineMetrics(): LineMetrics = LayoutLineMetrics(this)

/**
 * One segment view of a split container, reduced to what the anchor math reads.
 *
 * [lines] is null whenever the segment carries no measured text, and [hasText]
 * separates the two reasons for that: a table has no TextView at all but is
 * still anchorable at segment granularity, whereas a TextView whose Layout has
 * not been built yet is not anchorable and must not be guessed at.
 */
internal class SegmentPlacement(
    val top: Int,
    val height: Int,
    val hasText: Boolean,
    val lines: LineMetrics?
)

// ---------- Anchor math ----------

/**
 * Text offset at the top of the viewport for a split container, or null if the
 * container has no segments to anchor to.
 */
internal fun anchorOffsetInSplit(
    boundaries: List<Segment>,
    childCount: Int,
    scrollY: Int,
    placementAt: (Int) -> SegmentPlacement
): Int? {
    for (i in 0 until childCount.coerceAtMost(boundaries.size)) {
        val placement = placementAt(i)
        if (placement.top + placement.height <= scrollY) continue
        val segStart = boundaries[i].start
        // No measured text — anchor at segment granularity rather than by line.
        val lines = placement.lines ?: return segStart
        val localY = (scrollY - placement.top).coerceAtLeast(0)
        return segStart + lines.getLineStart(lines.getLineForVertical(localY))
    }
    return boundaries.lastOrNull()?.start
}

/**
 * Text offset at the top of the viewport for a single-TextView container.
 * [scrollY] is in the ScrollView's space; the layout's line 0 starts below the
 * TextView's top padding.
 */
internal fun anchorOffsetInText(lines: LineMetrics?, scrollY: Int, paddingTop: Int): Int? {
    if (lines == null) return null
    val line = lines.getLineForVertical((scrollY - paddingTop).coerceAtLeast(0))
    return lines.getLineStart(line)
}

/**
 * Scroll position that puts [offset] at the top of the viewport of a split
 * container, or null if it cannot be resolved. Null means "leave the scroll
 * position alone"; returning 0 here would silently jump to the top of the
 * document.
 */
internal fun scrollYForOffsetInSplit(
    boundaries: List<Segment>,
    childCount: Int,
    isLaidOut: Boolean,
    offset: Int,
    placementAt: (Int) -> SegmentPlacement
): Int? {
    // Child positions are meaningless until the container has been laid out.
    if (!isLaidOut) return null
    for (i in boundaries.indices) {
        val (segStart, segEnd, _) = boundaries[i]
        if (offset < segStart || offset >= segEnd) continue
        if (i >= childCount) break
        val placement = placementAt(i)
        // child.top is already measured from the container's padded origin, so the
        // container's own padding must not be added again.
        if (!placement.hasText) {
            // No TextView at all — a table. Still anchorable, just at segment
            // granularity rather than line granularity.
            return placement.top
        }
        // A TextView whose layout has not been built yet is not the same thing:
        // guessing here is what used to send the viewer to the top.
        val lines = placement.lines ?: return null
        val localOffset = (offset - segStart).coerceIn(
            0,
            lines.textLength.coerceAtLeast(1) - 1
        )
        return placement.top + lines.getLineTop(lines.getLineForOffset(localOffset))
    }
    return null
}

/** Scroll position that puts [offset] at the top of a single-TextView container. */
internal fun scrollYForOffsetInText(lines: LineMetrics, paddingTop: Int, offset: Int): Int =
    lines.getLineTop(lines.getLineForOffset(offset)) + paddingTop

/**
 * Index of the last heading whose line starts at or above [y], or -1 when the
 * viewport sits above the first one. Headings are ordered by offset, so this is
 * a binary search.
 */
internal fun findActiveHeadingIndex(
    lines: LineMetrics,
    headings: List<HeadingItem>,
    y: Int
): Int {
    var low = 0
    var high = headings.lastIndex
    var result = -1
    val textLength = lines.textLength
    while (low <= high) {
        val mid = (low + high) ushr 1
        val offset = headings[mid].offset.coerceIn(0, textLength - 1)
        val line = lines.getLineForOffset(offset)
        val top = lines.getLineTop(line)
        if (top <= y) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/**
 * The same lookup across a split container. Segments are laid out independently,
 * so there is no single monotonic line index to binary search — each segment's
 * headings are checked against its own layout.
 */
internal fun findActiveHeadingInSplit(
    boundaries: List<Segment>,
    childCount: Int,
    headings: List<HeadingItem>,
    adjustedY: Int,
    placementAt: (Int) -> SegmentPlacement
): Int {
    if (headings.isEmpty() || boundaries.isEmpty()) return -1
    var result = -1
    for (i in 0 until childCount.coerceAtMost(boundaries.size)) {
        val placement = placementAt(i)
        val lines = placement.lines ?: continue
        val (segStart, segEnd, _) = boundaries[i]
        for (h in headings.indices) {
            val offset = headings[h].offset
            if (offset < segStart || offset >= segEnd) continue
            val localOffset = (offset - segStart).coerceIn(0, lines.textLength - 1)
            val line = lines.getLineForOffset(localOffset)
            val top = placement.top + lines.getLineTop(line)
            if (top <= adjustedY) {
                result = h
            }
        }
    }
    return result
}

// ---------- View adapters ----------

internal fun segmentPlacement(view: View): SegmentPlacement {
    val tv = extractTextView(view)
    return SegmentPlacement(
        top = view.top,
        height = view.height,
        hasText = tv != null,
        lines = tv?.layout?.lineMetrics()
    )
}

internal fun extractTextView(view: View): TextView? {
    return when (view) {
        is FrameLayout -> view.getChildAt(0)?.let { extractTextView(it) }
        is HorizontalScrollView -> view.getChildAt(0) as? TextView
        is TextView -> view
        else -> null
    }
}

/** Text offset currently at the top of the viewport, whatever the view structure. */
internal fun getAnchorFromView(
    scrollView: ScrollView,
    child: View,
    boundaries: List<Segment>
): Int? {
    val sy = scrollView.scrollY
    return when (child) {
        is LinearLayout -> anchorOffsetInSplit(boundaries, child.childCount, sy) {
            segmentPlacement(child.getChildAt(it))
        }
        is HorizontalScrollView -> {
            val tv = child.getChildAt(0) as? TextView
            anchorOffsetInText(tv?.layout?.lineMetrics(), sy, tv?.paddingTop ?: 0)
        }
        is TextView -> anchorOffsetInText(child.layout?.lineMetrics(), sy, child.paddingTop)
        else -> null
    }
}

/**
 * Scroll position that puts [offset] at the top of the viewport, or null if it
 * cannot be resolved — the views may not be laid out yet.
 */
internal fun resolveScrollY(
    scrollView: ScrollView,
    boundaries: List<Segment>,
    offset: Int
): Int? {
    val child = scrollView.getChildAt(0) ?: return null
    if (child is LinearLayout && boundaries.isNotEmpty()) {
        return scrollYForOffsetInSplit(
            boundaries, child.childCount, child.isLaidOut, offset
        ) { segmentPlacement(child.getChildAt(it)) }
    }
    val tv = when (child) {
        is HorizontalScrollView -> child.getChildAt(0) as? TextView
        is TextView -> child
        else -> null
    } ?: return null
    val lines = tv.layout?.lineMetrics() ?: return null
    return scrollYForOffsetInText(lines, tv.paddingTop, offset)
}

internal fun findActiveHeadingInSplit(
    container: LinearLayout,
    boundaries: List<Segment>,
    headings: List<HeadingItem>,
    scrollY: Int
): Int = findActiveHeadingInSplit(
    boundaries = boundaries,
    childCount = container.childCount,
    headings = headings,
    adjustedY = scrollY + container.paddingTop
) { segmentPlacement(container.getChildAt(it)) }

internal fun computeMaxScrollY(scrollView: ScrollView): Int {
    val child = scrollView.getChildAt(0) ?: return 0
    return (child.height + scrollView.paddingTop + scrollView.paddingBottom - scrollView.height)
        .coerceAtLeast(0)
}
