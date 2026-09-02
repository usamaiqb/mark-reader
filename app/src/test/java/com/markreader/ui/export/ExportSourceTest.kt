package com.markreader.ui.export

import com.markreader.format.ReaderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What export renders, which is the format's rendering and not the file.
 *
 * The distinction is the whole point of taking export off its own Markdown parser: for as long
 * as it had one, every document was parsed as Markdown prose on the way out, so a Kotlin file
 * exported with its comments as paragraphs and its indented lines as code blocks.
 */
class ExportSourceTest {

    @Test
    fun `markdown exports as the source the renderer was given`() {
        assertEquals(
            "# Title",
            exportSourceFor(ReaderContent.Markdown("# Title"), rawText = "ignored"),
        )
    }

    @Test
    fun `a fenced code document keeps its fence`() {
        // What `SourceCodeFormat` produces. The fence is how a code file reaches a Markdown
        // renderer at all, so export must carry it rather than unwrap it — unwrapping is exactly
        // the mis-parse this replaced.
        val fenced = ReaderContent.Markdown("```kotlin\nfun main() {}\n```")

        assertEquals(fenced.source, exportSourceFor(fenced, rawText = "fun main() {}"))
    }

    @Test
    fun `plain text is fenced rather than parsed`() {
        val source = exportSourceFor(ReaderContent.Spans("* not a list\n# not a heading"), "")

        assertTrue("plain text was not fenced: $source", source.startsWith("```\n"))
        assertTrue(source.contains("* not a list\n# not a heading"))
    }

    @Test
    fun `plain text containing a fence gets a longer one`() {
        // Delegated to the same `fenced` the source-code format uses, so a document that contains
        // a fence cannot close its own block. Asserted here because export is the second caller,
        // and a reimplementation would pass every test above and fail this one.
        val source = exportSourceFor(ReaderContent.Spans("```\nnot really code\n```"), "")

        assertTrue("the fence was not lengthened: $source", source.startsWith("````\n"))
    }

    @Test
    fun `a document the format made nothing of falls back to its text`() {
        val source = exportSourceFor(rendered = null, rawText = "whatever was read")

        assertTrue(source.contains("whatever was read"))
    }
}
