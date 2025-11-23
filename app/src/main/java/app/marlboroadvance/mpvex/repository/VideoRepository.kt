package app.marlboroadvance.mpvex.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * VideoRepository - Pure filesystem-based video discovery
 */
class VideoRepository(
  private val metadataCache: VideoMetadataCacheRepository,
) {
  companion object {
    private const val TAG = "VideoRepository"
  }

  @SuppressLint("SdCardPath")
  private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

  private val externalStoragePath: String by lazy { Environment.getExternalStorageDirectory().path }

  /**
   * Gets all videos in a specific folder
   * @param bucketId Can be either a path or a bucket ID (path hash)
   */
  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val videos = mutableListOf<Video>()
      val processedPaths = mutableSetOf<String>()

      try {
        // Determine if bucketId is a path or hash
        val folderPath = if (bucketId.contains("/")) {
          bucketId // It's already a path
        } else {
          // It's a bucket ID (hash) - need to find the folder
          // For now, treat as path. If needed, we can add path lookup by hash
          bucketId
        }

        // Scan the directory for video files
        scanDirectoryForVideos(context, folderPath, videos, processedPaths)
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for bucket $bucketId", e)
      }

      videos.sortedBy { it.displayName.lowercase() }
    }

  /**
   * Gets videos for multiple bucket IDs (folders)
   */
  suspend fun getVideosForBuckets(
    context: Context,
    bucketIds: Set<String>,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val result = mutableListOf<Video>()
      for (id in bucketIds) {
        runCatching { result += getVideosInFolder(context, id) }
      }
      result
    }

  /**
   * Scans a directory for video files and creates Video objects
   */
  private suspend fun scanDirectoryForVideos(
    context: Context,
    folderPath: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    try {
      val directory = File(folderPath)
      if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
        Log.w(TAG, "Cannot access directory: $folderPath")
        return
      }

      val videoFiles = directory.listFiles()?.filter {
        it.isFile && StorageScanUtils.isVideoFile(it)
      } ?: emptyList()

      Log.d(TAG, "Found ${videoFiles.size} videos in $folderPath")

      for (videoFile in videoFiles) {
        try {
          val normalizedPath = normalizePath(videoFile.absolutePath)
          if (processedPaths.add(normalizedPath) && videoFile.exists()) {
            val video = createVideoFromFile(context, videoFile, folderPath, directory.name)
            videos.add(video)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Error processing video file: ${videoFile.absolutePath}", e)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning directory: $folderPath", e)
    }
  }

  /**
   * Creates a Video object from a file with full metadata extraction
   */
  private suspend fun createVideoFromFile(
    context: Context,
    file: File,
    bucketId: String,
    bucketDisplayName: String,
  ): Video {
    val path = file.absolutePath
    val displayName = file.name
    val title = file.nameWithoutExtension
    val dateModified = file.lastModified() / 1000

    val extension = file.extension.lowercase()
    val mimeType = StorageScanUtils.getMimeTypeFromExtension(extension)
    val uri = Uri.fromFile(file)

    // Extract metadata using cache (with MediaInfo fallback)
    var size = file.length()
    var duration = 0L
    var width = 0
    var height = 0
    var fps = 0f

    metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
      // Use MediaInfo's file size if available (more accurate for some formats)
      if (metadata.sizeBytes > 0) {
        size = metadata.sizeBytes
      }
      duration = metadata.durationMs
      width = metadata.width
      height = metadata.height
      fps = metadata.fps
      Log.d(
        TAG,
        "Metadata for $displayName: size=$size bytes, duration=$duration ms, resolution=${width}x${height}@${fps}fps",
      )
    } ?: run {
      Log.w(TAG, "Failed to extract metadata for $displayName, using file system size")
    }

    return Video(
      id = path.hashCode().toLong(),
      title = title,
      displayName = displayName,
      path = path,
      uri = uri,
      duration = duration,
      durationFormatted = formatDuration(duration),
      size = size,
      sizeFormatted = formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateModified,
      mimeType = mimeType,
      bucketId = bucketId,
      bucketDisplayName = bucketDisplayName,
      width = width,
      height = height,
      fps = fps,
      resolution = formatResolutionWithFps(width, height, fps),
    )
  }

  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--"
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
      hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
      minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
      else -> "${secs}s"
    }
  }

  private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
  }

  private fun formatResolution(width: Int, height: Int, fps: Float): String {
    if (width <= 0 || height <= 0) return "--"

    // For non-standard aspect ratios, use width to determine quality tier
    // This handles ultrawide/cinematic videos correctly (e.g., 1920x800, 1920x1036)
    val label = when {
      // Check width first for ultra-wide/cinematic content
      width >= 7680 || height >= 4320 -> "4320p" // 7680×4320
      width >= 3840 || height >= 2160 -> "2160p" // 3840×2160
      width >= 2560 || height >= 1440 -> "1440p" // 2560×1440 (2K/QHD)
      width >= 1920 || height >= 1080 -> "1080p" // 1920×1080 (Full HD) or ultrawide 1920x800
      width >= 1280 || height >= 720 -> "720p" // 1280×720 (HD)
      width >= 854 || height >= 480 -> "480p" // 854×480 or 720×480 (SD)
      width >= 640 || height >= 360 -> "360p" // 640×360
      width >= 426 || height >= 240 -> "240p" // 426×240
      width >= 256 || height >= 144 -> "144p" // 256×144
      else -> "${height}p" // For any other resolution, show height with 'p'
    }

    return label
  }

  private fun formatResolutionWithFps(width: Int, height: Int, fps: Float): String {
    val baseResolution = formatResolution(width, height, fps)
    if (baseResolution == "--" || fps <= 0f) return baseResolution

    // Format fps: show up to 2 decimals, but remove trailing zeros
    val fpsFormatted = if (fps % 1.0f == 0f) {
      // Integer fps (e.g., 30.0 -> "30")
      fps.toInt().toString()
    } else {
      // Decimal fps (e.g., 23.976 -> "23.98", 29.97 -> "29.97")
      String.format(Locale.getDefault(), "%.2f", fps).trimEnd('0').trimEnd('.')
    }

    return "$baseResolution@$fpsFormatted"
  }
}
