package com.markreader

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges the eager file read performed in MainActivity (while the Activity holds
 * the intent's URI grant) with ViewerViewModel's async read.
 *
 * Flow:
 *  1. MainActivity.handleIntent() calls register(uri) then launches a background read.
 *  2. On completion it calls complete(uri, content) — null on failure.
 *  3. ViewerViewModel.readTextFromUri() calls consume(uri) and awaits the result.
 */
internal object ExternalFileCache {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    fun register(uri: String) {
        pending[uri] = CompletableDeferred()
    }

    fun complete(uri: String, content: String?) {
        pending[uri]?.complete(content)
    }

    /** Returns the deferred for this URI (removing it), or null if none was registered. */
    fun consume(uri: String): CompletableDeferred<String?>? = pending.remove(uri)
}
