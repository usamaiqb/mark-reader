package com.markreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "markreader_preferences"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME
)

class PreferencesRepository private constructor(private val context: Context) {
    private object Keys {
        val LEGACY_THEME = stringPreferencesKey("theme")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val READER_LIGHT_THEME = stringPreferencesKey("reader_light_theme")
        val READER_DARK_THEME = stringPreferencesKey("reader_dark_theme")
        val READING_FONT = stringPreferencesKey("reading_font")
        val CODE_FONT = stringPreferencesKey("code_font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val TEXT_ALIGNMENT = stringPreferencesKey("text_alignment")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val legacyTheme = prefs[Keys.LEGACY_THEME]?.let { runCatching { ReaderThemePreference.valueOf(it) }.getOrNull() }
            val appThemeMode = prefs[Keys.APP_THEME_MODE]?.let { runCatching { AppThemeModePreference.valueOf(it) }.getOrNull() }
                ?: when (legacyTheme) {
                    ReaderThemePreference.Amoled,
                    ReaderThemePreference.Dark -> AppThemeModePreference.Dark
                    ReaderThemePreference.Sepia,
                    ReaderThemePreference.Light -> AppThemeModePreference.Light
                    null -> AppThemeModePreference.System
                }
            val readerLightTheme = prefs[Keys.READER_LIGHT_THEME]?.let {
                runCatching { ReaderThemePreference.valueOf(it) }.getOrNull()
            }?.takeIf { it == ReaderThemePreference.Light || it == ReaderThemePreference.Sepia }
                ?: when (legacyTheme) {
                    ReaderThemePreference.Sepia -> ReaderThemePreference.Sepia
                    else -> ReaderThemePreference.Light
                }
            val readerDarkTheme = prefs[Keys.READER_DARK_THEME]?.let {
                runCatching { ReaderThemePreference.valueOf(it) }.getOrNull()
            }?.takeIf { it == ReaderThemePreference.Dark || it == ReaderThemePreference.Amoled }
                ?: when (legacyTheme) {
                    ReaderThemePreference.Amoled -> ReaderThemePreference.Amoled
                    else -> ReaderThemePreference.Dark
                }
            val readingFont = prefs[Keys.READING_FONT]?.let { ReadingFontPreference.valueOf(it) }
                ?: ReadingFontPreference.Merriweather
            val codeFont = prefs[Keys.CODE_FONT]?.let { CodeFontPreference.valueOf(it) }
                ?: CodeFontPreference.JetBrainsMono
            val fontSize = prefs[Keys.FONT_SIZE] ?: 18f
            val lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.6f
            val alignment = prefs[Keys.TEXT_ALIGNMENT]?.let { TextAlignmentPreference.valueOf(it) }
                ?: TextAlignmentPreference.Left

            UserPreferences(
                appThemeMode = appThemeMode,
                readerLightTheme = readerLightTheme,
                readerDarkTheme = readerDarkTheme,
                readingFont = readingFont,
                codeFont = codeFont,
                fontSizeSp = fontSize,
                lineHeight = lineHeight,
                textAlignment = alignment
            )
        }

    suspend fun setAppThemeMode(themeMode: AppThemeModePreference) {
        context.dataStore.edit { it[Keys.APP_THEME_MODE] = themeMode.name }
    }

    suspend fun setReaderLightTheme(theme: ReaderThemePreference) {
        val safeTheme = if (theme == ReaderThemePreference.Sepia) ReaderThemePreference.Sepia else ReaderThemePreference.Light
        context.dataStore.edit { it[Keys.READER_LIGHT_THEME] = safeTheme.name }
    }

    suspend fun setReaderDarkTheme(theme: ReaderThemePreference) {
        val safeTheme = if (theme == ReaderThemePreference.Amoled) ReaderThemePreference.Amoled else ReaderThemePreference.Dark
        context.dataStore.edit { it[Keys.READER_DARK_THEME] = safeTheme.name }
    }

    suspend fun setReadingFont(font: ReadingFontPreference) {
        context.dataStore.edit { it[Keys.READING_FONT] = font.name }
    }

    suspend fun setCodeFont(font: CodeFontPreference) {
        context.dataStore.edit { it[Keys.CODE_FONT] = font.name }
    }

    suspend fun setFontSize(sizeSp: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = sizeSp }
    }

    suspend fun setLineHeight(height: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = height }
    }

    suspend fun setTextAlignment(alignment: TextAlignmentPreference) {
        context.dataStore.edit { it[Keys.TEXT_ALIGNMENT] = alignment.name }
    }

    companion object {
        @Volatile
        private var instance: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
