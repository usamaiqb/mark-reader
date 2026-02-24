package com.markreader

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges the eager file read performed in MainActivity (while the Activity holds
 * the intent's URI grant) with ViewerViewModel's async read.
 *
 * Flow:
 *  1. MainActivity.handleIntent() calls register(uri), receives the CompletableDeferred,
 *     then launches a background read that completes the deferred directly.
 *  2. ViewerViewModel.readTextFromUri() calls consume(uri) and awaits the result.
 */
internal object ExternalFileCache {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    /** Registers a pre-read for [uri] and returns the [CompletableDeferred] to complete directly. */
    fun register(uri: String): CompletableDeferred<String?> {
        val deferred = CompletableDeferred<String?>()
        pending[uri] = deferred
        return deferred
    }

    /** Returns the deferred for this URI (removing it), or null if none was registered. */
    fun consume(uri: String): CompletableDeferred<String?>? = pending.remove(uri)

    /** Removes all pending entries. Call from Activity.onDestroy to avoid leaks. */
    fun clear() {
        pending.clear()
    }
}
