package com.markreader.webview

import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.UserPreferences
import kotlin.math.roundToInt

/**
 * The reader's stored preferences, as the settings the renderer understands.
 *
 * One of the app's settings still does not survive the trip, and it is deliberate rather than
 * overlooked: **Sepia and AMOLED have no palette in the renderer.** It ships VS Code's two,
 * `vscode-light` and `vscode-dark`, and [RendererTheme] says so. Sepia reads as light and AMOLED
 * as dark until a third and fourth palette are written — and they belong in the *desktop* repo's
 * `document-overrides.css`, since this app only vendors a copy of it.
 *
 * The fonts do arrive now. Both stacks below name a bundled family first, [FontResourceHandler]
 * serves it out of `res/font/`, and `android-host.html` declares the `@font-face` rules that join
 * the two — so Merriweather and "System serif" are finally different fonts. **Each stack still
 * ends in a generic**, and that is not vestigial: the page falls back per family, so a font whose
 * fetch fails degrades to something a phone actually has rather than to the WebView's default.
 */
fun rendererSettingsFor(
    preferences: UserPreferences,
    isDark: Boolean,
    isCodeBlockWrapEnabled: Boolean,
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
    )
}

/**
 * The body stack. It does not reach fenced code, which takes its font from
 * `--vscode-editor-font-family` in the vendored theme — hence [codeStack] as a second setting
 * rather than one family for the whole document.
 */
private fun readingStack(font: ReadingFontPreference): String = when (font) {
    ReadingFontPreference.Merriweather -> "Merriweather, Georgia, \"Noto Serif\", serif"
    ReadingFontPreference.SystemSerif -> "Georgia, \"Noto Serif\", serif"
    ReadingFontPreference.MerriweatherSans ->
        "\"Merriweather Sans\", system-ui, \"Roboto\", \"Noto Sans\", sans-serif"
}

/**
 * The stack for fenced code, which the page applies over the theme's own monospace value.
 *
 * `SystemMono` deliberately keeps `"Roboto Mono"` ahead of the generic even though it is the
 * *system* choice: `monospace` on Android resolves to Droid Sans Mono on older builds, and the
 * point of the setting is "not the bundled font", not "the oldest font on the device".
 */
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
