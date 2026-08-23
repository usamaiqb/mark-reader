package com.markreader.ui.reader

import android.text.Spanned
import java.lang.reflect.Array as ReflectArray

/**
 * A [Spanned] that is just a string plus a list of ranges.
 *
 * `SpannableString` is unusable here — the stubbed `android.jar` the JVM test
 * runs against has no implementation behind it — and the splitter only ever
 * asks a Spanned for its length and its spans, so a hand-rolled one is enough.
 */
internal class FakeSpanned(
    private val text: String,
    private val spans: List<SpanRange> = emptyList()
) : Spanned {

    data class SpanRange(val span: Any, val start: Int, val end: Int)

    override val length: Int get() = text.length

    override fun get(index: Int): Char = text[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        text.substring(startIndex, endIndex)

    override fun toString(): String = text

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
        val matches = spans.filter {
            type.isInstance(it.span) && it.start < end && it.end > start
        }
        val array = ReflectArray.newInstance(type, matches.size) as Array<T>
        matches.forEachIndexed { i, match -> array[i] = match.span as T }
        return array
    }

    override fun getSpanStart(tag: Any?): Int = spans.firstOrNull { it.span === tag }?.start ?: -1

    override fun getSpanEnd(tag: Any?): Int = spans.firstOrNull { it.span === tag }?.end ?: -1

    override fun getSpanFlags(tag: Any?): Int = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = limit
}
