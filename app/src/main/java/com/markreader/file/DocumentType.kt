package com.markreader.file

/**
 * What a document is, as far as the app is concerned.
 *
 * Deliberately not a renderer's vocabulary. [SourceCode] carries the language's own name,
 * and mapping that to a highlighter grammar is the renderer's business — so a Rust file is
 * Rust here even where the highlighter has to borrow another grammar to colour it.
 *
 * "Unknown" is [PlainText], not [Markdown]. A file we cannot place is text, not prose.
 */
sealed interface DocumentType {
    data object Markdown : DocumentType

    data class SourceCode(val language: String) : DocumentType

    data object Csv : DocumentType

    data object PlainText : DocumentType

    data object Binary : DocumentType
}
