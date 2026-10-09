package com.markreader.file

/**
 * The single place that decides what a document is.
 *
 * Two tiers, cheapest first. The display name and the provider's MIME type place almost
 * every file without looking at a byte; content is consulted only to overrule them, when
 * what arrived is not text at all.
 *
 * The name beats the MIME type where both speak, because storage providers routinely
 * report `text/plain` or `application/octet-stream` for everything they are handed.
 */
object DocumentTypeResolver {

    /**
     * Type from the hints alone. Never returns [DocumentType.Binary] — whether the bytes
     * are text is not a question a file name can answer. Callers holding the content
     * should use [resolve] instead.
     */
    fun resolveFromHints(fileName: String, mimeType: String? = null): DocumentType {
        val name = fileName.substringAfterLast('/').lowercase()
        val extension = name.substringAfterLast('.', "")
        val byName = if (extension.isEmpty()) {
            EXTENSIONLESS_NAMES[name]
        } else {
            byExtension(extension)
        }
        return byName ?: byMimeType(mimeType) ?: DocumentType.PlainText
    }

    /**
     * Type from the hints, with [content] overruling them when it does not look like text.
     * A `.md` file full of NUL bytes is binary whatever its name claims.
     */
    fun resolve(fileName: String, mimeType: String?, content: String): DocumentType =
        if (isProbablyBinary(content)) {
            DocumentType.Binary
        } else {
            resolveFromHints(fileName, mimeType)
        }

    /**
     * The blob tier. NUL anywhere, or more than 5% unprintable characters in the opening
     * sample, and the file is not something to render as text.
     */
    fun isProbablyBinary(text: String): Boolean {
        if (text.contains('\u0000')) return true
        val sample = text.take(BINARY_SAMPLE_CHARS)
        if (sample.isEmpty()) return false
        val nonPrintable = sample.count { it < ' ' && it != '\n' && it != '\r' && it != '\t' }
        return nonPrintable > sample.length / BINARY_UNPRINTABLE_RATIO
    }

    /** Backs [OPENABLE_MIME_TYPES]; see there for what the three groups are for. */
    internal fun openableMimeTypes(): Array<String> = (
        listOf("text/*") +
            MIME_TYPES.keys.filterNot { it.startsWith("text/") } +
            "application/octet-stream"
        ).toTypedArray()

    private fun byExtension(extension: String): DocumentType? = when (extension) {
        in MARKDOWN_EXTENSIONS -> DocumentType.Markdown
        in CSV_EXTENSIONS -> DocumentType.Csv
        in PLAIN_TEXT_EXTENSIONS -> DocumentType.PlainText
        else -> SOURCE_CODE_EXTENSIONS[extension]?.let { DocumentType.SourceCode(it) }
    }

    private fun byMimeType(mimeType: String?): DocumentType? {
        val bare = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return MIME_TYPES[bare]
    }

    private const val BINARY_SAMPLE_CHARS = 2000
    private const val BINARY_UNPRINTABLE_RATIO = 20

    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mkdn")

    private val CSV_EXTENSIONS = setOf("csv")

    private val PLAIN_TEXT_EXTENSIONS = setOf("txt", "text", "log")

    private val SOURCE_CODE_EXTENSIONS = mapOf(
        "java" to "java",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "py" to "python",
        "js" to "javascript",
        "mjs" to "javascript",
        "cjs" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "jsx" to "javascript",
        "c" to "c",
        "h" to "c",
        "cpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "hpp" to "cpp",
        "cs" to "csharp",
        "swift" to "swift",
        "go" to "go",
        "rs" to "rust",
        "rb" to "ruby",
        "scala" to "scala",
        "groovy" to "groovy",
        "gradle" to "groovy",
        "dart" to "dart",
        "json" to "json",
        "yaml" to "yaml",
        "yml" to "yaml",
        "toml" to "toml",
        "html" to "html",
        "htm" to "html",
        "xml" to "xml",
        "svg" to "xml",
        "css" to "css",
        "sql" to "sql",
        "sh" to "bash",
        "bash" to "bash",
        "zsh" to "bash",
        "mk" to "makefile",
        "tex" to "latex",
        "latex" to "latex"
    )

    private val EXTENSIONLESS_NAMES = mapOf(
        "makefile" to DocumentType.SourceCode("makefile"),
        "dockerfile" to DocumentType.SourceCode("bash"),
        "license" to DocumentType.PlainText,
        "readme" to DocumentType.PlainText
    )

    private val MIME_TYPES = mapOf(
        "text/markdown" to DocumentType.Markdown,
        "text/x-markdown" to DocumentType.Markdown,
        "text/csv" to DocumentType.Csv,
        "text/comma-separated-values" to DocumentType.Csv,
        "text/html" to DocumentType.SourceCode("html"),
        "text/css" to DocumentType.SourceCode("css"),
        "text/javascript" to DocumentType.SourceCode("javascript"),
        "text/xml" to DocumentType.SourceCode("xml"),
        "text/x-java-source" to DocumentType.SourceCode("java"),
        "text/x-python" to DocumentType.SourceCode("python"),
        "text/x-yaml" to DocumentType.SourceCode("yaml"),
        "application/json" to DocumentType.SourceCode("json"),
        "application/xml" to DocumentType.SourceCode("xml"),
        "application/javascript" to DocumentType.SourceCode("javascript"),
        "application/x-yaml" to DocumentType.SourceCode("yaml"),
        "application/x-sh" to DocumentType.SourceCode("bash")
    )
}

/**
 * The picker's filter, derived from the resolver's own MIME table so the two cannot drift.
 *
 * The `text/` wildcard subsumes every `text/` entry in one line. `application/octet-stream`
 * is the escape hatch for providers that type nothing at all — the resolver's blob tier is
 * what catches the genuinely binary files it lets through.
 */
val OPENABLE_MIME_TYPES: Array<String> = DocumentTypeResolver.openableMimeTypes()
