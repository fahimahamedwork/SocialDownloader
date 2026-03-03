package com.socialdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SocialDownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Create notification channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Download progress channel
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active download progress"
                setShowBadge(false)
            }

            // Download complete channel
            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Download Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when downloads complete"
            }

            // Error channel
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR,
                "Download Errors",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when downloads fail"
            }

            notificationManager.createNotificationChannels(
                listOf(downloadChannel, completeChannel, errorChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD = "channel_download"
        const val CHANNEL_COMPLETE = "channel_complete"
        const val CHANNEL_ERROR = "channel_error"
    }
}
