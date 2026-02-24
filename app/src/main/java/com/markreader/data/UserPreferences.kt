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
    SystemSerif
}

enum class CodeFontPreference {
    JetBrainsMono,
    SystemMono
}

enum class TextAlignmentPreference {
    Left,
    Justified
}

data class UserPreferences(
    val appThemeMode: AppThemeModePreference = AppThemeModePreference.System,
    val readerLightTheme: ReaderThemePreference = ReaderThemePreference.Light,
    val readerDarkTheme: ReaderThemePreference = ReaderThemePreference.Dark,
    val readingFont: ReadingFontPreference = ReadingFontPreference.Merriweather,
    val codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    val fontSizeSp: Float = 18f,
    val lineHeight: Float = 1.6f,
    val textAlignment: TextAlignmentPreference = TextAlignmentPreference.Left
)
