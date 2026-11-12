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
import com.markreader.data.ReaderThemePreference
import com.markreader.data.RecentFilesRepository
import com.markreader.data.UserPreferences
import com.markreader.file.DocumentType
import com.markreader.file.DocumentTypeResolver
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.FormatRegistry
import com.markreader.format.ReaderContent
import com.markreader.ui.UiMessage
import com.markreader.ui.reader.NativeSearchMatches
import com.markreader.ui.reader.SearchHighlightColors
import com.markreader.ui.reader.SearchHighlights
import com.markreader.webview.HeadingItem
import com.markreader.webview.PageCommand
import com.markreader.R
import com.markreader.file.DocumentSource
import com.markreader.file.FileTooLargeException
import com.markreader.file.MAX_FILE_MIB
import com.markreader.file.TextCodec
import com.markreader.file.tryTakePersistablePermission
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
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
    /**
     * The matches of a search over text a `TextView` is drawing, for it to paint. Null while no
     * such search has matches, and always null where the page is drawing: it paints its own.
     */
    val searchHighlights: SearchHighlights? = null,
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
    private val repository = PreferencesRepository.getInstance(application)
    private val recentFilesRepository = RecentFilesRepository.getInstance(application)
    private var systemDarkTheme = false
    private val formats = FormatRegistry()
    /** The format for the document on screen, held so raw mode can ask it what it is. */
    private var currentFormat: DocumentFormat? = null
    private var currentUri: String? = null
    private var loadJob: Job? = null
    private var loadingJob: Job? = null
    private var searchJob: Job? = null
    private var baseRendered: ReaderContent? = null
    private var renderDarkModeOverride: Boolean? = null
    private var preferencesLoaded = false

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
            beginLoad()

            val uri = Uri.parse(uriString)
            ensureReadPermission(uri)
            val fileName = resolveFileName(uri)
                ?: getApplication<Application>().getString(R.string.untitled)
            val mimeType = resolveMimeType(uri)

            // Four outcomes, each its own function below, and each of them terminal: a failure,
            // an empty file, a document no format will render, and a document that renders.
            val read = readDocument(uri)
            val text = read.text
            if (text == null) {
                showReadFailure(fileName, read.error)
                return@launch
            }

            recentFilesRepository.recordOpen(uriString, fileName)
            if (text.isBlank()) {
                showEmptyDocument(fileName, text)
                return@launch
            }

            val documentType = DocumentTypeResolver.resolve(fileName, mimeType, text)
            val format = formats.formatFor(documentType)
            currentFormat = format
            val loaded = format?.load(text)
            if (format == null || loaded == null) {
                showUnrenderedDocument(fileName, text, documentType, format)
                return@launch
            }

            showDocument(fileName, text, documentType, format, loaded)
        }
    }

    /** One document's read: its text, or the failure that explains why there is none. */
    private class DocumentRead(val text: String?, val error: Exception?)

    /**
     * Clears every trace of the document being replaced, and arms the loading indicator.
     *
     * The find closes here rather than with the old document, because the page's find state
     * outlives what it was run against: without this a new document opens with the previous
     * one's query still painted into it.
     */
    private fun beginLoad() {
        loadingJob?.cancel()
        searchJob?.cancel()
        _scrollProgress.value = null
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isLoadingVisible = false,
            errorMessage = null,
            warningMessage = null,
            isEmptyFile = false,
            needsPermission = false,
            rawText = "",
            rendered = null,
            headings = emptyList(),
            activeHeadingSlug = null,
            isSearchActive = false,
            searchQuery = "",
            searchMatchCount = 0,
            searchMatchIndex = 0,
            searchHighlights = null
        )
        send(PageCommand.CloseFind)
        loadingJob = viewModelScope.launch {
            kotlinx.coroutines.delay(LOADING_INDICATOR_DELAY_MS)
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoadingVisible = true)
            }
        }
    }

    /**
     * Reads the document, keeping the failure rather than naming it away: which one it was
     * decides the message, and whether the screen offers to re-grant permission.
     */
    private suspend fun readDocument(uri: Uri): DocumentRead = try {
        DocumentRead(readTextFromUri(uri), null)
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: FileTooLargeException) {
        DocumentRead(null, ex)
    } catch (ex: SecurityException) {
        DocumentRead(null, ex)
    } catch (ex: IOException) {
        DocumentRead(null, ex)
    } catch (ex: IllegalArgumentException) {
        DocumentRead(null, ex)
    }

    private fun showReadFailure(fileName: String, readError: Exception?) {
        loadingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoadingVisible = false,
            fileName = fileName,
            errorMessage = if (readError is FileTooLargeException) {
                UiMessage(R.string.viewer_error_file_too_large, listOf(MAX_FILE_MIB))
            } else {
                describeReadFailure(readError)
            },
            needsPermission = readError is SecurityException,
            searchQuery = "",
            searchMatchCount = 0,
            searchMatchIndex = 0
        )
    }

    private fun showEmptyDocument(fileName: String, text: String) {
        loadingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoadingVisible = false,
            fileName = fileName,
            rawText = text,
            isEmptyFile = true,
            searchQuery = "",
            searchMatchCount = 0,
            searchMatchIndex = 0
        )
    }

    /** A document with no rendering: raw mode, and a warning saying why that is all there is. */
    private fun showUnrenderedDocument(
        fileName: String,
        text: String,
        documentType: DocumentType,
        format: DocumentFormat?
    ) {
        loadingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoadingVisible = false,
            fileName = fileName,
            rawText = text,
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
    }

    private fun showDocument(
        fileName: String,
        text: String,
        documentType: DocumentType,
        format: DocumentFormat,
        loaded: ReaderContent
    ) {
        loadingJob?.cancel()
        baseRendered = loaded
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoadingVisible = false,
            fileName = fileName,
            rawText = text,
            rendered = loaded,
            errorMessage = null,
            warningMessage = null,
            documentType = documentType,
            capabilities = format.capabilities,
            formatNameRes = format.nameRes
        )

        // A query survives the document it was typed against, so the new one is searched for it.
        if (!usesPageFind) runSearch(_uiState.value.searchQuery)
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
    // offsets into a string held here, found off the main thread and handed to the view to draw.
    // Everything below is that fork, and [usesPageFind] is the only place it is decided.

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
            clearTextSearch()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchMatchIndex = 0
        )
        // Debounced on both surfaces, for the same reason on each: a search reads the whole
        // document, as text out here and as DOM in the page. Clearing the query is not a search.
        runSearch(query, debounceMs = if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS)
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
     * something this side asked it, and in order, so the last answer is the current one.
     */
    fun onFindStateChanged(index: Int, total: Int) {
        _uiState.value = _uiState.value.copy(
            searchMatchCount = total,
            searchMatchIndex = index.coerceAtLeast(0)
        )
    }

    /**
     * Runs [query] on whichever surface is drawing, in place of any search still under way.
     *
     * One job holds the debounce and the search behind it, so cancelling it is all that
     * retiring a search takes — and everything that changes what is being searched does that
     * by coming through here, or through [beginLoad]. A search cancelled while it was matching
     * never resumes to publish, which is what keeps one document's offsets off the next.
     *
     * With no debounce the page's request is sent before this returns: the scope dispatches
     * immediately, and nothing suspends ahead of it.
     */
    private fun runSearch(query: String, debounceMs: Long = 0L) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) kotlinx.coroutines.delay(debounceMs)
            if (usesPageFind) {
                // The page reveals the first match itself, and reports the count back.
                send(PageCommand.Find(query))
            } else {
                findInText(query)
            }
        }
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
        val highlights = _uiState.value.searchHighlights ?: return
        val count = highlights.matches.size
        if (count == 0) return
        val next = ((highlights.activeIndex + delta) % count + count) % count
        // The same matches with another one current: nothing is searched again, and the view
        // redraws without its text being touched.
        _uiState.value = _uiState.value.copy(
            searchMatchIndex = next,
            searchHighlights = highlights.copy(activeIndex = next)
        )
        _scrollToOffset.value = highlights.matches.start(next)
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
        // Drop what the TextView was painting: those offsets index text that is no longer drawn,
        // and the search that replaces them does not land until it has run.
        clearTextSearch()
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
        repaintSearchHighlights()
    }

    fun onSystemDarkThemeChanged(isDark: Boolean) {
        if (systemDarkTheme == isDark) return
        systemDarkTheme = isDark
        if (renderDarkModeOverride == null) {
            repaintSearchHighlights()
        }
    }

    /**
     * The one thing a surface theme change still has to do on this side.
     *
     * Both surfaces restyle themselves — the page from its renderer settings, the raw TextView
     * from the colours the screen hands it — but a search highlight is drawn in colours this
     * class chose for whichever palette was on screen at the time. Nothing else would repaint
     * one, so a light/dark flip would otherwise leave the old palette's boxes behind.
     *
     * Only the colours are swapped. The matches and the current one are kept, so nothing is
     * searched again and the reader stays on the match they were on.
     */
    private fun repaintSearchHighlights() {
        val highlights = _uiState.value.searchHighlights ?: return
        _uiState.value = _uiState.value.copy(
            searchHighlights = highlights.copy(colors = searchHighlightColors())
        )
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
                val paletteChanged = prefs.appThemeMode != previous.appThemeMode ||
                    prefs.readerLightTheme != previous.readerLightTheme ||
                    prefs.readerDarkTheme != previous.readerDarkTheme

                // Typography and palette both reach the surfaces through composition — the page
                // through its renderer settings, the raw TextView through its style key — so a
                // preference change has nothing to re-render here. The one exception is below.
                _uiState.value = _uiState.value.copy(userPreferences = prefs)
                if (paletteChanged) {
                    repaintSearchHighlights()
                }
            }
        }
    }

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
            // Rethrown before the clauses below, and load-bearing: CancellationException is an
            // IllegalStateException, so catching that would swallow the coroutine's cancellation.
            throw ex
        // Fall back to the last path segment if the provider can't be queried.
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
        return@withContext uri.lastPathSegment
    }

    /** Only a hint: providers mislabel freely, so the resolver trusts the name over this. */
    private suspend fun resolveMimeType(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.getType(uri)
        } catch (ex: CancellationException) {
            // See resolveFileName: rethrown before the IllegalStateException clause.
            throw ex
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
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

    /**
     * The text a `TextView` is drawing, or null where there is none to search.
     *
     * Asked of [readerContentFor], so the text searched is the text on screen. A rendering that
     * is not text — a table — has no offsets to walk, and a document the page is drawing never
     * gets here at all, because the page searches it instead.
     */
    private fun drawnText(): CharSequence? {
        val state = _uiState.value
        val drawn = readerContentFor(
            isRawMode = state.viewMode == ViewMode.Raw,
            rawText = state.rawText,
            rendered = baseRendered,
            rendererUnavailable = state.rendererUnavailable
        )
        return (drawn as? ReaderContent.Spans)?.text
    }

    /**
     * Finds [query] in the drawn text and publishes the matches for the view to paint.
     *
     * The matching runs off the main thread and checks for cancellation as it goes: a large
     * document can take long enough that the next keystroke arrives first, and that keystroke
     * should not wait behind a search nobody wants any more.
     */
    private suspend fun findInText(query: String) {
        val text = if (query.isBlank()) null else drawnText()
        if (text == null) {
            clearTextSearch()
            return
        }
        val matches = withContext(Dispatchers.Default) {
            NativeSearchMatches.find(text, query) { ensureActive() }
        }
        if (matches.size == 0) {
            clearTextSearch()
            return
        }
        _uiState.value = _uiState.value.copy(
            searchMatchCount = matches.size,
            searchMatchIndex = 0,
            searchHighlights = SearchHighlights(matches, 0, searchHighlightColors())
        )
        // Bring the first match into view; without this, a query whose matches are all
        // off-screen gives no visible feedback.
        _scrollToOffset.value = matches.start(0)
    }

    /** Leaves the `TextView` with no matches to draw and the bar with none to count. */
    private fun clearTextSearch() {
        _uiState.value = _uiState.value.copy(
            searchMatchCount = 0,
            searchMatchIndex = 0,
            searchHighlights = null
        )
    }

    private fun searchHighlightColors(): SearchHighlightColors {
        val prefs = _uiState.value.userPreferences
        val isDark = renderDarkModeOverride ?: isDarkTheme(prefs, systemDarkTheme)
        val activeTheme = if (isDark) prefs.readerDarkTheme else prefs.readerLightTheme
        val isSepia = activeTheme == ReaderThemePreference.Sepia
        val passiveBackground = when {
            isDark -> 0x66FFD54F.toInt()
            isSepia -> 0x55A85A00.toInt()
            else -> 0x553197F5.toInt()
        }
        val activeBackground = when {
            isDark -> 0xCCFFD54F.toInt()
            isSepia -> 0xDD8B4513.toInt()
            else -> 0xFF1565C0.toInt()
        }
        val activeText = when {
            isDark -> 0xFF1A1A1A.toInt()
            isSepia -> 0xFFFFFFFF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        return SearchHighlightColors(passiveBackground, activeBackground, activeText)
    }

    companion object {
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
