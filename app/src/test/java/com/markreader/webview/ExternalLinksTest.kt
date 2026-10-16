package com.markreader.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `openExternal` allowlist, mirroring `commands.rs`'s own tests in the desktop repo case for
 * case. The two hosts hand the same three schemes to their platform, and a test that only exists
 * on one side is how that stops being true.
 */
class ExternalLinksTest {

    @Test
    fun `opens the three allowed schemes`() {
        assertTrue(isOpenableExternal("https://example.com/x"))
        assertTrue(isOpenableExternal("http://example.com/x"))
        assertTrue(isOpenableExternal("mailto:someone@example.com"))
    }

    @Test
    fun `ignores the case a document wrote the scheme in`() {
        assertTrue(isOpenableExternal("HTTPS://example.com"))
        assertTrue(isOpenableExternal("MailTo:someone@example.com"))
    }

    @Test
    fun `refuses everything else`() {
        assertFalse(isOpenableExternal("javascript:alert(1)"))
        assertFalse(isOpenableExternal("file:///etc/passwd"))
        assertFalse(isOpenableExternal("content://com.android.providers.downloads/1"))
        assertFalse(isOpenableExternal("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isOpenableExternal("tel:+15550100"))
        assertFalse(isOpenableExternal("data:text/html,<script>alert(1)</script>"))
        assertFalse(isOpenableExternal(""))
    }

    /** A scheme has to start the URL, not appear in it. */
    @Test
    fun `refuses an allowed scheme buried in another`() {
        assertFalse(isOpenableExternal("javascript:void(location='https://example.com')"))
        assertFalse(isOpenableExternal(" https://example.com"))
    }
}
