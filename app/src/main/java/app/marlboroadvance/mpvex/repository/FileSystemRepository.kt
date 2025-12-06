package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Repository for filesystem operations - based on Fossify File Manager ItemsFragment logic
 * Handles directory scanning, file detection, and metadata extraction
 */
object FileSystemRepository : KoinComponent {
  private const val TAG = "FileSystemRepository"
  private val metadataCache: VideoMetadataCacheRepository by inject()

  // Folders to skip during scanning (system/cache folders) - from Fossify reference
  // These are skipped by default but shown when showHiddenFiles is enabled
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
   * Equivalent to internalStoragePath from Fossify
   */
  fun getDefaultRootPath(): String = Environment.getExternalStorageDirectory().absolutePath

  /**
   * Parses a path into breadcrumb components
   * Similar to Fossify's Breadcrumbs component logic
   */
  fun getPathComponents(path: String): List<PathComponent> {
    if (path.isBlank()) return emptyList()

    val components = mutableListOf<PathComponent>()
    val normalizedPath = path.trimEnd('/')
    val parts = normalizedPath.split("/").filter { it.isNotEmpty() }

    // Build cumulative paths - similar to Fossify's breadcrumb builder
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
   * Based on Fossify's ItemsFragment.getRegularItemsOf() and getItems() logic
   *
   * @param showAllFileTypes If true, shows all files. If false, shows only videos.
   * @param showHiddenFiles If true, shows hidden files and folders (starting with .). If false, hides them.
   */
  suspend fun scanDirectory(
    context: Context,
    path: String,
    showAllFileTypes: Boolean = false,
    showHiddenFiles: Boolean = false,
  ): Result<List<FileSystemItem>> =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(path)

        // Validation checks - similar to Fossify's directory validation
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

        // Process subdirectories - based on Fossify's getListItemFromFile logic
        // Only include folders that contain files (videos or all files based on mode)
        files
          .filter { it.isDirectory && it.canRead() && !shouldSkipFolder(it, showHiddenFiles) }
          .forEach { subdir ->
            val folderInfo = getDirectChildrenCount(subdir, showHiddenFiles, showAllFileTypes)

            // Only add folder if it contains files
            if (folderInfo.videoCount > 0) {
              items.add(
                FileSystemItem.Folder(
                  name = subdir.name,
                  path = subdir.absolutePath,
                  lastModified = subdir.lastModified(),
                  videoCount = folderInfo.videoCount,
                  totalSize = folderInfo.totalSize,
                  totalDuration = 0L, // Duration calculated on-demand for performance
                  hasSubfolders = folderInfo.hasSubfolders,
                ),
              )
            }
          }

        // Process files in current directory - based on Fossify's file detection
        val targetFiles = if (showAllFileTypes) {
          // Show all files in file manager mode
          files.filter { it.isFile }
        } else {
          // Show only videos in video player mode
          files.filter { it.isFile && StorageScanUtils.isVideoFile(it) }
        }

        // Create Video objects for each file - using MediaStore when available
        val videos = getVideosFromFiles(context, targetFiles)

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

        Log.d(
          TAG,
          "Scanned directory: $path, found ${items.size} items (${items.filterIsInstance<FileSystemItem.Folder>().size} folders, ${items.filterIsInstance<FileSystemItem.VideoFile>().size} videos)",
        )
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
   * Based on Fossify's storage detection logic (internalStoragePath, sdCardPath, OTG)
   */
  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    withContext(Dispatchers.IO) {
      val roots = mutableListOf<FileSystemItem.Folder>()

      try {
        // Primary storage (internal) - equivalent to Fossify's internalStoragePath
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

        // External volumes (SD cards, USB OTG) - similar to Fossify's SD and OTG detection
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

        Log.d(TAG, "Found ${roots.size} storage roots")
      } catch (e: Exception) {
        Log.e(TAG, "Error getting storage roots", e)
      }

      roots
    }

  /**
   * Gets direct children count for a folder (not recursive)
   * Based on Fossify's FileDirItem.getDirectChildrenCount() extension
   *
   * @param showAllFileTypes If true, counts all files. If false, counts only videos.
   */
  private fun getDirectChildrenCount(
    folder: File,
    showHiddenFiles: Boolean,
    showAllFileTypes: Boolean = false,
  ): FolderInfo {
    var videoCount = 0
    var totalSize = 0L
    var hasSubfolders = false

    try {
      val files = folder.listFiles()
      if (files != null) {
        for (file in files) {
          when {
            // Skip hidden files if needed
            !showHiddenFiles && file.name.startsWith(".") -> continue

            // Skip system folders
            file.isDirectory && shouldSkipFolder(file, showHiddenFiles) -> continue

            // Count subdirectories
            file.isDirectory -> {
              hasSubfolders = true
              // Recursively count files in subfolders (up to depth 10)
              val subInfo =
                countVideosRecursive(file, showHiddenFiles, showAllFileTypes, maxDepth = 10, currentDepth = 0)
              videoCount += subInfo.videoCount
              totalSize += subInfo.totalSize
            }

            // Count files (videos only or all files based on mode)
            file.isFile -> {
              val shouldCount = if (showAllFileTypes) {
                true // Count all files in file manager mode
              } else {
                StorageScanUtils.isVideoFile(file) // Count only videos in video player mode
              }

              if (shouldCount) {
                videoCount++
                totalSize += file.length()
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error counting folder children: ${folder.absolutePath}", e)
    }

    return FolderInfo(videoCount, totalSize, hasSubfolders)
  }

  /**
   * Recursively counts videos in a folder hierarchy
   * Similar to Fossify's recursive folder analysis
   *
   * @param showAllFileTypes If true, counts all files. If false, counts only videos.
   */
  private fun countVideosRecursive(
    folder: File,
    showHiddenFiles: Boolean,
    showAllFileTypes: Boolean,
    maxDepth: Int,
    currentDepth: Int,
  ): FolderInfo {
    if (currentDepth >= maxDepth) return FolderInfo(0, 0L, false)

    var videoCount = 0
    var totalSize = 0L
    var hasSubfolders = false

    try {
      val files = folder.listFiles()
      if (files != null) {
        for (file in files) {
          when {
            !showHiddenFiles && file.name.startsWith(".") -> continue
            file.isDirectory && shouldSkipFolder(file, showHiddenFiles) -> continue

            file.isFile -> {
              val shouldCount = if (showAllFileTypes) {
                true // Count all files in file manager mode
              } else {
                StorageScanUtils.isVideoFile(file) // Count only videos in video player mode
              }

              if (shouldCount) {
                videoCount++
                totalSize += file.length()
              }
            }

            file.isDirectory -> {
              hasSubfolders = true
              val subInfo = countVideosRecursive(file, showHiddenFiles, showAllFileTypes, maxDepth, currentDepth + 1)
              videoCount += subInfo.videoCount
              totalSize += subInfo.totalSize
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error counting videos recursively: ${folder.absolutePath}", e)
    }

    return FolderInfo(videoCount, totalSize, hasSubfolders)
  }

  /**
   * Creates Video objects from file paths by querying MediaStore
   * Based on Fossify's MediaStore query logic and FileDirItem creation
   * Uses MediaInfo fallback for videos with missing metadata
   */
  private suspend fun getVideosFromFiles(
    context: Context,
    files: List<File>,
  ): List<Video> {
    val videos = mutableListOf<Video>()

    if (files.isEmpty()) return emptyList()

    // MediaStore projection - similar to Fossify's video queries
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

    // Build selection for all file paths - Fossify uses similar batch queries
    val pathList = files.map { it.absolutePath }
    val selection = "${MediaStore.Video.Media.DATA} IN (${pathList.joinToString(",") { "?" }})"
    val selectionArgs = pathList.toTypedArray()

    try {
      // Query MediaStore for video metadata
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

          // Extract framerate and fallback metadata using MediaInfo cache
          var fps = 0f
          val file = File(path)

          // Use metadata cache for fps and fallback data
          if (width <= 0 || height <= 0 || duration <= 0) {
            // MediaStore has incomplete data - extract everything from MediaInfo
            Log.d(TAG, "MediaStore incomplete for $displayName, using MediaInfo fallback")
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
              fps = metadata.fps
            } ?: run {
              Log.w(TAG, "MediaInfo fallback failed for $displayName")
            }
          } else {
            // MediaStore has basic data, but we still need fps from MediaInfo
            metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
              fps = metadata.fps
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
              fps = fps,
              resolution = formatResolutionWithFps(width, height, fps),
              mimeType = mimeType,
              dateAdded = dateAdded,
              dateModified = dateModified,
              bucketId = bucketId,
              bucketDisplayName = bucketName,
            ),
          )
        }
      }

      // Handle files not in MediaStore - similar to Fossify's fallback for unindexed files
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
          var fps = 0f

          // Use metadata cache with MediaInfo fallback
          metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
            if (metadata.sizeBytes > 0) size = metadata.sizeBytes
            duration = metadata.durationMs
            width = metadata.width
            height = metadata.height
            fps = metadata.fps
            Log.d(TAG, "Metadata for $displayName (not in MediaStore): ${width}x${height}@${fps}fps, ${duration}ms")
          } ?: run {
            Log.w(TAG, "Failed to extract metadata for $displayName (not in MediaStore)")
          }

          val extension = file.extension.lowercase()
          val mimeType = StorageScanUtils.getMimeTypeFromExtension(extension)

          // Use file path hash as ID for files not in MediaStore
          val fileId = file.absolutePath.hashCode().toLong()

          videos.add(
            Video(
              id = fileId,
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
              fps = fps,
              resolution = formatResolutionWithFps(width, height, fps),
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
   * Based on Fossify's folder filtering logic
   *
   * When showHiddenFiles = false: Skip hidden folders (starting with .) and SKIP_FOLDERS
   * When showHiddenFiles = true: Show everything, don't skip anything
   */
  private fun shouldSkipFolder(folder: File, showHiddenFiles: Boolean): Boolean {
    // If showHiddenFiles is enabled, don't skip anything
    if (showHiddenFiles) {
      return false
    }

    // If showHiddenFiles is disabled, skip hidden folders and folders in SKIP_FOLDERS
    val name = folder.name.lowercase()
    val isHidden = name.startsWith(".")
    return isHidden || SKIP_FOLDERS.contains(name)
  }

  /**
   * Folder analysis result
   */
  private data class FolderInfo(
    val videoCount: Int,
    val totalSize: Long,
    val hasSubfolders: Boolean,
  )

  /**
   * Formats duration in milliseconds to human-readable string
   * Similar to Fossify's duration formatting
   */
  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0s"

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

  /**
   * Formats file size in bytes to human-readable string
   * Based on Fossify's formatSize extension
   */
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

  /**
   * Formats resolution to human-readable string with quality label
   * Similar to Fossify's resolution display logic
   */
  private fun formatResolution(
    width: Int,
    height: Int,
  ): String {
    if (width <= 0 || height <= 0) return "--"

    // Determine quality label based on resolution
    val label =
      when {
        width >= 7680 || height >= 4320 -> "4320p"
        width >= 3840 || height >= 2160 -> "2160p"
        width >= 2560 || height >= 1440 -> "1440p"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        width >= 640 || height >= 360 -> "360p"
        width >= 426 || height >= 240 -> "240p"
        else -> "${height}p"
      }

    return label
  }

  /**
   * Formats resolution with framerate
   */
  private fun formatResolutionWithFps(
    width: Int,
    height: Int,
    fps: Float,
  ): String {
    val baseResolution = formatResolution(width, height)
    if (baseResolution == "--" || fps <= 0f) return baseResolution

    // Format fps: show up to 2 decimals, but remove trailing zeros
    val fpsFormatted =
      if (fps % 1.0f == 0f) {
        fps.toInt().toString()
      } else {
        String.format(Locale.getDefault(), "%.2f", fps).trimEnd('0').trimEnd('.')
      }

    return "$baseResolution@$fpsFormatted"
  }
}
