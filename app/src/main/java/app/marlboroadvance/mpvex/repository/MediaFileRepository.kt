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

  // In-memory cache for fast subsequent loads
  private val videoFoldersCache = mutableMapOf<String, Pair<List<VideoFolder>, Long>>()
  private val videosCache = mutableMapOf<String, Pair<List<Video>, Long>>()
  private const val CACHE_VALIDITY_MS = Long.MAX_VALUE // Never expire - only clear on manual refresh

  /**
   * Clears all in-memory caches
   * Call this when media library changes are detected
   */
  fun clearCache() {
    videoFoldersCache.clear()
    videosCache.clear()
    Log.d(TAG, "Cleared all in-memory caches")
  }

  /**
   * Clears cache for a specific folder (all variants)
   */
  fun clearCacheForFolder(bucketId: String) {
    // Clear both cached variants (with and without hidden files)
    videosCache.remove("$bucketId-true")
    videosCache.remove("$bucketId-false")
    Log.d(TAG, "Cleared cache for bucket: $bucketId")
  }

  // =============================================================================
  // FOLDER OPERATIONS (Album View)
  // =============================================================================

  /**
   * Scans all storage volumes to find all folders containing videos
   * Used by FolderListViewModel for album view
   *
   * LEGACY VERSION: Maintains backward compatibility with metadata extraction
   * For better performance, use getAllVideoFoldersFast() instead
   */
  suspend fun getAllVideoFolders(
    context: Context,
    showHiddenFiles: Boolean,
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      val cacheKey = "all_folders_$showHiddenFiles"

      // Check cache first
      videoFoldersCache[cacheKey]?.let { (cached, timestamp) ->
        if (System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS) {
          Log.d(TAG, "Returning cached video folders (${cached.size} folders)")
          return@withContext cached
        }
      }

      try {
        val folders = FolderScanUtils.scanAllStorageForVideoFolders(
          context = context,
          showHiddenFiles = showHiddenFiles,
          metadataCache = metadataCache,
        )
        val result = FolderScanUtils.convertToVideoFolders(folders)

        // Update cache
        videoFoldersCache[cacheKey] = Pair(result, System.currentTimeMillis())

        result
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning for video folders", e)
        // Return cached data even if expired on error
        videoFoldersCache[cacheKey]?.first ?: emptyList()
      }
    }

  /**
   * OPTIMIZED: Ultra-fast scan for video folders with parallel processing
   * Returns folders immediately with basic info (count, size)
   * Duration will be 0 initially - call enrichVideoFolders() to populate
   *
   * This is 5-10x faster than getAllVideoFolders() for large file systems
   *
   * @param context Application context
   * @param showHiddenFiles Whether to show hidden files/folders
   * @param onProgress Optional callback for progress updates (folder count found)
   * @return List of VideoFolder with basic info (no duration)
   */
  suspend fun getAllVideoFoldersFast(
    context: Context,
    showHiddenFiles: Boolean,
    onProgress: ((Int) -> Unit)? = null,
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      try {
        val folders = FolderScanUtils.scanAllStorageForVideoFoldersOptimized(
          context = context,
          showHiddenFiles = showHiddenFiles,
          onProgress = onProgress,
        )
        FolderScanUtils.convertToVideoFolders(folders)
      } catch (e: Exception) {
        Log.e(TAG, "Error fast scanning for video folders", e)
        emptyList()
      }
    }

  /**
   * OPTIMIZED: Enriches folder list with metadata (duration) in background
   * Call after getAllVideoFoldersFast() to add duration information
   *
   * @param context Application context
   * @param folders List of folders to enrich (from fast scan)
   * @param onProgress Optional callback for progress (processed, total)
   * @return Updated list with duration information
   */
  suspend fun enrichVideoFolders(
    context: Context,
    folders: List<VideoFolder>,
    onProgress: ((Int, Int) -> Unit)? = null,
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      try {
        // Convert back to FolderData map
        val folderDataMap = folders.associate { folder ->
          folder.path to FolderScanUtils.FolderData(
            path = folder.path,
            name = folder.name,
            videoCount = folder.videoCount,
            totalSize = folder.totalSize,
            totalDuration = folder.totalDuration,
            lastModified = folder.lastModified,
          )
        }

        // Enrich with metadata
        val enrichedMap = FolderScanUtils.enrichFolderMetadata(
          context = context,
          folders = folderDataMap,
          metadataCache = metadataCache,
          onProgress = onProgress,
        )

        // Convert back to VideoFolder list
        FolderScanUtils.convertToVideoFolders(enrichedMap)
      } catch (e: Exception) {
        Log.e(TAG, "Error enriching video folders", e)
        folders // Return original on error
      }
    }

  // =============================================================================
  // VIDEO FILE OPERATIONS
  // =============================================================================

  /**
   * Gets all videos in a specific folder
   * OPTIMIZED: Uses batch metadata extraction for faster loading
   * @param bucketId Can be either a path or a bucket ID (path hash)
   * @param showHiddenFiles Whether to show hidden files
   */
  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
    showHiddenFiles: Boolean = true,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      // Check cache first
      // Cache key includes showHiddenFiles to ensure different results are cached separately
      val cacheKey = "$bucketId-$showHiddenFiles"
      videosCache[cacheKey]?.let { (cached, timestamp) ->
        if (System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS) {
          Log.d(TAG, "Returning cached videos for bucket $bucketId (${cached.size} videos)")
          return@withContext cached
        }
      }

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
          // Return cached data even if expired on error
          return@withContext videosCache[cacheKey]?.first ?: emptyList()
        }

        val videoFiles = directory.listFiles()?.filter {
          it.isFile && StorageScanUtils.isVideoFile(it) && !StorageScanUtils.shouldSkipFile(it, showHiddenFiles)
        } ?: emptyList()

        Log.d(TAG, "Found ${videoFiles.size} videos in $folderPath")

        // Batch extract metadata for all videos
        val fileTriples = videoFiles.map { file ->
          Triple(file, Uri.fromFile(file), file.name)
        }

        val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)

        // Create video objects with metadata
        val videos = videoFiles.mapNotNull { videoFile ->
          try {
            if (videoFile.exists()) {
              createVideoFromFileWithMetadata(
                videoFile,
                folderPath,
                directory.name,
                metadataMap[videoFile.absolutePath],
              )
            } else {
              null
            }
          } catch (e: Exception) {
            Log.w(TAG, "Error processing video file: ${videoFile.absolutePath}", e)
            null
          }
        }

        val result = videos.sortedBy { it.displayName.lowercase() }

        // Update cache
        videosCache[cacheKey] = Pair(result, System.currentTimeMillis())

        result
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for bucket $bucketId", e)
        // Return cached data even if expired on error
        return@withContext videosCache[cacheKey]?.first ?: emptyList()
      }
    }

  /**
   * Gets videos from multiple folders
   */
  suspend fun getVideosForBuckets(
    context: Context,
    bucketIds: Set<String>,
    showHiddenFiles: Boolean = true,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val result = mutableListOf<Video>()
      for (id in bucketIds) {
        runCatching { result += getVideosInFolder(context, id, showHiddenFiles) }
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
    var hasEmbeddedSubtitles = false
    var subtitleCodec = ""

    metadataCache.getOrExtractMetadata(file, uri, displayName)?.let { metadata ->
      if (metadata.sizeBytes > 0) size = metadata.sizeBytes
      duration = metadata.durationMs
      width = metadata.width
      height = metadata.height
      fps = metadata.fps
      hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles
      subtitleCodec = metadata.subtitleCodec
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
      hasEmbeddedSubtitles = hasEmbeddedSubtitles,
      subtitleCodec = subtitleCodec,
    )
  }

  /**
   * OPTIMIZED: Creates a Video object from a file with pre-fetched metadata
   * Use this when metadata has already been batch-extracted
   */
  private fun createVideoFromFileWithMetadata(
    file: File,
    bucketId: String,
    bucketDisplayName: String,
    metadata: app.marlboroadvance.mpvex.utils.media.MediaInfoOps.VideoMetadata?,
  ): Video {
    val path = file.absolutePath
    val displayName = file.name
    val title = file.nameWithoutExtension
    val dateModified = file.lastModified() / 1000

    val extension = file.extension.lowercase()
    val mimeType = StorageScanUtils.getMimeTypeFromExtension(extension)
    val uri = Uri.fromFile(file)

    // Use pre-fetched metadata
    var size = file.length()
    var duration = 0L
    var width = 0
    var height = 0
    var fps = 0f

    metadata?.let {
      if (it.sizeBytes > 0) size = it.sizeBytes
      duration = it.durationMs
      width = it.width
      height = it.height
      fps = it.fps
    }
    val hasEmbeddedSubtitles = metadata?.hasEmbeddedSubtitles ?: false
    val subtitleCodec = metadata?.subtitleCodec ?: ""

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
      hasEmbeddedSubtitles = hasEmbeddedSubtitles,
      subtitleCodec = subtitleCodec,
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
   * @param useFastCount If true, uses fast shallow counting (immediate children only). If false, uses deep recursive counting.
   */
  suspend fun scanDirectory(
    context: Context,
    path: String,
    showAllFileTypes: Boolean = false,
    showHiddenFiles: Boolean = false,
    useFastCount: Boolean = false,
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
            val folderInfo = if (useFastCount) {
              FolderScanUtils.getDirectChildrenCountFast(subdir, showHiddenFiles, showAllFileTypes)
            } else {
              FolderScanUtils.getDirectChildrenCount(subdir, showHiddenFiles, showAllFileTypes)
            }

            // Only add folder if it contains files (recursively counted)
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
          files.filter { it.isFile && !StorageScanUtils.shouldSkipFile(it, showHiddenFiles) }
        } else {
          files.filter { it.isFile && StorageScanUtils.isVideoFile(it) && !StorageScanUtils.shouldSkipFile(it, showHiddenFiles) }
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
          "Scanned directory: $path, found ${items.size} items (${items.filterIsInstance<FileSystemItem.Folder>().size} folders, ${items.filterIsInstance<FileSystemItem.VideoFile>().size} videos) [fastCount=$useFastCount]",
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

    // Show only the integer part for frame rates, without rounding
    val fpsFormatted = fps.toInt().toString()

    return "$baseResolution@$fpsFormatted"
  }
}
