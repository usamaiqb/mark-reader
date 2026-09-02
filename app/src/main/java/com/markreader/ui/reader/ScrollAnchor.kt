package com.markreader.ui.reader

import android.widget.ScrollView
import android.widget.TextView

/*
 * Text offset to scroll position, which is the one direction the reader still travels in: a search
 * result names an offset, and something has to put it on screen.
 *
 * There used to be four functions here — capture and resolve, each over two container shapes. The
 * split container of per-segment views went with the marker spans that drove it, and capturing an
 * anchor went with the rebuild that was its only caller.
 */

/** Scroll position that puts [offset] at the top of a single-TextView container. */
internal fun scrollYForOffsetInText(lines: LineMetrics, paddingTop: Int, offset: Int): Int =
    lines.getLineTop(lines.getLineForOffset(offset)) + paddingTop

// ---------- View adapters ----------

/**
 * Scroll position that puts [offset] at the top of the viewport, or null if it
 * cannot be resolved — the view may not be laid out yet. Null means "leave the scroll position
 * alone"; returning 0 here would silently jump to the top of the document.
 */
internal fun resolveScrollY(scrollView: ScrollView, offset: Int): Int? {
    val tv = scrollView.getChildAt(0) as? TextView ?: return null
    val lines = tv.layout?.lineMetrics() ?: return null
    return scrollYForOffsetInText(lines, tv.paddingTop, offset)
}

internal fun computeMaxScrollY(scrollView: ScrollView): Int {
    val child = scrollView.getChildAt(0) ?: return 0
    return (child.height + scrollView.paddingTop + scrollView.paddingBottom - scrollView.height)
        .coerceAtLeast(0)
}
