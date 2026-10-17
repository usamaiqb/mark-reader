package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markreader.R
import com.markreader.file.DocumentSource
import com.markreader.file.FileTooLargeException
import com.markreader.file.DecodedText
import com.markreader.file.TextEncoding
import com.markreader.file.TextCodec
import com.markreader.file.tryTakePersistablePermission
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.RecentFilesRepository
import com.markreader.data.UserPreferences
import com.markreader.file.DocumentType
import com.markreader.file.DocumentTypeResolver
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.FormatRegistry
import com.markreader.format.ReaderContent
import com.markreader.format.RenderTheme
import com.markreader.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Default for a format that has nothing more specific to ask the picker for. */
private const val PLAIN_TEXT_MIME = "text/plain"

enum class EditorTab { Edit, Preview }

sealed class SaveResult {
    data object Success : SaveResult()
    data object NoPermission : SaveResult()

    /**
     * The document was decoded lossily, so its bytes cannot be reproduced. Writing would
     * save the replacement characters over whatever the original bytes were. Save As is
     * the remedy: a new file, honestly UTF-8, with the original left alone.
     */
    data object UnknownEncoding : SaveResult()
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    /** What the editor may offer, decided by the document's format rather than by its type. */
    val capabilities: FormatCapabilities = FormatCapabilities(),
    /** What an empty document of this format invites the user to type. */
    @StringRes val placeholderRes: Int = R.string.editor_placeholder_text,
    /** The MIME type Save As asks the system picker for. */
    val creationMimeType: String = PLAIN_TEXT_MIME,
    val isModified: Boolean = false,
    val isSaving: Boolean = false,
    val saveResult: SaveResult? = null,
    val previewText: ReaderContent? = null,
    /** Null once a document has loaded means it cannot be written back — see [SaveResult.UnknownEncoding]. */
    val encoding: TextEncoding? = null,
    val activeTab: EditorTab = EditorTab.Edit,
    val userPreferences: UserPreferences = UserPreferences(),
    val errorMessage: UiMessage? = null
)

class EditorViewModel(
    application: Application,
    private val initialUri: String?
) : AndroidViewModel(application) {

    private val repository = PreferencesRepository.getInstance(application)
    private val recentFilesRepository = RecentFilesRepository.getInstance(application)
    private var currentUri: String? = initialUri
    private var systemDarkTheme = false
    private val formats = FormatRegistry(application)
    private var format: DocumentFormat? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        if (initialUri != null) {
            loadContent()
        } else {
            // No document to resolve a type from, and this app's default is Markdown.
            _uiState.value = _uiState.value
                .withFormat(formats.formatFor(DocumentType.Markdown))
                .copy(
                    isLoading = false,
                    fileName = UNTITLED_MARKDOWN,
                    encoding = TextEncoding.Utf8
                )
        }
    }

    fun onSystemDarkThemeChanged(isDark: Boolean) {
        if (systemDarkTheme == isDark) return
        systemDarkTheme = isDark
        formats.invalidateRenderers()
        if (isPreviewShowing()) {
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
            val fileName = resolveFileName(uri)
                ?: getApplication<Application>().getString(R.string.untitled)
            var tooLarge = false
            val decoded = try {
                readDocument(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                tooLarge = true
                null
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.withFormatFor(fileName).copy(
                isLoading = false,
                fileName = fileName,
                textFieldValue = TextFieldValue(decoded?.text ?: ""),
                encoding = decoded?.encoding,
                errorMessage = when {
                    tooLarge -> UiMessage(R.string.editor_error_file_too_large)
                    decoded == null -> UiMessage(R.string.editor_error_read_failed)
                    else -> null
                }
            )
        }
    }

    fun onTextChanged(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(textFieldValue = value, isModified = true)
        if (isPreviewShowing()) {
            viewModelScope.launch { renderPreview(value.text) }
        }
    }

    fun onTabChanged(tab: EditorTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (tab == EditorTab.Preview && _uiState.value.capabilities.hasEditorPreview) {
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
        // Refuse rather than guess. The read was lossy, so UTF-8 here would write replacement
        // characters over bytes we never understood.
        val encoding = _uiState.value.encoding ?: run {
            _uiState.value = _uiState.value.copy(saveResult = SaveResult.UnknownEncoding)
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, saveResult = null)
        viewModelScope.launch {
            val written = DocumentSource.write(
                getApplication<Application>().contentResolver,
                uri,
                TextCodec.encode(content, encoding)
            )
            _uiState.value = if (written) {
                _uiState.value.copy(
                    isSaving = false, isModified = false, saveResult = SaveResult.Success
                )
            } else {
                _uiState.value.copy(isSaving = false, saveResult = SaveResult.NoPermission)
            }
        }
    }

    fun onSaveAs(newUri: Uri) {
        val content = _uiState.value.textFieldValue.text
        _uiState.value = _uiState.value.copy(isSaving = true, saveResult = null)
        val encoding = _uiState.value.encoding ?: TextEncoding.Utf8
        viewModelScope.launch {
            try {
                val written = DocumentSource.write(
                    getApplication<Application>().contentResolver,
                    newUri,
                    TextCodec.encode(content, encoding)
                )
                if (!written) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false, saveResult = SaveResult.NoPermission
                    )
                    return@launch
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
                _uiState.value = _uiState.value.withFormatFor(newFileName).copy(
                    isSaving = false, isModified = false,
                    fileName = newFileName, saveResult = SaveResult.Success,
                    // The copy is written in a known encoding, so this one can be saved to.
                    encoding = encoding
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

    private fun isPreviewShowing(): Boolean =
        _uiState.value.capabilities.hasEditorPreview &&
            _uiState.value.activeTab == EditorTab.Preview

    private suspend fun renderPreview(text: String) {
        val isDark = when (_uiState.value.userPreferences.appThemeMode) {
            AppThemeModePreference.Dark -> true
            AppThemeModePreference.Light -> false
            AppThemeModePreference.System -> systemDarkTheme
        }
        // The format owns the renderer and its per-theme cache, so the preview and the viewer
        // now render through one Markwon configuration rather than two built separately.
        _uiState.value = _uiState.value.copy(
            previewText = format?.load(text, RenderTheme(isDark))
        )
    }

    /** As [withFormat], for the format a document named [fileName] resolves to. */
    private fun EditorUiState.withFormatFor(fileName: String): EditorUiState =
        withFormat(formats.formatFor(DocumentTypeResolver.resolveFromHints(fileName)))

    /**
     * The state that follows from [next] — and **adopts it as the current format**, which the
     * preview renders through. The side effect is why the two travel together: a state that said
     * "Markdown" while [format] still held the previous document's would show a preview tab that
     * rendered the wrong thing.
     *
     * Null cannot arrive from a name alone, since `resolveFromHints` never answers
     * [DocumentType.Binary]. The editor is honest about it rather than asserting, and plain text
     * is the right floor if it ever does.
     */
    private fun EditorUiState.withFormat(next: DocumentFormat?): EditorUiState {
        format = next
        return copy(
            capabilities = next?.capabilities ?: FormatCapabilities(),
            placeholderRes = next?.editorPlaceholderRes ?: R.string.editor_placeholder_text,
            creationMimeType = next?.creationMimeType ?: PLAIN_TEXT_MIME
        )
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

    private suspend fun readDocument(uri: Uri): DecodedText? {
        val bytes = DocumentSource.read(getApplication<Application>().contentResolver, uri)
            ?: return null
        return withContext(Dispatchers.Default) { TextCodec.decode(bytes) }
    }

    companion object {
        private const val UNTITLED_MARKDOWN = "untitled.md"

        fun factory(application: Application, uri: String?): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return EditorViewModel(application, uri) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
