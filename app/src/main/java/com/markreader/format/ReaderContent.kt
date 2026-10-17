package com.markreader.format

/**
 * What a [DocumentFormat] produces, and the only thing the reader surface knows how to draw.
 *
 * This is Fossify Documents' `StructuredDocumentContent` idea — one renderer, several loaders —
 * and it is what lets a format that produces a table share a screen with one that produces
 * prose. The set is closed on purpose: three shapes, and a fourth format has to fit one of them
 * or argue for a fourth.
 *
 * Lives here rather than beside `ReaderContentView` so that the format layer never imports UI.
 * It moves to `render/` if and when that package is carved out with the dependency running that
 * way.
 */
sealed interface ReaderContent {
    /**
     * A rendered document as HTML, for the WebView host to load. Produced from P6a, when the
     * shared renderer replaces Markwon.
     */
    data class Html(val html: String) : ReaderContent

    /**
     * A document as text, styled or not.
     *
     * [text] is a `CharSequence` rather than a `Spanned` because unstyled text is a real case,
     * not a placeholder for one: raw mode and the plain-text format both hand over a `String`,
     * and wrapping those in a `SpannableString` would copy up to `MAX_FILE_BYTES` for no styling
     * benefit. Consumers that need markers — split mode, which reads them to give code blocks
     * and tables their own scrollable panes — narrow to `Spanned` at the point of that
     * requirement.
     *
     * The Markwon and Prism paths today; after P6a it survives for raw mode, which needs no
     * renderer at all.
     */
    data class Spans(val text: CharSequence) : ReaderContent

    /** A tabular document, drawn by `TableRenderer`. Produced from P5, by the CSV format. */
    data class Table(val rows: List<List<String>>) : ReaderContent
}
