package com.markreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSearchMatchesTest {
    private fun starts(matches: NativeSearchMatches) = (0 until matches.size).map(matches::start)

    @Test fun literalCaseInsensitiveAndNonOverlapping() {
        assertEquals(listOf(0, 4), starts(NativeSearchMatches.find("A.b a.B axb", "a.b")))
        assertEquals(listOf(0, 2), starts(NativeSearchMatches.find("aaaaa", "aa")))
        assertEquals(listOf(0, 2), starts(NativeSearchMatches.find("ababa", "ab")))
    }

    @Test fun unicodeOffsetsRemainUtf16() {
        val result = NativeSearchMatches.find("😀Ä Σςσ İiı", "ä")
        assertEquals(listOf(2), starts(result))
        assertEquals(3, result.end(0))
        assertEquals(listOf(4, 5, 6), starts(NativeSearchMatches.find("😀Ä Σςσ İiı", "σ")))
        assertEquals(3, NativeSearchMatches.find("İiı", "i").size)
    }

    @Test fun emptyBlankAndTooLongQueries() {
        for (query in listOf("", " ", "\n", "long query")) {
            assertEquals(0, NativeSearchMatches.find("abc", query).size)
        }
        assertEquals(0, NativeSearchMatches.find("", "a").size)
    }

    @Test fun binaryLookupUsesExclusiveEnd() {
        val result = NativeSearchMatches.find("ab--ab--ab", "ab")
        assertEquals(0, result.firstIntersecting(-1))
        assertEquals(0, result.firstIntersecting(1))
        assertEquals(1, result.firstIntersecting(2))
        assertEquals(1, result.firstIntersecting(4))
        assertEquals(3, result.firstIntersecting(10))
    }

    @Test fun denseResultsAreNotCappedAndNavigationSharesStorage() {
        val source = "x".repeat(20_000)
        val matches = NativeSearchMatches.find(source, "x")
        assertEquals(20_000, matches.size)
        val original = NativeSearchText(source, matches, 0, 1, 2)
        val next = original.withActiveIndex(19_999)
        assertSame(source, next.source)
        assertSame(matches, next.matches)
        assertEquals(19_999, next.activeIndex)
        assertEquals(19_999, next.matches.start(next.activeIndex))
    }

    @Test fun repetitiveLongPrefixHasLinearCharacterReads() {
        var reads = 0
        val source = "a".repeat(100_000)
        val counted = object : CharSequence by source {
            override fun get(index: Int): Char { reads++; return source[index] }
        }
        assertEquals(0, NativeSearchMatches.find(counted, "a".repeat(5_000) + "b").size)
        assertTrue("Expected linear reads, got $reads", reads < source.length * 5)
    }

    @Test fun cancellationRunsWithoutAnyMatches() {
        var checks = 0
        val cancelled = IllegalStateException("cancelled")
        try {
            NativeSearchMatches.find("a".repeat(100_000), "b") {
                if (++checks == 4) throw cancelled
            }
            throw AssertionError("Expected cancellation")
        } catch (error: IllegalStateException) {
            assertSame(cancelled, error)
        }
        assertEquals(4, checks)
    }

    @Test fun cancellationRunsDuringLongQueryPreprocessing() {
        var checks = 0
        try {
            NativeSearchMatches.find("a".repeat(100_000), "a".repeat(50_000)) {
                if (++checks == 2) error("cancelled")
            }
            throw AssertionError("Expected cancellation")
        } catch (_: IllegalStateException) {
            assertEquals(2, checks)
        }
    }
}
