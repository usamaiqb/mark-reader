package com.markreader.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _launchPickerSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchPickerSignal: SharedFlow<Unit> = _launchPickerSignal.asSharedFlow()

    private val _launchCreateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchCreateSignal: SharedFlow<Unit> = _launchCreateSignal.asSharedFlow()

    private val _navigateToViewer = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToViewer: SharedFlow<String> = _navigateToViewer.asSharedFlow()

    private val _navigateToEditor = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 1)
    val navigateToEditor: SharedFlow<Pair<String, Boolean>> = _navigateToEditor.asSharedFlow()

    fun onOpenFileRequested() {
        _launchPickerSignal.tryEmit(Unit)
    }

    fun onNewFileRequested() {
        _launchCreateSignal.tryEmit(Unit)
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _navigateToViewer.emit(uri.toString())
        }
    }

    fun onNewFileCreated(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val displayName = resolveDisplayName(uri)
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val isMarkdown = ext == "md" || ext == "markdown"
            _navigateToEditor.emit(Pair(uri.toString(), isMarkdown))
        }
    }

    private suspend fun resolveDisplayName(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val cursor = getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }
}
