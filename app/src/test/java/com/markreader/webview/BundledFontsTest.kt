package com.markreader.webview

import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.UserPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled fonts are one fact spread over three files, and this is what holds them together.
 *
 * [FontResourceHandler] serves the bytes, `android-host.html` declares the `@font-face` rules
 * that ask for them, and [rendererSettingsFor] names the families in a CSS stack. Break the
 * agreement between any two and **nothing reports an error**: the page requests a URL, gets a
 * 404 it treats as one unavailable face, and renders the document in the next family in the
 * stack. That is exactly the symptom this plumbing was written to fix, so it cannot be the way it
 * regresses.
 *
 * The desktop repo learned this the hard way on the stylesheet split — `document.ts` registered
 * highlights that only `app.css` painted, which the stub host could not catch because the page
 * rendered, just wrongly. A test that reads both files is the only kind that catches it.
 *
 * What is *not* here is the handler's own behaviour — a hit streams, a miss 404s. `Resources` and
 * `WebResourceResponse` are both android.jar stubs on this classpath, so a JVM test could
 * construct the response and learn nothing from it; that half belongs on a device, or to
 * Robolectric if the project ever takes it on.
 */
class BundledFontsTest {

    // ------------------------------------------------------------------ the two sides agree

    @Test
    fun `every font the page asks for is one the host serves`() {
        for (face in faces()) {
            assertTrue(
                "@font-face for ${face.family} requests ${face.url}, outside $FONT_PREFIX",
                face.url.startsWith(FONT_PREFIX),
            )
            val name = face.url.removePrefix(FONT_PREFIX)
            assertTrue(
                "$name is requested by android-host.html and not in BUNDLED_FONTS",
                name in FontResourceHandler.BUNDLED_FONTS,
            )
        }
    }

    @Test
    fun `every font the host serves is one the page asks for`() {
        // The inverse matters as much: a resource served and never referenced is a door held open
        // for nothing, and it is how a renamed `@font-face` src goes unnoticed — the old entry
        // still serves, the new URL 404s, and the document quietly loses its font.
        val requested = faces().map { it.url.removePrefix(FONT_PREFIX) }.toSet()

        assertEquals(
            "BUNDLED_FONTS and the @font-face rules name different files",
            FontResourceHandler.BUNDLED_FONTS.keys,
            requested,
        )
    }

    // ------------------------------------------------------------------------- the families

    @Test
    fun `each declared family has the weights the reader draws`() {
        val weights = faces().groupBy({ it.family }, { it.weight })

        // Every family needs a regular. The two body families need a bold as well: headings and
        // strong text are ordinary Markdown, and a family with only a 400 face has its bold
        // synthesised by the WebView — a smeared approximation of a weight that is in the APK.
        assertEquals(setOf(400, 700), weights["Merriweather"]?.toSet())
        assertEquals(setOf(400, 700), weights["Merriweather Sans"]?.toSet())
        assertEquals(setOf(400), weights["JetBrains Mono"]?.toSet())
    }

    @Test
    fun `a bundled preference leads with a family the page declares`() {
        val declared = faces().map { it.family }.toSet()

        assertTrue("Merriweather", leadingReadingFamily(ReadingFontPreference.Merriweather) in declared)
        assertTrue(
            "Merriweather Sans",
            leadingReadingFamily(ReadingFontPreference.MerriweatherSans) in declared,
        )
        assertTrue("JetBrains Mono", leadingCodeFamily(CodeFontPreference.JetBrainsMono) in declared)
    }

    @Test
    fun `a system preference leads with a family the page does not declare`() {
        // The inverse of the setting: "System serif" that led with Merriweather would be the same
        // font as Merriweather, which is the state this whole change came out of — the two were
        // indistinguishable because neither was served.
        val declared = faces().map { it.family }.toSet()

        assertTrue(leadingReadingFamily(ReadingFontPreference.SystemSerif) !in declared)
        assertTrue(leadingCodeFamily(CodeFontPreference.SystemMono) !in declared)
    }

    // ------------------------------------------------------------------------------- reading

    private fun leadingReadingFamily(font: ReadingFontPreference): String =
        leadingFamily(settings(UserPreferences(readingFont = font)).fontFamily)

    private fun leadingCodeFamily(font: CodeFontPreference): String =
        leadingFamily(settings(UserPreferences(codeFont = font)).codeFontFamily)

    private fun settings(preferences: UserPreferences) =
        rendererSettingsFor(preferences, isDark = false, isCodeBlockWrapEnabled = true)

    private companion object {

        /** The first family in a CSS stack, unquoted. */
        fun leadingFamily(stack: String?): String =
            stack.orEmpty().substringBefore(',').trim().trim('"')

        private data class Face(val family: String, val weight: Int, val url: String)

        // Raw strings: every one of these ends in `)` so the closing quote is unambiguous, and
        // the trailing `"` of a CSS value is left to `[^"]+` to stop at rather than matched.
        private val FACE_BLOCK = Regex("""@font-face\s*\{([^}]*)\}""")
        private val FAMILY = Regex("""font-family:\s*"([^"]+)""")
        private val WEIGHT = Regex("""font-weight:\s*(\d+)""")
        private val SRC_URL = Regex("""url\("([^"]+)""")

        /**
         * The `@font-face` rules in the host page.
         *
         * Read off disk rather than mirrored in the test, because a copy of the block here would
         * agree with itself while the page had drifted.
         */
        fun faces(): List<Face> {
            val blocks = FACE_BLOCK.findAll(hostPage()).toList()
            assertTrue("android-host.html declares no @font-face rules", blocks.isNotEmpty())
            return blocks.map { block ->
                val body = block.groupValues[1]
                Face(
                    family = requireIn(FAMILY, body, "font-family"),
                    weight = requireIn(WEIGHT, body, "font-weight").toInt(),
                    url = requireIn(SRC_URL, body, "src url"),
                )
            }
        }

        /** A face missing any of the three is a failure, not something to skip over. */
        fun requireIn(pattern: Regex, body: String, what: String): String =
            pattern.find(body)?.groupValues?.get(1)
                ?: throw AssertionError("an @font-face rule has no $what:\n$body")

        /**
         * Walks up from the working directory, which Gradle sets to the module and other runners
         * do not, so the test is not the thing that breaks when it is run from the IDE.
         */
        fun hostPage(): String {
            val relative = "src/main/assets/renderer/android-host.html"
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (candidate in listOf(File(directory, relative), File(directory, "app/$relative"))) {
                    if (candidate.isFile) {
                        return candidate.readText()
                    }
                }
                directory = directory.parentFile
            }
            throw AssertionError("no $relative above ${File("").absolutePath}")
        }
    }
}
