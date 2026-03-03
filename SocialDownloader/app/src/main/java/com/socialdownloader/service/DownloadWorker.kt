package com.socialdownloader.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.socialdownloader.R
import com.socialdownloader.SocialDownloaderApp
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.data.repository.DownloadRepository
import com.socialdownloader.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DownloadRepository,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1)
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "download.mp4"
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)

        if (downloadId == -1L) return@withContext Result.failure()

        val notificationId = downloadId.toInt() + NOTIFICATION_ID_BASE

        try {
            repository.updateProgress(downloadId, 0, DownloadStatus.DOWNLOADING)
            showProgressNotification(notificationId, fileName, 0)

            // Determine save directory
            val saveDir = if (isAudio) {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "SocialDownloader"
                )
            } else {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "SocialDownloader"
                )
            }
            saveDir.mkdirs()

            val outputFile = File(saveDir, fileName)

            // Execute download
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Android) SocialDownloader/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                repository.markFailed(downloadId, "Server error: ${response.code}")
                showFailedNotification(notificationId, fileName)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                repository.markFailed(downloadId, "Empty response body")
                showFailedNotification(notificationId, fileName)
                return@withContext Result.failure()
            }

            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            FileOutputStream(outputFile).use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    var lastProgressUpdate = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress every 2%
                        val progress = if (contentLength > 0) {
                            ((downloadedBytes.toFloat() / contentLength) * 100).toInt()
                        } else -1

                        if (progress - lastProgressUpdate >= 2 || progress == 100) {
                            lastProgressUpdate = progress
                            repository.updateProgress(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING)
                            showProgressNotification(notificationId, fileName, progress)

                            setProgress(
                                workDataOf(
                                    KEY_PROGRESS to progress,
                                    KEY_DOWNLOADED_BYTES to downloadedBytes,
                                    KEY_TOTAL_BYTES to contentLength
                                )
                            )
                        }

                        // Check if cancelled
                        if (isStopped) {
                            outputFile.delete()
                            repository.cancelDownload(downloadId)
                            return@withContext Result.failure()
                        }
                    }
                }
            }

            // Success!
            repository.markCompleted(downloadId, outputFile.absolutePath)
            showCompleteNotification(notificationId, fileName, outputFile)

            // Scan file to media library
            scanMediaFile(outputFile)

            Timber.d("Download completed: $fileName")
            Result.success(
                workDataOf(
                    KEY_FILE_PATH to outputFile.absolutePath,
                    KEY_DOWNLOAD_ID to downloadId
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Download failed for id: $downloadId")
            repository.markFailed(downloadId, e.message ?: "Unknown error")
            showFailedNotification(notificationId, fileName)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun showProgressNotification(notificationId: Int, fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, SocialDownloaderApp.CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(fileName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress < 0)
            .apply {
                if (progress >= 0) {
                    setSubText("$progress%")
                }
            }
            .build()

        setForegroundAsync(
            ForegroundInfo(notificationId, notification)
        )
    }

    private fun showCompleteNotification(notificationId: Int, fileName: String, file: File) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("tab", "library")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SocialDownloaderApp.CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId + 10000, notification)
    }

    private fun showFailedNotification(notificationId: Int, fileName: String) {
        val notification = NotificationCompat.Builder(context, SocialDownloaderApp.CHANNEL_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(fileName)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId + 20000, notification)
    }

    private fun scanMediaFile(file: File) {
        androidx.media.MediaScannerCompat.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_IS_AUDIO = "is_audio"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ERROR = "error"
        const val NOTIFICATION_ID_BASE = 1000
        private const val DEFAULT_BUFFER_SIZE = 8192
    }
}
