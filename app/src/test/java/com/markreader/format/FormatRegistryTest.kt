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
 * The markdown format is constructed with a factory that throws, which is the point of taking
 * one: everything asserted here is decided without a `Context` or a renderer.
 */
class FormatRegistryTest {

    private val markdown = MarkdownFormat { error("no renderer is needed for these assertions") }
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
        assertEquals(R.string.viewer_warning_markdown, markdown.renderFailureWarningRes)
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
    fun `a language keeps its format, so the highlighter cache survives a theme toggle`() {
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

    @Test
    fun `invalidation reaches every format the registry has handed out`() {
        val recording = RecordingFormat()
        val withRecording = FormatRegistry(recording)
        val code = withRecording.formatFor(DocumentType.SourceCode("kotlin"))

        withRecording.invalidateRenderers()

        assertEquals(1, recording.invalidations)
        // The source-code format caches renderers too, so a stale one there would paint the
        // previous palette just as visibly.
        assertSame(code, withRecording.formatFor(DocumentType.SourceCode("kotlin")))
    }

    private class RecordingFormat : DocumentFormat {
        var invalidations = 0
        override val capabilities = FormatCapabilities()
        override val nameRes = R.string.viewer_subtitle_markdown
        override val editorPlaceholderRes = R.string.editor_placeholder_markdown
        override val creationMimeType = "text/markdown"
        override val renderFailureWarningRes = R.string.viewer_warning_markdown
        override suspend fun load(text: String, theme: RenderTheme): ReaderContent? = null
        override fun invalidateRenderers() {
            invalidations++
        }
    }
}
