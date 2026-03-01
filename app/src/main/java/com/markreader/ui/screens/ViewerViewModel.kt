package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.graphics.Typeface
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.UserPreferences
import com.markreader.ui.markdown.MarkwonRenderer
import com.markreader.ui.markdown.SourceCodeRenderer
import com.markreader.ExternalFileCache
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ViewMode {
    Rendered,
    Raw
}

data class HeadingItem(
    val text: String,
    val level: Int,
    val offset: Int
)

data class ViewerUiState(
    val isLoading: Boolean = true,
    val isLoadingVisible: Boolean = false,
    val fileName: String = "",
    val rawText: String = "",
    val rawHighlighted: Spanned? = null,
    val rendered: Spanned? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val isEmptyFile: Boolean = false,
    val needsPermission: Boolean = false,
    val viewMode: ViewMode = ViewMode.Rendered,
    val headings: List<HeadingItem> = emptyList(),
    val activeHeadingIndex: Int = -1,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchCount: Int = 0,
    val searchMatchIndex: Int = 0,
    val userPreferences: UserPreferences = UserPreferences(),
    val isSourceCode: Boolean = false
)

class ViewerViewModel(
    application: Application,
    private val initialUri: String?
) : AndroidViewModel(application) {
    private val repository = PreferencesRepository.getInstance(application)
    private var systemDarkTheme = false
    private var renderer = run {
        val dark = isDarkTheme(UserPreferences(), systemDarkTheme)
        MarkwonRenderer(application, dark, codeBackground(dark))
    }
    private var currentUri: String? = null
    private var detectedCodeLanguage: String? = null
    private var sourceCodeRenderer = SourceCodeRenderer(false)
    private var loadingJob: Job? = null
    private var baseRendered: Spanned? = null
    private var renderDarkModeOverride: Boolean? = null

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _scrollToOffset = MutableStateFlow<Int?>(null)
    val scrollToOffset: StateFlow<Int?> = _scrollToOffset.asStateFlow()

    private val _scrollY = MutableStateFlow(0)
    val scrollY: StateFlow<Int> = _scrollY.asStateFlow()

    init {
        observePreferences()
        if (initialUri == null) {
            _uiState.value = ViewerUiState(
                isLoading = false,
                errorMessage = "No file selected."
            )
        }
    }

    fun loadUri(uriString: String, forceReload: Boolean = false) {
        if (!forceReload && currentUri == uriString && _uiState.value.rawText.isNotEmpty()) {
            return
        }
        currentUri = uriString
        viewModelScope.launch {
            loadingJob?.cancel()
            _matchOffsets = emptyList()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isLoadingVisible = false,
                errorMessage = null,
                warningMessage = null,
                isEmptyFile = false,
                needsPermission = false,
                rawText = "",
                rawHighlighted = null,
                rendered = null,
                headings = emptyList(),
                activeHeadingIndex = -1,
                isSearchActive = false,
                searchQuery = "",
                searchMatchCount = 0,
                searchMatchIndex = 0
            )

            loadingJob = viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                if (_uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(isLoadingVisible = true)
                }
            }

            val context = getApplication<Application>()
            val uri = Uri.parse(uriString)

            ensureReadPermission(uri)

            val fileName = resolveFileName(uri) ?: "Untitled"

            var readError: Exception? = null
            val markdown = try {
                readTextFromUri(uri)
            } catch (ex: Exception) {
                readError = ex
                null
            }

            if (markdown == null) {
                loadingJob?.cancel()
                val needsPermission = readError is SecurityException
                val errorDetails = readError?.let { ex ->
                    val message = ex.message?.takeIf { it.isNotBlank() } ?: "no details"
                    " (${ex.javaClass.simpleName}: $message)"
                } ?: ""
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingVisible = false,
                    fileName = fileName,
                    errorMessage = "Unable to open this file.$errorDetails",
                    needsPermission = needsPermission,
                    rawHighlighted = null,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = 0
                )
                return@launch
            }

            if (markdown.isBlank()) {
                loadingJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingVisible = false,
                    fileName = fileName,
                    rawText = markdown,
                    rawHighlighted = null,
                    isEmptyFile = true,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = 0
                )
                return@launch
            }

            val codeLanguage = detectCodeLanguage(fileName)
            detectedCodeLanguage = codeLanguage
            val isSourceCode = codeLanguage != null
            val isBinary = isProbablyBinary(markdown)
            val isDark = renderDarkModeOverride ?: isDarkTheme(_uiState.value.userPreferences, systemDarkTheme)
            val rendered: android.text.Spanned? = when {
                isBinary -> null
                isSourceCode -> {
                    sourceCodeRenderer = SourceCodeRenderer(isDark)
                    renderSourceCode(markdown, codeLanguage!!)
                }
                else -> {
                    renderer = MarkwonRenderer(getApplication(), isDark, codeBackground(isDark))
                    renderMarkdown(markdown)
                }
            }
            val headings = if (isSourceCode) emptyList() else parseHeadings(markdown, rendered?.toString())

            if (rendered == null) {
                loadingJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingVisible = false,
                    fileName = fileName,
                    rawText = markdown,
                    rawHighlighted = null,
                    viewMode = ViewMode.Raw,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = 0,
                    warningMessage = when {
                        isBinary -> "This file looks binary or malformed. Showing raw text."
                        isSourceCode -> "Unable to highlight this source code file. Showing raw text."
                        else -> "Unable to render this Markdown file. Showing raw text."
                    }
                )
                baseRendered = null
                return@launch
            }

            loadingJob?.cancel()
            baseRendered = rendered
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingVisible = false,
                fileName = fileName,
                rawText = markdown,
                rawHighlighted = null,
                rendered = rendered,
                errorMessage = null,
                warningMessage = null,
                headings = headings,
                activeHeadingIndex = clampActiveHeadingIndex(_uiState.value.activeHeadingIndex, headings),
                isSourceCode = isSourceCode
            )

            applySearchQuery(_uiState.value.searchQuery)
        }
    }

    fun toggleViewMode() {
        val next = if (_uiState.value.viewMode == ViewMode.Rendered) {
            ViewMode.Raw
        } else {
            ViewMode.Rendered
        }
        _uiState.value = _uiState.value.copy(viewMode = next)
        applySearchQuery(_uiState.value.searchQuery)
    }

    fun onSearchToggled() {
        val active = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (active) _uiState.value.searchQuery else "",
            searchMatchCount = 0,
            searchMatchIndex = 0
        )
        if (!active) {
            applySearchQuery("")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchMatchIndex = 0
        )
        applySearchQuery(query)
    }

    fun onNextMatch() {
        val count = _uiState.value.searchMatchCount
        if (count == 0) return
        val next = (_uiState.value.searchMatchIndex + 1) % count
        _uiState.value = _uiState.value.copy(searchMatchIndex = next)
        _scrollToOffset.value = _matchOffsets.getOrNull(next)
        applySearchQuery(_uiState.value.searchQuery)
    }

    fun onPreviousMatch() {
        val count = _uiState.value.searchMatchCount
        if (count == 0) return
        val prev = if (_uiState.value.searchMatchIndex - 1 < 0) count - 1 else _uiState.value.searchMatchIndex - 1
        _uiState.value = _uiState.value.copy(searchMatchIndex = prev)
        _scrollToOffset.value = _matchOffsets.getOrNull(prev)
        applySearchQuery(_uiState.value.searchQuery)
    }

    fun onHeadingSelected(offset: Int) {
        _scrollToOffset.value = offset
    }

    fun onScrollPositionChanged(y: Int) {
        _scrollY.value = y
    }

    fun onScrollConsumed() {
        _scrollToOffset.value = null
    }

    fun onActiveHeadingChanged(index: Int) {
        val clamped = clampActiveHeadingIndex(index, _uiState.value.headings)
        if (_uiState.value.activeHeadingIndex != clamped) {
            _uiState.value = _uiState.value.copy(activeHeadingIndex = clamped)
        }
    }

    fun onReadingSurfaceModeChanged(isDarkSurface: Boolean) {
        if (renderDarkModeOverride == isDarkSurface) return
        renderDarkModeOverride = isDarkSurface
        rebuildRenderedForTheme()
    }

    fun onSystemDarkThemeChanged(isDark: Boolean) {
        if (systemDarkTheme == isDark) return
        systemDarkTheme = isDark
        if (renderDarkModeOverride == null) {
            rebuildRenderedForTheme()
        }
    }

    fun onPermissionRequestConsumed() {
        _uiState.value = _uiState.value.copy(needsPermission = false)
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                val previous = _uiState.value.userPreferences
                val requiresRebuild = prefs.readingFont != previous.readingFont ||
                    prefs.codeFont != previous.codeFont ||
                    prefs.fontSizeSp != previous.fontSizeSp ||
                    prefs.lineHeight != previous.lineHeight ||
                    prefs.appThemeMode != previous.appThemeMode ||
                    prefs.readerLightTheme != previous.readerLightTheme ||
                    prefs.readerDarkTheme != previous.readerDarkTheme

                _uiState.value = _uiState.value.copy(userPreferences = prefs)

                if (requiresRebuild) {
                    val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
                    val markdown = _uiState.value.rawText
                    if (markdown.isNotEmpty()) {
                        val codeLanguage = detectedCodeLanguage
                        val isSourceCode = _uiState.value.isSourceCode
                        val rendered = if (isSourceCode && codeLanguage != null) {
                            sourceCodeRenderer = SourceCodeRenderer(isDark)
                            renderSourceCode(markdown, codeLanguage)
                        } else {
                            renderer = MarkwonRenderer(getApplication(), isDark, codeBackground(isDark))
                            renderMarkdown(markdown)
                        }
                        if (rendered != null) {
                            baseRendered = rendered
                            _uiState.value = _uiState.value.copy(rendered = rendered)
                            applySearchQuery(_uiState.value.searchQuery)
                        }
                    }
                }
            }
        }
    }

    private suspend fun renderMarkdown(markdown: String): Spanned? {
        return try {
            renderer.render(markdown)
        } catch (ex: Exception) {
            null
        }
    }

    private suspend fun renderSourceCode(code: String, language: String): Spanned? {
        return try {
            sourceCodeRenderer.highlight(code, language)
        } catch (ex: Exception) {
            null
        }
    }

    private fun rebuildRenderedForTheme() {
        val markdown = _uiState.value.rawText
        if (markdown.isBlank()) return
        val prefs = _uiState.value.userPreferences
        val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
        val codeLanguage = detectedCodeLanguage
        val isSourceCode = _uiState.value.isSourceCode
        viewModelScope.launch {
            val rendered = if (isSourceCode && codeLanguage != null) {
                sourceCodeRenderer = SourceCodeRenderer(isDark)
                renderSourceCode(markdown, codeLanguage)
            } else {
                renderer = MarkwonRenderer(getApplication(), isDark, codeBackground(isDark))
                renderMarkdown(markdown)
            }
            if (rendered != null) {
                baseRendered = rendered
                _uiState.value = _uiState.value.copy(rendered = rendered)
                applySearchQuery(_uiState.value.searchQuery)
            }
        }
    }

    private fun isProbablyBinary(text: String): Boolean {
        if (text.contains('\u0000')) return true
        val sample = text.take(2000)
        if (sample.isEmpty()) return false
        val nonPrintable = sample.count { it < ' ' && it != '\n' && it != '\r' && it != '\t' }
        return nonPrintable > sample.length / 20
    }

    private suspend fun resolveFileName(uri: Uri): String? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        try {
            val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return@withContext it.getString(0)
                }
            }
        } catch (ex: SecurityException) {
            // Fall back to the last path segment if we don't have query permission.
        }
        return@withContext uri.lastPathSegment
    }

    private suspend fun readTextFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()

        // If MainActivity registered a pre-read for this URI (ACTION_VIEW flow), await
        // its result. This handles restricted providers like DownloadStorageProvider that
        // require ACTION_OPEN_DOCUMENT-style access and reject reads from ViewModel scope.
        val deferred = ExternalFileCache.consume(uriString)
        if (deferred != null) {
            val preloaded = withTimeoutOrNull(5_000) { deferred.await() }
            if (preloaded != null) return@withContext preloaded
            // Pre-read failed or timed out; fall through to a direct attempt below.
        }

        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { stream ->
            return@withContext stream.bufferedReader().readText()
        }
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                return@withContext reader.readText()
            }
        }
        return@withContext null
    }

    private fun ensureReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (ex: SecurityException) {
            // Ignore: we may already have permission or the provider may not support persistable grants.
        } catch (ex: IllegalArgumentException) {
            // Ignore: not a persistable URI.
        }
    }

    private var _matchOffsets: List<Int> = emptyList()

    private fun applySearchQuery(query: String) {
        val currentState = _uiState.value
        val activeViewMode = currentState.viewMode
        val baseText = when (activeViewMode) {
            ViewMode.Raw -> currentState.rawText
            ViewMode.Rendered -> baseRendered?.toString().orEmpty()
        }

        if (activeViewMode == ViewMode.Rendered && baseRendered == null) {
            _matchOffsets = emptyList()
            _uiState.value = currentState.copy(
                rawHighlighted = null,
                searchMatchCount = 0,
                searchMatchIndex = 0
            )
            return
        }

        if (query.isBlank()) {
            _matchOffsets = emptyList()
            _uiState.value = _uiState.value.copy(
                rendered = baseRendered,
                rawHighlighted = null,
                searchMatchCount = 0,
                searchMatchIndex = 0
            )
            return
        }

        val pattern = Regex(Regex.escape(query), setOf(RegexOption.IGNORE_CASE))
        val ranges = pattern.findAll(baseText).map { it.range }.toList()
        val offsets = ranges.map { it.first }

        _matchOffsets = offsets

        val highlighted = when (activeViewMode) {
            ViewMode.Rendered -> SpannableString(baseRendered)
            ViewMode.Raw -> SpannableString(baseText)
        }
        val prefs = _uiState.value.userPreferences
        val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
        val activeTheme = if (isDark) prefs.readerDarkTheme else prefs.readerLightTheme
        val isSepia = activeTheme == com.markreader.data.ReaderThemePreference.Sepia
        val matchBackgroundColor = when {
            isDark -> 0x66FFD54F.toInt()
            isSepia -> 0x55A85A00.toInt()
            else -> 0x553197F5.toInt()
        }
        val activeMatchBackgroundColor = when {
            isDark -> 0xCCFFD54F.toInt()
            isSepia -> 0xDD8B4513.toInt()
            else -> 0xFF1565C0.toInt()
        }
        val activeMatchTextColor = when {
            isDark -> 0xFF1A1A1A.toInt()
            isSepia -> 0xFFFFFFFF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        for (range in ranges) {
            val start = range.first
            val end = (range.last + 1).coerceAtMost(highlighted.length)
            highlighted.setSpan(
                BackgroundColorSpan(matchBackgroundColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            highlighted.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val activeIndex = _uiState.value.searchMatchIndex.coerceIn(0, (offsets.size - 1).coerceAtLeast(0))
        if (ranges.isNotEmpty()) {
            val activeRange = ranges[activeIndex]
            val activeOffset = activeRange.first
            val activeEnd = (activeRange.last + 1).coerceAtMost(highlighted.length)
            highlighted.setSpan(
                BackgroundColorSpan(activeMatchBackgroundColor),
                activeOffset,
                activeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            highlighted.setSpan(
                ForegroundColorSpan(activeMatchTextColor),
                activeOffset,
                activeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            highlighted.setSpan(
                StyleSpan(Typeface.BOLD),
                activeOffset,
                activeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        _uiState.value = when (activeViewMode) {
            ViewMode.Rendered -> _uiState.value.copy(
                rendered = highlighted,
                rawHighlighted = null,
                searchMatchCount = offsets.size,
                searchMatchIndex = if (offsets.isEmpty()) 0 else activeIndex
            )
            ViewMode.Raw -> _uiState.value.copy(
                rendered = baseRendered,
                rawHighlighted = highlighted,
                searchMatchCount = offsets.size,
                searchMatchIndex = if (offsets.isEmpty()) 0 else activeIndex
            )
        }
    }

    private fun parseHeadings(markdown: String, renderedText: String?): List<HeadingItem> {
        val headings = mutableListOf<HeadingItem>()
        var offset = 0
        var renderedCursor = 0
        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..3) {
                    val text = trimmed.drop(level).trim()
                    if (text.isNotBlank()) {
                        val renderedOffset = if (!renderedText.isNullOrEmpty()) {
                            val found = renderedText.indexOf(text, renderedCursor)
                            if (found >= 0) {
                                renderedCursor = (found + text.length).coerceAtMost(renderedText.length)
                                found
                            } else {
                                offset.coerceAtMost(renderedText.length - 1)
                            }
                        } else {
                            offset
                        }
                        headings.add(HeadingItem(text = text, level = level, offset = renderedOffset))
                    }
                }
            }
            offset += line.length + 1
        }
        return headings
    }

    private fun clampActiveHeadingIndex(current: Int, headings: List<HeadingItem>): Int {
        if (headings.isEmpty()) return -1
        if (current < 0) return -1
        return current.coerceAtMost(headings.lastIndex)
    }

    companion object {
        private fun detectCodeLanguage(fileName: String): String? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext == fileName.lowercase()) {
                // No extension — check full name
                return when (fileName.lowercase()) {
                    "makefile" -> "makefile"
                    else -> null
                }
            }
            return when (ext) {
                "java" -> "java"
                "kt", "kts" -> "kotlin"
                "py" -> "python"
                "js" -> "javascript"
                "ts" -> "javascript"
                "c" -> "c"
                "cpp", "cc", "cxx", "hpp" -> "cpp"
                "h" -> "c"
                "cs" -> "csharp"
                "swift" -> "swift"
                "go" -> "go"
                "rs" -> "c"
                "rb" -> "python"
                "scala" -> "scala"
                "groovy" -> "groovy"
                "dart" -> "dart"
                "json" -> "json"
                "yaml", "yml" -> "yaml"
                "html", "xml", "svg" -> "markup"
                "css" -> "css"
                "sql" -> "sql"
                "sh", "bash", "zsh" -> "bash"
                "mk" -> "makefile"
                "tex", "latex" -> "latex"
                "gradle" -> "groovy"
                "toml" -> "yaml"
                "md", "txt" -> null
                else -> null
            }
        }

        /** Matches Markwon's default: text color at ~10% alpha. */
        private fun codeBackground(isDark: Boolean): Int {
            val textColor = if (isDark) Color.WHITE else Color.BLACK
            return ColorUtils.setAlphaComponent(textColor, 25)
        }

        private fun isDarkTheme(prefs: UserPreferences, isSystemDarkTheme: Boolean): Boolean {
            return when (prefs.appThemeMode) {
                AppThemeModePreference.System -> isSystemDarkTheme
                AppThemeModePreference.Dark -> true
                AppThemeModePreference.Light -> false
            }
        }

        fun factory(application: Application, uri: String?): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ViewerViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return ViewerViewModel(application, uri) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
