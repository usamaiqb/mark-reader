package com.markreader.ui.reader

/** Sorted, non-overlapping UTF-16 ranges. No per-match objects or Android dependencies. */
class NativeSearchMatches private constructor(private val bounds: IntArray) {
    val size: Int get() = bounds.size / 2
    fun start(index: Int): Int = bounds[index * 2]
    fun end(index: Int): Int = bounds[index * 2 + 1]

    /** First range whose end lies after [offset], in O(log matches). */
    fun firstIntersecting(offset: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (end(mid) <= offset) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        private const val CANCELLATION_CHECK_MASK = 1023
        private const val INITIAL_BOUNDS_CAPACITY = 32

        val Empty = NativeSearchMatches(IntArray(0))

        /**
         * Literal, case-insensitive, non-overlapping search. Check cancellation even on a
         * document with no matches (Regex.findAll/indexOf can otherwise monopolise a worker).
         * UTF-16 offsets remain valid for Android Layout; no lowercase copy changes lengths.
         */
        fun find(text: CharSequence, query: String, checkCancelled: () -> Unit = {}): NativeSearchMatches {
            if (query.isBlank() || query.length > text.length) return Empty
            // KMP avoids quadratic work for long near-matching queries (aaaa...ab).
            val prefix = buildPrefix(query, checkCancelled)
            var bounds = IntArray(INITIAL_BOUNDS_CAPACITY)
            var used = 0
            var matched = 0
            for (offset in text.indices) {
                if ((offset and CANCELLATION_CHECK_MASK) == 0) checkCancelled()
                while (matched > 0 && !text[offset].equals(query[matched], ignoreCase = true)) {
                    matched = prefix[matched - 1]
                }
                if (text[offset].equals(query[matched], ignoreCase = true)) matched++
                if (matched == query.length) {
                    if (used == bounds.size) bounds = bounds.copyOf(bounds.size * 2)
                    bounds[used++] = offset - query.length + 1
                    bounds[used++] = offset + 1
                    matched = 0 // Preserve the original non-overlapping search semantics.
                }
            }
            checkCancelled()
            return if (used == 0) Empty else NativeSearchMatches(bounds.copyOf(used))
        }

        private fun buildPrefix(query: String, checkCancelled: () -> Unit): IntArray {
            val prefix = IntArray(query.length)
            var matched = 0
            for (i in 1 until query.length) {
                if ((i and CANCELLATION_CHECK_MASK) == 0) checkCancelled()
                while (matched > 0 && !query[i].equals(query[matched], ignoreCase = true)) {
                    matched = prefix[matched - 1]
                }
                if (query[i].equals(query[matched], ignoreCase = true)) matched++
                prefix[i] = matched
            }
            return prefix
        }
    }
}

/** Search decoration, kept separate from the original text and all of its formatting spans. */
class NativeSearchText(
    val source: CharSequence,
    val matches: NativeSearchMatches,
    val activeIndex: Int,
    val passiveColor: Int,
    val activeColor: Int
) : CharSequence by source {
    override fun toString(): String = source.toString()

    fun withActiveIndex(index: Int) = NativeSearchText(source, matches, index, passiveColor, activeColor)
}
