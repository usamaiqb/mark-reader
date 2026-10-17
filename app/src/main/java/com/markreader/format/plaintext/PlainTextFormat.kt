package com.markreader.format.plaintext

import com.markreader.R
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.ReaderContent
import com.markreader.format.RenderTheme

/**
 * Text that renders as itself.
 *
 * Every capability is false and [load] cannot fail, which is what makes this the floor the other
 * formats are measured against: a `.txt` gets no table of contents, no view-mode toggle, no
 * preview pane and no formatting toolbar, because none of those mean anything without markup.
 *
 * It also stands in for CSV until P5. A `.csv` read as plain text is at least honest about what
 * it is showing, which is more than Markdown prose was.
 */
object PlainTextFormat : DocumentFormat {

    override val capabilities = FormatCapabilities()

    override val nameRes = R.string.viewer_subtitle_plain_text

    override val editorPlaceholderRes = R.string.editor_placeholder_text

    override val creationMimeType = "text/plain"

    /** Nothing to fall back to: this format's rendering is its source. */
    override val renderFailureWarningRes: Int? = null

    // Handed over as-is. [ReaderContent.Spans] takes a CharSequence precisely so that a format
    // with no styling to add does not copy the whole document into a Spannable to say so.
    override suspend fun load(text: String, theme: RenderTheme): ReaderContent =
        ReaderContent.Spans(text)
}
