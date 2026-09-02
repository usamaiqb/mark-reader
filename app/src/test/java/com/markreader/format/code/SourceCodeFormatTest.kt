package com.markreader.format.code

import com.markreader.format.ReaderContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A code file reaches the Markdown renderer as one fenced block, which is what gives it a real
 * highlight.js grammar. The fence length is the part that can go wrong.
 */
class SourceCodeFormatTest {

    private fun load(language: String, text: String): String {
        val content = runBlocking {
            SourceCodeFormat(language).load(text)
        }
        return (content as ReaderContent.Markdown).source
    }

    @Test
    fun `source becomes a fenced block tagged with its language`() {
        assertEquals("```kotlin\nfun main() {}\n```", load("kotlin", "fun main() {}"))
    }

    @Test
    fun `a document containing a fence gets a longer one`() {
        // Three backticks inside would close a three-backtick fence early, and the rest of the
        // file would render as prose.
        val source = load("markdown", "before\n```\ncode\n```\nafter")

        assertTrue(source.startsWith("````markdown\n"))
        assertTrue(source.endsWith("\n````"))
    }

    @Test
    fun `the fence outgrows the longest run, not the first one`() {
        val source = load("markdown", "```\na\n`````\nb\n````")

        assertTrue(source.startsWith("``````markdown\n"))
        assertTrue(source.endsWith("\n``````"))
    }

    @Test
    fun `inline backticks do not inflate the fence`() {
        assertTrue(load("python", "x = `y`").startsWith("```python\n"))
    }

    @Test
    fun `an empty file still produces a block`() {
        assertEquals("```rust\n\n```", load("rust", ""))
    }

    @Test
    fun `fencing cannot fail, so there is no failure to warn about`() {
        assertNull(SourceCodeFormat("kotlin").renderFailureWarningRes)
    }
}
