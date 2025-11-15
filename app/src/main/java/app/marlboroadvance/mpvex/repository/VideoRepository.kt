package app.marlboroadvance.mpvex.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
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

  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      Log.d(TAG, "========================================")
      Log.d(TAG, "Starting to query videos for bucket: $bucketId")
      val videos = mutableListOf<Video>()
      val processedPaths = mutableSetOf<String>()

      try {
        // First, try MediaStore queries across all volumes
        queryAllStorageVolumes(context, bucketId, videos, processedPaths)
        Log.d(TAG, "MediaStore queries returned ${videos.size} videos")

        // Then, try direct filesystem scan using the bucket ID
        scanFileSystemForBucket(context, bucketId, videos, processedPaths)
        Log.d(TAG, "After filesystem scan: ${videos.size} total videos")
      } catch (e: Exception) {
        Log.e(TAG, "Error querying videos for bucket $bucketId", e)
      }

      Log.d(TAG, "Final result: Returning ${videos.size} videos (${processedPaths.size} unique paths)")
      Log.d(TAG, "========================================")
      videos.sortedBy { it.displayName.lowercase() }
    }

  private fun queryAllStorageVolumes(
    context: Context,
    bucketId: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    try {
      val volumes = StorageScanUtils.getAllStorageVolumes(context)

      for (volume in volumes) {
        val contentUri = StorageScanUtils.getContentUriForVolume(context, volume)
        Log.d(TAG, "Querying volume: ${volume.getDescription(context)} with URI: $contentUri")
        queryMediaStoreBucket(context, contentUri, bucketId, videos, processedPaths)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error querying storage volumes", e)
      // Final fallback
      queryMediaStoreBucket(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, bucketId, videos, processedPaths)
    }
  }

  private fun scanFileSystemForBucket(
    context: Context,
    bucketId: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    try {
      val externalVolumes = StorageScanUtils.getExternalStorageVolumes(context)

      for (volume in externalVolumes) {
        val volumePath = StorageScanUtils.getVolumePath(volume)
        if (volumePath != null) {
          Log.d(TAG, "Scanning ${volume.getDescription(context)} at $volumePath for bucket $bucketId")

          val targetDirectory = StorageScanUtils.findDirectoryByBucketId(File(volumePath), bucketId)
          if (targetDirectory != null) {
            Log.d(TAG, "Found matching directory: ${targetDirectory.absolutePath}")
            val videoFiles =
              targetDirectory.listFiles()?.filter {
                it.isFile && StorageScanUtils.isVideoFile(it)
              } ?: emptyList()

            Log.d(TAG, "Found ${videoFiles.size} video files in matching directory")

            for (videoFile in videoFiles) {
              try {
                val normalizedPath = normalizePath(videoFile.absolutePath)
                if (processedPaths.add(normalizedPath) && videoFile.exists()) {
                  val video = createVideoFromFile(videoFile, bucketId, targetDirectory.name)
                  videos.add(video)
                  Log.d(TAG, "Added video from filesystem: ${video.displayName}")
                }
              } catch (e: Exception) {
                Log.w(TAG, "Error processing video file: ${videoFile.absolutePath}", e)
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in filesystem scan for bucket", e)
    }
  }

  private fun createVideoFromFile(
    file: File,
    bucketId: String,
    bucketDisplayName: String,
  ): Video {
    val path = file.absolutePath
    val displayName = file.name
    val title = file.nameWithoutExtension
    val size = file.length()
    val dateModified = file.lastModified() / 1000

    // Extract metadata using utility
    val metadata = StorageScanUtils.extractVideoMetadata(file)

    // For filesystem-scanned videos, we create a file URI
    val uri = Uri.fromFile(file)

    return Video(
      id = path.hashCode().toLong(),
      title = title,
      displayName = displayName,
      path = path,
      uri = uri,
      duration = metadata.duration,
      durationFormatted = formatDuration(metadata.duration),
      size = size,
      sizeFormatted = formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateModified,
      mimeType = metadata.mimeType,
      bucketId = bucketId,
      bucketDisplayName = bucketDisplayName,
    )
  }

  private fun queryMediaStoreBucket(
    context: Context,
    contentUri: Uri,
    bucketId: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    try {
      context.contentResolver
        .query(
          contentUri,
          PROJECTION,
          "${MediaStore.Video.Media.BUCKET_ID} = ?",
          arrayOf(bucketId),
          SORT_ORDER,
        )?.use { cursor ->
          Log.d(TAG, "MediaStore bucket query result: ${cursor.count} videos found in $contentUri")
          processVideoCursor(cursor, videos, processedPaths)
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error querying MediaStore bucket from $contentUri", e)
    }
  }

  private fun processVideoCursor(
    cursor: Cursor,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    val columnIndices = getCursorColumnIndices(cursor)

    while (cursor.moveToNext()) {
      extractVideoFromCursor(cursor, columnIndices)?.let { video ->
        val normalizedPath = normalizePath(video.path)
        if (processedPaths.add(normalizedPath)) {
          videos.add(video)
          Log.d(TAG, "Added video: ${video.displayName} at ${video.path}")
        } else {
          Log.d(TAG, "Skipping duplicate video from MediaStore: ${video.displayName}")
        }
      }
    }
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
