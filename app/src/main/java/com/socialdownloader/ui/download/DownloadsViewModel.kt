package com.socialdownloader.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val activeDownloads: StateFlow<List<DownloadItem>> = repository.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> = repository.getCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun cancelDownload(item: DownloadItem) {
        viewModelScope.launch {
            try {
                if (item.workerId.isNotEmpty()) {
                    workManager.cancelWorkById(UUID.fromString(item.workerId))
                }
                repository.cancelDownload(item.id)
                _snackbarMessage.emit("Download cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel download")
            }
        }
    }

    fun retryDownload(item: DownloadItem) {
        viewModelScope.launch {
            try {
                // Reset status and re-enqueue
                repository.updateDownload(
                    item.copy(
                        status = DownloadStatus.PENDING,
                        errorMessage = "",
                        downloadedBytes = 0
                    )
                )
                _snackbarMessage.emit("Download restarted")
            } catch (e: Exception) {
                Timber.e(e, "Failed to retry download")
            }
        }
    }

    fun deleteDownload(item: DownloadItem, deleteFile: Boolean = false) {
        viewModelScope.launch {
            try {
                if (item.workerId.isNotEmpty()) {
                    workManager.cancelWorkById(UUID.fromString(item.workerId))
                }
                if (deleteFile && item.filePath.isNotEmpty()) {
                    java.io.File(item.filePath).delete()
                }
                repository.deleteDownload(item.id)
                _snackbarMessage.emit("Download deleted")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete download")
            }
        }
    }

    fun clearAllCompleted(deleteFiles: Boolean = false) {
        viewModelScope.launch {
            try {
                if (deleteFiles) {
                    completedDownloads.value.forEach { item ->
                        if (item.filePath.isNotEmpty()) {
                            java.io.File(item.filePath).delete()
                        }
                    }
                }
                repository.clearCompleted()
                _snackbarMessage.emit("Cleared all completed downloads")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear completed downloads")
            }
        }
    }
}
