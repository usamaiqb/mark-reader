package com.markreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.markreader.data.AppThemeModePreference
import com.markreader.data.PreferencesRepository
import com.markreader.data.UserPreferences
import com.markreader.ui.MarkReaderApp
import com.markreader.ui.theme.AppTheme
import com.markreader.ui.theme.MarkReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<String?>(null)
    private var launchedExternally by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val preferences by PreferencesRepository
                .getInstance(applicationContext)
                .preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            MarkReaderTheme(theme = preferences.appThemeMode.toAppTheme()) {
                MarkReaderApp(
                    externalUri = externalUri,
                    launchedExternally = launchedExternally
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val data: Uri? = resolveIncomingUri(intent)
        if (data == null) return

        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val flags = intent.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    contentResolver.takePersistableUriPermission(
                        data,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ex: SecurityException) {
                    // Ignore: the provider may not allow persistable grants.
                } catch (ex: IllegalArgumentException) {
                    // Ignore: not a persistable URI.
                }
            }
            val uriString = data.toString()
            externalUri = uriString
            launchedExternally = true

            // Eagerly read the file on a background thread while the Activity holds
            // the intent's URI grant. Downloads-provider URIs (and similar restricted
            // providers) reject reads from ViewModelScope but allow them here because
            // the Activity is the direct recipient of the FLAG_GRANT_READ_URI_PERMISSION
            // grant. The result (or null on failure) is published to ExternalFileCache
            // so ViewerViewModel can consume it instead of re-opening the URI.
            ExternalFileCache.register(uriString)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val content = contentResolver.openInputStream(data)
                        ?.use { it.bufferedReader().readText() }
                    ExternalFileCache.complete(uriString, content)
                } catch (_: Exception) {
                    ExternalFileCache.complete(uriString, null)
                }
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
        val extra = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return extra
    }
}

private fun AppThemeModePreference.toAppTheme(): AppTheme = when (this) {
    AppThemeModePreference.System -> AppTheme.System
    AppThemeModePreference.Light -> AppTheme.Light
    AppThemeModePreference.Dark -> AppTheme.Dark
}
