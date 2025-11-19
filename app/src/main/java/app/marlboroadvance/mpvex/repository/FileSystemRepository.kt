package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter.formatFileSize
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileSystemRepository : KoinComponent {
  private const val TAG = "FileSystemRepository"
  private val metadataCache: VideoMetadataCacheRepository by inject()
  private val browserPreferences: BrowserPreferences by inject()

  // Folders to skip during scanning (system/cache folders)
  private val SKIP_FOLDERS =
    setOf(
      "android",
      "data",
      ".thumbnails",
      ".cache",
      "cache",
      "lost.dir",
      "system",
      ".android_secure",
      ".trash",
      ".trashbin",
    )

  /**
   * Gets the default root path for the filesystem browser
   */
  fun getDefaultRootPath(): String = Environment.getExternalStorageDirectory().absolutePath

  /**
   * Parses a path into breadcrumb components
   */
  fun getPathComponents(path: String): List<PathComponent> {
    if (path.isBlank()) return emptyList()

    val components = mutableListOf<PathComponent>()
    val normalizedPath = path.trimEnd('/')
    val parts = normalizedPath.split("/").filter { it.isNotEmpty() }

    // Build cumulative paths
    val rootPath = "/"
    components.add(PathComponent("Root", rootPath))

    var currentPath = ""
    for (part in parts) {
      currentPath += "/$part"
      components.add(PathComponent(part, currentPath))
    }

    return components
  }

  /**
   * Scans a directory and returns its contents (folders and video files)
   */
  suspend fun scanDirectory(
    context: Context,
    path: String,
  ): Result<List<FileSystemItem>> =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(path)

        if (!directory.exists()) {
          return@withContext Result.failure(Exception("Directory does not exist: $path"))
        }

        if (!directory.canRead()) {
          return@withContext Result.failure(Exception("Cannot read directory: $path"))
        }

        if (!directory.isDirectory) {
          return@withContext Result.failure(Exception("Path is not a directory: $path"))
        }

        val items = mutableListOf<FileSystemItem>()
        val files = directory.listFiles()

        if (files == null) {
          return@withContext Result.failure(Exception("Failed to list directory contents: $path"))
        }

        // Check if current directory contains .nomedia and should be skipped
        if (browserPreferences.respectNomedia.get() && files.any { it.name == ".nomedia" }) {
          return@withContext Result.success(emptyList())
        }

        // Process subdirectories - only include folders that contain videos
        files
          .filter { it.isDirectory && it.canRead() && !shouldSkipFolder(it) }
          .forEach { subdir ->
            val folderInfo = analyzeFolderContents(subdir)
            // Only add folder if it contains at least one video (directly or in subfolders)
            if (folderInfo.videoCount > 0) {
              items.add(
                FileSystemItem.Folder(
                  name = subdir.name,
                  path = subdir.absolutePath,
                  lastModified = subdir.lastModified(),
                  videoCount = folderInfo.videoCount,
                  totalSize = folderInfo.totalSize,
                  totalDuration = folderInfo.totalDuration,
                  hasSubfolders = folderInfo.hasSubfolders,
                ),
              )
            }
          }

        // Process video files in current directory
        val videoFiles = files.filter {
          it.isFile &&
            StorageScanUtils.isVideoFile(it) &&
            (browserPreferences.showHiddenFiles.get() || !it.name.startsWith("."))
        }

        // Create Video objects for each video file
        val videos = getVideosFromFiles(context, videoFiles)

        videos.forEach { video ->
          items.add(
            FileSystemItem.VideoFile(
              name = video.displayName,
              path = video.path,
              lastModified = File(video.path).lastModified(),
              video = video,
            ),
          )
        }

        Log.d(TAG, "Scanned directory: $path, found ${items.size} items")
        Result.success(items)
      } catch (e: SecurityException) {
        Log.e(TAG, "Security exception scanning directory: $path", e)
        Result.failure(Exception("Permission denied: ${e.message}"))
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning directory: $path", e)
        Result.failure(e)
      }
    }

  /**
   * Gets all storage volume roots
   * Optimized: doesn't pre-scan for video counts (done lazily when navigating)
   */
  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    withContext(Dispatchers.IO) {
      val roots = mutableListOf<FileSystemItem.Folder>()

      try {
        // Primary storage
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage.exists() && primaryStorage.canRead()) {
          roots.add(
            FileSystemItem.Folder(
              name = "Internal Storage",
              path = primaryStorage.absolutePath,
              lastModified = primaryStorage.lastModified(),
              videoCount = 0, // Will be calculated when user navigates into it
              totalSize = 0L,
              totalDuration = 0L,
              hasSubfolders = true,
            ),
          )
        }

        // External volumes (SD cards, USB OTG)
        val externalVolumes = StorageScanUtils.getExternalStorageVolumes(context)
        for (volume in externalVolumes) {
          val volumePath = StorageScanUtils.getVolumePath(volume)
          if (volumePath != null) {
            val volumeDir = File(volumePath)
            if (volumeDir.exists() && volumeDir.canRead()) {
              val volumeName = volume.getDescription(context)
              roots.add(
                FileSystemItem.Folder(
                  name = volumeName,
                  path = volumeDir.absolutePath,
                  lastModified = volumeDir.lastModified(),
                  videoCount = 0, // Will be calculated when user navigates into it
                  totalSize = 0L,
                  totalDuration = 0L,
                  hasSubfolders = true,
                ),
              )
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting storage roots", e)
      }

      roots
    }

  /**
   * Analyzes folder contents to get video count, size, and duration
   * Uses lightweight recursive counting (depth limited to 2 levels for performance)
   */
  private fun analyzeFolderContents(folder: File): FolderInfo {
    var videoCount = 0
    var totalSize = 0L
    var totalDuration = 0L
    var hasSubfolders = false
    val showHidden = browserPreferences.showHiddenFiles.get()

    try {
      val files = folder.listFiles()
      if (files != null) {
        for (file in files) {
          when {
            file.isDirectory && !shouldSkipFolder(file) -> {
              hasSubfolders = true
              // Lightweight count - only check if folder has videos (limited depth)
              val subCount = countVideosRecursive(file, maxDepth = 2, currentDepth = 0)
              videoCount += subCount
            }

            file.isFile && StorageScanUtils.isVideoFile(file) && (showHidden || !file.name.startsWith(".")) -> {
              videoCount++
              totalSize += file.length()
              // Skip duration extraction for performance - it's expensive
              // Duration will be extracted when user actually views the folder
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error analyzing folder: ${folder.absolutePath}", e)
    }

    return FolderInfo(videoCount, totalSize, totalDuration, hasSubfolders)
  }

  /**
   * Recursively counts videos in a folder
   */
  private fun countVideosRecursive(
    folder: File,
    maxDepth: Int,
    currentDepth: Int,
  ): Int {
    if (currentDepth >= maxDepth) return 0

    var count = 0
    val showHidden = browserPreferences.showHiddenFiles.get()
    try {
      val files = folder.listFiles()
      if (files != null) {
        for (file in files) {
          when {
            file.isFile && StorageScanUtils.isVideoFile(file) && (showHidden || !file.name.startsWith(".")) -> count++
            file.isDirectory && !shouldSkipFolder(file) -> {
              count += countVideosRecursive(file, maxDepth, currentDepth + 1)
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error counting videos in: ${folder.absolutePath}", e)
    }
    return count
  }

  /**
   * Creates Video objects from file paths by querying MediaStore
   * Uses MediaInfo fallback for videos with missing metadata
   */
  private suspend fun getVideosFromFiles(
    context: Context,
    files: List<File>,
  ): List<Video> {
    val videos = mutableListOf<Video>()

    val projection =
      arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
      )

    // Build selection for all file paths
    val pathList = files.map { it.absolutePath }
    if (pathList.isEmpty()) return emptyList()

    val selection = "${MediaStore.Video.Media.DATA} IN (${pathList.joinToString(",") { "?" }})"
    val selectionArgs = pathList.toTypedArray()

    try {
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null,
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
        val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
        val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val displayName = cursor.getString(displayNameColumn)
          val path = cursor.getString(dataColumn)
          var size = cursor.getLong(sizeColumn)
          var duration = cursor.getLong(durationColumn)
          var width = cursor.getInt(widthColumn)
          var height = cursor.getInt(heightColumn)
          val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
          val dateAdded = cursor.getLong(dateAddedColumn)
          val dateModified = cursor.getLong(dateModifiedColumn)
          val bucketId = cursor.getString(bucketIdColumn) ?: ""
          val bucketName = cursor.getString(bucketNameColumn) ?: ""

          val uri =
            Uri.withAppendedPath(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
              id.toString(),
            )

          // Fallback to MediaInfo if MediaStore doesn't have resolution or duration (unsupported format)
          if (width <= 0 || height <= 0 || duration <= 0) {
            Log.d(TAG, "MediaStore has incomplete data for $displayName, trying MediaInfo fallback")
            val file = File(path)
            metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
              if (metadata.width > 0 && metadata.height > 0) {
                width = metadata.width
                height = metadata.height
              }
              if (metadata.durationMs > 0) {
                duration = metadata.durationMs
              }
              if (metadata.sizeBytes > 0) {
                size = metadata.sizeBytes
              }
              Log.d(TAG, "MediaInfo fallback for $displayName: ${width}x${height}, ${duration}ms")
            } ?: run {
              Log.w(TAG, "MediaInfo fallback failed for $displayName")
            }
          }

          videos.add(
            Video(
              id = id,
              title = displayName.substringBeforeLast('.'),
              displayName = displayName,
              path = path,
              uri = uri,
              size = size,
              sizeFormatted = formatFileSize(size),
              duration = duration,
              durationFormatted = formatDuration(duration),
              width = width,
              height = height,
              resolution = formatResolution(width, height),
              mimeType = mimeType,
              dateAdded = dateAdded,
              dateModified = dateModified,
              bucketId = bucketId,
              bucketDisplayName = bucketName,
            ),
          )
        }
      }

      // For files not in MediaStore, create Video objects using cached MediaInfo extraction
      val foundPaths = videos.map { it.path }.toSet()
      files
        .filter { it.absolutePath !in foundPaths }
        .forEach { file ->
          val uri = Uri.fromFile(file)
          val displayName = file.name
          var size = file.length()
          var duration = 0L
          var width = 0
          var height = 0

          // Use metadata cache with MediaInfo fallback
          metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
            if (metadata.sizeBytes > 0) size = metadata.sizeBytes
            duration = metadata.durationMs
            width = metadata.width
            height = metadata.height
            Log.d(
              TAG,
              "Metadata for $displayName (not in MediaStore): ${width}x${height}, ${duration}ms",
            )
          } ?: run {
            Log.w(TAG, "Failed to extract metadata for $displayName (not in MediaStore)")
          }

          val extension = file.extension.lowercase()
          val mimeType = StorageScanUtils.getMimeTypeFromExtension(extension)

          videos.add(
            Video(
              id = file.absolutePath.hashCode().toLong(),
              title = file.nameWithoutExtension,
              displayName = displayName,
              path = file.absolutePath,
              uri = uri,
              size = size,
              sizeFormatted = formatFileSize(size),
              duration = duration,
              durationFormatted = formatDuration(duration),
              width = width,
              height = height,
              resolution = formatResolution(width, height),
              mimeType = mimeType,
              dateAdded = file.lastModified() / 1000,
              dateModified = file.lastModified() / 1000,
              bucketId = file.parent ?: "",
              bucketDisplayName = file.parentFile?.name ?: "",
            ),
          )
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error querying MediaStore for videos", e)
    }

    return videos
  }

  /**
   * Checks if a folder should be skipped during scanning
   */
  private fun shouldSkipFolder(folder: File): Boolean {
    val name = folder.name.lowercase()
    val showHidden = browserPreferences.showHiddenFiles.get()

    if (!showHidden && name.startsWith(".")) return true
    if (SKIP_FOLDERS.contains(name)) return true

    if (browserPreferences.respectNomedia.get()) {
      if (File(folder, ".nomedia").exists()) return true
    }

    return false
  }

  private data class FolderInfo(
    val videoCount: Int,
    val totalSize: Long,
    val totalDuration: Long,
    val hasSubfolders: Boolean,
  )

  /**
   * Formats duration in milliseconds to human-readable string
   */
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

  /**
   * Formats file size in bytes to human-readable string
   */
  private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
  }

  /**
   * Formats resolution to human-readable string
   */
  private fun formatResolution(
    width: Int,
    height: Int,
  ): String {
    if (width <= 0 || height <= 0) return "--"

    val label =
      when {
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

    return label
  }
}
