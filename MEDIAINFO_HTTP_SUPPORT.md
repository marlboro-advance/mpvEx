# Integration Guide: Adding MediaInfo HTTP Streaming Support to mpvEx

This document contains step-by-step instructions and reference code to integrate `mediainfo-android` into **mpvEx** with full support for remote **HTTP / HTTPS network streams** as well as local files and SAF content URIs.

---

## 1. Dependency Configuration

### Option A: Via Version Catalog (`gradle/libs.versions.toml`)
Add to `gradle/libs.versions.toml`:
```toml
[versions]
mediainfo = "1.0.2"

[libraries]
mediainfo-android = { module = "io.github.marlboro-advance:mediainfo-android", version.ref = "mediainfo" }
# Or via JitPack:
# mediainfo-android = { module = "com.github.marlboro-advance:mediainfoAndroid", version.ref = "mediainfo" }
```

In your app module `build.gradle.kts` (or `build.gradle`):
```kotlin
dependencies {
    implementation(libs.mediainfo.android)
}
```

### Option B: Direct Dependency
```groovy
// Groovy DSL
dependencies {
    implementation 'io.github.marlboro-advance:mediainfo-android:1.0.2'
}
```
Or Kotlin DSL:
```kotlin
dependencies {
    implementation("io.github.marlboro-advance:mediainfo-android:1.0.2")
}
```

Ensure `mavenCentral()` (or `maven { url = uri("https://jitpack.io") }`) is present in `dependencyResolutionManagement.repositories` inside `settings.gradle.kts`.

---

## 2. Key Architecture & Capabilities

`net.mediaarea.mediainfo.lib.MediaInfo` in `mediainfo-android` provides built-in seeking over HTTP via `HttpSeekableSource`:
- **Fast & Incremental:** Uses HTTP `Range: bytes=start-end` requests to inspect container headers, moov atoms, and index tables at the beginning/end of the file without downloading the entire media file.
- **Custom Headers Support:** Passes user-agents, authentication tokens, cookies, and referrers if the stream requires authorization.
- **Unified API:** Same `MediaInfo` class handles URLs, File Descriptors (Scoped Storage / SAF), and local files.

---

## 3. Required Implementation in mpvEx

### A. MediaInfo Helper / Repository

Create a helper utility (e.g., `MediaInfoHelper.kt`) to analyze media on a background dispatcher (`Dispatchers.IO`):

```kotlin
package com.example.mpvex.util // Adjust to your mpvEx package

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mediaarea.mediainfo.lib.MediaInfo

object MediaInfoHelper {

    /**
     * Inspects media from a URI (HTTP/HTTPS URL, file path, or Android content:// URI)
     * and returns the full formatted text report.
     */
    suspend fun getMediaInfoReport(
        context: Context,
        uri: Uri,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        MediaInfo().use { mi ->
            val scheme = uri.scheme?.lowercase()

            when {
                // 1. Remote HTTP/HTTPS streams
                scheme == "http" || scheme == "https" -> {
                    val url = uri.toString()
                    val filename = uri.lastPathSegment ?: "stream"
                    mi.Open(url = url, headers = headers, filename = filename)
                }

                // 2. Android Content Provider URIs (SAF / File Picker)
                scheme == "content" -> {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        mi.Open(pfd.fd, uri.lastPathSegment ?: "")
                    } ?: return@withContext "Unable to open file descriptor for URI"
                }

                // 3. Local file URIs or direct paths
                scheme == "file" || scheme == null -> {
                    val path = uri.path ?: uri.toString()
                    mi.Open(path)
                }

                else -> {
                    // Fallback to URL string if seekable source supports it
                    mi.Open(uri.toString())
                }
            }

            // Optional: mi.Option("Inform", "MIXML") for XML, or leave default for clean text
            mi.Inform()
        }
    }

    /**
     * Queries specific structured metadata parameters from the media stream.
     */
    suspend fun getMediaDetails(
        context: Context,
        uri: Uri,
        headers: Map<String, String> = emptyMap()
    ): MediaDetails = withContext(Dispatchers.IO) {
        MediaInfo().use { mi ->
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                mi.Open(url = uri.toString(), headers = headers)
            } else if (scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mi.Open(pfd.fd)
                }
            } else {
                mi.Open(uri.path ?: uri.toString())
            }

            MediaDetails(
                format = mi.Get(MediaInfo.Stream.General, 0, "Format"),
                durationMs = mi.Get(MediaInfo.Stream.General, 0, "Duration").toLongOrNull() ?: 0L,
                overallBitRate = mi.Get(MediaInfo.Stream.General, 0, "OverallBitRate"),
                videoCodec = mi.Get(MediaInfo.Stream.Video, 0, "Format"),
                videoProfile = mi.Get(MediaInfo.Stream.Video, 0, "Format_Profile"),
                videoWidth = mi.Get(MediaInfo.Stream.Video, 0, "Width").toIntOrNull() ?: 0,
                videoHeight = mi.Get(MediaInfo.Stream.Video, 0, "Height").toIntOrNull() ?: 0,
                frameRate = mi.Get(MediaInfo.Stream.Video, 0, "FrameRate"),
                hdrFormat = mi.Get(MediaInfo.Stream.Video, 0, "HDR_Format"),
                audioCodec = mi.Get(MediaInfo.Stream.Audio, 0, "Format"),
                audioChannels = mi.Get(MediaInfo.Stream.Audio, 0, "Channels"),
                audioBitRate = mi.Get(MediaInfo.Stream.Audio, 0, "BitRate"),
                audioTrackCount = mi.Count_Get(MediaInfo.Stream.Audio),
                subtitleTrackCount = mi.Count_Get(MediaInfo.Stream.Text)
            )
        }
    }
}

data class MediaDetails(
    val format: String,
    val durationMs: Long,
    val overallBitRate: String,
    val videoCodec: String,
    val videoProfile: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val frameRate: String,
    val hdrFormat: String,
    val audioCodec: String,
    val audioChannels: String,
    val audioBitRate: String,
    val audioTrackCount: Int,
    val subtitleTrackCount: Int
)
```

---

## 4. Hooking Into mpvEx UI (Dialog / Sheet / Track Info)

Where mpvEx displays media properties or track information (e.g., in a `MediaInfoSheet`, `PropertyDialog`, or `PlayerActivity` options menu):

```kotlin
lifecycleScope.launch {
    // Show loading spinner
    binding.progressBar.isVisible = true

    try {
        val currentUri = playerViewModel.currentUri ?: return@launch
        
        // Pass any HTTP headers configured in mpvEx (e.g. User-Agent or Referer)
        val headers = playerViewModel.networkHeaders ?: emptyMap()
        
        val report = MediaInfoHelper.getMediaInfoReport(context, currentUri, headers)
        
        // Display report in TextView / BottomSheetDialog
        binding.mediaInfoTextView.text = report
    } catch (e: Exception) {
        binding.mediaInfoTextView.text = "Failed to parse MediaInfo: ${e.localizedMessage}"
    } finally {
        binding.progressBar.isVisible = false
    }
}
```

---

## 5. Network Permissions in `AndroidManifest.xml`
Ensure standard internet permissions are present in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
If connecting to non-HTTPS local servers (HTTP LAN / NAS / Plex), ensure `android:usesCleartextTraffic="true"` is enabled or configured via network security config.

---

## 6. ProGuard / R8 Rules
If ProGuard or R8 minification is enabled in mpvEx, keep the native JNI methods and models:
```proguard
-keep class net.mediaarea.mediainfo.lib.** { *; }
-keepclassmembers class net.mediaarea.mediainfo.lib.** {
    native <methods>;
}
```
*(Note: `mediainfo-android` includes consumer rules automatically, but adding this ensures custom build types won't strip JNI symbols).*
