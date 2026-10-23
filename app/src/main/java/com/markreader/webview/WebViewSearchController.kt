package com.markreader.webview

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI-thread-only owner of native rendered-text search and its callback serialization fence. */
internal class WebViewSearchController(
    private val webView: WebView,
    private val scope: CoroutineScope,
) {
    private var destroyed = false
    private var searchQuery = ""
    private var searchGeneration = 0L
    // Native callbacks have no request ID; never replace a request until its final callback.
    private var inFlightSearch: Pair<Long, String>? = null
    private var searchReady = false
    private var searchDocumentReady = false
    private var searchDebounce: Job? = null
    private var searchResult: (String, Int, Int) -> Unit = { _, _, _ -> }
    private var searchMatchCount = 0
    private val pendingSearchNavigation = java.util.ArrayDeque<Boolean>()

    init {
        webView.setFindListener { activeIndex, count, done ->
            if (done && !destroyed) onFindResult(activeIndex, count)
        }
    }

    fun setSearchQuery(query: String, onResult: (String, Int, Int) -> Unit) {
        searchResult = onResult
        if (query == searchQuery) return
        searchQuery = query
        scheduleSearch()
    }

    /** Native navigation uses the existing match cache, not a new scan. */
    fun findNext(forward: Boolean) {
        if (!destroyed && searchReady && searchMatchCount > 0) {
            if (inFlightSearch != null) {
                pendingSearchNavigation.addLast(forward)
                return
            }
            inFlightSearch = searchGeneration to searchQuery
            webView.findNext(forward)
        }
    }

    fun invalidateDocument() {
        searchDocumentReady = false
        // Keep inFlightSearch: a late callback must still belong to the old document/query.
        // If WebView drops that callback on replacement, the fence can remain blocked. Native
        // callbacks carry no document ID, so resetting it here would instead misattribute results.
        scheduleSearch()
    }

    /** Called after every renderer layout, including same-document settings re-renders. */
    fun rendered() {
        searchDocumentReady = true
        scheduleSearch()
    }

    fun destroy() {
        destroyed = true
        searchDebounce?.cancel()
        ++searchGeneration
        webView.setFindListener(null)
        webView.clearMatches()
    }

    private fun onFindResult(activeIndex: Int, count: Int) {
        val request = inFlightSearch
        inFlightSearch = null
        if (request != null && request.first == searchGeneration) {
            searchMatchCount = count.coerceAtLeast(0)
            searchResult(request.second, searchMatchCount,
                if (count == 0) 0 else activeIndex.coerceIn(0, count - 1))
        }
        if (request != null && searchQuery.isEmpty()) webView.clearMatches()
        else if (request != null && request.first != searchGeneration) startSearchIfReady()
        else if (pendingSearchNavigation.isNotEmpty()) findNext(pendingSearchNavigation.removeFirst())
    }

    private fun scheduleSearch() {
        ++searchGeneration
        pendingSearchNavigation.clear()
        searchDebounce?.cancel()
        searchReady = false
        searchMatchCount = 0
        searchResult(searchQuery, 0, 0)
        if (searchQuery.isEmpty()) {
            // Do not cancel an in-flight scan: its final callback is our serialization fence.
            if (inFlightSearch == null) webView.clearMatches()
            return
        }
        searchDebounce = scope.launch(Dispatchers.Main) {
            delay(SEARCH_DEBOUNCE_MS)
            if (!destroyed) {
                searchReady = true
                startSearchIfReady()
            }
        }
    }

    private fun startSearchIfReady() {
        if (destroyed || !searchDocumentReady || !searchReady) return
        if (inFlightSearch != null || searchQuery.isEmpty()) return
        inFlightSearch = searchGeneration to searchQuery
        webView.findAllAsync(searchQuery)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
