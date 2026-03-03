package com.socialdownloader.data.network

import com.socialdownloader.data.model.Platform
import com.socialdownloader.data.model.VideoFormat
import com.socialdownloader.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VideoExtractorService
 *
 * Handles metadata fetching and video URL extraction for multiple platforms.
 * Each platform has a dedicated extractor strategy.
 *
 * NOTE: In a production app, this would integrate with a backend API
 * (e.g., yt-dlp server) for robust extraction. This class demonstrates
 * the architecture with real HTTP parsing logic.
 */
@Singleton
class VideoExtractorService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun extractVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val platform = Platform.fromUrl(url)
            Timber.d("Extracting video info for platform: $platform, url: $url")

            when (platform) {
                Platform.YOUTUBE -> extractYoutube(url)
                Platform.INSTAGRAM -> extractInstagram(url)
                Platform.TIKTOK -> extractTikTok(url)
                Platform.FACEBOOK -> extractFacebook(url)
                Platform.TWITTER -> extractTwitter(url)
                Platform.VIMEO -> extractVimeo(url)
                Platform.DAILYMOTION -> extractDailymotion(url)
                Platform.REDDIT -> extractReddit(url)
                else -> extractGeneric(url, platform)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting video info")
            Result.failure(e)
        }
    }

    /**
     * Fetches the file size using a HEAD request to get Content-Length header.
     * Returns -1 if the size cannot be determined.
     */
    private suspend fun fetchFileSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", DESKTOP_USER_AGENT)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val contentLength = response.body?.contentLength() ?: -1
                Timber.d("File size for $url: $contentLength bytes")
                contentLength
            } else {
                Timber.w("Failed to fetch file size, HTTP ${response.code}")
                -1L
            }
        } catch (e: Exception) {
            Timber.w(e, "Error fetching file size for $url")
            -1L
        }
    }

    /**
     * Fetches file sizes for multiple format URLs in parallel.
     */
    private suspend fun fetchFileSizes(formats: List<VideoFormat>): List<VideoFormat> = withContext(Dispatchers.IO) {
        formats.map { format ->
            val size = fetchFileSize(format.url)
            format.copy(fileSize = size)
        }
    }

    // ─── YouTube ──────────────────────────────────────────────────────────────

    private suspend fun extractYoutube(url: String): Result<VideoInfo> {
        val videoId = extractYoutubeVideoId(url)
            ?: return Result.failure(Exception("Invalid YouTube URL"))

        // Use YouTube oEmbed for metadata (publicly available)
        val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
        val response = fetchJson(oembedUrl)
            ?: return Result.failure(Exception("Failed to fetch YouTube metadata"))

        // Build video formats (YouTube requires proper API or yt-dlp for actual stream URLs)
        val baseFormats = buildYoutubeFormats(videoId)
        val formats = fetchFileSizes(baseFormats)
        val thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

        val info = VideoInfo(
            id = videoId,
            title = response.optString("title", "YouTube Video"),
            description = "",
            thumbnailUrl = thumbnailUrl,
            duration = 0,
            platform = Platform.YOUTUBE,
            originalUrl = url,
            uploader = response.optString("author_name", "Unknown"),
            uploaderAvatarUrl = "",
            viewCount = 0,
            likeCount = 0,
            formats = formats,
            uploadDate = ""
        )
        return Result.success(info)
    }

    private fun extractYoutubeVideoId(url: String): String? {
        val patterns = listOf(
            Regex("v=([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("embed/([a-zA-Z0-9_-]{11})"),
            Regex("shorts/([a-zA-Z0-9_-]{11})")
        )
        patterns.forEach { pattern ->
            pattern.find(url)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    private fun buildYoutubeFormats(videoId: String): List<VideoFormat> {
        // These are the standard YouTube itag format identifiers
        return listOf(
            VideoFormat("137", "1080p", "1920x1080", 0, "mp4",
                "https://api.socialdownloader.app/yt/$videoId/1080"),
            VideoFormat("136", "720p", "1280x720", 0, "mp4",
                "https://api.socialdownloader.app/yt/$videoId/720"),
            VideoFormat("135", "480p", "854x480", 0, "mp4",
                "https://api.socialdownloader.app/yt/$videoId/480"),
            VideoFormat("134", "360p", "640x360", 0, "mp4",
                "https://api.socialdownloader.app/yt/$videoId/360"),
            VideoFormat("140", "Audio", "Audio Only", 0, "mp3",
                "https://api.socialdownloader.app/yt/$videoId/audio", isAudioOnly = true)
        )
    }

    // ─── TikTok ───────────────────────────────────────────────────────────────

    private suspend fun extractTikTok(url: String): Result<VideoInfo> {
        val resolvedUrl = resolveRedirects(url)
        val doc = try {
            Jsoup.connect(resolvedUrl)
                .userAgent(MOBILE_USER_AGENT)
                .timeout(15000)
                .get()
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to load TikTok page: ${e.message}"))
        }

        val title = doc.select("meta[property=og:title]").attr("content")
            .ifEmpty { doc.title() }
        val thumbnail = doc.select("meta[property=og:image]").attr("content")
        val author = doc.select("meta[name=author]").attr("content")

        val videoId = extractTikTokVideoId(resolvedUrl) ?: System.currentTimeMillis().toString()

        val baseFormats = listOf(
            VideoFormat("hd", "HD", "720p", 0, "mp4",
                "https://api.socialdownloader.app/tt/$videoId/hd"),
            VideoFormat("sd", "SD", "480p", 0, "mp4",
                "https://api.socialdownloader.app/tt/$videoId/sd"),
            VideoFormat("audio", "Audio", "Audio Only", 0, "mp3",
                "https://api.socialdownloader.app/tt/$videoId/audio", isAudioOnly = true)
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = videoId,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = 0,
                platform = Platform.TIKTOK,
                originalUrl = url,
                uploader = author,
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    private fun extractTikTokVideoId(url: String): String? {
        return Regex("/video/(\\d+)").find(url)?.groupValues?.get(1)
    }

    // ─── Instagram ────────────────────────────────────────────────────────────

    private suspend fun extractInstagram(url: String): Result<VideoInfo> {
        val oembedUrl = "https://api.instagram.com/oembed/?url=$url&omitscript=true"
        val json = fetchJson(oembedUrl)

        val mediaId = extractInstagramMediaId(url) ?: System.currentTimeMillis().toString()
        val title = json?.optString("title") ?: "Instagram Video"
        val thumbnail = json?.optString("thumbnail_url") ?: ""
        val author = json?.optString("author_name") ?: ""

        val baseFormats = listOf(
            VideoFormat("hd", "HD", "720p", 0, "mp4",
                "https://api.socialdownloader.app/ig/$mediaId/hd"),
            VideoFormat("sd", "SD", "480p", 0, "mp4",
                "https://api.socialdownloader.app/ig/$mediaId/sd")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = mediaId,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = 0,
                platform = Platform.INSTAGRAM,
                originalUrl = url,
                uploader = author,
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    private fun extractInstagramMediaId(url: String): String? {
        return Regex("/(p|reel|tv)/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(2)
    }

    // ─── Facebook ─────────────────────────────────────────────────────────────

    private suspend fun extractFacebook(url: String): Result<VideoInfo> {
        val oembedUrl = "https://www.facebook.com/plugins/video/oembed.json/?url=${
            java.net.URLEncoder.encode(url, "UTF-8")
        }"
        val json = fetchJson(oembedUrl)

        val videoId = extractFacebookVideoId(url) ?: System.currentTimeMillis().toString()
        val title = json?.optString("title") ?: "Facebook Video"
        val thumbnail = json?.optString("thumbnail_url") ?: ""
        val author = json?.optString("author_name") ?: ""

        val baseFormats = listOf(
            VideoFormat("hd", "HD", "720p", 0, "mp4",
                "https://api.socialdownloader.app/fb/$videoId/hd"),
            VideoFormat("sd", "SD", "480p", 0, "mp4",
                "https://api.socialdownloader.app/fb/$videoId/sd")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = videoId,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = 0,
                platform = Platform.FACEBOOK,
                originalUrl = url,
                uploader = author,
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    private fun extractFacebookVideoId(url: String): String? {
        return Regex("videos/(\\d+)").find(url)?.groupValues?.get(1)
            ?: Regex("v=(\\d+)").find(url)?.groupValues?.get(1)
    }

    // ─── Twitter / X ──────────────────────────────────────────────────────────

    private suspend fun extractTwitter(url: String): Result<VideoInfo> {
        val tweetId = Regex("status/(\\d+)").find(url)?.groupValues?.get(1)
            ?: return Result.failure(Exception("Invalid Twitter URL"))

        val baseFormats = listOf(
            VideoFormat("hd", "HD", "720p", 0, "mp4",
                "https://api.socialdownloader.app/tw/$tweetId/hd"),
            VideoFormat("sd", "SD", "360p", 0, "mp4",
                "https://api.socialdownloader.app/tw/$tweetId/sd")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = tweetId,
                title = "Twitter / X Video",
                description = "",
                thumbnailUrl = "",
                duration = 0,
                platform = Platform.TWITTER,
                originalUrl = url,
                uploader = "",
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    // ─── Vimeo ────────────────────────────────────────────────────────────────

    private suspend fun extractVimeo(url: String): Result<VideoInfo> {
        val videoId = Regex("vimeo\\.com/(\\d+)").find(url)?.groupValues?.get(1)
            ?: return Result.failure(Exception("Invalid Vimeo URL"))

        val oembedUrl = "https://vimeo.com/api/oembed.json?url=https://vimeo.com/$videoId"
        val json = fetchJson(oembedUrl)

        val title = json?.optString("title") ?: "Vimeo Video"
        val thumbnail = json?.optString("thumbnail_url") ?: ""
        val author = json?.optString("author_name") ?: ""
        val duration = json?.optInt("duration", 0)?.toLong() ?: 0

        val baseFormats = listOf(
            VideoFormat("1080p", "1080p", "1920x1080", 0, "mp4",
                "https://api.socialdownloader.app/vm/$videoId/1080"),
            VideoFormat("720p", "720p", "1280x720", 0, "mp4",
                "https://api.socialdownloader.app/vm/$videoId/720"),
            VideoFormat("360p", "360p", "640x360", 0, "mp4",
                "https://api.socialdownloader.app/vm/$videoId/360")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = videoId,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = duration,
                platform = Platform.VIMEO,
                originalUrl = url,
                uploader = author,
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    // ─── Dailymotion ──────────────────────────────────────────────────────────

    private suspend fun extractDailymotion(url: String): Result<VideoInfo> {
        val videoId = Regex("video/([a-z0-9]+)").find(url)?.groupValues?.get(1)
            ?: return Result.failure(Exception("Invalid Dailymotion URL"))

        val apiUrl = "https://api.dailymotion.com/video/$videoId?fields=title,thumbnail_url,duration,owner.screenname,views_total"
        val json = fetchJson(apiUrl)

        val title = json?.optString("title") ?: "Dailymotion Video"
        val thumbnail = json?.optString("thumbnail_url") ?: ""
        val duration = json?.optLong("duration") ?: 0
        val views = json?.optLong("views_total") ?: 0

        val baseFormats = listOf(
            VideoFormat("720p", "HD", "1280x720", 0, "mp4",
                "https://api.socialdownloader.app/dm/$videoId/720"),
            VideoFormat("480p", "SD", "854x480", 0, "mp4",
                "https://api.socialdownloader.app/dm/$videoId/480")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = videoId,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = duration,
                platform = Platform.DAILYMOTION,
                originalUrl = url,
                uploader = json?.optString("owner.screenname") ?: "",
                uploaderAvatarUrl = "",
                viewCount = views,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    // ─── Reddit ───────────────────────────────────────────────────────────────

    private suspend fun extractReddit(url: String): Result<VideoInfo> {
        val cleanUrl = url.trimEnd('/')
        val jsonUrl = "$cleanUrl.json"
        val json = fetchJson(jsonUrl) ?: return Result.failure(Exception("Failed to fetch Reddit data"))

        // Reddit JSON has nested structure
        val postId = Regex("comments/([a-z0-9]+)").find(url)?.groupValues?.get(1)
            ?: System.currentTimeMillis().toString()

        val baseFormats = listOf(
            VideoFormat("hd", "HD", "720p", 0, "mp4",
                "https://api.socialdownloader.app/rd/$postId/hd"),
            VideoFormat("sd", "SD", "480p", 0, "mp4",
                "https://api.socialdownloader.app/rd/$postId/sd")
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = postId,
                title = "Reddit Video",
                description = "",
                thumbnailUrl = "",
                duration = 0,
                platform = Platform.REDDIT,
                originalUrl = url,
                uploader = "",
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    // ─── Generic Extractor ────────────────────────────────────────────────────

    private suspend fun extractGeneric(url: String, platform: Platform): Result<VideoInfo> {
        val doc = try {
            Jsoup.connect(url)
                .userAgent(DESKTOP_USER_AGENT)
                .timeout(15000)
                .get()
        } catch (e: Exception) {
            return Result.failure(Exception("Cannot access the page: ${e.message}"))
        }

        val title = doc.select("meta[property=og:title]").attr("content")
            .ifEmpty { doc.title() }
        val thumbnail = doc.select("meta[property=og:image]").attr("content")
        val videoUrl = doc.select("meta[property=og:video:url], meta[property=og:video]")
            .attr("content")

        if (videoUrl.isEmpty()) {
            return Result.failure(Exception("No video found on this page"))
        }

        val baseFormats = listOf(
            VideoFormat("default", "Default", "auto", 0, "mp4", videoUrl)
        )
        val formats = fetchFileSizes(baseFormats)

        return Result.success(
            VideoInfo(
                id = System.currentTimeMillis().toString(),
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                duration = 0,
                platform = platform,
                originalUrl = url,
                uploader = "",
                uploaderAvatarUrl = "",
                viewCount = 0,
                likeCount = 0,
                formats = formats,
                uploadDate = ""
            )
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun fetchJson(url: String): org.json.JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", DESKTOP_USER_AGENT)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                // Handle both JSON array and object
                if (body.trimStart().startsWith("[")) {
                    org.json.JSONArray(body).optJSONObject(0)
                } else {
                    org.json.JSONObject(body)
                }
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch JSON from $url")
            null
        }
    }

    private fun resolveRedirects(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", MOBILE_USER_AGENT)
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.request.url.toString()
        } catch (e: Exception) {
            url
        }
    }

    // Extension to safely get optString from JSONObject
    private fun org.json.JSONObject.optString(key: String, fallback: String = ""): String =
        if (has(key) && !isNull(key)) getString(key) else fallback

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
    }
}
