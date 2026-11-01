package com.markreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** A layout of uniform lines: [charsPerLine] characters and [lineHeight] pixels each. */
private class FakeLines(
    private val charsPerLine: Int = 10,
    private val lineHeight: Int = 20
) : LineMetrics {
    override fun getLineForOffset(offset: Int): Int = offset / charsPerLine
    override fun getLineTop(line: Int): Int = line * lineHeight
}

/**
 * The anchor math, over the one container shape and the one direction there are.
 *
 * The split container's half of this — segments, a table anchored at segment granularity, an
 * unmeasured segment resolving to null rather than to the top of the document — went with the
 * marker spans that drove it, and the capture half went with the rebuild that was its only
 * caller. What a search result still needs is this.
 */
class ScrollAnchorTest {

    @Test
    fun `a TextView resolves through its top padding`() {
        assertEquals(56, scrollYForOffsetInText(FakeLines(), paddingTop = 16, offset = 25))
    }

    @Test
    fun `an offset in the first line resolves to the padding alone`() {
        assertEquals(16, scrollYForOffsetInText(FakeLines(), paddingTop = 16, offset = 3))
    }
}
