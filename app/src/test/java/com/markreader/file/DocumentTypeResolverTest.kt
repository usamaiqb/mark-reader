package com.markreader.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTypeResolverTest {

    @Test
    fun `markdown extensions resolve to Markdown`() {
        listOf("notes.md", "NOTES.MD", "readme.markdown", "a.mkd").forEach {
            assertEquals(it, DocumentType.Markdown, DocumentTypeResolver.resolveFromHints(it))
        }
    }

    @Test
    fun `csv no longer reads as markdown prose`() {
        assertEquals(DocumentType.Csv, DocumentTypeResolver.resolveFromHints("sales.csv"))
    }

    @Test
    fun `an unknown extension is plain text, not markdown`() {
        assertEquals(DocumentType.PlainText, DocumentTypeResolver.resolveFromHints("data.xyz"))
        assertEquals(DocumentType.PlainText, DocumentTypeResolver.resolveFromHints("notes.txt"))
        assertEquals(DocumentType.PlainText, DocumentTypeResolver.resolveFromHints("CHANGES"))
    }

    @Test
    fun `source code carries its own language, not a highlighter grammar`() {
        assertEquals(
            DocumentType.SourceCode("rust"),
            DocumentTypeResolver.resolveFromHints("main.rs")
        )
        assertEquals(
            DocumentType.SourceCode("ruby"),
            DocumentTypeResolver.resolveFromHints("app.rb")
        )
        assertEquals(
            DocumentType.SourceCode("toml"),
            DocumentTypeResolver.resolveFromHints("Cargo.toml")
        )
        assertEquals(
            DocumentType.SourceCode("kotlin"),
            DocumentTypeResolver.resolveFromHints("Main.kt")
        )
    }

    @Test
    fun `extensionless names are recognised by the whole name`() {
        assertEquals(
            DocumentType.SourceCode("makefile"),
            DocumentTypeResolver.resolveFromHints("Makefile")
        )
    }

    @Test
    fun `a path is reduced to its last segment`() {
        assertEquals(
            DocumentType.Markdown,
            DocumentTypeResolver.resolveFromHints("/tree/docs/plan/README.md")
        )
    }

    @Test
    fun `the name beats the mime type, because providers mislabel`() {
        assertEquals(
            DocumentType.Markdown,
            DocumentTypeResolver.resolveFromHints("notes.md", "application/octet-stream")
        )
        assertEquals(
            DocumentType.SourceCode("kotlin"),
            DocumentTypeResolver.resolveFromHints("Main.kt", "text/plain")
        )
    }

    @Test
    fun `the mime type answers only when the name does not`() {
        assertEquals(
            DocumentType.Markdown,
            DocumentTypeResolver.resolveFromHints("download", "text/markdown")
        )
        assertEquals(
            DocumentType.Csv,
            DocumentTypeResolver.resolveFromHints("export.dat", "text/csv; charset=utf-8")
        )
        assertEquals(
            DocumentType.PlainText,
            DocumentTypeResolver.resolveFromHints("download", "text/plain")
        )
    }

    @Test
    fun `hints never claim a file is binary`() {
        assertEquals(DocumentType.Markdown, DocumentTypeResolver.resolveFromHints("a.md", null))
    }

    @Test
    fun `content overrules the name when the bytes are not text`() {
        assertEquals(
            DocumentType.Binary,
            DocumentTypeResolver.resolve("notes.md", null, "text\u0000more")
        )
        assertEquals(
            DocumentType.Markdown,
            DocumentTypeResolver.resolve("notes.md", null, "# Heading\n\nBody.\n")
        )
    }

    @Test
    fun `an empty document is not binary`() {
        assertFalse(DocumentTypeResolver.isProbablyBinary(""))
        assertEquals(DocumentType.PlainText, DocumentTypeResolver.resolve("a.txt", null, ""))
    }

    @Test
    fun `ordinary whitespace does not count against a text file`() {
        assertFalse(DocumentTypeResolver.isProbablyBinary("a\tb\r\nc\n".repeat(200)))
    }

    @Test
    fun `a heavy run of control characters reads as binary`() {
        assertTrue(DocumentTypeResolver.isProbablyBinary("\u0001\u0002\u0003\u0004".repeat(100)))
    }

    @Test
    fun `the picker allowlist covers every mime the resolver can place`() {
        val allowlist = OPENABLE_MIME_TYPES.toSet()
        assertTrue("text/*" in allowlist)
        assertTrue("application/json" in allowlist)
        assertTrue("application/octet-stream" in allowlist)
        // Every `text/` entry is covered by the wildcard rather than listed twice.
        assertEquals(listOf("text/*"), OPENABLE_MIME_TYPES.filter { it.startsWith("text/") })
    }
}
