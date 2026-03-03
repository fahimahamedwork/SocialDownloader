package com.socialdownloader.data.repository

import com.socialdownloader.data.AppDatabase
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.data.model.Platform
import com.socialdownloader.data.model.VideoInfo
import com.socialdownloader.data.network.VideoExtractorService
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val database: AppDatabase,
    private val extractorService: VideoExtractorService
) {
    private val dao = database.downloadDao()

    // ─── Video Info Extraction ────────────────────────────────────────────────

    suspend fun extractVideoInfo(url: String): Result<VideoInfo> {
        return extractorService.extractVideoInfo(url)
    }

    // ─── Downloads ────────────────────────────────────────────────────────────

    fun getAllDownloads(): Flow<List<DownloadItem>> = dao.getAllDownloads()

    fun getActiveDownloads(): Flow<List<DownloadItem>> = dao.getActiveDownloads()

    fun getCompletedDownloads(): Flow<List<DownloadItem>> = dao.getCompletedDownloads()

    fun getDownloadsByPlatform(platform: Platform): Flow<List<DownloadItem>> =
        dao.getDownloadsByPlatform(platform)

    fun searchDownloads(query: String): Flow<List<DownloadItem>> = dao.searchDownloads(query)

    fun getActiveDownloadCount(): Flow<Int> = dao.getActiveDownloadCount()

    suspend fun getDownloadById(id: Long): DownloadItem? = dao.getDownloadById(id)

    suspend fun getDownloadByWorkerId(workerId: String): DownloadItem? =
        dao.getDownloadByWorkerId(workerId)

    suspend fun insertDownload(item: DownloadItem): Long = dao.insertDownload(item)

    suspend fun updateDownload(item: DownloadItem) = dao.updateDownload(item)

    suspend fun updateProgress(id: Long, bytes: Long, status: DownloadStatus) =
        dao.updateProgress(id, bytes, status)

    suspend fun markCompleted(id: Long, filePath: String) =
        dao.markCompleted(id, System.currentTimeMillis(), filePath)

    suspend fun markFailed(id: Long, error: String) =
        dao.updateStatus(id, DownloadStatus.FAILED, error)

    suspend fun cancelDownload(id: Long) =
        dao.updateStatus(id, DownloadStatus.CANCELLED)

    suspend fun deleteDownload(id: Long) = dao.deleteDownloadById(id)

    suspend fun clearCompleted() = dao.clearCompleted()

    suspend fun getTotalDownloadedBytes(): Long = dao.getTotalDownloadedBytes() ?: 0

    suspend fun getCompletedCount(): Int = dao.getCompletedCount()

    // ─── URL Validation ───────────────────────────────────────────────────────

    fun isValidUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https") &&
                    uri.host != null && uri.host!!.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun isSupportedPlatform(url: String): Boolean {
        return Platform.fromUrl(url) != Platform.UNKNOWN
    }
}
