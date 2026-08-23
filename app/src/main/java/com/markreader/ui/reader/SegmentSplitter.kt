package com.markreader.ui.reader

import android.text.Spanned
import com.markreader.ui.markdown.CodeBlockMarkerSpan
import com.markreader.ui.markdown.TableMarkerSpan

internal enum class SegmentType { Text, Code, Table }

/** A half-open `[start, end)` range of the rendered text and how it is displayed. */
internal data class Segment(val start: Int, val end: Int, val type: SegmentType)

/**
 * Cut [text] into segments at the code-block and table markers the renderer left
 * behind, so each one can get its own view. The gaps between markers become
 * [SegmentType.Text] segments; a document with no markers yields a single one
 * spanning the whole text.
 */
internal fun splitByMarkers(
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

internal fun hasCodeBlocks(text: Spanned): Boolean {
    return text.getSpans(0, text.length, CodeBlockMarkerSpan::class.java).isNotEmpty()
}

internal fun hasTables(text: Spanned): Boolean {
    return text.getSpans(0, text.length, TableMarkerSpan::class.java).isNotEmpty()
}
