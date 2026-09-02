package com.markreader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the reader has to put text into its view again.
 *
 * Worth pinning because the rule has three cases and reads like two, and the one that reads like
 * an afterthought is the one that shipped broken: clearing a search in raw mode hands the same
 * String back with its highlight spans gone, so every content comparison says "unchanged" and the
 * highlights stayed on screen over a query nobody was running.
 */
class ReaderContentControllerTest {

    private val controller = ReaderContentController(textColor = 0, selectionHighlightColor = 0)

    /** The document's characters. What changes between the cases is the styling over them. */
    private val document = "the same characters throughout".hashCode()

    @Test
    fun `the first text is always applied`() {
        assertTrue(controller.needsText(isStyled = false, contentHash = document))
    }

    @Test
    fun `unstyled text already on screen is not applied again`() {
        controller.tookText(isStyled = false, contentHash = document)

        assertFalse(controller.needsText(isStyled = false, contentHash = document))
    }

    @Test
    fun `different characters are applied`() {
        controller.tookText(isStyled = false, contentHash = document)

        assertTrue(
            controller.needsText(isStyled = false, contentHash = "another document".hashCode())
        )
    }

    /** Spans change while the string does not, so a styled text is never assumed to be on screen. */
    @Test
    fun `styled text is always applied, even over the same characters`() {
        controller.tookText(isStyled = true, contentHash = document)

        assertTrue(controller.needsText(isStyled = true, contentHash = document))
    }

    /** The regression: search highlights cleared, same characters, spans to remove. */
    @Test
    fun `unstyled text is applied over styled text with the same characters`() {
        controller.tookText(isStyled = true, contentHash = document)

        assertTrue(controller.needsText(isStyled = false, contentHash = document))
    }
}
