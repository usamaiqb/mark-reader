package com.markreader.format.plaintext

import com.markreader.format.ReaderContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floor format, and the one that pins down why [ReaderContent.Spans] carries a
 * `CharSequence` rather than a `Spanned`.
 */
class PlainTextFormatTest {

    private fun load(text: String): ReaderContent =
        runBlocking { PlainTextFormat.load(text, com.markreader.format.RenderTheme(isDark = false)) }

    @Test
    fun `the document is handed over without being copied`() {
        val source = "plain text, no markup\nand a second line"

        val content = load(source)

        // Identity, not equality: a format with no styling to add must not copy the document
        // into a Spannable to say so — at MAX_FILE_BYTES that copy is 10 MB.
        assertSame(source, (content as ReaderContent.Spans).text)
    }

    @Test
    fun `an empty document still loads`() {
        assertSame("", (load("") as ReaderContent.Spans).text)
    }

    @Test
    fun `rendering is the source, so there is no failure to warn about`() {
        assertNull(PlainTextFormat.renderFailureWarningRes)
    }

    @Test
    fun `no capability survives without markup`() {
        val capabilities = PlainTextFormat.capabilities

        assertFalse(capabilities.hasHeadings)
        assertFalse(capabilities.hasRawView)
        assertFalse(capabilities.hasEditorPreview)
        assertFalse(capabilities.hasFormattingToolbar)
        assertFalse(capabilities.isMonospaced)
    }

    @Test
    fun `headings are not offered, whatever the document says`() {
        assertTrue(PlainTextFormat.headings("# Not a heading here", load("# Not a heading here")).isEmpty())
    }
}
