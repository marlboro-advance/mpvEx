package app.marlboroadvance.mpvex.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object VideoRepository {
  private const val TAG = "VideoRepository"

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
    )

  private const val SORT_ORDER = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"

  @SuppressLint("SdCardPath")
  private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

  private val externalStoragePath: String by lazy { Environment.getExternalStorageDirectory().path }

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

  private fun queryByBucketId(
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
            extractVideoFromCursor(cursor, columnIndices)?.let { video ->
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
                  extractVideoFromCursor(cursor, columnIndices)?.let { video ->
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

    // Extract metadata using MediaInfo API for external storage videos
    // This provides accurate duration and size information
    var size = file.length()
    var duration = 0L

    MediaInfoOps.extractBasicMetadata(context, uri, displayName)
      .onSuccess { metadata ->
        // Use MediaInfo's file size if available (more accurate for some formats)
        if (metadata.sizeBytes > 0) {
          size = metadata.sizeBytes
        }
        duration = metadata.durationMs
        Log.d(TAG, "Extracted metadata for $displayName: size=$size bytes, duration=$duration ms")
      }
      .onFailure { error ->
        Log.w(TAG, "Failed to extract metadata for $displayName using MediaInfo, using file system size", error)
        // Fallback to file system size, duration remains 0
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
    )

  private fun extractVideoFromCursor(
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

    val normalizedPath = if (path.isNotEmpty()) normalizePath(path) else path

    if (normalizedPath.isEmpty() || !File(normalizedPath).exists()) {
      Log.w(TAG, "Skipping non-existent file: $normalizedPath")
      return null
    }

    val (finalBucketId, finalBucketDisplayName) = getFinalBucketInfo(videoBucketId, bucketDisplayName, normalizedPath)

    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

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
