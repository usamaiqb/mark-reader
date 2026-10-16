package com.markreader.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * The Android half of the `openExternal` allowlist.
 *
 * The desktop app has checked these three schemes in `commands.rs` since its first commit; the
 * Android host had nothing, which is what this closes. The renderer checks `^(https?|mailto):`
 * in `document.ts` before it calls, and the sanitizer strips `javascript:` before that — so this
 * is the third check of the same thing, and deliberately so. **A link in an untrusted document
 * must not be what decides which Intent the platform resolves**, and this is the one place that
 * decision cannot be routed around: everything above it is inside the WebView, running over
 * content the app did not write.
 *
 * Kept to exactly the desktop's three schemes and its exact test. A host that quietly allowed
 * one more would be a difference between the two apps that nobody wrote down.
 */
private val ALLOWED_PREFIXES = listOf("http://", "https://", "mailto:")

private const val TAG = "MarkReaderWebView"

/**
 * Whether the app will hand [url] to the platform.
 *
 * Prefix matching on a lowercased string, mirroring `is_openable_external` in the desktop repo
 * rather than parsing — a document writes the scheme in whatever case it likes, and `Uri.parse`
 * is lenient in ways that differ between platforms.
 */
internal fun isOpenableExternal(url: String): Boolean {
    val lowered = url.lowercase()
    return ALLOWED_PREFIXES.any { lowered.startsWith(it) }
}

/**
 * Opens [url] outside the app if its scheme is allowed. Returns whether it was handed over.
 *
 * A URL that is not allowed is dropped silently, as it is on desktop. There is nothing useful to
 * tell the reader — the link is in the document, not in anything they did.
 */
internal fun openExternalUrl(context: Context, url: String): Boolean {
    if (!isOpenableExternal(url)) {
        Log.i(TAG, "refused a link outside the allowed schemes")
        return false
    }
    // NEW_TASK so the helper is safe with a non-Activity context; a browser is a separate task
    // anyway, and back still returns to the reader.
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (ex: ActivityNotFoundException) {
        // No browser, or no mail client. Not an error worth interrupting reading for.
        Log.i(TAG, "nothing installed to handle the link", ex)
        false
    }
}
