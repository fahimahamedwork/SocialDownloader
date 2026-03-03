package com.socialdownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.socialdownloader.R
import com.socialdownloader.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service for managing downloads.
 * This service is used to keep the download process running in the foreground
 * when needed, ensuring the system doesn't kill the download process.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "Downloads"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.socialdownloader.action.START_DOWNLOAD"
        const val ACTION_STOP = "com.socialdownloader.action.STOP_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun createIntent(context: Context, action: String, downloadId: String? = null): Intent {
            return Intent(context, DownloadService::class.java).apply {
                this.action = action
                downloadId?.let { putExtra(EXTRA_DOWNLOAD_ID, it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                startForeground(NOTIFICATION_ID, createNotification(downloadId))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(downloadId: String?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.downloading))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
    }
}
