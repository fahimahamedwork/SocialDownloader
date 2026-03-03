package com.socialdownloader.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// ─── Platform Enum ───────────────────────────────────────────────────────────

enum class Platform(
    val displayName: String,
    val iconRes: String,
    val color: Int,
    val domains: List<String>
) {
    YOUTUBE(
        displayName = "YouTube",
        iconRes = "ic_youtube",
        color = 0xFFFF0000.toInt(),
        domains = listOf("youtube.com", "youtu.be", "m.youtube.com")
    ),
    INSTAGRAM(
        displayName = "Instagram",
        iconRes = "ic_instagram",
        color = 0xFFE1306C.toInt(),
        domains = listOf("instagram.com", "www.instagram.com")
    ),
    TIKTOK(
        displayName = "TikTok",
        iconRes = "ic_tiktok",
        color = 0xFF010101.toInt(),
        domains = listOf("tiktok.com", "www.tiktok.com", "vm.tiktok.com")
    ),
    FACEBOOK(
        displayName = "Facebook",
        iconRes = "ic_facebook",
        color = 0xFF1877F2.toInt(),
        domains = listOf("facebook.com", "www.facebook.com", "fb.watch", "m.facebook.com")
    ),
    TWITTER(
        displayName = "Twitter / X",
        iconRes = "ic_twitter",
        color = 0xFF1DA1F2.toInt(),
        domains = listOf("twitter.com", "x.com", "t.co")
    ),
    SNAPCHAT(
        displayName = "Snapchat",
        iconRes = "ic_snapchat",
        color = 0xFFFFFC00.toInt(),
        domains = listOf("snapchat.com", "www.snapchat.com")
    ),
    VIMEO(
        displayName = "Vimeo",
        iconRes = "ic_vimeo",
        color = 0xFF1AB7EA.toInt(),
        domains = listOf("vimeo.com", "player.vimeo.com")
    ),
    DAILYMOTION(
        displayName = "Dailymotion",
        iconRes = "ic_dailymotion",
        color = 0xFF0066DC.toInt(),
        domains = listOf("dailymotion.com", "www.dailymotion.com", "dai.ly")
    ),
    REDDIT(
        displayName = "Reddit",
        iconRes = "ic_reddit",
        color = 0xFFFF4500.toInt(),
        domains = listOf("reddit.com", "www.reddit.com", "redd.it", "v.redd.it")
    ),
    LINKEDIN(
        displayName = "LinkedIn",
        iconRes = "ic_linkedin",
        color = 0xFF0A66C2.toInt(),
        domains = listOf("linkedin.com", "www.linkedin.com")
    ),
    PINTEREST(
        displayName = "Pinterest",
        iconRes = "ic_pinterest",
        color = 0xFFE60023.toInt(),
        domains = listOf("pinterest.com", "pin.it")
    ),
    UNKNOWN(
        displayName = "Other",
        iconRes = "ic_web",
        color = 0xFF607D8B.toInt(),
        domains = emptyList()
    );

    companion object {
        fun fromUrl(url: String): Platform {
            val lowerUrl = url.lowercase()
            return values().firstOrNull { platform ->
                platform.domains.any { domain -> lowerUrl.contains(domain) }
            } ?: UNKNOWN
        }
    }
}

// ─── Download Status ──────────────────────────────────────────────────────────

enum class DownloadStatus {
    PENDING,
    FETCHING_INFO,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

// ─── Video Quality ────────────────────────────────────────────────────────────

enum class VideoQuality(val label: String, val resolution: String) {
    BEST("Best Quality", "auto"),
    FHD("Full HD", "1080p"),
    HD("HD", "720p"),
    SD("SD", "480p"),
    LOW("Low", "360p"),
    AUDIO_ONLY("Audio Only", "mp3")
}

// ─── Video Format ─────────────────────────────────────────────────────────────

@Parcelize
data class VideoFormat(
    val formatId: String,
    val quality: String,
    val resolution: String,
    val fileSize: Long,
    val extension: String,
    val url: String,
    val isAudioOnly: Boolean = false,
    val fps: Int = 0,
    val vcodec: String = "",
    val acodec: String = ""
) : Parcelable {
    val fileSizeFormatted: String
        get() = when {
            fileSize <= 0 -> "Unknown size"
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            fileSize < 1024 * 1024 * 1024 -> "${fileSize / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", fileSize.toFloat() / (1024 * 1024 * 1024))
        }
}

// ─── Video Info ───────────────────────────────────────────────────────────────

@Parcelize
data class VideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val duration: Long, // seconds
    val platform: Platform,
    val originalUrl: String,
    val uploader: String,
    val uploaderAvatarUrl: String,
    val viewCount: Long,
    val likeCount: Long,
    val formats: List<VideoFormat>,
    val uploadDate: String
) : Parcelable {
    val durationFormatted: String
        get() {
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val viewCountFormatted: String
        get() = when {
            viewCount < 1000 -> "$viewCount"
            viewCount < 1_000_000 -> "${viewCount / 1000}K"
            viewCount < 1_000_000_000 -> "${viewCount / 1_000_000}M"
            else -> "${viewCount / 1_000_000_000}B"
        }
}

// ─── Download Item (Room Entity) ──────────────────────────────────────────────

@Entity(tableName = "downloads")
@Parcelize
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val originalUrl: String,
    val platform: Platform,
    val fileSize: Long,
    val downloadedBytes: Long = 0,
    val filePath: String = "",
    val fileName: String,
    val quality: String,
    val format: String,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val workerId: String = "",
    val isAudioOnly: Boolean = false
) : Parcelable {
    val progress: Int
        get() = if (fileSize > 0) {
            ((downloadedBytes.toFloat() / fileSize) * 100).toInt()
        } else 0

    val fileSizeFormatted: String
        get() = when {
            fileSize <= 0 -> "Unknown"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            fileSize < 1024 * 1024 * 1024 -> "${fileSize / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", fileSize.toFloat() / (1024 * 1024 * 1024))
        }

    val downloadedFormatted: String
        get() = when {
            downloadedBytes < 1024 * 1024 -> "${downloadedBytes / 1024} KB"
            else -> "${downloadedBytes / (1024 * 1024)} MB"
        }
}
