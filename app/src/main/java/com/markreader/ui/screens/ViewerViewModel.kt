package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.RecentFilesRepository
import com.markreader.data.UserPreferences
import com.markreader.file.DocumentType
import com.markreader.file.DocumentTypeResolver
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.FormatRegistry
import com.markreader.format.HeadingItem
import com.markreader.format.ReaderContent
import com.markreader.format.RenderTheme
import com.markreader.ui.reader.NativeSearchMatches
import com.markreader.ui.reader.NativeSearchText
import com.markreader.ui.UiMessage
import com.markreader.R
import com.markreader.file.DocumentSource
import com.markreader.file.FileTooLargeException
import com.markreader.file.MAX_FILE_BYTES
import com.markreader.file.TextCodec
import com.markreader.file.tryTakePersistablePermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewMode {
    Rendered,
    Raw
}

data class ViewerUiState(
    val isLoading: Boolean = true,
    val isLoadingVisible: Boolean = false,
    val fileName: String = "",
    val rawText: String = "",
    val rawHighlighted: ReaderContent? = null,
    val rendered: ReaderContent? = null,
    val errorMessage: UiMessage? = null,
    val warningMessage: UiMessage? = null,
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
    val documentType: DocumentType = DocumentType.PlainText,
    /** What the chrome may offer, decided by the document's format rather than by its type. */
    val capabilities: FormatCapabilities = FormatCapabilities(),
    /** Shown in place of a view mode where [capabilities] says there is no raw view. */
    @StringRes val formatNameRes: Int = R.string.viewer_subtitle_plain_text
)

class ViewerViewModel(
    application: Application,
    private val initialUri: String?
) : AndroidViewModel(application) {
    private data class RenderCacheKey(
        val documentType: DocumentType,
        val theme: RenderTheme
    )

    private val repository = PreferencesRepository.getInstance(application)
    private val recentFilesRepository = RecentFilesRepository.getInstance(application)
    private var systemDarkTheme = false
    private val formats = FormatRegistry(application)
    /** The format for the document on screen, held so its renderers survive a theme toggle. */
    private var currentFormat: DocumentFormat? = null
    private var currentUri: String? = null
    private var loadJob: Job? = null
    private var loadingJob: Job? = null
    private var rebuildJob: Job? = null
    private var searchJob: Job? = null
    private var baseRendered: ReaderContent? = null
    private var renderDarkModeOverride: Boolean? = null
    private var preferencesLoaded = false
    private var pendingExternalRerender = initialUri != null
    private var renderCacheTextHash = 0
    private val renderCache = mutableMapOf<RenderCacheKey, ReaderContent>()

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
                errorMessage = UiMessage(R.string.viewer_error_no_file_selected)
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
        // Invalidate synchronously, before dispatching the replacement load.
        invalidateNativeSearch()
        rebuildJob?.cancel()
        currentFormat = null
        baseRendered = null
        _scrollToOffset.value = null
        loadJob = viewModelScope.launch {
            loadingJob?.cancel()
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

            val fileName = resolveFileName(uri) ?: getApplication<Application>().getString(R.string.untitled)
            val mimeType = resolveMimeType(uri)

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
                    UiMessage(
                        R.string.viewer_error_file_too_large,
                        listOf(MAX_FILE_BYTES / (1024 * 1024))
                    )
                } else {
                    describeReadFailure(readError)
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

            val documentType = DocumentTypeResolver.resolve(fileName, mimeType, markdown)
            val format = formats.formatFor(documentType)
            currentFormat = format
            val theme = RenderTheme(
                renderDarkModeOverride ?: isDarkTheme(_uiState.value.userPreferences, systemDarkTheme)
            )
            val loaded = format?.load(markdown, theme)

            if (format == null || loaded == null) {
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
                    documentType = documentType,
                    // A null format is DocumentType.Binary: bytes that are not text, so there
                    // is no format to ask and nothing but the raw view to offer.
                    capabilities = format?.capabilities ?: FormatCapabilities(),
                    formatNameRes = format?.nameRes ?: R.string.viewer_subtitle_raw_mode,
                    warningMessage = UiMessage(
                        format?.renderFailureWarningRes ?: R.string.viewer_warning_binary
                    )
                )
                baseRendered = null
                return@launch
            }

            val headings = format.headings(markdown, loaded)
            loadingJob?.cancel()
            baseRendered = loaded
            renderCache[RenderCacheKey(documentType, theme)] = loaded
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingVisible = false,
                fileName = fileName,
                rawText = markdown,
                rawHighlighted = null,
                rendered = loaded,
                errorMessage = null,
                warningMessage = null,
                headings = headings,
                activeHeadingIndex = clampActiveHeadingIndex(_uiState.value.activeHeadingIndex, headings),
                documentType = documentType,
                capabilities = format.capabilities,
                formatNameRes = format.nameRes
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
        applySearchQuery(query, debounce = true, scrollToFirst = true)
    }

    fun onNextMatch() = navigateNativeMatch(1)

    fun onPreviousMatch() = navigateNativeMatch(-1)

    private fun navigateNativeMatch(delta: Int) {
        val count = nativeMatches.size
        if (count == 0) return
        val index = (_uiState.value.searchMatchIndex + delta + count) % count
        val decoration = nativeDecoration?.withActiveIndex(index) ?: return
        nativeDecoration = decoration
        publishNativeDecoration(decoration, index)
        _scrollToOffset.value = nativeMatches.start(index)
    }

    fun onHeadingSelected(offset: Int) {
        _scrollToOffset.value = offset
    }

    fun onScrollPositionChanged(y: Int, maxY: Int) {
        _scrollY.value = y
        _scrollProgress.value = progressFor(y, maxY)
    }

    /**
     * The scrollable extent changed without the position moving — a relayout
     * rather than a scroll. Updates progress only: publishing a position here
     * would make layout passes indistinguishable from user scrolling.
     */
    fun onScrollExtentChanged(maxY: Int) {
        _scrollProgress.value = progressFor(_scrollY.value, maxY)
    }

    private fun progressFor(y: Int, maxY: Int): Float? = if (maxY > 0) {
        (y.toFloat() / maxY).coerceIn(0f, 1f)
    } else {
        null
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
                    formats.invalidateRenderers()
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

    private fun rebuildRenderedForTheme() {
        val markdown = _uiState.value.rawText
        if (markdown.isBlank()) return
        val format = currentFormat ?: return
        val theme = currentTheme()
        val documentType = _uiState.value.documentType
        rebuildJob = viewModelScope.launch {
            if (tryApplyCachedRender(documentType, theme)) {
                return@launch
            }
            val loaded = format.load(markdown, theme) ?: return@launch
            if (_uiState.value.rawText !== markdown || currentFormat !== format || currentTheme() != theme) {
                return@launch
            }
            baseRendered = loaded
            renderCache[RenderCacheKey(documentType, theme)] = loaded
            _uiState.value = _uiState.value.copy(rendered = loaded)
            applySearchQuery(_uiState.value.searchQuery)
        }
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        if (tryApplyCachedRender(_uiState.value.documentType, currentTheme())) {
            return
        }
        rebuildJob = viewModelScope.launch {
            kotlinx.coroutines.delay(REBUILD_DEBOUNCE_MS)
            rebuildRenderedForTheme()
        }
    }

    private fun tryApplyCachedRender(documentType: DocumentType, theme: RenderTheme): Boolean {
        val cached = renderCache[RenderCacheKey(documentType, theme)] ?: return false
        baseRendered = cached
        _uiState.value = _uiState.value.copy(rendered = cached)
        applySearchQuery(_uiState.value.searchQuery)
        return true
    }

    /** The surface the document is being drawn on: the reader's own override, else the app theme. */
    private fun currentTheme(): RenderTheme = RenderTheme(
        renderDarkModeOverride ?: isDarkTheme(_uiState.value.userPreferences, systemDarkTheme)
    )

    /**
     * The read failure, with whatever the exception gave us appended. The class name
     * and message are diagnostics rather than prose, so they are passed in as
     * arguments instead of being spelled out in the resource.
     */
    private fun describeReadFailure(readError: Exception?): UiMessage {
        if (readError == null) return UiMessage(R.string.viewer_error_open_failed)
        val className = readError.javaClass.simpleName
        val message = readError.message?.takeIf { it.isNotBlank() }
            ?: return UiMessage(R.string.viewer_error_open_failed_class, listOf(className))
        return UiMessage(R.string.viewer_error_open_failed_detail, listOf(className, message))
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

    /** Only a hint: providers mislabel freely, so the resolver trusts the name over this. */
    private suspend fun resolveMimeType(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.getType(uri)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            null
        }
    }

    private suspend fun readTextFromUri(uri: Uri): String? {
        val bytes = DocumentSource.read(getApplication<Application>().contentResolver, uri)
            ?: return null
        // The viewer never writes, so it wants the text and not the encoding that came with it.
        // Off the main thread regardless: this decodes up to MAX_FILE_BYTES.
        return withContext(Dispatchers.Default) { TextCodec.decode(bytes).text }
    }

    private fun ensureReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        getApplication<Application>().contentResolver.tryTakePersistablePermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    private var searchGeneration = 0L
    private var nativeMatches = NativeSearchMatches.Empty
    private var nativeDecoration: NativeSearchText? = null

    /** Exposed for the screen's WebView find-in-page routing; raw mode stays native. */
    val isWebViewSearch: Boolean
        get() = _uiState.value.viewMode == ViewMode.Rendered && baseRendered is ReaderContent.Markdown

    /** Ignore callbacks from a closed search, old query, or another reading surface. */
    fun onWebViewSearchResult(query: String, count: Int, index: Int) {
        val state = _uiState.value
        if (!isWebViewSearch || state.rendered !is ReaderContent.Markdown) return
        if (!state.isSearchActive || state.searchQuery != query) return
        val safeCount = count.coerceAtLeast(0)
        _uiState.value = state.copy(
            searchMatchCount = safeCount,
            searchMatchIndex = if (safeCount == 0) 0 else index.coerceIn(0, safeCount - 1)
        )
    }

    private fun invalidateNativeSearch() {
        searchJob?.cancel()
        searchGeneration++
        nativeMatches = NativeSearchMatches.Empty
        nativeDecoration = null
    }

    private fun applySearchQuery(
        query: String,
        debounce: Boolean = false,
        scrollToFirst: Boolean = false
    ) {
        invalidateNativeSearch()
        val generation = searchGeneration
        val state = _uiState.value
        val mode = state.viewMode
        val rendered = baseRendered
        val raw = state.rawText
        // Capture only the original text, never a previous search decoration.
        val source = when (mode) {
            ViewMode.Raw -> raw
            ViewMode.Rendered -> (rendered as? ReaderContent.Spans)?.text
        }
        _uiState.value = state.copy(
            rendered = rendered,
            rawHighlighted = null,
            searchMatchCount = 0,
            searchMatchIndex = 0
        )
        if (query.isBlank() || source == null) return
        searchJob = viewModelScope.launch {
            if (debounce) kotlinx.coroutines.delay(200)
            val matches = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                NativeSearchMatches.find(source, query) { context.ensureActive() }
            }
            val latest = _uiState.value
            // Cancellation plus explicit identity guards: a completed worker must not
            // publish offsets into a replacement document, view mode or theme render.
            if (generation != searchGeneration || latest.searchQuery != query) return@launch
            if (latest.viewMode != mode || latest.rawText !== raw || baseRendered !== rendered) return@launch
            publishNativeMatches(source, matches, latest, scrollToFirst)
        }
    }

    private fun publishNativeMatches(
        source: CharSequence,
        matches: NativeSearchMatches,
        state: ViewerUiState,
        scrollToFirst: Boolean
    ) {
        nativeMatches = matches
        if (matches.size == 0) return
        val decoration = createNativeDecoration(source, matches, state.userPreferences)
        nativeDecoration = decoration
        publishNativeDecoration(decoration, 0)
        if (scrollToFirst) _scrollToOffset.value = matches.start(0)
    }

    private fun createNativeDecoration(
        source: CharSequence,
        matches: NativeSearchMatches,
        prefs: UserPreferences
    ): NativeSearchText {
        val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
        val activeTheme = if (isDark) prefs.readerDarkTheme else prefs.readerLightTheme
        val isSepia = activeTheme == com.markreader.data.ReaderThemePreference.Sepia
        val passiveColor = when {
            isDark -> 0x66FFD54F.toInt()
            isSepia -> 0x55A85A00.toInt()
            else -> 0x553197F5.toInt()
        }
        val activeColor = when {
            isDark -> 0xCCFFD54F.toInt()
            isSepia -> 0xDD8B4513.toInt()
            else -> 0xFF1565C0.toInt()
        }
        return NativeSearchText(source, matches, 0, passiveColor, activeColor)
    }

    private fun publishNativeDecoration(decoration: NativeSearchText, index: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            rendered = if (state.viewMode == ViewMode.Rendered) ReaderContent.Spans(decoration) else baseRendered,
            rawHighlighted = if (state.viewMode == ViewMode.Raw) ReaderContent.Spans(decoration) else null,
            searchMatchCount = decoration.matches.size,
            searchMatchIndex = index
        )
    }

    private fun clampActiveHeadingIndex(current: Int, headings: List<HeadingItem>): Int {
        if (headings.isEmpty()) return -1
        if (current < 0) return -1
        return current.coerceAtMost(headings.lastIndex)
    }

    companion object {
        /** Coalesces the burst of preference emissions a single theme switch produces. */
        private const val REBUILD_DEBOUNCE_MS = 50L

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
