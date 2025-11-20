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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val size = file.length()

    val cachedMetadata = metadataCache.getCachedMetadata(file, dateModified, size)
    val duration = cachedMetadata?.durationMs ?: 0L
    val width = cachedMetadata?.width ?: 0
    val height = cachedMetadata?.height ?: 0
    val fps = cachedMetadata?.fps ?: 0f

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
      resolution = if (width > 0 && height > 0) formatResolutionWithFps(width, height, fps) else "--",
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
    val width = cursor.getInt(indices.width)
    val height = cursor.getInt(indices.height)

    val normalizedPath = if (path.isNotEmpty()) normalizePath(path) else path

    if (normalizedPath.isEmpty() || !File(normalizedPath).exists()) {
      Log.w(TAG, "Skipping non-existent file: $normalizedPath")
      return null
    }

    val (finalBucketId, finalBucketDisplayName) = getFinalBucketInfo(videoBucketId, bucketDisplayName, normalizedPath)

    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

    val file = File(normalizedPath)
    val cachedMetadata = metadataCache.getCachedMetadata(file, dateModified, size)

    val finalWidth: Int
    val finalHeight: Int
    val fps: Float

    if (cachedMetadata != null) {
      finalWidth = if (width > 0) width else cachedMetadata.width
      finalHeight = if (height > 0) height else cachedMetadata.height
      fps = cachedMetadata.fps
    } else {
      finalWidth = if (width > 0) width else 0
      finalHeight = if (height > 0) height else 0
      fps = 0f
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
      width = finalWidth,
      height = finalHeight,
      fps = fps,
      resolution = if (finalWidth > 0 && finalHeight > 0) formatResolutionWithFps(finalWidth, finalHeight, fps) else "--",
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

  private fun formatResolution(width: Int, height: Int, fps: Float): String {
    if (width <= 0 || height <= 0) return "--"

    return when {
      width >= 7680 || height >= 4320 -> "8K"
      width >= 3840 || height >= 2160 -> "4K"
      width >= 2560 || height >= 1440 -> "1440p"
      width >= 1920 || height >= 1080 -> "1080p"
      width >= 1280 || height >= 720 -> "720p"
      width >= 854 || height >= 480 -> "480p"
      width >= 640 || height >= 360 -> "360p"
      width >= 426 || height >= 240 -> "240p"
      width >= 256 || height >= 144 -> "144p"
      else -> "${height}p"
    }
  }

  private fun formatResolutionWithFps(width: Int, height: Int, fps: Float): String {
    val baseResolution = formatResolution(width, height, fps)
    if (baseResolution == "--" || fps <= 0f) return baseResolution

    val fpsFormatted = if (fps % 1.0f == 0f) {
      fps.toInt().toString()
    } else {
      String.format(Locale.getDefault(), "%.3f", fps).trimEnd('0').trimEnd('.')
    }

    return "$baseResolution@$fpsFormatted"
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

  suspend fun enrichVideoMetadata(
    context: Context,
    video: Video,
  ): Video =
    withContext(Dispatchers.IO) {
      if (video.fps > 0f && video.width > 0 && video.height > 0 && video.duration > 0) {
        return@withContext video
      }

      val file = File(video.path)
      var duration = video.duration
      var width = video.width
      var height = video.height
      var fps = video.fps

      metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)?.let { metadata ->
        if (duration <= 0) duration = metadata.durationMs
        if (width <= 0 || height <= 0) {
          width = metadata.width
          height = metadata.height
        }
        fps = metadata.fps
      }

      video.copy(
        duration = duration,
        durationFormatted = formatDuration(duration),
        width = width,
        height = height,
        fps = fps,
        resolution = formatResolutionWithFps(width, height, fps),
      )
    }

  suspend fun enrichVideosMetadata(
    context: Context,
    videos: List<Video>,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val videosToEnrich = videos.filter { it.fps <= 0f || it.width <= 0 || it.height <= 0 }
      if (videosToEnrich.isEmpty()) return@withContext videos

      val enrichedMap = mutableMapOf<Long, Video>()

      videosToEnrich.chunked(6).forEach { batch ->
        coroutineScope {
          val results = batch.map { video ->
            async(Dispatchers.IO) {
              video.id to enrichVideoMetadata(context, video)
            }
          }.map { it.await() }

          enrichedMap.putAll(results)
        }
      }

      videos.map { video ->
        enrichedMap[video.id] ?: video
      }
    }

}
