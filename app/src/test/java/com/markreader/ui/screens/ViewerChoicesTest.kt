package com.markreader.ui.screens

import com.markreader.R
import com.markreader.format.ReaderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's what-to-show decisions, and in particular the branch nobody can reach by hand: a
 * WebView too old to run the shared renderer. Reproducing that needs a Chromium 52 emulator, so
 * without these the fallback would be verified once and then quietly rot.
 */
class ViewerChoicesTest {

    private val rendered = ReaderContent.Markdown("# rendered")
    private val highlighted = ReaderContent.Spans("raw with matches")

    // ------------------------------------------------------------------ what gets drawn

    @Test
    fun `a working renderer draws what the format produced`() {
        val content = readerContentFor(
            isRawMode = false,
            rawText = "# source",
            rawHighlighted = null,
            rendered = rendered,
            rendererUnavailable = false
        )

        assertSame(rendered, content)
    }

    @Test
    fun `no renderer falls back to the source, even in rendered mode`() {
        val content = readerContentFor(
            isRawMode = false,
            rawText = "# source",
            rawHighlighted = null,
            // The format still produced Markdown; there is simply nothing able to draw it.
            rendered = rendered,
            rendererUnavailable = true
        )

        assertEquals(ReaderContent.Spans("# source"), content)
    }

    @Test
    fun `the fallback keeps its search highlighting, as raw mode does`() {
        // The source is on screen either way, so a search over the source has to be able to
        // paint it either way. It used to draw the unmarked text and report matches nobody
        // could see.
        val content = readerContentFor(
            isRawMode = false,
            rawText = "# source",
            rawHighlighted = highlighted,
            rendered = rendered,
            rendererUnavailable = true
        )

        assertSame(highlighted, content)
    }

    @Test
    fun `raw mode keeps its search highlighting, renderer or not`() {
        for (unavailable in listOf(false, true)) {
            val content = readerContentFor(
                isRawMode = true,
                rawText = "# source",
                rawHighlighted = highlighted,
                rendered = rendered,
                rendererUnavailable = unavailable
            )

            assertSame(highlighted, content)
        }
    }

    @Test
    fun `a format that produced nothing still shows the source`() {
        val content = readerContentFor(
            isRawMode = false,
            rawText = "# source",
            rawHighlighted = null,
            rendered = null,
            rendererUnavailable = false
        )

        assertEquals(ReaderContent.Spans("# source"), content)
    }

    // --------------------------------------------------------------- which surface is drawing

    @Test
    fun `only a rendered Markdown document with a working renderer is drawn by the page`() {
        assertTrue(
            isDrawnByPage(isRawMode = false, rendered = rendered, rendererUnavailable = false)
        )
        assertFalse(
            isDrawnByPage(isRawMode = true, rendered = rendered, rendererUnavailable = false)
        )
        assertFalse(
            isDrawnByPage(isRawMode = false, rendered = rendered, rendererUnavailable = true)
        )
        // A format that produced text, or nothing at all, is drawn by a TextView.
        assertFalse(
            isDrawnByPage(isRawMode = false, rendered = highlighted, rendererUnavailable = false)
        )
        assertFalse(
            isDrawnByPage(isRawMode = false, rendered = null, rendererUnavailable = false)
        )
    }

    @Test
    fun `the surface test agrees with what is drawn, in every combination`() {
        // The two are read for different reasons — one routes a search, the other picks a view —
        // and a disagreement between them is a search running against a surface that is not
        // there. Asserted rather than assumed, across the whole input space.
        for (isRawMode in listOf(false, true)) {
            for (unavailable in listOf(false, true)) {
                for (produced in listOf(rendered, highlighted, null)) {
                    val content = readerContentFor(
                        isRawMode = isRawMode,
                        rawText = "# source",
                        rawHighlighted = null,
                        rendered = produced,
                        rendererUnavailable = unavailable
                    )

                    assertEquals(
                        "isRawMode=$isRawMode unavailable=$unavailable rendered=$produced",
                        content is ReaderContent.Markdown,
                        isDrawnByPage(isRawMode, produced, unavailable)
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------- what it is called

    @Test
    fun `the chip says raw when the renderer could not start`() {
        // The source is what is on screen, so "Rendered mode" would be untrue — the same lie
        // hasRawView already guards against for formats whose rendering is their source.
        assertEquals(
            R.string.viewer_subtitle_raw_mode,
            viewModeLabelFor(
                hasRawView = true,
                isRawMode = false,
                formatNameRes = R.string.viewer_subtitle_markdown,
                rendererUnavailable = true
            )
        )
    }

    @Test
    fun `the chip says rendered only when something actually rendered it`() {
        assertEquals(
            R.string.viewer_subtitle_rendered_mode,
            viewModeLabelFor(
                hasRawView = true,
                isRawMode = false,
                formatNameRes = R.string.viewer_subtitle_markdown,
                rendererUnavailable = false
            )
        )
    }

    @Test
    fun `a format with no raw view is named, never given a view mode`() {
        for (unavailable in listOf(false, true)) {
            assertEquals(
                R.string.viewer_subtitle_plain_text,
                viewModeLabelFor(
                    hasRawView = false,
                    isRawMode = false,
                    formatNameRes = R.string.viewer_subtitle_plain_text,
                    rendererUnavailable = unavailable
                )
            )
        }
    }

    // ------------------------------------------------------------------------- the toggle

    @Test
    fun `there is nothing to toggle to without a renderer`() {
        assertFalse(
            canToggleViewMode(hasRawView = true, hasRendered = true, rendererUnavailable = true)
        )
    }

    @Test
    fun `the toggle needs a raw view and something rendered`() {
        assertTrue(
            canToggleViewMode(hasRawView = true, hasRendered = true, rendererUnavailable = false)
        )
        assertFalse(
            canToggleViewMode(hasRawView = false, hasRendered = true, rendererUnavailable = false)
        )
        assertFalse(
            canToggleViewMode(hasRawView = true, hasRendered = false, rendererUnavailable = false)
        )
    }
}
