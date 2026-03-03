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
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "download_${System.currentTimeMillis()}.mp4"
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)

        Timber.d("Starting download: id=$downloadId, url=$downloadUrl, fileName=$fileName")

        if (downloadId == -1L) {
            Timber.e("Invalid download ID")
            return@withContext Result.failure()
        }

        if (downloadUrl.isEmpty()) {
            Timber.e("Empty download URL")
            repository.markFailed(downloadId, "Invalid download URL")
            return@withContext Result.failure()
        }

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
            
            if (!saveDir.exists()) {
                val created = saveDir.mkdirs()
                Timber.d("Save directory created: $created, path: ${saveDir.absolutePath}")
            }

            val outputFile = File(saveDir, fileName)

            // Build request with proper headers
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Encoding", "identity") // Disable compression for accurate size
                .build()

            Timber.d("Executing download request...")
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = "Server error: ${response.code} ${response.message}"
                Timber.e(errorMsg)
                repository.markFailed(downloadId, errorMsg)
                showFailedNotification(notificationId, fileName)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                Timber.e("Empty response body")
                repository.markFailed(downloadId, "Empty response from server")
                showFailedNotification(notificationId, fileName)
                return@withContext Result.failure()
            }

            val contentLength = body.contentLength()
            Timber.d("Content length: $contentLength bytes")
            
            // Update file size in database
            if (contentLength > 0) {
                repository.updateFileSize(downloadId, contentLength)
            }
            
            var downloadedBytes = 0L
            var lastProgressUpdate = 0

            FileOutputStream(outputFile).use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress every 5% or every 1MB
                        val progress = if (contentLength > 0) {
                            ((downloadedBytes.toFloat() / contentLength) * 100).toInt()
                        } else {
                            // Show indeterminate progress
                            -1
                        }

                        val shouldUpdate = progress >= 0 && (progress - lastProgressUpdate >= 5 || progress == 100) ||
                                (progress < 0 && downloadedBytes - lastProgressUpdate * 1024 * 1024 / 5 >= 1024 * 1024)

                        if (shouldUpdate) {
                            lastProgressUpdate = if (progress >= 0) progress else (downloadedBytes / (1024 * 1024)).toInt()
                            repository.updateProgress(downloadId, downloadedBytes, DownloadStatus.DOWNLOADING)
                            showProgressNotification(notificationId, fileName, progress)

                            setProgressAsync(
                                workDataOf(
                                    KEY_PROGRESS to progress,
                                    KEY_DOWNLOADED_BYTES to downloadedBytes,
                                    KEY_TOTAL_BYTES to contentLength
                                )
                            )
                        }

                        // Check if cancelled
                        if (isStopped) {
                            Timber.d("Download cancelled by user")
                            outputStream.close()
                            outputFile.delete()
                            repository.cancelDownload(downloadId)
                            return@withContext Result.failure()
                        }
                    }
                }
            }

            // Verify file was written
            if (!outputFile.exists() || outputFile.length() == 0L) {
                Timber.e("File was not created or is empty")
                repository.markFailed(downloadId, "Download failed: File is empty")
                showFailedNotification(notificationId, fileName)
                return@withContext Result.failure()
            }

            // Success!
            Timber.d("Download completed: ${outputFile.absolutePath}, size: ${outputFile.length()}")
            repository.markCompleted(downloadId, outputFile.absolutePath)
            showCompleteNotification(notificationId, fileName, outputFile)

            // Scan file to media library
            scanMediaFile(outputFile)

            Result.success(
                workDataOf(
                    KEY_FILE_PATH to outputFile.absolutePath,
                    KEY_DOWNLOAD_ID to downloadId
                )
            )

        } catch (e: SocketTimeoutException) {
            Timber.e(e, "Download timeout")
            repository.markFailed(downloadId, "Connection timed out")
            showFailedNotification(notificationId, fileName)
            Result.retry()
        } catch (e: UnknownHostException) {
            Timber.e(e, "No internet connection")
            repository.markFailed(downloadId, "No internet connection")
            showFailedNotification(notificationId, fileName)
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            val errorMsg = e.message ?: "Unknown error occurred"
            repository.markFailed(downloadId, errorMsg.take(100))
            showFailedNotification(notificationId, fileName)
            Result.failure(workDataOf(KEY_ERROR to errorMsg))
        }
    }

    private fun showProgressNotification(notificationId: Int, fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, SocialDownloaderApp.CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(fileName.take(50))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (progress >= 0) progress else 0, progress < 0)
            .apply {
                if (progress >= 0) {
                    setSubText("$progress%")
                } else {
                    setSubText("Downloading...")
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
            .setContentText(fileName.take(50))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId + 10000, notification)
    }

    private fun showFailedNotification(notificationId: Int, fileName: String) {
        val notification = NotificationCompat.Builder(context, SocialDownloaderApp.CHANNEL_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(fileName.take(50))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId + 20000, notification)
    }

    private fun scanMediaFile(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan media file")
        }
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
