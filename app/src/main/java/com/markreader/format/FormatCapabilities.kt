package com.markreader.format

/**
 * What the chrome around a document may offer.
 *
 * These used to be ten separate `is this Markdown` / `is this source code` tests spread across
 * the viewer and the editor, which is why adding a third format meant editing ten call sites.
 * Each field here exists because one of those sites asked the question — a capability with no
 * caller is a comment pretending to be code, so none are declared ahead of a use.
 *
 * The defaults describe the least capable document there is: text that renders as itself.
 */
data class FormatCapabilities(
    /**
     * The document has a heading structure, so a table of contents is meaningful.
     *
     * Says only that the *format* has headings, not where they are. The outline itself comes from
     * whatever drew the document — for the shared renderer, from the page — because a heading's
     * position is a fact about the rendering. Without this a code file, which reaches the renderer
     * as one fenced block, would offer a table of contents that is always empty.
     */
    val hasHeadings: Boolean = false,
    /**
     * The rendered form differs from the source, so the raw/rendered toggle does something.
     * Where it is false the chrome names the format instead of claiming a view mode, because
     * "Rendered" is not a true thing to say about a document whose rendering *is* its source.
     */
    val hasRawView: Boolean = false,
    /** Read and edited in the code font rather than the reading font. */
    val isMonospaced: Boolean = false,
    /** The editor offers Edit/Preview tabs and can render a live preview. */
    val hasEditorPreview: Boolean = false,
    /** The editor offers the markup formatting toolbar. */
    val hasFormattingToolbar: Boolean = false
)
