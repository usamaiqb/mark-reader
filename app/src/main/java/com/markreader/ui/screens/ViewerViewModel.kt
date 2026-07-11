package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.RecentFilesRepository
import com.markreader.data.UserPreferences
import com.markreader.ui.markdown.MarkwonRenderer
import com.markreader.ui.markdown.SourceCodeRenderer
import com.markreader.ExternalFileCache
import com.markreader.FileTooLargeException
import com.markreader.MAX_FILE_BYTES
import com.markreader.readBoundedText
import com.markreader.tryTakePersistablePermission
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private data class RenderCacheKey(
        val isDark: Boolean,
        val isSourceCode: Boolean,
        val codeLanguage: String?
    )

    private val repository = PreferencesRepository.getInstance(application)
    private val recentFilesRepository = RecentFilesRepository.getInstance(application)
    private var systemDarkTheme = false
    private val rendererCache = mutableMapOf<Boolean, MarkwonRenderer>()
    private val sourceCodeRendererCache = mutableMapOf<Boolean, SourceCodeRenderer>()
    private var currentUri: String? = null
    private var detectedCodeLanguage: String? = null
    private var loadJob: Job? = null
    private var loadingJob: Job? = null
    private var rebuildJob: Job? = null
    private var searchJob: Job? = null
    private var baseRendered: Spanned? = null
    private var renderDarkModeOverride: Boolean? = null
    private var preferencesLoaded = false
    private var pendingExternalRerender = initialUri != null
    private var renderCacheTextHash = 0
    private val renderCache = mutableMapOf<RenderCacheKey, Spanned>()

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _scrollToOffset = MutableStateFlow<Int?>(null)
    val scrollToOffset: StateFlow<Int?> = _scrollToOffset.asStateFlow()

    private val _scrollY = MutableStateFlow(0)
    val scrollY: StateFlow<Int> = _scrollY.asStateFlow()

    // Fraction of the document scrolled past, or null while the content is
    // shorter than the viewport (no meaningful progress to show).
    private val _scrollProgress = MutableStateFlow<Float?>(null)
    val scrollProgress: StateFlow<Float?> = _scrollProgress.asStateFlow()

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
        // Cancel any in-flight load: a slow read for a previous URI must not
        // finish after this one and overwrite the newer content.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadingJob?.cancel()
            searchJob?.cancel()
            _matchOffsets = emptyList()
            _scrollProgress.value = null
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

            val uri = Uri.parse(uriString)

            ensureReadPermission(uri)

            val fileName = resolveFileName(uri) ?: "Untitled"

            var readError: Exception? = null
            val markdown = try {
                readTextFromUri(uri)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                readError = ex
                null
            }

            if (markdown == null) {
                loadingJob?.cancel()
                val needsPermission = readError is SecurityException
                val errorMessage = if (readError is FileTooLargeException) {
                    "This file is too large to open (over ${MAX_FILE_BYTES / (1024 * 1024)} MB)."
                } else {
                    val errorDetails = readError?.let { ex ->
                        val message = ex.message?.takeIf { it.isNotBlank() } ?: "no details"
                        " (${ex.javaClass.simpleName}: $message)"
                    } ?: ""
                    "Unable to open this file.$errorDetails"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingVisible = false,
                    fileName = fileName,
                    errorMessage = errorMessage,
                    needsPermission = needsPermission,
                    rawHighlighted = null,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = 0
                )
                return@launch
            }

            recentFilesRepository.recordOpen(uriString, fileName)

            val textHash = markdown.hashCode()
            if (renderCacheTextHash != textHash) {
                renderCacheTextHash = textHash
                renderCache.clear()
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
                isSourceCode -> renderSourceCode(isDark, markdown, codeLanguage!!)
                else -> renderMarkdown(isDark, markdown)
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
            renderCache[RenderCacheKey(isDark, isSourceCode, codeLanguage)] = rendered
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
            if (pendingExternalRerender && preferencesLoaded) {
                pendingExternalRerender = false
                scheduleRebuild()
            }
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
            searchJob?.cancel()
            applySearchQuery("")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchMatchIndex = 0
        )
        searchJob?.cancel()
        if (query.isBlank()) {
            applySearchQuery(query)
            return
        }
        // Debounced: the highlight pass rebuilds a span per match over the whole
        // document, which is too heavy to run per keystroke on large files.
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            applySearchQuery(query)
            // Bring the first match into view; without this, a query whose
            // matches are all off-screen gives no visible feedback.
            _matchOffsets.firstOrNull()?.let { _scrollToOffset.value = it }
        }
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

    fun onScrollPositionChanged(y: Int, maxY: Int) {
        _scrollY.value = y
        _scrollProgress.value = if (maxY > 0) {
            (y.toFloat() / maxY).coerceIn(0f, 1f)
        } else {
            null
        }
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
        scheduleRebuild()
    }

    fun onSystemDarkThemeChanged(isDark: Boolean) {
        if (systemDarkTheme == isDark) return
        systemDarkTheme = isDark
        if (renderDarkModeOverride == null) {
            scheduleRebuild()
        }
    }

    fun onPermissionRequestConsumed() {
        _uiState.value = _uiState.value.copy(needsPermission = false)
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                if (!preferencesLoaded) {
                    preferencesLoaded = true
                }
                val previous = _uiState.value.userPreferences
                val themeChanged = prefs.appThemeMode != previous.appThemeMode ||
                    prefs.readerLightTheme != previous.readerLightTheme ||
                    prefs.readerDarkTheme != previous.readerDarkTheme
                val requiresRebuild = themeChanged ||
                    prefs.readingFont != previous.readingFont ||
                    prefs.codeFont != previous.codeFont ||
                    prefs.fontSizeSp != previous.fontSizeSp ||
                    prefs.lineHeight != previous.lineHeight

                if (themeChanged) {
                    invalidateRendererCaches()
                }

                _uiState.value = _uiState.value.copy(userPreferences = prefs)

                if (pendingExternalRerender && _uiState.value.rawText.isNotBlank()) {
                    pendingExternalRerender = false
                    scheduleRebuild()
                }

                if (requiresRebuild) {
                    scheduleRebuild()
                }
            }
        }
    }

    private fun getMarkwonRenderer(isDark: Boolean): MarkwonRenderer {
        return rendererCache.getOrPut(isDark) {
            MarkwonRenderer(getApplication(), isDark, codeBackground(isDark))
        }
    }

    private fun getSourceCodeRenderer(isDark: Boolean): SourceCodeRenderer {
        return sourceCodeRendererCache.getOrPut(isDark) {
            SourceCodeRenderer(isDark)
        }
    }

    private fun invalidateRendererCaches() {
        rendererCache.clear()
        sourceCodeRendererCache.clear()
    }

    private suspend fun renderMarkdown(isDark: Boolean, markdown: String): Spanned? {
        return try {
            getMarkwonRenderer(isDark).render(markdown)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            null
        }
    }

    private suspend fun renderSourceCode(isDark: Boolean, code: String, language: String): Spanned? {
        return try {
            getSourceCodeRenderer(isDark).highlight(code, language)
        } catch (ex: CancellationException) {
            throw ex
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
            if (tryApplyCachedRender(isDark, isSourceCode, codeLanguage)) {
                return@launch
            }
            val rendered = if (isSourceCode && codeLanguage != null) {
                renderSourceCode(isDark, markdown, codeLanguage)
            } else {
                renderMarkdown(isDark, markdown)
            }
            if (rendered != null) {
                baseRendered = rendered
                renderCache[RenderCacheKey(isDark, isSourceCode, codeLanguage)] = rendered
                _uiState.value = _uiState.value.copy(rendered = rendered)
                applySearchQuery(_uiState.value.searchQuery)
            }
        }
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        val prefs = _uiState.value.userPreferences
        val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
        val codeLanguage = detectedCodeLanguage
        val isSourceCode = _uiState.value.isSourceCode
        if (tryApplyCachedRender(isDark, isSourceCode, codeLanguage)) {
            return
        }
        rebuildJob = viewModelScope.launch {
            kotlinx.coroutines.delay(50)
            rebuildRenderedForTheme()
        }
    }

    private fun tryApplyCachedRender(
        isDark: Boolean,
        isSourceCode: Boolean,
        codeLanguage: String?
    ): Boolean {
        val cached = renderCache[RenderCacheKey(isDark, isSourceCode, codeLanguage)] ?: return false
        baseRendered = cached
        _uiState.value = _uiState.value.copy(rendered = cached)
        applySearchQuery(_uiState.value.searchQuery)
        return true
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
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // Fall back to the last path segment if the provider can't be queried.
        }
        return@withContext uri.lastPathSegment
    }

    private suspend fun readTextFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()

        // If MainActivity registered a pre-read for this URI (ACTION_VIEW flow), await
        // its result. This handles restricted providers like DownloadStorageProvider that
        // require ACTION_OPEN_DOCUMENT-style access and reject reads from ViewModel scope.
        // The Activity guarantees the deferred completes (null on failure/cancellation),
        // so awaiting without a timeout cannot hang.
        val deferred = ExternalFileCache.consume(uriString)
        if (deferred != null) {
            val preloaded = deferred.await()
            if (preloaded != null) return@withContext preloaded
            // Pre-read failed; fall through to a direct attempt below.
        }

        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { stream ->
            return@withContext readBoundedText(stream)
        }
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { stream ->
                return@withContext readBoundedText(stream)
            }
        }
        return@withContext null
    }

    private fun ensureReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        getApplication<Application>().contentResolver.tryTakePersistablePermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
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
        // Passive highlights are capped: thousands of spans stall the TextView.
        // Every match stays countable and navigable via _matchOffsets, and the
        // active match is always styled below regardless of this cap.
        for (range in ranges.take(400)) {
            val start = range.first
            val end = (range.last + 1).coerceAtMost(highlighted.length)
            highlighted.setSpan(
                SearchHighlightSpan(matchBackgroundColor, isActive = false),
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
                SearchHighlightSpan(activeMatchBackgroundColor, isActive = true),
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
