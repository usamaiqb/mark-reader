package com.markreader.webview

import com.markreader.data.ReaderThemePreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's reader preferences as the renderer's settings. The unit mismatch on line height is
 * the one that would be invisible until a device showed it.
 */
class ReaderRendererSettingsTest {

    private fun settings(
        preferences: UserPreferences = UserPreferences(),
        isDark: Boolean = false,
        wrap: Boolean = true,
    ) = rendererSettingsFor(preferences, isDark, wrap)

    @Test
    fun `line height crosses as pixels, not as the multiplier`() {
        val result = settings(UserPreferences(fontSizeSp = 20f, lineHeight = 1.5f))

        assertEquals(20, result.fontSize)
        // 1.5 would be read as 1.5px and clamped away to the host default.
        assertEquals(30, result.lineHeight)
    }

    @Test
    fun `an extreme font size is clamped into the range the page accepts`() {
        // `parseConfig` drops an out-of-range value and falls back per setting, so an unclamped
        // 400 would silently become the 16px host default rather than the largest legal size.
        assertEquals(96, settings(UserPreferences(fontSizeSp = 400f)).fontSize)
        assertEquals(6, settings(UserPreferences(fontSizeSp = 1f)).fontSize)
    }

    @Test
    fun `line height is clamped independently of the size that produced it`() {
        assertEquals(200, settings(UserPreferences(fontSizeSp = 96f, lineHeight = 4f)).lineHeight)
    }

    @Test
    fun `sepia reads as light and amoled as dark, the only two palettes there are`() {
        val sepia = UserPreferences(readerLightTheme = ReaderThemePreference.Sepia)
        val amoled = UserPreferences(readerDarkTheme = ReaderThemePreference.Amoled)

        assertEquals(RendererTheme.LIGHT, settings(sepia, isDark = false).theme)
        assertEquals(RendererTheme.DARK, settings(amoled, isDark = true).theme)
    }

    @Test
    fun `the theme is stated, never left to the page to guess`() {
        // SYSTEM would follow the WebView's own prefers-color-scheme and ignore the reader's
        // in-app dark override.
        assertEquals(RendererTheme.LIGHT, settings(isDark = false).theme)
        assertEquals(RendererTheme.DARK, settings(isDark = true).theme)
    }

    @Test
    fun `the bundled family is named first so it works the day it is served`() {
        val merriweather = settings(UserPreferences(readingFont = ReadingFontPreference.Merriweather))

        assertTrue(merriweather.fontFamily!!.startsWith("Merriweather,"))
        // ...and falls through to something a phone actually has today.
        assertTrue(merriweather.fontFamily!!.endsWith("serif"))
    }

    @Test
    fun `code font preference reaches renderer independently of body font`() {
        val bundled = settings(UserPreferences(codeFont = com.markreader.data.CodeFontPreference.JetBrainsMono))
        val system = settings(UserPreferences(codeFont = com.markreader.data.CodeFontPreference.SystemMono))
        assertTrue(bundled.codeFontFamily!!.startsWith("\"JetBrains Mono\""))
        assertTrue(system.codeFontFamily!!.startsWith("\"Roboto Mono\""))
        assertEquals(bundled.fontFamily, system.fontFamily)
    }

    @Test
    fun `source document presentation is explicit not inferred from a single fence`() {
        val source = rendererSettingsFor(UserPreferences(), false, true, sourceCode = true)
        assertTrue(source.sourceCode)
        assertEquals(false, settings().sourceCode)
    }

    @Test
    fun `the code block wrap toggle drives word wrap`() {
        assertTrue(settings(wrap = true).wordWrap)
        assertEquals(false, settings(wrap = false).wordWrap)
    }
}
