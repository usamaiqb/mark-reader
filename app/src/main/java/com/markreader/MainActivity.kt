package com.markreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.UserPreferences
import com.markreader.file.DocumentSource
import com.markreader.file.ExternalFileCache
import com.markreader.file.tryTakePersistablePermission
import com.markreader.ui.MarkReaderApp
import com.markreader.ui.theme.AppTheme
import com.markreader.ui.theme.MarkReaderTheme
import com.markreader.webview.LocalReaderHostHolder
import com.markreader.webview.ReaderHostHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<String?>(null)
    private var externalUriNonce by mutableLongStateOf(0L)
    private var launchedExternally by mutableStateOf(false)

    /**
     * The reader's WebView, held here rather than rebuilt per document.
     *
     * Owned by the Activity because that is the widest scope a WebView can safely have — see
     * [ReaderHostHolder]. It is warmed after the first frame and released in [onDestroy]; a
     * configuration change builds a new one, with the new resources.
     */
    private val readerHost by lazy { ReaderHostHolder(this, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only handle the intent on a fresh launch. After a config change the
        // ViewerViewModel already holds the content; re-running handleIntent would
        // re-read the whole file into ExternalFileCache with nothing to consume it.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        // After the first frame, not before it. Loading the Chromium provider is main-thread work
        // and there is no reason for someone opening the app to their file list to wait for it.
        readerHost.warmUp()

        setContent {
            val preferences by PreferencesRepository
                .getInstance(applicationContext)
                .preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            MarkReaderTheme(
                theme = preferences.appThemeMode.toAppTheme(),
                dynamicColor = preferences.useDynamicColors
            ) {
                CompositionLocalProvider(LocalReaderHostHolder provides readerHost) {
                    MarkReaderApp(
                        externalUri = externalUri,
                        launchedExternally = launchedExternally,
                        externalUriNonce = externalUriNonce
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ExternalFileCache.clear()
        // The WebView outlives its Activity if nobody says otherwise.
        readerHost.destroy()
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val data: Uri? = resolveIncomingUri(intent)
        if (data == null) {
            if (action == Intent.ACTION_SEND) handleSharedText(intent)
            return
        }

        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                contentResolver.tryTakePersistablePermission(
                    data,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val uriString = data.toString()
            externalUri = uriString
            externalUriNonce++
            launchedExternally = true

            // Eagerly read the file on a background thread while the Activity holds
            // the intent's URI grant. Downloads-provider URIs (and similar restricted
            // providers) reject reads from ViewModelScope but allow them here because
            // the Activity is the direct recipient of the FLAG_GRANT_READ_URI_PERMISSION
            // grant. The result (or null on failure) is published to ExternalFileCache
            // so ViewerViewModel can consume it instead of re-opening the URI. The
            // deferred is guaranteed to complete — even if this scope is cancelled —
            // so consumers can await it without a timeout.
            val deferred = ExternalFileCache.register(uriString)
            val job = lifecycleScope.launch(Dispatchers.IO) {
                val bytes = try {
                    contentResolver.openInputStream(data)?.use { DocumentSource.readBounded(it) }
                } catch (_: Exception) {
                    null
                }
                deferred.complete(bytes)
            }
            job.invokeOnCompletion {
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }
    }

    /**
     * ACTION_SEND of plain text usually carries EXTRA_TEXT rather than a stream.
     * Persist it to a cache file so the viewer can treat it like any other document.
     */
    private fun handleSharedText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(cacheDir, "shared-text.md")
            file.writeText(text)
            val uriString = Uri.fromFile(file).toString()
            ExternalFileCache.register(uriString).complete(text.toByteArray())
            withContext(Dispatchers.Main) {
                externalUri = uriString
                externalUriNonce++
                launchedExternally = true
            }
        }
    }

    private fun resolveIncomingUri(intent: Intent): Uri? {
        intent.data?.let { return it }
        intent.clipData?.let { clip ->
            if (clip.itemCount > 0) {
                return clip.getItemAt(0).uri
            }
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

private fun AppThemeModePreference.toAppTheme(): AppTheme = when (this) {
    AppThemeModePreference.System -> AppTheme.System
    AppThemeModePreference.Light -> AppTheme.Light
    AppThemeModePreference.Dark -> AppTheme.Dark
}
