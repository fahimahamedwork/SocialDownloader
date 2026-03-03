package com.socialdownloader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar

// ─── Network Utils ────────────────────────────────────────────────────────────

object NetworkUtils {
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

// ─── View Extensions ─────────────────────────────────────────────────────────

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.showSnackbar(
    message: String,
    actionText: String,
    duration: Int = Snackbar.LENGTH_LONG,
    action: () -> Unit
) {
    Snackbar.make(this, message, duration)
        .setAction(actionText) { action() }
        .show()
}

// ─── String Extensions ────────────────────────────────────────────────────────

fun String.isValidUrl(): Boolean {
    return try {
        val uri = android.net.Uri.parse(this)
        uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https") &&
                uri.host != null && uri.host!!.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

fun String.sanitizeFileName(): String {
    return replace(Regex("[^a-zA-Z0-9\\s._-]"), "")
        .trim()
        .replace(Regex("\\s+"), "_")
        .take(100)
}

// ─── File Size Formatting ─────────────────────────────────────────────────────

fun Long.toFormattedFileSize(): String = when {
    this <= 0 -> "Unknown"
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
    else -> String.format("%.2f GB", this.toFloat() / (1024 * 1024 * 1024))
}

// ─── Time Formatting ──────────────────────────────────────────────────────────

fun Long.toFormattedDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// ─── LiveData Extension ───────────────────────────────────────────────────────

fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    observe(lifecycleOwner, object : Observer<T> {
        override fun onChanged(t: T) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
}
