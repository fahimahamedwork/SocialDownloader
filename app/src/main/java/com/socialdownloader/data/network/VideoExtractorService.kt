package com.socialdownloader.data.network

import com.socialdownloader.data.model.Platform
import com.socialdownloader.data.model.VideoFormat
import com.socialdownloader.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VideoExtractorService
 *
 * Handles metadata fetching and video URL extraction for multiple platforms.
 * Uses cobalt.tools API for video extraction.
 */
@Singleton
class VideoExtractorService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    
    companion object {
        private const val COBALT_API = "https://api.cobalt.tools/api/json"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
    }

    suspend fun extractVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val platform = Platform.fromUrl(url)
            Timber.d("Extracting video info for platform: $platform, url: $url")

            // First get metadata from the page
            val metadata = fetchPageMetadata(url, platform)
            
            // Then get download URL from cobalt API
            val downloadInfo = fetchFromCobalt(url)
            
            if (downloadInfo == null) {
                Result.failure(Exception("Could not extract video. The video may be private or unavailable."))
            } else {
                val info = VideoInfo(
                    id = metadata.id,
                    title = metadata.title,
                    description = "",
                    thumbnailUrl = metadata.thumbnail,
                    duration = metadata.duration,
                    platform = platform,
                    originalUrl = url,
                    uploader = metadata.author,
                    uploaderAvatarUrl = "",
                    viewCount = 0,
                    likeCount = 0,
                    formats = downloadInfo,
                    uploadDate = ""
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting video info")
            Result.failure(e)
        }
    }

    private data class PageMetadata(
        val id: String,
        val title: String,
        val thumbnail: String,
        val duration: Long,
        val author: String
    )

    private fun fetchPageMetadata(url: String, platform: Platform): PageMetadata {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(DESKTOP_USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .get()

            val title = doc.select("meta[property=og:title]").attr("content")
                .ifEmpty { doc.select("title").text() }
                .ifEmpty { "Video" }
            
            val thumbnail = doc.select("meta[property=og:image]").attr("content")
                .ifEmpty { "" }
            
            val author = when (platform) {
                Platform.YOUTUBE -> doc.select("link[itemprop=name]").attr("content")
                    .ifEmpty { doc.select("meta[name=author]").attr("content") }
                else -> doc.select("meta[name=author]").attr("content")
            }.ifEmpty { "Unknown" }

            val videoId = extractVideoId(url, platform) ?: System.currentTimeMillis().toString()
            
            PageMetadata(
                id = videoId,
                title = title,
                thumbnail = thumbnail,
                duration = 0,
                author = author
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch page metadata")
            PageMetadata(
                id = System.currentTimeMillis().toString(),
                title = "Video",
                thumbnail = "",
                duration = 0,
                author = "Unknown"
            )
        }
    }

    private fun extractVideoId(url: String, platform: Platform): String? {
        return when (platform) {
            Platform.YOUTUBE -> {
                val patterns = listOf(
                    Regex("v=([a-zA-Z0-9_-]{11})"),
                    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
                    Regex("embed/([a-zA-Z0-9_-]{11})"),
                    Regex("shorts/([a-zA-Z0-9_-]{11})")
                )
                patterns.forEach { pattern ->
                    pattern.find(url)?.groupValues?.get(1)?.let { return it }
                }
                null
            }
            Platform.TIKTOK -> Regex("/video/(\\d+)").find(url)?.groupValues?.get(1)
            Platform.INSTAGRAM -> Regex("/(p|reel|tv)/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(2)
            Platform.FACEBOOK -> Regex("videos/(\\d+)").find(url)?.groupValues?.get(1)
                ?: Regex("v=(\\d+)").find(url)?.groupValues?.get(1)
            Platform.TWITTER -> Regex("status/(\\d+)").find(url)?.groupValues?.get(1)
            Platform.VIMEO -> Regex("vimeo\\.com/(\\d+)").find(url)?.groupValues?.get(1)
            Platform.DAILYMOTION -> Regex("video/([a-z0-9]+)").find(url)?.groupValues?.get(1)
            Platform.REDDIT -> Regex("comments/([a-z0-9]+)").find(url)?.groupValues?.get(1)
            else -> null
        }
    }

    private fun fetchFromCobalt(url: String): List<VideoFormat>? {
        return try {
            val jsonBody = JSONObject().apply {
                put("url", url)
                put("vCodec", "h264")
                put("aCodec", "mp3")
                put("videoQuality", "1080")
                put("audioQuality", "320")
                put("filenameStyle", "classic")
                put("tiktokFullAudio", true)
                put("twitterGif", true)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(COBALT_API)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "SocialDownloader/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Timber.w("Cobalt API failed with code: ${response.code}")
                return null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Timber.w("Empty response from Cobalt API")
                return null
            }

            val json = JSONObject(responseBody)
            parseCobaltResponse(json)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch from Cobalt API")
            null
        }
    }

    private fun parseCobaltResponse(json: JSONObject): List<VideoFormat>? {
        val status = json.optString("status", "error")
        
        return when (status) {
            "redirect" -> {
                // Direct URL
                val url = json.optString("url", "")
                if (url.isNotEmpty()) {
                    val filename = json.optString("filename", "video")
                    val isAudio = filename.contains(".mp3") || url.contains("audio")
                    listOf(
                        VideoFormat(
                            formatId = "default",
                            quality = if (isAudio) "Audio" else "Best",
                            resolution = if (isAudio) "Audio Only" else "Best Quality",
                            fileSize = 0,
                            extension = if (isAudio) "mp3" else "mp4",
                            url = url,
                            isAudioOnly = isAudio
                        )
                    )
                } else null
            }
            "stream" -> {
                // Stream URL
                val url = json.optString("url", "")
                if (url.isNotEmpty()) {
                    listOf(
                        VideoFormat(
                            formatId = "stream",
                            quality = "Best",
                            resolution = "Best Quality",
                            fileSize = 0,
                            extension = "mp4",
                            url = url,
                            isAudioOnly = false
                        )
                    )
                } else null
            }
            "picker" -> {
                // Multiple formats available
                val picker = json.optJSONArray("picker") ?: return null
                val formats = mutableListOf<VideoFormat>()
                
                for (i in 0 until picker.length()) {
                    val item = picker.optJSONObject(i) ?: continue
                    val type = item.optString("type", "video")
                    val url = item.optString("url", "")
                    
                    if (url.isNotEmpty()) {
                        val isAudio = type == "audio"
                        formats.add(
                            VideoFormat(
                                formatId = "picker_$i",
                                quality = item.optString("quality", if (isAudio) "Audio" else "Video"),
                                resolution = if (isAudio) "Audio Only" else item.optString("quality", "Unknown"),
                                fileSize = 0,
                                extension = if (isAudio) "mp3" else "mp4",
                                url = url,
                                isAudioOnly = isAudio
                            )
                        )
                    }
                }
                
                if (formats.isEmpty()) null else formats
            }
            else -> {
                val error = json.optString("error", "Unknown error")
                Timber.w("Cobalt API error: $error")
                null
            }
        }
    }
}
