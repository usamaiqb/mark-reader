package com.markreader.ui.screens

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.ColorUtils
import com.markreader.ExternalFileCache
import com.markreader.FileTooLargeException
import com.markreader.readBoundedText
import com.markreader.tryTakePersistablePermission
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.RecentFilesRepository
import com.markreader.data.UserPreferences
import com.markreader.ui.markdown.MarkwonRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTab { Edit, Preview }

sealed class SaveResult {
    data object Success : SaveResult()
    data object NoPermission : SaveResult()
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val isMarkdown: Boolean = false,
    val isModified: Boolean = false,
    val isSaving: Boolean = false,
    val saveResult: SaveResult? = null,
    val previewText: android.text.Spanned? = null,
    val activeTab: EditorTab = EditorTab.Edit,
    val userPreferences: UserPreferences = UserPreferences(),
    val errorMessage: String? = null
)

class EditorViewModel(
    application: Application,
    private val initialUri: String?,
    isMarkdown: Boolean
) : AndroidViewModel(application) {

    private val repository = PreferencesRepository.getInstance(application)
    private val recentFilesRepository = RecentFilesRepository.getInstance(application)
    private var currentUri: String? = initialUri
    private var systemDarkTheme = false
    private var renderer: MarkwonRenderer? = null

    private val _uiState = MutableStateFlow(EditorUiState(isMarkdown = isMarkdown))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        if (initialUri != null) {
            loadContent()
        } else {
            val defaultName = if (isMarkdown) "untitled.md" else "untitled.txt"
            _uiState.value = _uiState.value.copy(isLoading = false, fileName = defaultName)
        }
    }

    fun onSystemDarkThemeChanged(isDark: Boolean) {
        if (systemDarkTheme == isDark) return
        systemDarkTheme = isDark
        renderer = null
        if (_uiState.value.isMarkdown && _uiState.value.activeTab == EditorTab.Preview) {
            viewModelScope.launch { renderPreview(_uiState.value.textFieldValue.text) }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(userPreferences = prefs)
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            val uri = Uri.parse(initialUri)
            val fileName = resolveFileName(uri) ?: "Untitled"
            var tooLarge = false
            val content = try {
                readTextFromUri(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                tooLarge = true
                null
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                fileName = fileName,
                textFieldValue = TextFieldValue(content ?: ""),
                errorMessage = when {
                    tooLarge -> "This file is too large to edit."
                    content == null -> "Unable to read file."
                    else -> null
                }
            )
        }
    }

    fun onTextChanged(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(textFieldValue = value, isModified = true)
        if (_uiState.value.isMarkdown && _uiState.value.activeTab == EditorTab.Preview) {
            viewModelScope.launch { renderPreview(value.text) }
        }
    }

    fun onTabChanged(tab: EditorTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (tab == EditorTab.Preview && _uiState.value.isMarkdown) {
            viewModelScope.launch { renderPreview(_uiState.value.textFieldValue.text) }
        }
    }

    // Formatting helpers

    fun formatBold() = wrapSelection("**", "**")
    fun formatItalic() = wrapSelection("*", "*")
    fun formatStrikethrough() = wrapSelection("~~", "~~")
    fun formatHeading(level: Int) = insertLinePrefix("#".repeat(level) + " ")
    fun formatBullet() = insertLinePrefix("- ")
    fun formatNumbered() = insertLinePrefix("1. ")
    fun formatHorizontalRule() = insertAtCursor("\n\n---\n\n")
    fun formatInlineCode() = wrapSelection("`", "`")
    fun formatCodeBlock() = wrapSelection("\n```\n", "\n```\n")
    fun formatBlockquote() = insertLinePrefix("> ")
    fun formatLink() = wrapSelection("[", "](url)")

    private fun wrapSelection(prefix: String, suffix: String) {
        val current = _uiState.value.textFieldValue
        val text = current.text
        val sel = current.selection
        val newText: String
        val newSelection: TextRange
        if (sel.collapsed) {
            newText = text.substring(0, sel.start) + prefix + suffix + text.substring(sel.start)
            newSelection = TextRange(sel.start + prefix.length)
        } else {
            val selected = text.substring(sel.start, sel.end)
            newText = text.substring(0, sel.start) + prefix + selected + suffix + text.substring(sel.end)
            newSelection = TextRange(sel.start + prefix.length, sel.end + prefix.length)
        }
        _uiState.value = _uiState.value.copy(
            textFieldValue = TextFieldValue(text = newText, selection = newSelection),
            isModified = true
        )
    }

    private fun insertLinePrefix(prefix: String) {
        val current = _uiState.value.textFieldValue
        val text = current.text
        val sel = current.selection
        val lineStart = text.lastIndexOf('\n', sel.start - 1) + 1
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val newSelection = TextRange(sel.start + prefix.length, sel.end + prefix.length)
        _uiState.value = _uiState.value.copy(
            textFieldValue = TextFieldValue(text = newText, selection = newSelection),
            isModified = true
        )
    }

    private fun insertAtCursor(insertion: String) {
        val current = _uiState.value.textFieldValue
        val pos = current.selection.end
        val newText = current.text.substring(0, pos) + insertion + current.text.substring(pos)
        _uiState.value = _uiState.value.copy(
            textFieldValue = TextFieldValue(text = newText, selection = TextRange(pos + insertion.length)),
            isModified = true
        )
    }

    fun save() {
        val uri = Uri.parse(currentUri ?: return)
        val content = _uiState.value.textFieldValue.text
        _uiState.value = _uiState.value.copy(isSaving = true, saveResult = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = getApplication<Application>().contentResolver
                    .openOutputStream(uri, "wt")
                if (stream != null) {
                    stream.use { it.write(content.toByteArray()) }
                    _uiState.value = _uiState.value.copy(
                        isSaving = false, isModified = false, saveResult = SaveResult.Success
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false, saveResult = SaveResult.NoPermission
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SecurityException (revoked grant), FileNotFoundException (deleted
                // document), IOException — all remedied by saving a copy instead.
                _uiState.value = _uiState.value.copy(
                    isSaving = false, saveResult = SaveResult.NoPermission
                )
            }
        }
    }

    fun onSaveAs(newUri: Uri) {
        val content = _uiState.value.textFieldValue.text
        _uiState.value = _uiState.value.copy(isSaving = true, saveResult = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(newUri, "wt")?.use { it.write(content.toByteArray()) }
                }
                // Keep the new document reachable after this session: without a
                // persisted grant it can't be reopened, and recordOpen would skip it.
                getApplication<Application>().contentResolver.tryTakePersistablePermission(
                    newUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val newFileName = resolveFileName(newUri) ?: newUri.lastPathSegment ?: _uiState.value.fileName
                recentFilesRepository.recordOpen(newUri.toString(), newFileName)
                currentUri = newUri.toString()
                _uiState.value = _uiState.value.copy(
                    isSaving = false, isModified = false,
                    fileName = newFileName, saveResult = SaveResult.Success
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false, saveResult = SaveResult.NoPermission
                )
            }
        }
    }

    fun onSaveResultConsumed() {
        _uiState.value = _uiState.value.copy(saveResult = null)
    }

    private suspend fun renderPreview(text: String) {
        val prefs = _uiState.value.userPreferences
        val isDark = when (prefs.appThemeMode) {
            AppThemeModePreference.Dark -> true
            AppThemeModePreference.Light -> false
            AppThemeModePreference.System -> systemDarkTheme
        }
        if (renderer == null) {
            val bg = ColorUtils.setAlphaComponent(if (isDark) Color.WHITE else Color.BLACK, 25)
            renderer = MarkwonRenderer(getApplication(), isDark, bg)
        }
        val spanned = withContext(Dispatchers.Default) {
            try {
                renderer!!.render(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        _uiState.value = _uiState.value.copy(previewText = spanned)
    }

    private suspend fun resolveFileName(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        try {
            val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use { if (it.moveToFirst()) return@withContext it.getString(0) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) { }
        return@withContext uri.lastPathSegment
    }

    private suspend fun readTextFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val deferred = ExternalFileCache.consume(uri.toString())
        if (deferred != null) {
            val preloaded = deferred.await()
            if (preloaded != null) return@withContext preloaded
        }
        getApplication<Application>().contentResolver
            .openInputStream(uri)?.use { readBoundedText(it) }
    }

    companion object {
        fun factory(application: Application, uri: String?, isMarkdown: Boolean): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return EditorViewModel(application, uri, isMarkdown) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
