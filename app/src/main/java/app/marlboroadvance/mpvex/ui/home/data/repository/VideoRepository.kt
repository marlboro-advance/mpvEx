package app.marlboroadvance.mpvex.ui.home.data.repository

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.ui.home.data.model.Video
import app.marlboroadvance.mpvex.ui.utils.TVUtils
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object VideoRepository {

  private const val tag = "VideoRepository"

  private fun normalizePath(path: String): String {
    // Normalize paths to avoid duplicates
    return when {
      path.startsWith("/sdcard") -> path.replace("/sdcard", TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT)
      else -> {
        try {
          File(path).canonicalPath
        } catch (e: Exception) {
          path
        }
      }
    }
  }

  fun getVideosInFolder(context: Context, bucketId: String): List<Video> {
    Log.d(tag, "Starting to query videos for bucket: $bucketId")
    val videos = mutableListOf<Video>()
    val processedPaths = mutableSetOf<String>() // Track which video files we've already added
    val isTV = TVUtils.isAndroidTV(context)

    val projection = getProjection()
    val sortOrder = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"

    try {
      // First, try to query with the bucket ID directly (in case it's a MediaStore bucket ID)
      var cursor: Cursor? = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Video.Media.BUCKET_ID} = ?",
        arrayOf(bucketId),
        sortOrder,
      )

      Log.d(tag, "MediaStore bucket query result: ${cursor?.count ?: 0} videos found")

      cursor?.use { c ->
        processVideoCursor(c, videos, processedPaths)
      }

      // If no videos found, it might be our hash-based bucket ID
      // Query all videos and filter by matching folder path
      if (videos.isEmpty()) {
        Log.d(tag, "No videos with bucket ID, trying to match by folder path hash")
        cursor = context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          null,
          null,
          sortOrder,
        )

        cursor?.use { c ->
          processVideoCursorWithBucketFilter(c, bucketId, videos, processedPaths)
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error querying videos for bucket $bucketId", e)
    }

    // If still no videos found and we're on Android TV, try direct file scan
    if (videos.isEmpty() && isTV) {
      Log.d(tag, "No videos found via MediaStore on Android TV, trying direct scan")
      scanFolderDirectly(bucketId, videos, context, processedPaths)
    }

    Log.d(tag, "Returning ${videos.size} videos (${processedPaths.size} unique paths)")
    return videos
  }

  private fun processVideoCursorWithBucketFilter(
    cursor: Cursor,
    targetBucketId: String,
    videos: MutableList<Video>,
    processedPaths: MutableSet<String>,
  ) {
    val columnIndices = getCursorColumnIndices(cursor)

    while (cursor.moveToNext()) {
      extractVideoFromCursor(cursor, columnIndices)?.let { video ->
        // Check if this video's bucket ID matches our target
        if (video.bucketId == targetBucketId) {
          val normalizedPath = normalizePath(video.path)
          if (processedPaths.add(normalizedPath)) {
            videos.add(video)
            Log.d(tag, "Added video: ${video.displayName} at ${video.path}")
          } else {
            Log.d(tag, "Skipping duplicate video from MediaStore: ${video.displayName}")
          }
        }
      }
    }
  }

  private fun scanFolderDirectly(
    bucketId: String,
    videos: MutableList<Video>,
    context: Context,
    processedPaths: MutableSet<String>,
  ) {
    try {
      // Collect all potential folders to check
      val allFolders = mutableListOf<File>()

      // Scan internal storage
      scanInternalStorage(allFolders)

      // Scan external USB/SD mounts using /proc/mounts
      scanExternalMountsForVideos(allFolders)

      // Scan common USB mount locations as fallback
      scanCommonUsbLocations(allFolders)

      // Find the folder with matching bucketId
      for (folder in allFolders) {
        val normalizedPath = normalizePath(folder.absolutePath)
        val folderBucketId = normalizedPath.hashCode().toString()
        if (folderBucketId == bucketId) {
          Log.d(tag, "Found matching folder: ${folder.absolutePath}")
          scanDirectoryForVideos(folder, videos, context, processedPaths)
          break
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error scanning folder directly", e)
    }
  }

  private fun scanInternalStorage(allFolders: MutableList<File>) {
    try {
      val internalRoot = File(TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT)
      if (internalRoot.exists() && internalRoot.canRead()) {
        internalRoot.listFiles()?.forEach { file ->
          if (file.isDirectory && file.canRead()) {
            allFolders.add(file)
            // Check subdirectories too (up to 2 levels deep)
            file.listFiles()?.forEach { subFile ->
              if (subFile.isDirectory && subFile.canRead()) {
                allFolders.add(subFile)
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Error scanning internal storage", e)
    }
  }

  private fun scanExternalMountsForVideos(allFolders: MutableList<File>) {
    try {
      val mountsFile = File("/proc/mounts")
      if (mountsFile.exists() && mountsFile.canRead()) {
        mountsFile.bufferedReader().use { reader ->
          reader.lineSequence().forEach { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 3) {
              val device = parts[0]
              val mountPoint = parts[1]
              val fsType = parts[2]

              if (TVUtils.isExternalMount(device, mountPoint, fsType)) {
                val mountDir = File(mountPoint)
                if (mountDir.exists() && mountDir.isDirectory && mountDir.canRead()) {
                  Log.d(tag, "Found USB mount for video scan: $mountPoint")
                  addDirectoryAndSubdirectories(mountDir, allFolders, 2)
                }
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Error scanning /proc/mounts for videos", e)
    }
  }

  private fun scanCommonUsbLocations(allFolders: MutableList<File>) {
    TVUtils.FileSystemPaths.COMMON_USB_MOUNT_PATHS.forEach { basePath ->
      try {
        val dir = File(basePath)
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
          dir.listFiles()?.forEach { mountPoint ->
            if (mountPoint.isDirectory && mountPoint.canRead()) {
              addDirectoryAndSubdirectories(mountPoint, allFolders, 2)
            }
          }
        }
      } catch (e: Exception) {
        Log.w(tag, "Error scanning USB location: $basePath", e)
      }
    }
  }

  private fun addDirectoryAndSubdirectories(
    directory: File,
    allFolders: MutableList<File>,
    maxDepth: Int,
    currentDepth: Int = 0,
  ) {
    if (currentDepth > maxDepth) return

    try {
      if (directory.exists() && directory.isDirectory && directory.canRead()) {
        allFolders.add(directory)

        directory.listFiles()?.forEach { subDir ->
          if (subDir.isDirectory && subDir.canRead()) {
            addDirectoryAndSubdirectories(subDir, allFolders, maxDepth, currentDepth + 1)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Error adding directory: ${directory.absolutePath}", e)
    }
  }

  private fun scanDirectoryForVideos(
    directory: File,
    videos: MutableList<Video>,
    context: Context,
    processedPaths: MutableSet<String>,
  ) {
    try {
      val files = directory.listFiles() ?: return

      for (file in files) {
        try {
          if (file.isFile && isVideoFile(file)) {
            val video = createVideoFromFile(file, context)
            val normalizedPath = normalizePath(file.absolutePath)
            if (processedPaths.add(normalizedPath)) {
              videos.add(video)
              Log.d(tag, "Added video from direct scan: ${file.name}")
            } else {
              Log.d(tag, "Skipping duplicate video from file scan: ${file.name}")
            }
          }
        } catch (e: SecurityException) {
          Log.w(tag, "Security exception accessing: ${file.absolutePath}", e)
        }
      }

      videos.sortBy { it.displayName }
    } catch (e: Exception) {
      Log.e(tag, "Error scanning directory for videos: ${directory.absolutePath}", e)
    }
  }

  private fun isVideoFile(file: File): Boolean {
    val videoExtensions = setOf(
      "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
      "mpg", "mpeg", "3gp", "3g2", "ts", "m2ts", "mts", "vob",
    )
    return videoExtensions.contains(file.extension.lowercase())
  }

  private fun createVideoFromFile(file: File, context: Context): Video {
    val uri = Uri.fromFile(file)
    val folderPath = normalizePath(file.parent ?: "")
    val folderName = File(folderPath).name
    val bucketId = folderPath.hashCode().toString()

    // Try to extract duration using MediaMetadataRetriever
    var duration = 0L
    try {
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(file.absolutePath)
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      duration = durationStr?.toLongOrNull() ?: 0L
      retriever.release()
      Log.d(tag, "Extracted duration for ${file.name}: $duration ms")
    } catch (e: Exception) {
      Log.w(tag, "Could not extract duration for ${file.name}: ${e.message}")
    }

    return Video(
      id = file.absolutePath.hashCode().toLong(),
      title = file.nameWithoutExtension,
      displayName = file.name,
      path = file.absolutePath,
      uri = uri,
      duration = duration,
      durationFormatted = formatDuration(duration),
      size = file.length(),
      sizeFormatted = formatFileSize(file.length()),
      dateModified = file.lastModified() / 1000,
      dateAdded = file.lastModified() / 1000,
      mimeType = getMimeTypeFromExtension(file.extension),
      bucketId = bucketId,
      bucketDisplayName = folderName,
    )
  }

  private fun getMimeTypeFromExtension(extension: String): String {
    return when (extension.lowercase()) {
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "m4v" -> "video/x-m4v"
      "mpg", "mpeg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "3g2" -> "video/3gpp2"
      "ts" -> "video/mp2ts"
      "m2ts", "mts" -> "video/mp2t"
      "vob" -> "video/dvd"
      else -> "video/*"
    }
  }

  private fun getProjection(): Array<String> {
    return arrayOf(
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
  }

  private fun processVideoCursor(cursor: Cursor, videos: MutableList<Video>, processedPaths: MutableSet<String>) {
    val columnIndices = getCursorColumnIndices(cursor)

    while (cursor.moveToNext()) {
      extractVideoFromCursor(cursor, columnIndices)?.let { video ->
        val normalizedPath = normalizePath(video.path)
        if (processedPaths.add(normalizedPath)) {
          videos.add(video)
          Log.d(tag, "Added video: ${video.displayName} at ${video.path}")
        } else {
          Log.d(tag, "Skipping duplicate video from MediaStore: ${video.displayName}")
        }
      }
    }
  }

  private fun getCursorColumnIndices(cursor: Cursor): VideoColumnIndices {
    return VideoColumnIndices(
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
  }

  private fun extractVideoFromCursor(cursor: Cursor, indices: VideoColumnIndices): Video? {
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

    // Normalize the path to avoid duplicates
    val normalizedPath = if (path.isNotEmpty()) normalizePath(path) else path

    val (finalBucketId, finalBucketDisplayName) = getFinalBucketInfo(
      videoBucketId,
      bucketDisplayName,
      normalizedPath,
    )

    if (normalizedPath.isEmpty() || !File(normalizedPath).exists()) {
      Log.w(tag, "Skipping non-existent file: $normalizedPath")
      return null
    }

    val uri = Uri.withAppendedPath(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      id.toString(),
    )

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
    val finalBucketDisplayName = bucketDisplayName.ifEmpty {
      val isRootStorage = parentPath.startsWith("${TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT}/") &&
        parentPath == TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT
      if (isRootStorage) {
        "Internal Storage"
      } else {
        File(parentPath).name.takeIf { it.isNotEmpty() } ?: "Unknown Folder"
      }
    }
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

    return String.format(
      Locale.getDefault(),
      "%.1f %s",
      bytes / 1024.0.pow(digitGroups.toDouble()),
      units[digitGroups],
    )
  }
}
