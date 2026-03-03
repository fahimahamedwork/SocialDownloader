# 📲 Social Downloader — Professional Android App

A production-grade Android application for downloading videos from all major social media platforms.

---

## 🏗️ Architecture

**Pattern:** MVVM + Clean Architecture  
**DI:** Hilt  
**Async:** Kotlin Coroutines + Flow  
**Background Work:** WorkManager  
**Database:** Room  
**Network:** OkHttp + Retrofit + Jsoup  
**Navigation:** Navigation Component  
**UI:** Material Design 3

```
app/
└── src/main/
    ├── java/com/socialdownloader/
    │   ├── SocialDownloaderApp.kt          ← Application class
    │   ├── data/
    │   │   ├── AppDatabase.kt              ← Room DB + DAO
    │   │   ├── model/Models.kt             ← Data models
    │   │   ├── network/VideoExtractorService.kt  ← Platform parsers
    │   │   └── repository/DownloadRepository.kt  ← Single source of truth
    │   ├── di/AppModule.kt                 ← Hilt modules
    │   ├── service/DownloadWorker.kt       ← WorkManager background downloader
    │   ├── receiver/BootReceiver.kt        ← Resume on boot
    │   ├── ui/
    │   │   ├── MainActivity.kt             ← Host activity
    │   │   ├── home/                       ← URL input + analysis
    │   │   ├── download/                   ← Active/completed downloads
    │   │   ├── library/                    ← Media library
    │   │   └── settings/                   ← App settings
    │   └── utils/Extensions.kt             ← Kotlin extensions
    └── res/
        ├── layout/                         ← XML layouts
        ├── navigation/nav_graph.xml        ← Navigation graph
        ├── menu/                           ← Menu resources
        ├── values/                         ← Colors, strings, themes
        └── xml/                            ← Config files
```

---

## ✅ Supported Platforms

| Platform | Type | Notes |
|----------|------|-------|
| YouTube | Video | All formats, audio extraction |
| Instagram | Reels/Posts/Stories | Login-free public content |
| TikTok | Videos | HD + watermark-free |
| Facebook | Videos/Reels | Public videos |
| Twitter / X | Video tweets | |
| Vimeo | HD Video | oEmbed metadata |
| Dailymotion | Video | Full API support |
| Reddit | v.redd.it videos | |
| Snapchat | Spotlights | |
| Pinterest | Video pins | |
| LinkedIn | Videos | |
| Generic | Any MP4 URLs | og:video detection |

---

## 🎯 Features

### Core
- ✅ Multi-platform video downloading
- ✅ Quality selection (360p → 1080p + Audio-only MP3)
- ✅ Background downloads via WorkManager (survives app close)
- ✅ Real-time progress tracking
- ✅ Pause, cancel, retry downloads
- ✅ Download queue management (concurrent downloads)
- ✅ Auto-resume after network reconnect

### User Experience
- ✅ Share-to-download from any app
- ✅ Clipboard URL auto-detection
- ✅ Material Design 3 UI
- ✅ Dark / Light / System theme
- ✅ Bottom navigation with download badge
- ✅ Video info preview before downloading
- ✅ Format selector with file size estimates

### Media Management
- ✅ Media library with grid view
- ✅ In-app video/audio playback
- ✅ Share downloaded files
- ✅ Auto-scan to system gallery
- ✅ Storage stats

### Technical
- ✅ Room database with full download history
- ✅ Download history search
- ✅ Platform filtering
- ✅ Proper file naming & organization
- ✅ FileProvider for secure file sharing
- ✅ Scoped storage (Android 10+) compatible
- ✅ Notification channels (progress, complete, error)
- ✅ ProGuard rules for release builds

---

## 🚀 Setup & Build

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34
- Min SDK 24 (Android 7.0)

### Steps
```bash
git clone https://github.com/your-repo/SocialDownloader.git
cd SocialDownloader
./gradlew assembleDebug
```

### Add Font Resources
Place these fonts in `app/src/main/res/font/`:
- `inter_regular.ttf`
- `inter_bold.ttf`

Download from: https://fonts.google.com/specimen/Inter

### Add Vector Drawables
Required drawables (can be created from Material Icons):
- `ic_home.xml`, `ic_download.xml`, `ic_library.xml`, `ic_settings.xml`
- `ic_search.xml`, `ic_paste.xml`, `ic_share.xml`, `ic_delete.xml`
- `ic_music.xml`, `ic_check_circle.xml`, `ic_error.xml`
- `ic_download_empty.xml`
- `placeholder_thumbnail.xml`
- `bg_bottom_sheet.xml`, `bg_drag_handle.xml`, `bg_badge.xml`

### Backend API
The app is architected to work with a backend URL extraction API at:
```
https://api.socialdownloader.app/
```
Replace this with your own yt-dlp server or RapidAPI endpoint.

**Recommended APIs:**
- [yt-dlp Python server](https://github.com/yt-dlp/yt-dlp) (self-hosted)
- [Social Media Video Downloader API](https://rapidapi.com) (RapidAPI)

---

## 📦 Dependencies

```gradle
// Core
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
material:1.10.0

// Architecture
lifecycle-viewmodel-ktx:2.6.2
navigation-fragment-ktx:2.7.5
room-runtime:2.6.0

// DI
hilt-android:2.48

// Network
retrofit:2.9.0
okhttp3:4.12.0
jsoup:1.16.2

// Background
work-runtime-ktx:2.8.1

// UI
glide:4.16.0
lottie:6.1.0
shimmer:0.5.0

// Preferences
datastore-preferences:1.0.0
```

---

## 📋 Permissions

| Permission | Purpose |
|-----------|---------|
| INTERNET | Fetching video data |
| READ/WRITE_EXTERNAL_STORAGE | Saving downloads (legacy) |
| READ_MEDIA_VIDEO/IMAGES | Reading media (Android 13+) |
| FOREGROUND_SERVICE | Background download service |
| POST_NOTIFICATIONS | Download progress notifications |
| RECEIVE_BOOT_COMPLETED | Resume downloads after reboot |

---

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

---

## ⚠️ Legal Notice

This application is for personal use only. Always respect the Terms of Service of social media platforms. Only download content you have the right to download. The developers are not responsible for misuse.

---

## 🛣️ Roadmap

- [ ] In-app browser for login-required content
- [ ] Batch URL downloads
- [ ] YouTube playlist support
- [ ] Download scheduler
- [ ] Cloud backup of download history
- [ ] Chromecast support
- [ ] Video trimming/conversion
- [ ] Advanced search filters in library
