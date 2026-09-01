package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import com.markreader.format.ReaderContent
import com.markreader.format.RenderTheme
import com.markreader.ui.UiMessage
import com.markreader.ui.reader.SearchHighlightSpan
import com.markreader.webview.HeadingItem
import com.markreader.webview.PageCommand
import com.markreader.R
import com.markreader.file.DocumentSource
import com.markreader.file.FileTooLargeException
import com.markreader.file.MAX_FILE_BYTES
import com.markreader.file.TextCodec
import com.markreader.file.tryTakePersistablePermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** The outline, as the page rendered it. Empty on every surface but the page. */
    val headings: List<HeadingItem> = emptyList(),
    /** The heading being read, by slug. Null before the page reports one, and in a flat document. */
    val activeHeadingSlug: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchCount: Int = 0,
    val searchMatchIndex: Int = 0,
    val userPreferences: UserPreferences = UserPreferences(),
    val documentType: DocumentType = DocumentType.PlainText,
    /** What the chrome may offer, decided by the document's format rather than by its type. */
    val capabilities: FormatCapabilities = FormatCapabilities(),
    /** Shown in place of a view mode where [capabilities] says there is no raw view. */
    @StringRes val formatNameRes: Int = R.string.viewer_subtitle_plain_text,
    /**
     * This WebView cannot run the shared renderer, so the source is drawn instead.
     *
     * Held for the session rather than per document: a WebView too old for the renderer will not
     * become new enough for the next one, and retrying would cost every document a ten-second
     * wait before falling back.
     */
    val rendererUnavailable: Boolean = false,
    /** The WebView's version, when it was known, for a warning worth reading. */
    val rendererWebViewVersion: String? = null
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

    /**
     * Search and outline requests bound for the page.
     *
     * A channel and not a [StateFlow], because these are requests and not state: "next match" has
     * no value to hold, and three of them are three requests. It is also why they survive the
     * WebView being rebuilt — which every toggle into raw mode and back does — instead of being
     * lost, or replayed at whatever page comes next forever.
     *
     * Drops the oldest when it fills. A backlog that deep means the page is not collecting, and
     * in that state the newest request is the only one still worth delivering.
     */
    private val pageCommandQueue = Channel<PageCommand>(
        capacity = PAGE_COMMAND_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pageCommands: Flow<PageCommand> = pageCommandQueue.receiveAsFlow()

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
                activeHeadingSlug = null,
                isSearchActive = false,
                searchQuery = "",
                searchMatchCount = 0,
                searchMatchIndex = 0
            )

            // The page's find state outlives the document it was run against, so a new document
            // would otherwise open with the previous one's query still painted in it.
            send(PageCommand.CloseFind)

            loadingJob = viewModelScope.launch {
                kotlinx.coroutines.delay(LOADING_INDICATOR_DELAY_MS)
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
        resyncSearch()
    }

    // ------------------------------------------------------------------------- search
    //
    // A search runs on whichever surface is drawing, and the two are not alike. Where the page is
    // drawing, it owns the match set — matches are DOM ranges over a rendering only it has — so
    // the query goes in and a count comes back as an event. Where a TextView is, the matches are
    // offsets into a string held here, painted with spans. Everything below is that fork, and
    // [usesPageFind] is the only place it is decided.

    fun onSearchToggled() {
        val active = !_uiState.value.isSearchActive
        val query = if (active) _uiState.value.searchQuery else ""
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = query,
            searchMatchCount = 0,
            searchMatchIndex = 0
        )
        searchJob?.cancel()
        if (active) {
            // The bar keeps its text when it closes, so re-opening it re-runs that query rather
            // than showing a stale count against a document nothing has searched.
            runSearch(query)
        } else if (usesPageFind) {
            send(PageCommand.CloseFind)
        } else {
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
            runSearch(query)
            return
        }
        // Debounced on both surfaces, for the same reason on each: the span pass rebuilds a span
        // per match over the whole document, and the page's walks its whole DOM for them.
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    fun onNextMatch() = stepMatch(1)

    fun onPreviousMatch() = stepMatch(-1)

    /** The reader picked a heading out of the table of contents. */
    fun onHeadingSelected(heading: HeadingItem) {
        send(PageCommand.Reveal(heading.slug))
    }

    /**
     * The page's find result: which match is current, and how many there are. An index of -1
     * means there are none, which the search bar shows as its own no-matches state.
     *
     * Deliberately not filtered against the query in the bar. The page only ever answers
     * something this side asked it, and in order, so the last answer is the current one — the
     * same guarantee the debounced span pass relies on.
     */
    fun onFindStateChanged(index: Int, total: Int) {
        _uiState.value = _uiState.value.copy(
            searchMatchCount = total,
            searchMatchIndex = index.coerceAtLeast(0)
        )
    }

    /** Runs [query] on whichever surface is drawing. */
    private fun runSearch(query: String) {
        if (usesPageFind) {
            // The page reveals the first match itself, and reports the count back.
            send(PageCommand.Find(query))
            return
        }
        applySearchQuery(query)
        // Bring the first match into view; without this, a query whose matches are all
        // off-screen gives no visible feedback.
        _matchOffsets.firstOrNull()?.let { _scrollToOffset.value = it }
    }

    /**
     * Moves to another match, wrapping at either end.
     *
     * On the page the index is not ours to compute: it holds the match set, so it steps and says
     * where it landed. Out here the offsets are already known and the index is arithmetic.
     */
    private fun stepMatch(delta: Int) {
        if (usesPageFind) {
            send(PageCommand.StepFind(delta))
            return
        }
        val count = _uiState.value.searchMatchCount
        if (count == 0) return
        val next = ((_uiState.value.searchMatchIndex + delta) % count + count) % count
        _uiState.value = _uiState.value.copy(searchMatchIndex = next)
        _scrollToOffset.value = _matchOffsets.getOrNull(next)
        applySearchQuery(_uiState.value.searchQuery)
    }

    /**
     * Re-runs a live search after the surface underneath it changed — a view-mode toggle, or a
     * renderer that turned out not to start.
     *
     * Neither surface can read the other's result, so the one arriving is given the query again
     * from scratch. Nothing to do while the bar is closed, because then there is no query.
     */
    private fun resyncSearch() {
        if (!_uiState.value.isSearchActive) return
        val query = _uiState.value.searchQuery
        _uiState.value = _uiState.value.copy(searchMatchCount = 0, searchMatchIndex = 0)
        if (usesPageFind) {
            // Drop the spans the TextView was painting; they index text that is no longer drawn.
            applySearchQuery("")
        }
        // Nothing is sent to close the find on the way out: leaving the page destroys it, and
        // whatever WebView replaces it starts with no find at all.
        runSearch(query)
    }

    /**
     * Whether a search goes to the page's own find.
     *
     * Asked of [readerContentFor]'s own decision, through [isDrawnByPage], so the two cannot
     * disagree: a document searched by the page is exactly a document the page is drawing.
     */
    private val usesPageFind: Boolean
        get() = isDrawnByPage(
            isRawMode = _uiState.value.viewMode == ViewMode.Raw,
            rendered = baseRendered,
            rendererUnavailable = _uiState.value.rendererUnavailable
        )

    private fun send(command: PageCommand) {
        pageCommandQueue.trySend(command)
    }

    // ------------------------------------------------------------- the page's own reports

    /** The document's outline, as the page rendered it. Re-reported on every render. */
    fun onOutlineChanged(outline: List<HeadingItem>) {
        if (_uiState.value.headings == outline) return
        _uiState.value = _uiState.value.copy(headings = outline)
    }

    fun onActiveHeadingChanged(slug: String?) {
        if (_uiState.value.activeHeadingSlug == slug) return
        _uiState.value = _uiState.value.copy(activeHeadingSlug = slug)
    }

    /**
     * This WebView never started the renderer, so `Markdown` has nothing to draw it with and the
     * source is drawn instead. See [ViewerUiState.rendererUnavailable] for why it is not cleared
     * per document.
     */
    fun onRendererUnavailable(webViewVersion: String?) {
        if (_uiState.value.rendererUnavailable) return
        _uiState.value = _uiState.value.copy(
            rendererUnavailable = true,
            rendererWebViewVersion = webViewVersion,
            headings = emptyList(),
            activeHeadingSlug = null
        )
        // A live search was just left holding a surface that no longer exists.
        resyncSearch()
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
        viewModelScope.launch {
            if (tryApplyCachedRender(documentType, theme)) {
                return@launch
            }
            val loaded = format.load(markdown, theme) ?: return@launch
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

    private var _matchOffsets: List<Int> = emptyList()

    private fun applySearchQuery(query: String) {
        val currentState = _uiState.value
        val drawsSource = currentState.viewMode == ViewMode.Raw || currentState.rendererUnavailable
        // Search out here is span-based: it counts matches in a string and paints them with
        // spans. That is a requirement of this implementation and not of the reader, so the
        // narrowing lives here rather than at the format boundary — and a document the page is
        // drawing never reaches this function at all, because the page searches it instead.
        //
        // The text searched is whatever [readerContentFor] says is on screen, asked with no
        // highlighting so it answers the unmarked original. Reading `uiState.rendered` instead
        // would search the *previous* query's spans.
        val drawn = readerContentFor(
            isRawMode = currentState.viewMode == ViewMode.Raw,
            rawText = currentState.rawText,
            rawHighlighted = null,
            rendered = baseRendered,
            rendererUnavailable = currentState.rendererUnavailable
        )
        val baseSpans = (drawn as? ReaderContent.Spans)?.text

        if (baseSpans == null) {
            // A rendering that is not text — a table — has no offsets to walk.
            _matchOffsets = emptyList()
            _uiState.value = currentState.copy(
                rawHighlighted = null,
                searchMatchCount = 0,
                searchMatchIndex = 0
            )
            return
        }
        val baseText = baseSpans.toString()

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

        val highlighted = SpannableString(baseSpans)
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

        // Where the source is on screen the highlighting has to arrive as `rawHighlighted`,
        // because that is the field [readerContentFor] draws from in both of the cases that put
        // it there: raw mode, and a renderer that could not start.
        _uiState.value = if (drawsSource) {
            _uiState.value.copy(
                rendered = baseRendered,
                rawHighlighted = ReaderContent.Spans(highlighted),
                searchMatchCount = offsets.size,
                searchMatchIndex = if (offsets.isEmpty()) 0 else activeIndex
            )
        } else {
            _uiState.value.copy(
                rendered = ReaderContent.Spans(highlighted),
                rawHighlighted = null,
                searchMatchCount = offsets.size,
                searchMatchIndex = if (offsets.isEmpty()) 0 else activeIndex
            )
        }
    }

    companion object {
        /** Coalesces the burst of preference emissions a single theme switch produces. */
        private const val REBUILD_DEBOUNCE_MS = 50L

        /** How long a query is left alone before it is run. Neither surface searches cheaply. */
        private const val SEARCH_DEBOUNCE_MS = 200L

        /** A read faster than this shows no spinner at all, rather than one that flashes. */
        private const val LOADING_INDICATOR_DELAY_MS = 300L

        /**
         * How many page requests are held while no page is collecting them.
         *
         * Deep enough for the burst a view-mode toggle produces while the WebView is being
         * rebuilt, and shallow enough that a backlog beyond it is a page that is not coming back.
         */
        private const val PAGE_COMMAND_BUFFER = 16

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
