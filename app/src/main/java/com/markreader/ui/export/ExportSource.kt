package com.markreader.ui.export

import com.markreader.format.ReaderContent
import com.markreader.format.code.fenced

/**
 * The text an export renders, for a document the reader has already decided how to draw.
 *
 * Export is "what you see", and what you see is [ReaderContent] — not the file. Asking the format
 * rather than reading the source is what stops a `.kt` file exporting as mis-parsed Markdown
 * prose, which is what happened for as long as export ran a Markdown parser of its own over the
 * raw text of whatever was open.
 *
 * It is emphatically *not* what is on screen at the moment the sheet opens. Raw mode shows the
 * source; "Export as PDF — print-ready document" does not offer to print the source, so the mode
 * is not consulted here. [rendered] is the format's own answer, which raw mode leaves untouched.
 *
 * @param rendered what the format made of the document, or null if it made nothing.
 * @param rawText the document's source, which is all there is to fall back on.
 */
fun exportSourceFor(rendered: ReaderContent?, rawText: String): String = when (rendered) {
    // The renderer's own language. `SourceCodeFormat` has already fenced its document by here,
    // which is why a code file exports highlighted rather than as prose.
    is ReaderContent.Markdown -> rendered.source

    // Text that renders as itself — plain text, and any format whose rendering is its source.
    // Fencing it is how it reaches a Markdown renderer at all, and it keeps the exported file
    // preformatted rather than reflowing a document nobody wrote paragraphs into. The visible
    // cost is the font: on screen a `.txt` is drawn in the reading font, and a fenced block is
    // monospace. Line structure is the property worth keeping of the two, and this goes away with
    // the fencing when the renderer takes a document kind.
    //
    // The second call site of [fenced], and deliberately so: when that happens, both go at once.
    // A third implementation here would be one more thing for the change to find.
    is ReaderContent.Spans -> fenced(rendered.text.toString(), language = "")

    // No producer today; the planned CSV format is the first. Its source is the honest answer
    // until there is a rendering to ask about — a table has no Markdown form to offer.
    is ReaderContent.Table,
    null -> fenced(rawText, language = "")
}
