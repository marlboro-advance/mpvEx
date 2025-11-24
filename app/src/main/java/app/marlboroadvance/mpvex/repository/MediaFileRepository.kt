package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.utils.storage.FolderScanUtils
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
 * Unified repository for ALL media file operations
 * Consolidates FileSystemRepository, VideoRepository functionality
 *
 * This repository handles:
 * - Video folder discovery (album view)
 * - File system browsing (tree view)
 * - Video file listing
 * - Metadata extraction
 * - Path operations
 * - Storage volume detection
 */
object MediaFileRepository : KoinComponent {
  private const val TAG = "MediaFileRepository"
  private val metadataCache: VideoMetadataCacheRepository by inject()

  // =============================================================================
  // FOLDER OPERATIONS (Album View)
  // =============================================================================

  /**
   * Scans all storage volumes to find all folders containing videos
   * Used by FolderListViewModel for album view
   */
  suspend fun getAllVideoFolders(
    context: Context,
    showHiddenFiles: Boolean,
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      try {
        val folders = FolderScanUtils.scanAllStorageForVideoFolders(
          context = context,
          showHiddenFiles = showHiddenFiles,
          metadataCache = metadataCache,
        )
        FolderScanUtils.convertToVideoFolders(folders)
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning for video folders", e)
        emptyList()
      }
    }

  // =============================================================================
  // VIDEO FILE OPERATIONS
  // =============================================================================

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
          bucketId // Treat as path for now
        }

        // Scan the directory for video files
        val directory = File(folderPath)
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
          Log.w(TAG, "Cannot access directory: $folderPath")
          return@withContext emptyList()
        }

        val videoFiles = directory.listFiles()?.filter {
          it.isFile && StorageScanUtils.isVideoFile(it)
        } ?: emptyList()

        Log.d(TAG, "Found ${videoFiles.size} videos in $folderPath")

        for (videoFile in videoFiles) {
          try {
            val normalizedPath = videoFile.canonicalPath
            if (processedPaths.add(normalizedPath) && videoFile.exists()) {
              val video = createVideoFromFile(videoFile, folderPath, directory.name)
              videos.add(video)
            }
          } catch (e: Exception) {
            Log.w(TAG, "Error processing video file: ${videoFile.absolutePath}", e)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for bucket $bucketId", e)
      }

      videos.sortedBy { it.displayName.lowercase() }
    }

  /**
   * Gets videos from multiple folders
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
   * Creates Video objects from a list of files
   */
  suspend fun getVideosFromFiles(
    files: List<File>,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      files.mapNotNull { file ->
        try {
          val folderPath = file.parent ?: ""
          val folderName = file.parentFile?.name ?: ""
          createVideoFromFile(file, folderPath, folderName)
        } catch (e: Exception) {
          Log.w(TAG, "Error creating video from file: ${file.absolutePath}", e)
          null
        }
      }
    }

  /**
   * Creates a Video object from a file with full metadata extraction
   */
  private suspend fun createVideoFromFile(
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
      if (metadata.sizeBytes > 0) size = metadata.sizeBytes
      duration = metadata.durationMs
      width = metadata.width
      height = metadata.height
      fps = metadata.fps
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

  // =============================================================================
  // FILE SYSTEM BROWSING (Tree View)
  // =============================================================================

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

    components.add(PathComponent("Root", "/"))

    var currentPath = ""
    for (part in parts) {
      currentPath += "/$part"
      components.add(PathComponent(part, currentPath))
    }

    return components
  }

  /**
   * Scans a directory and returns its contents (folders and video files)
   * @param showAllFileTypes If true, shows all files. If false, shows only videos.
   * @param showHiddenFiles If true, shows hidden files and folders. If false, hides them.
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

        // Validation checks
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

        // Process subdirectories
        files
          .filter { it.isDirectory && it.canRead() && !StorageScanUtils.shouldSkipFolder(it, showHiddenFiles) }
          .forEach { subdir ->
            val folderInfo = FolderScanUtils.getDirectChildrenCount(subdir, showHiddenFiles, showAllFileTypes)

            // Only add folder if it contains files
            if (folderInfo.videoCount > 0) {
              items.add(
                FileSystemItem.Folder(
                  name = subdir.name,
                  path = subdir.absolutePath,
                  lastModified = subdir.lastModified(),
                  videoCount = folderInfo.videoCount,
                  totalSize = folderInfo.totalSize,
                  totalDuration = 0L,
                  hasSubfolders = folderInfo.hasSubfolders,
                ),
              )
            }
          }

        // Process files in current directory
        val targetFiles = if (showAllFileTypes) {
          files.filter { it.isFile }
        } else {
          files.filter { it.isFile && StorageScanUtils.isVideoFile(it) }
        }

        val videos = getVideosFromFiles(targetFiles)

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
   */
  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    withContext(Dispatchers.IO) {
      val roots = mutableListOf<FileSystemItem.Folder>()

      try {
        // Primary storage (internal)
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage.exists() && primaryStorage.canRead()) {
          roots.add(
            FileSystemItem.Folder(
              name = "Internal Storage",
              path = primaryStorage.absolutePath,
              lastModified = primaryStorage.lastModified(),
              videoCount = 0,
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
                  videoCount = 0,
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

  // =============================================================================
  // FORMATTING UTILITIES
  // =============================================================================

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

  private fun formatResolution(
    width: Int,
    height: Int,
  ): String {
    if (width <= 0 || height <= 0) return "--"

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

  private fun formatResolutionWithFps(
    width: Int,
    height: Int,
    fps: Float,
  ): String {
    val baseResolution = formatResolution(width, height)
    if (baseResolution == "--" || fps <= 0f) return baseResolution

    val fpsFormatted =
      if (fps % 1.0f == 0f) {
        fps.toInt().toString()
      } else {
        String.format(Locale.getDefault(), "%.2f", fps).trimEnd('0').trimEnd('.')
      }

    return "$baseResolution@$fpsFormatted"
  }
}
