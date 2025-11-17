package app.marlboroadvance.mpvex.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class VideoRepository(
  private val metadataCache: VideoMetadataCacheRepository,
) {
  companion object {
    private const val TAG = "VideoRepository"
    private const val SORT_ORDER = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"
  }

  @SuppressLint("SdCardPath")
  private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

  private val externalStoragePath: String by lazy { Environment.getExternalStorageDirectory().path }

  private val PROJECTION =
    arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.TITLE,
      MediaStore.Video.Media.DISPLAY_NAME,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.DATE_ADDED,
      MediaStore.Video.Media.MIME_TYPE,
      MediaStore.Video.Media.BUCKET_ID,
      MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.HEIGHT,
    )

  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val videos = mutableListOf<Video>()
      val processedPaths = mutableSetOf<String>()

      try {
        val isPath = bucketId.contains("/")

        if (isPath) {
          queryByPath(context, bucketId, videos, processedPaths)
        } else {
          queryByBucketId(context, bucketId, videos, processedPaths)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error querying videos for bucket $bucketId", e)
      }

      videos.sortedBy { it.displayName.lowercase() }
    }

  private suspend fun queryByBucketId(
    context: Context,
    bucketId: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    val volumes = StorageScanUtils.getAllStorageVolumes(context)

    for (volume in volumes) {
      val contentUri = StorageScanUtils.getContentUriForVolume(context, volume)

      context.contentResolver
        .query(
          contentUri,
          PROJECTION,
          "${MediaStore.Video.Media.BUCKET_ID} = ?",
          arrayOf(bucketId),
          SORT_ORDER,
        )?.use { cursor ->
          val columnIndices = getCursorColumnIndices(cursor)

          while (cursor.moveToNext()) {
            extractVideoFromCursor(context, cursor, columnIndices)?.let { video ->
              val normalizedPath = normalizePath(video.path)
              if (processedPaths.add(normalizedPath)) {
                videos.add(video)
              }
            }
          }
        }
    }
  }

  private suspend fun queryByPath(
    context: Context,
    folderPath: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    // Strategy: Try MediaStore first (fast), fallback to filesystem (reliable but slow)
    val volumes = StorageScanUtils.getAllStorageVolumes(context)

    for (volume in volumes) {
      try {
        val contentUri = StorageScanUtils.getContentUriForVolume(context, volume)

        context.contentResolver
          .query(
            contentUri,
            PROJECTION,
            null,
            null,
            SORT_ORDER,
          )?.use { cursor ->
            val columnIndices = getCursorColumnIndices(cursor)

            while (cursor.moveToNext()) {
              val videoPath = cursor.getString(columnIndices.data)
              if (videoPath != null) {
                val parentPath = File(videoPath).parent?.let { normalizePath(it) }

                if (parentPath == folderPath) {
                  extractVideoFromCursor(context, cursor, columnIndices)?.let { video ->
                    val normalizedPath = normalizePath(video.path)
                    if (processedPaths.add(normalizedPath)) {
                      videos.add(video)
                    }
                  }
                }
              }
            }
          }
      } catch (e: IllegalArgumentException) {
        // Volume not accessible via MediaStore - will try filesystem scan
      } catch (e: Exception) {
        Log.e(TAG, "Error querying volume ${volume.getDescription(context)}", e)
      }
    }

    // Fallback: Try direct filesystem scan if MediaStore found nothing
    // This is slower but catches unindexed files (like USB OTG, external SD cards)
    if (videos.isEmpty()) {
      scanDirectoryForVideos(context, folderPath, videos, processedPaths)
    }
  }

  private suspend fun scanDirectoryForVideos(
    context: Context,
    folderPath: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    try {
      val directory = File(folderPath)
      if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
        return
      }

      val videoFiles = directory.listFiles()?.filter {
        it.isFile && StorageScanUtils.isVideoFile(it)
      } ?: emptyList()

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
    // This provides accurate duration, size, and resolution information
    var size = file.length()
    var duration = 0L
    var width = 0
    var height = 0

    metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
      // Use MediaInfo's file size if available (more accurate for some formats)
      if (metadata.sizeBytes > 0) {
        size = metadata.sizeBytes
      }
      duration = metadata.durationMs
      width = metadata.width
      height = metadata.height
      Log.d(
        TAG,
        "Metadata for $displayName: size=$size bytes, duration=$duration ms, resolution=${width}x${height}",
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
      resolution = formatResolution(width, height),
    )
  }

  private fun getCursorColumnIndices(cursor: Cursor): VideoColumnIndices =
    VideoColumnIndices(
      id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID),
      title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE),
      displayName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME),
      data = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA),
      duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION),
      size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE),
      dateModified = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED),
      dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED),
      mimeType = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE),
      bucketId = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID),
      bucketDisplayName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME),
      width = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH),
      height = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT),
    )

  private suspend fun extractVideoFromCursor(
    context: Context,
    cursor: Cursor,
    indices: VideoColumnIndices,
  ): Video? {
    val id = cursor.getLong(indices.id)
    val title = cursor.getString(indices.title) ?: ""
    val displayName = cursor.getString(indices.displayName) ?: ""
    val path = cursor.getString(indices.data) ?: ""
    val duration = cursor.getLong(indices.duration)
    val size = cursor.getLong(indices.size)
    val dateModified = cursor.getLong(indices.dateModified)
    val dateAdded = cursor.getLong(indices.dateAdded)
    val mimeType = cursor.getString(indices.mimeType) ?: ""
    val videoBucketId = cursor.getString(indices.bucketId) ?: ""
    val bucketDisplayName = cursor.getString(indices.bucketDisplayName) ?: ""
    var width = cursor.getInt(indices.width)
    var height = cursor.getInt(indices.height)

    val normalizedPath = if (path.isNotEmpty()) normalizePath(path) else path

    if (normalizedPath.isEmpty() || !File(normalizedPath).exists()) {
      Log.w(TAG, "Skipping non-existent file: $normalizedPath")
      return null
    }

    val (finalBucketId, finalBucketDisplayName) = getFinalBucketInfo(videoBucketId, bucketDisplayName, normalizedPath)

    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

    // Fallback to MediaInfo if MediaStore doesn't have resolution (unsupported format)
    if (width <= 0 || height <= 0) {
      Log.d(TAG, "MediaStore has no resolution for $displayName, trying MediaInfo fallback")
      val file = File(normalizedPath)
      metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
        width = metadata.width
        height = metadata.height
        Log.d(TAG, "MediaInfo fallback succeeded for $displayName: ${width}x${height}")
      } ?: run {
        Log.w(TAG, "MediaInfo fallback failed for $displayName")
      }
    }

    return Video(
      id = id,
      title = title,
      displayName = displayName,
      path = normalizedPath,
      uri = uri,
      duration = duration,
      durationFormatted = formatDuration(duration),
      size = size,
      sizeFormatted = formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateAdded,
      mimeType = mimeType,
      bucketId = finalBucketId,
      bucketDisplayName = finalBucketDisplayName,
      width = width,
      height = height,
      resolution = formatResolution(width, height),
    )
  }

  private fun getFinalBucketInfo(
    bucketId: String,
    bucketDisplayName: String,
    path: String,
  ): Pair<String, String> {
    val parentPath = File(path).parent?.let { normalizePath(it) } ?: ""
    // Use MediaStore's BUCKET_ID if available, otherwise generate from path hash
    val finalBucketId = bucketId.ifEmpty { parentPath.hashCode().toString() }
    val finalBucketDisplayName =
      bucketDisplayName.ifEmpty { File(parentPath).name.takeIf { it.isNotEmpty() } ?: "Unknown Folder" }
    return Pair(finalBucketId, finalBucketDisplayName)
  }

  private data class VideoColumnIndices(
    val id: Int,
    val title: Int,
    val displayName: Int,
    val data: Int,
    val duration: Int,
    val size: Int,
    val dateModified: Int,
    val dateAdded: Int,
    val mimeType: Int,
    val bucketId: Int,
    val bucketDisplayName: Int,
    val width: Int,
    val height: Int,
  )

  private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
      hours > 0 -> "${hours}h ${minutes}m ${secs}s"
      minutes > 0 -> "${minutes}m ${secs}s"
      else -> "${secs}s"
    }
  }

  private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
  }

  private fun formatResolution(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "--"

    // For non-standard aspect ratios, use width to determine quality tier
    // This handles ultrawide/cinematic videos correctly (e.g., 1920x800, 1920x1036)
    val label = when {
      // Check width first for ultra-wide/cinematic content
      width >= 7680 || height >= 4320 -> "8K" // 7680×4320
      width >= 3840 || height >= 2160 -> "4K" // 3840×2160
      width >= 2560 || height >= 1440 -> "1440p" // 2560×1440 (2K/QHD)
      width >= 1920 || height >= 1080 -> "1080p" // 1920×1080 (Full HD) or ultrawide 1920x800
      width >= 1280 || height >= 720 -> "720p" // 1280×720 (HD)
      width >= 854 || height >= 480 -> "480p" // 854×480 or 720×480 (SD)
      width >= 640 || height >= 360 -> "360p" // 640×360
      width >= 426 || height >= 240 -> "240p" // 426×240
      width >= 256 || height >= 144 -> "144p" // 256×144
      else -> "${height}p" // For any other resolution, show height with 'p'
    }

    Log.d(TAG, "formatResolution: ${width}x${height} -> $label")
    return label
  }

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

}
