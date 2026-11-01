package com.markreader.ui.export

import com.markreader.renderer.RendererAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one name the export builder does not derive, held against the file that owns it.
 *
 * `android-host.js` collects the export's stylesheets from the page's own `<link>` elements, so
 * nothing there can drift — except KaTeX, which is decided rather than copied. `lazy.js` links
 * that stylesheet the first time *any* document needs math and never unlinks it, so an export
 * that simply copied the live set would carry 1.4 MB of stylesheet into a document with no math
 * in it. The builder therefore filters that one href out and puts it back only for a document
 * that needs it — and that filter is a string comparison against a name a re-vendor may change.
 *
 * Both halves of getting it wrong are silent. A stale name leaves the filter matching nothing,
 * so every export grows by 1.4 MB; and re-adding a name that no longer resolves fetches a 404
 * and exports math unstyled. Neither throws anywhere.
 */
class ExportPageTest {

    @Test
    fun `the export builder and lazy js name the same stylesheet`() {
        assertEquals(
            "android-host.js filters a KaTeX stylesheet lazy.js no longer links",
            katexStylesheetIn("lazy.js"),
            katexStylesheetIn("android-host.js"),
        )
    }

    @Test
    fun `that stylesheet is vendored`() {
        val name = katexStylesheetIn("android-host.js")
        assertTrue("$name is named by the renderer and not in assets", RendererAssets.exists(name))
    }

    @Test
    fun `the host page does not link it`() {
        // The premise of the filter-and-re-add: KaTeX reaches the page only through `lazy.js`,
        // never from the page's own `<head>`. Linking it there would put it in every export and
        // pay its cost on every start-up besides.
        assertFalse(
            "android-host.html links KaTeX, which lazy.js exists to avoid",
            KATEX_LINK.containsMatchIn(RendererAssets.read("android-host.html")),
        )
    }

    private companion object {

        /** `const KATEX_STYLESHEET = 'css/katex.css';`, in either file. */
        private val KATEX_CONSTANT = Regex("""KATEX_STYLESHEET\s*=\s*'([^']+)'""")

        private val KATEX_LINK = Regex("""<link[^>]+katex\.css""")

        fun katexStylesheetIn(file: String): String =
            KATEX_CONSTANT.find(RendererAssets.read(file))?.groupValues?.get(1)
                ?: throw AssertionError("$file declares no KATEX_STYLESHEET")
    }
}
