package com.markreader.data

enum class AppThemeModePreference {
    System,
    Light,
    Dark
}

enum class ReaderThemePreference {
    Light,
    Dark,
    Amoled,
    Sepia
}

enum class ReadingFontPreference {
    Merriweather,
    SystemSerif,
    MerriweatherSans
}

enum class CodeFontPreference {
    JetBrainsMono,
    SystemMono
}

enum class TextAlignmentPreference {
    Left,
    Justified
}

/**
 * The reader's typography defaults, named once because two places need them: the defaults on
 * [UserPreferences] and the fallbacks [PreferencesRepository] reads when the store holds nothing.
 */
const val DEFAULT_FONT_SIZE_SP = 18f
const val DEFAULT_LINE_HEIGHT = 1.6f

data class UserPreferences(
    val appThemeMode: AppThemeModePreference = AppThemeModePreference.System,
    val useDynamicColors: Boolean = true,
    val readerLightTheme: ReaderThemePreference = ReaderThemePreference.Light,
    val readerDarkTheme: ReaderThemePreference = ReaderThemePreference.Dark,
    val readingFont: ReadingFontPreference = ReadingFontPreference.Merriweather,
    val codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val textAlignment: TextAlignmentPreference = TextAlignmentPreference.Left
)
