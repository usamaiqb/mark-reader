package com.markreader.format.markdown

import com.markreader.R
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.ReaderContent

/**
 * Markdown, handed to the shared renderer as source.
 *
 * An object with no state, and that is the shape the whole migration was heading for. It used to
 * take a renderer factory and cache one Markwon per surface theme, because building Markwon cost
 * milliseconds and a theme toggle re-rendered the document. Both surfaces that draw Markdown are
 * the page now — the reader and the editor's preview — and the page owns its own rendering, so
 * there is nothing here to build, cache or invalidate.
 */
object MarkdownFormat : DocumentFormat {

    override val capabilities = FormatCapabilities(
        hasHeadings = true,
        hasRawView = true,
        isMonospaced = false,
        hasEditorPreview = true,
        hasFormattingToolbar = true
    )

    override val nameRes = R.string.viewer_subtitle_markdown

    override val editorPlaceholderRes = R.string.editor_placeholder_markdown

    override val creationMimeType = "text/markdown"

    /**
     * Null: handing text over cannot fail.
     *
     * The Markwon path could — a malformed document, and commonmark and Markwon between them had
     * a great many ways to throw on one — and it fell back to the source with a warning. Nothing
     * here parses anything. A renderer that cannot start at all is a *host* failure rather than a
     * format's, and the reader falls back on its own for that.
     */
    override val renderFailureWarningRes: Int? = null

    /** The source, unchanged. The page renders it; nothing on this side does. */
    override suspend fun load(text: String): ReaderContent =
        ReaderContent.Markdown(text)
}
