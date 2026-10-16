package com.markreader.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parse half of the virtual path space. The walk itself needs a `ContentResolver` and belongs
 * on a device; this is the part that decides what is even in the space, and it is reached by both
 * the host and by untrusted document content.
 */
class VirtualPathTest {

    @Test
    fun `splits a path into its source and the path within it`() {
        val parsed = parseVirtualPath("/saf/abc123/assets/badge.svg")
        assertEquals("abc123", parsed?.token)
        assertEquals("assets/badge.svg", parsed?.relativePath)
    }

    @Test
    fun `accepts the form the asset loader hands over, without its leading slash`() {
        assertEquals(
            parseVirtualPath("/saf/abc123/doc.md"),
            parseVirtualPath("saf/abc123/doc.md"),
        )
    }

    @Test
    fun `a source root has an empty relative path`() {
        assertEquals("", parseVirtualPath("/saf/abc123")?.relativePath)
        assertEquals("", parseVirtualPath("/saf/abc123/")?.relativePath)
    }

    /** The contract's first guarantee: a host may emit `\`, and the renderer normalizes it away. */
    @Test
    fun `treats a backslash as a separator`() {
        assertEquals("assets/badge.svg", parseVirtualPath("/saf/abc123\\assets\\badge.svg")?.relativePath)
    }

    @Test
    fun `drops empty segments and dots`() {
        assertEquals("a/b", parseVirtualPath("/saf/t//a/./b")?.relativePath)
    }

    /**
     * `resolvePath` collapses `..` on the renderer's side before a path ever comes back here, so
     * this should be unreachable — which is exactly why it is refused rather than trusted.
     */
    @Test
    fun `refuses to walk upward`() {
        assertNull(parseVirtualPath("/saf/t/../../etc/passwd"))
        assertNull(parseVirtualPath("/saf/t/assets/../../secret.md"))
    }

    @Test
    fun `nothing outside the space parses`() {
        assertNull(parseVirtualPath("/etc/passwd"))
        assertNull(parseVirtualPath("C:/Users/someone/notes.md"))
        assertNull(parseVirtualPath("/saf"))
        assertNull(parseVirtualPath("/saf/"))
        assertNull(parseVirtualPath(""))
        assertNull(parseVirtualPath("/SAF/t/doc.md"))
    }
}
