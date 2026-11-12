package com.markreader.format

import com.markreader.R
import com.markreader.file.DocumentType
import com.markreader.format.markdown.MarkdownFormat
import com.markreader.format.plaintext.PlainTextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The registry's whole job is the [DocumentType] to [DocumentFormat] mapping and the capability
 * table that hangs off it — which is what replaced ten `is this Markdown` tests in the UI, so it
 * is worth pinning rather than trusting to a reading of the `when`.
 *
 * Nothing here needs a `Context`: no format on this side holds a renderer any more. The registry
 * still takes its markdown format as an argument, which is the seam [RecordingFormat] uses.
 */
class FormatRegistryTest {

    private val markdown = MarkdownFormat
    private val registry = FormatRegistry(markdown)

    @Test
    fun `markdown maps to the markdown format`() {
        assertSame(markdown, registry.formatFor(DocumentType.Markdown))
    }

    @Test
    fun `markdown offers everything`() {
        assertEquals(
            FormatCapabilities(
                hasHeadings = true,
                hasRawView = true,
                isMonospaced = false,
                hasEditorPreview = true,
                hasFormattingToolbar = true
            ),
            markdown.capabilities
        )
        assertEquals("text/markdown", markdown.creationMimeType)
        // Handing source to the page cannot fail, so there is no failure to describe.
        assertNull(markdown.renderFailureWarningRes)
    }

    @Test
    fun `source code is monospaced and nothing else`() {
        val format = registry.formatFor(DocumentType.SourceCode("kotlin"))!!
        assertEquals(FormatCapabilities(isMonospaced = true), format.capabilities)
        assertEquals(R.string.viewer_subtitle_source_code, format.nameRes)
        // Naming the language here would have the picker suggest an extension for a language
        // the user has not chosen yet.
        assertEquals("text/plain", format.creationMimeType)
    }

    @Test
    fun `a language keeps its format`() {
        val first = registry.formatFor(DocumentType.SourceCode("kotlin"))
        assertSame(first, registry.formatFor(DocumentType.SourceCode("kotlin")))
        assertNotSame(first, registry.formatFor(DocumentType.SourceCode("python")))
    }

    @Test
    fun `plain text offers nothing, and cannot fail`() {
        val format = registry.formatFor(DocumentType.PlainText)!!
        assertSame(PlainTextFormat, format)
        assertEquals(FormatCapabilities(), format.capabilities)
        assertNull(format.renderFailureWarningRes)
    }

    /** Until CSV gets a format. A `.csv` read as text is at least honest, which prose was not. */
    @Test
    fun `csv reads as plain text for now`() {
        assertSame(PlainTextFormat, registry.formatFor(DocumentType.Csv))
    }

    /** The one case a caller has to handle rather than render. */
    @Test
    fun `binary has no format`() {
        assertNull(registry.formatFor(DocumentType.Binary))
    }

    /** The registry maps; it does not substitute. Whatever it was given is what it hands out. */
    @Test
    fun `the markdown format is the one it was given`() {
        val recording = RecordingFormat()
        val withRecording = FormatRegistry(recording)

        assertSame(recording, withRecording.formatFor(DocumentType.Markdown))
    }

    private class RecordingFormat : DocumentFormat {
        override val capabilities = FormatCapabilities()
        override val nameRes = R.string.viewer_subtitle_markdown
        override val editorPlaceholderRes = R.string.editor_placeholder_markdown
        override val creationMimeType = "text/markdown"
        override val renderFailureWarningRes: Int? = null
        override suspend fun load(text: String): ReaderContent? = null
    }
}
