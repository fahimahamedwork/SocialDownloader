package com.socialdownloader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.socialdownloader.data.model.*
import com.socialdownloader.data.repository.DownloadRepository
import com.socialdownloader.service.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager
) : ViewModel() {

    // ─── State Flows ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _pastedUrl = MutableStateFlow("")
    val pastedUrl: StateFlow<String> = _pastedUrl.asStateFlow()

    val activeDownloadCount: StateFlow<Int> = repository.getActiveDownloadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentDownloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── URL Input ────────────────────────────────────────────────────────────

    fun onUrlChanged(url: String) {
        _pastedUrl.value = url
        if (_uiState.value is HomeUiState.Error) {
            _uiState.value = HomeUiState.Idle
        }
    }

    fun clearUrl() {
        _pastedUrl.value = ""
        _uiState.value = HomeUiState.Idle
    }

    // ─── Analyze URL ──────────────────────────────────────────────────────────

    fun analyzeUrl(url: String = _pastedUrl.value) {
        if (url.isBlank()) {
            _uiState.value = HomeUiState.Error("Please enter a URL")
            return
        }

        if (!repository.isValidUrl(url)) {
            _uiState.value = HomeUiState.Error("Invalid URL format")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading("Analyzing URL...")

            repository.extractVideoInfo(url)
                .onSuccess { videoInfo ->
                    _uiState.value = HomeUiState.VideoInfoLoaded(videoInfo)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to analyze URL: $url")
                    _uiState.value = HomeUiState.Error(
                        when {
                            error.message?.contains("timeout", true) == true ->
                                "Connection timed out. Check your internet connection."
                            error.message?.contains("connect", true) == true ->
                                "Could not connect. Check your internet connection."
                            error.message?.contains("not found", true) == true ->
                                "Video not found or may be private."
                            else -> error.message ?: "Failed to load video information"
                        }
                    )
                }
        }
    }

    // ─── Start Download ───────────────────────────────────────────────────────

    fun startDownload(videoInfo: VideoInfo, format: VideoFormat) {
        viewModelScope.launch {
            try {
                val sanitizedTitle = videoInfo.title
                    .replace(Regex("[^a-zA-Z0-9\\s._-]"), "")
                    .trim()
                    .take(80)
                    .ifEmpty { "video_${System.currentTimeMillis()}" }

                val extension = if (format.isAudioOnly) "mp3" else format.extension.ifEmpty { "mp4" }
                val fileName = "${sanitizedTitle}_${format.quality}.$extension"

                val workerId = UUID.randomUUID().toString()

                // Create DB record
                val downloadItem = DownloadItem(
                    videoId = videoInfo.id,
                    title = videoInfo.title,
                    thumbnailUrl = videoInfo.thumbnailUrl,
                    originalUrl = videoInfo.originalUrl,
                    platform = videoInfo.platform,
                    fileSize = format.fileSize,
                    fileName = fileName,
                    quality = format.quality,
                    format = extension,
                    status = DownloadStatus.PENDING,
                    workerId = workerId,
                    isAudioOnly = format.isAudioOnly
                )

                val downloadId = repository.insertDownload(downloadItem)

                // Enqueue WorkManager task
                val inputData = workDataOf(
                    DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    DownloadWorker.KEY_DOWNLOAD_URL to format.url,
                    DownloadWorker.KEY_FILE_NAME to fileName,
                    DownloadWorker.KEY_IS_AUDIO to format.isAudioOnly
                )

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setId(UUID.fromString(workerId))
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag(TAG_DOWNLOAD)
                    .addTag("download_$downloadId")
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                workManager.enqueue(downloadRequest)

                _uiState.value = HomeUiState.DownloadStarted(downloadItem.copy(id = downloadId))
                Timber.d("Download queued: $fileName (id: $downloadId)")

            } catch (e: Exception) {
                Timber.e(e, "Failed to start download")
                _uiState.value = HomeUiState.Error("Failed to start download: ${e.message}")
            }
        }
    }

    // ─── Cancel Download ──────────────────────────────────────────────────────

    fun cancelDownload(downloadItem: DownloadItem) {
        viewModelScope.launch {
            if (downloadItem.workerId.isNotEmpty()) {
                workManager.cancelWorkById(UUID.fromString(downloadItem.workerId))
            }
            repository.cancelDownload(downloadItem.id)
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
        _pastedUrl.value = ""
    }

    companion object {
        const val TAG_DOWNLOAD = "social_download"
    }
}

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class HomeUiState {
    object Idle : HomeUiState()
    data class Loading(val message: String) : HomeUiState()
    data class VideoInfoLoaded(val videoInfo: VideoInfo) : HomeUiState()
    data class DownloadStarted(val downloadItem: DownloadItem) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
