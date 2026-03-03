package com.socialdownloader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed - checking for pending downloads")
            // WorkManager automatically resumes constrained work after reboot
            // No extra action needed as WorkManager handles persistence
        }
    }
}
