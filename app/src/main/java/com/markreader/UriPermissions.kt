package com.markreader

import android.content.ContentResolver
import android.net.Uri

/** Best-effort persistable grant; not all providers support them. */
fun ContentResolver.tryTakePersistablePermission(uri: Uri, flags: Int) {
    try {
        takePersistableUriPermission(uri, flags)
    } catch (ex: SecurityException) {
        // Provider does not allow persistable grants.
    } catch (ex: IllegalArgumentException) {
        // Not a persistable URI.
    }
}
