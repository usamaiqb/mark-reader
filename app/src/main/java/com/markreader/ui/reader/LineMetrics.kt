package com.markreader.ui.reader

import android.text.Layout

/**
 * The part of a laid-out [Layout] the anchor math in `ScrollAnchor.kt` reads.
 *
 * A seam rather than a direct call so the math can be exercised without inflating anything: what
 * it has to get right is a layout that is *absent*, which is awkward to stage with real views and
 * easy to get wrong — returning 0 instead of null once sent the viewer to the top of the document.
 *
 * **Two members, because two are read.** It had four while there was a second container shape to
 * anchor and an anchor to capture; both went with the marker spans that drove them.
 */
internal interface LineMetrics {
    fun getLineForOffset(offset: Int): Int
    fun getLineTop(line: Int): Int
}

private class LayoutLineMetrics(private val layout: Layout) : LineMetrics {
    override fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset)
    override fun getLineTop(line: Int): Int = layout.getLineTop(line)
}

internal fun Layout.lineMetrics(): LineMetrics = LayoutLineMetrics(this)
