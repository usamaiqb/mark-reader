package com.markreader.ui.reader

import com.markreader.ui.screens.HeadingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A layout of uniform lines: [charsPerLine] characters and [lineHeight] pixels each. */
private class FakeLines(
    override val textLength: Int,
    private val charsPerLine: Int = 10,
    private val lineHeight: Int = 20
) : LineMetrics {
    override fun getLineForOffset(offset: Int): Int = offset / charsPerLine
    override fun getLineForVertical(y: Int): Int = y / lineHeight
    override fun getLineTop(line: Int): Int = line * lineHeight
    override fun getLineStart(line: Int): Int = line * charsPerLine
}

private fun measured(top: Int, height: Int, textLength: Int) =
    SegmentPlacement(top, height, hasText = true, lines = FakeLines(textLength))

/** A TextView that exists but has not built its Layout yet. */
private fun unmeasured(top: Int, height: Int) =
    SegmentPlacement(top, height, hasText = true, lines = null)

/** A table: no TextView at all, so anchorable only at segment granularity. */
private fun table(top: Int, height: Int) =
    SegmentPlacement(top, height, hasText = false, lines = null)

class ScrollAnchorTest {

    private val boundaries = listOf(
        Segment(0, 100, SegmentType.Text),
        Segment(100, 150, SegmentType.Table),
        Segment(150, 300, SegmentType.Text)
    )

    private fun placements(vararg placements: SegmentPlacement): (Int) -> SegmentPlacement =
        { placements[it] }

    // ---------- resolving an offset back to a scroll position ----------

    @Test
    fun `an unmeasured segment resolves to null, not to the top of the document`() {
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            isLaidOut = true,
            offset = 160,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                unmeasured(300, 300)
            )
        )

        assertNull(y)
    }

    @Test
    fun `a container that has not been laid out resolves to null`() {
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            isLaidOut = false,
            offset = 10,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        assertNull(y)
    }

    @Test
    fun `an offset inside a measured segment resolves to that line`() {
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            isLaidOut = true,
            offset = 175,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        // Offset 175 is 25 into the segment: line 2, whose top is 40 past the
        // segment's own top of 300.
        assertEquals(340, y)
    }

    @Test
    fun `an offset inside a table resolves to the table's top`() {
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            isLaidOut = true,
            offset = 120,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        assertEquals(200, y)
    }

    @Test
    fun `an offset past every boundary resolves to null`() {
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            isLaidOut = true,
            offset = 900,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        assertNull(y)
    }

    @Test
    fun `an offset in a segment with no view yet resolves to null`() {
        // Boundaries were rebuilt but the container has only its first child.
        val y = scrollYForOffsetInSplit(
            boundaries = boundaries,
            childCount = 1,
            isLaidOut = true,
            offset = 175,
            placementAt = placements(measured(0, 200, 100))
        )

        assertNull(y)
    }

    @Test
    fun `a single TextView resolves through its top padding`() {
        assertEquals(56, scrollYForOffsetInText(FakeLines(100), paddingTop = 16, offset = 25))
    }

    // ---------- capturing the offset at the top of the viewport ----------

    @Test
    fun `the anchor is the first line of the topmost visible segment`() {
        val offset = anchorOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            scrollY = 250,
            placementAt = placements(
                measured(0, 200, 100),
                measured(200, 100, 50),
                measured(300, 300, 150)
            )
        )

        // 50px into the second segment: line 2, which starts at local offset 20.
        assertEquals(120, offset)
    }

    @Test
    fun `an unmeasured topmost segment anchors at its start`() {
        val offset = anchorOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            scrollY = 250,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        assertEquals(100, offset)
    }

    @Test
    fun `scrolled past every segment anchors at the last one`() {
        val offset = anchorOffsetInSplit(
            boundaries = boundaries,
            childCount = 3,
            scrollY = 10_000,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        assertEquals(150, offset)
    }

    @Test
    fun `a split container with no boundaries has no anchor`() {
        val offset = anchorOffsetInSplit(
            boundaries = emptyList(),
            childCount = 0,
            scrollY = 0,
            placementAt = { throw AssertionError("no segments to look at") }
        )

        assertNull(offset)
    }

    @Test
    fun `a single TextView with no layout has no anchor`() {
        assertNull(anchorOffsetInText(lines = null, scrollY = 400, paddingTop = 16))
    }

    @Test
    fun `a single TextView anchors below its top padding`() {
        // 400 - 16 = 384, which falls in line 19, starting at offset 190.
        assertEquals(190, anchorOffsetInText(FakeLines(300), scrollY = 400, paddingTop = 16))
    }

    // ---------- active heading ----------

    private val headings = listOf(
        HeadingItem("Intro", 1, 0),
        HeadingItem("Middle", 2, 35),
        HeadingItem("End", 2, 72)
    )

    @Test
    fun `no heading is active above the first one`() {
        assertEquals(-1, findActiveHeadingIndex(FakeLines(100), headings, y = -1))
    }

    @Test
    fun `the heading exactly at the viewport top is active`() {
        // Offsets 0, 35, 72 sit on lines 0, 3 and 7 — tops 0, 60 and 140.
        assertEquals(0, findActiveHeadingIndex(FakeLines(100), headings, y = 0))
        assertEquals(0, findActiveHeadingIndex(FakeLines(100), headings, y = 59))
        assertEquals(1, findActiveHeadingIndex(FakeLines(100), headings, y = 60))
        assertEquals(1, findActiveHeadingIndex(FakeLines(100), headings, y = 139))
        assertEquals(2, findActiveHeadingIndex(FakeLines(100), headings, y = 140))
        assertEquals(2, findActiveHeadingIndex(FakeLines(100), headings, y = 10_000))
    }

    @Test
    fun `a document with no headings has no active one`() {
        assertEquals(-1, findActiveHeadingIndex(FakeLines(100), emptyList(), y = 500))
    }

    @Test
    fun `a heading offset past the end of the text is clamped`() {
        val stale = listOf(HeadingItem("Stale", 1, 5_000))

        // Clamped to offset 99 — line 9, top 180.
        assertEquals(-1, findActiveHeadingIndex(FakeLines(100), stale, y = 179))
        assertEquals(0, findActiveHeadingIndex(FakeLines(100), stale, y = 180))
    }

    @Test
    fun `the split lookup picks the last heading above the viewport`() {
        val splitHeadings = listOf(
            HeadingItem("First", 1, 5),
            HeadingItem("Second", 2, 160),
            HeadingItem("Third", 2, 250)
        )

        val active = findActiveHeadingInSplit(
            boundaries = boundaries,
            childCount = 3,
            headings = splitHeadings,
            adjustedY = 320,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                measured(300, 300, 150)
            )
        )

        // "Second" is 10 into the third segment: line 1, top 300 + 20 = 320.
        // "Third" is at line 10, top 500, still below the viewport.
        assertEquals(1, active)
    }

    @Test
    fun `segments without a layout are skipped rather than guessed at`() {
        val splitHeadings = listOf(HeadingItem("Only", 1, 160))

        val active = findActiveHeadingInSplit(
            boundaries = boundaries,
            childCount = 3,
            headings = splitHeadings,
            adjustedY = 10_000,
            placementAt = placements(
                measured(0, 200, 100),
                table(200, 100),
                unmeasured(300, 300)
            )
        )

        assertEquals(-1, active)
    }
}
