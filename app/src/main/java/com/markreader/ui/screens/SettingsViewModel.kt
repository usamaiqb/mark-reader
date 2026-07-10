package com.markreader.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.ReadingFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository.getInstance(application)

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setAppThemeMode(theme: AppThemeModePreference) {
        viewModelScope.launch { repository.setAppThemeMode(theme) }
    }

    fun setUseDynamicColors(enabled: Boolean) {
        viewModelScope.launch { repository.setUseDynamicColors(enabled) }
    }

    fun setReaderLightTheme(theme: ReaderThemePreference) {
        viewModelScope.launch { repository.setReaderLightTheme(theme) }
    }

    fun setReaderDarkTheme(theme: ReaderThemePreference) {
        viewModelScope.launch { repository.setReaderDarkTheme(theme) }
    }

    fun setReadingFont(font: ReadingFontPreference) {
        viewModelScope.launch { repository.setReadingFont(font) }
    }

    fun setCodeFont(font: CodeFontPreference) {
        viewModelScope.launch { repository.setCodeFont(font) }
    }

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { repository.setFontSize(sizeSp) }
    }

    fun setLineHeight(height: Float) {
        viewModelScope.launch { repository.setLineHeight(height) }
    }

    fun setTextAlignment(alignment: TextAlignmentPreference) {
        viewModelScope.launch { repository.setTextAlignment(alignment) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return SettingsViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
