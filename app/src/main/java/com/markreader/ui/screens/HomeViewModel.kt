package com.markreader.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _launchPickerSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchPickerSignal: SharedFlow<Unit> = _launchPickerSignal.asSharedFlow()

    private val _navigateToViewer = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToViewer: SharedFlow<String> = _navigateToViewer.asSharedFlow()

    fun onOpenFileRequested() {
        _launchPickerSignal.tryEmit(Unit)
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _navigateToViewer.emit(uri.toString())
        }
    }
}
