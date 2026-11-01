package com.markreader.format

import com.markreader.file.DocumentType
import com.markreader.format.code.SourceCodeFormat
import com.markreader.format.markdown.MarkdownFormat
import com.markreader.format.plaintext.PlainTextFormat

/**
 * The [DocumentType] to [DocumentFormat] lookup.
 *
 * `architecture.md` seam 4 sketched this as a plain list, ordered by precedence. Precedence was
 * for *recognition* — walk the list, first format that claims the file wins — and formats no
 * longer recognise anything, so a list would carry an ordering that means nothing. What protects
 * us instead is exhaustiveness: [formatFor] is a `when` over a sealed interface, so adding a
 * document type fails to compile until someone decides which format reads it. A CSV format is
 * still one
 * field and one branch.
 *
 * Not a singleton, and not injected: one instance per view model. It used to hold the formats'
 * renderers for as long as the screen lived, which is why. Nothing caches a rendering on this side
 * any more — the page does — so what is left is a lookup, and [markdown] is a seam for tests.
 */
class FormatRegistry(private val markdown: DocumentFormat = MarkdownFormat) {

    /** One format per language seen. Bounded by the languages opened on one screen. */
    private val sourceCode = mutableMapOf<String, DocumentFormat>()

    /**
     * The format that reads [type], or **null for [DocumentType.Binary]** — bytes that are not
     * text have no format, and that is the one case the caller has to handle rather than render.
     */
    fun formatFor(type: DocumentType): DocumentFormat? = when (type) {
        DocumentType.Markdown -> markdown
        is DocumentType.SourceCode -> sourceCode.getOrPut(type.language) {
            SourceCodeFormat(type.language)
        }
        // Csv reads as what it also is, until it gets a format of its own.
        DocumentType.Csv, DocumentType.PlainText -> PlainTextFormat
        DocumentType.Binary -> null
    }
}
