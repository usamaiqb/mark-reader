package com.markreader.webview

import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.UserPreferences
import kotlin.math.roundToInt

/**
 * The reader's stored preferences, as the settings the renderer understands.
 *
 * Bundled body and code fonts are served by the local asset loader. Code uses its own font
 * preference rather than silently falling back to the document's reading font.
 * Sepia currently maps to light and AMOLED to dark until dedicated palettes are provided.
 */
fun rendererSettingsFor(
    preferences: UserPreferences,
    isDark: Boolean,
    isCodeBlockWrapEnabled: Boolean,
    sourceCode: Boolean = false,
): RendererSettings {
    // `fontSize` and `lineHeight` are both CSS pixels over there, where the app holds a size in
    // sp and a *multiplier*. Sending the multiplier would be a line height of 2px.
    val fontSize = preferences.fontSizeSp.roundToInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    val lineHeight = (fontSize * preferences.lineHeight).roundToInt()
    return RendererSettings(
        theme = if (isDark) RendererTheme.DARK else RendererTheme.LIGHT,
        wordWrap = isCodeBlockWrapEnabled,
        fontSize = fontSize,
        lineHeight = lineHeight.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT),
        fontFamily = readingStack(preferences.readingFont),
        codeFontFamily = codeStack(preferences.codeFont),
        sourceCode = sourceCode,
    )
}

/**
 * The body stack. Code is not asked about here: a fenced block takes its font from
 * `--vscode-editor-font-family` in the vendored theme, not from the document's body font.
 */
private fun readingStack(font: ReadingFontPreference): String = when (font) {
    ReadingFontPreference.Merriweather -> "Merriweather, Georgia, \"Noto Serif\", serif"
    ReadingFontPreference.SystemSerif -> "Georgia, \"Noto Serif\", serif"
    ReadingFontPreference.MerriweatherSans ->
        "\"Merriweather Sans\", system-ui, \"Roboto\", \"Noto Sans\", sans-serif"
}

/** Code fonts are independent of the prose family, for both fences and whole source files. */
internal fun codeStack(font: CodeFontPreference): String = when (font) {
    CodeFontPreference.JetBrainsMono -> "\"JetBrains Mono\", \"Roboto Mono\", monospace"
    CodeFontPreference.SystemMono -> "\"Roboto Mono\", monospace"
}

// The page validates again through `parseConfig` and falls back per setting, so a value outside
// these would be silently replaced by the host default rather than refused. Clamping here means
// an extreme reader setting stays extreme instead of snapping back to 16px.
private const val MIN_FONT_SIZE = 6
private const val MAX_FONT_SIZE = 96
private const val MIN_LINE_HEIGHT = 8
private const val MAX_LINE_HEIGHT = 200
