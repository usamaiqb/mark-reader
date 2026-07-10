package com.markreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val DATASTORE_NAME = "markreader_recent_files"

private val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME
)

data class RecentFile(
    val uri: String,
    val displayName: String,
    val lastOpenedMillis: Long
)

class RecentFilesRepository private constructor(private val context: Context) {
    private object Keys {
        val RECENT_FILES = stringPreferencesKey("recent_files")
    }

    val recentFiles: Flow<List<RecentFile>> = context.recentFilesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> parse(prefs[Keys.RECENT_FILES]) }

    /**
     * Records a file open, de-duplicating by URI (most recent open wins) and capping
     * the list. Skipped when the app holds no persistable read grant for the URI
     * (e.g. transient ACTION_VIEW documents that we could not reopen later anyway).
     */
    suspend fun recordOpen(
        uriString: String,
        displayName: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (displayName.isBlank()) return
        if (!hasPersistedReadPermission(uriString)) return
        context.recentFilesDataStore.edit { prefs ->
            val current = parse(prefs[Keys.RECENT_FILES])
            val updated = listOf(RecentFile(uriString, displayName, timestamp)) +
                current.filterNot { it.uri == uriString }
            updated.drop(MAX_RECENT_FILES).forEach { releasePermission(it.uri) }
            prefs[Keys.RECENT_FILES] = serialize(updated.take(MAX_RECENT_FILES))
        }
    }

    suspend fun remove(uriString: String) {
        context.recentFilesDataStore.edit { prefs ->
            val current = parse(prefs[Keys.RECENT_FILES])
            val kept = current.filterNot { it.uri == uriString }
            if (kept.size != current.size) {
                releasePermission(uriString)
                prefs[Keys.RECENT_FILES] = serialize(kept)
            }
        }
    }

    private fun hasPersistedReadPermission(uriString: String): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.isReadPermission && it.uri.toString() == uriString
        }
    }

    private fun releasePermission(uriString: String) {
        val grant = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri.toString() == uriString } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
        } catch (ex: SecurityException) {
            // Grant already revoked by the provider.
        } catch (ex: IllegalArgumentException) {
            // Grant no longer held.
        }
    }

    private fun parse(json: String?): List<RecentFile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uri = obj.optString("uri")
                    val name = obj.optString("name")
                    if (uri.isNotBlank() && name.isNotBlank()) {
                        add(RecentFile(uri, name, obj.optLong("openedAt")))
                    }
                }
            }.sortedByDescending { it.lastOpenedMillis }
        }.getOrDefault(emptyList())
    }

    private fun serialize(files: List<RecentFile>): String {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("uri", file.uri)
                    .put("name", file.displayName)
                    .put("openedAt", file.lastOpenedMillis)
            )
        }
        return array.toString()
    }

    companion object {
        private const val MAX_RECENT_FILES = 15

        @Volatile
        private var instance: RecentFilesRepository? = null

        fun getInstance(context: Context): RecentFilesRepository {
            return instance ?: synchronized(this) {
                instance ?: RecentFilesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
