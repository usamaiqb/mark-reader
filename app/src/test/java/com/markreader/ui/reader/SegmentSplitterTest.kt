package com.markreader.ui.reader

import com.markreader.ui.markdown.CodeBlockMarkerSpan
import com.markreader.ui.markdown.TableMarkerSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentSplitterTest {

    private fun spanned(
        text: String,
        vararg spans: FakeSpanned.SpanRange
    ) = FakeSpanned(text, spans.toList())

    private fun code(start: Int, end: Int) =
        FakeSpanned.SpanRange(CodeBlockMarkerSpan(), start, end)

    private fun table(start: Int, end: Int) =
        FakeSpanned.SpanRange(TableMarkerSpan(), start, end)

    @Test
    fun `text with no markers is one text segment`() {
        val segments = splitByMarkers(spanned("hello world"), splitCode = true, splitTables = true)

        assertEquals(listOf(Segment(0, 11, SegmentType.Text)), segments)
    }

    @Test
    fun `empty text still yields one segment`() {
        val segments = splitByMarkers(spanned(""), splitCode = true, splitTables = true)

        assertEquals(listOf(Segment(0, 0, SegmentType.Text)), segments)
    }

    @Test
    fun `a marker in the middle splits the text either side of it`() {
        val segments = splitByMarkers(
            spanned("aaaaCODEbbbb", code(4, 8)),
            splitCode = true,
            splitTables = true
        )

        assertEquals(
            listOf(
                Segment(0, 4, SegmentType.Text),
                Segment(4, 8, SegmentType.Code),
                Segment(8, 12, SegmentType.Text)
            ),
            segments
        )
    }

    @Test
    fun `markers at both ends leave no empty text segments`() {
        val segments = splitByMarkers(
            spanned("CODEmiddleTBL", code(0, 4), table(10, 13)),
            splitCode = true,
            splitTables = true
        )

        assertEquals(
            listOf(
                Segment(0, 4, SegmentType.Code),
                Segment(4, 10, SegmentType.Text),
                Segment(10, 13, SegmentType.Table)
            ),
            segments
        )
    }

    @Test
    fun `segments stay ordered when spans arrive out of order`() {
        val segments = splitByMarkers(
            spanned("xxTBLxxCODExx", table(2, 5), code(7, 11)),
            splitCode = true,
            splitTables = true
        )

        assertEquals(
            listOf(
                Segment(0, 2, SegmentType.Text),
                Segment(2, 5, SegmentType.Table),
                Segment(5, 7, SegmentType.Text),
                Segment(7, 11, SegmentType.Code),
                Segment(11, 13, SegmentType.Text)
            ),
            segments
        )
    }

    @Test
    fun `abutting markers produce no zero-length gap`() {
        val segments = splitByMarkers(
            spanned("CODETBL!", code(0, 4), table(4, 7)),
            splitCode = true,
            splitTables = true
        )

        assertEquals(
            listOf(
                Segment(0, 4, SegmentType.Code),
                Segment(4, 7, SegmentType.Table),
                Segment(7, 8, SegmentType.Text)
            ),
            segments
        )
    }

    @Test
    fun `code markers are ignored when code is not being split out`() {
        val text = spanned("aaaaCODEbbbb", code(4, 8))

        assertEquals(
            listOf(Segment(0, 12, SegmentType.Text)),
            splitByMarkers(text, splitCode = false, splitTables = true)
        )
    }

    @Test
    fun `table markers are ignored when tables are not being split out`() {
        val text = spanned("aaaaTBLbbbb", table(4, 7))

        assertEquals(
            listOf(Segment(0, 11, SegmentType.Text)),
            splitByMarkers(text, splitCode = true, splitTables = false)
        )
    }

    @Test
    fun `marker detection is independent of the split flags`() {
        val text = spanned("aaaaCODEbbbb", code(4, 8))

        assertTrue(hasCodeBlocks(text))
        assertFalse(hasTables(text))
    }
}
