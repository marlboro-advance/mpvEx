package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.utils.media.MediaFormatUtils
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.storage.FileFilterUtils
import app.marlboroadvance.mpvex.utils.storage.FileTypeUtils
import app.marlboroadvance.mpvex.utils.storage.StorageVolumeUtils
import app.marlboroadvance.mpvex.utils.storage.VideoScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Unified repository for ALL media file operations.
 *
 * Consolidates media discovery, folder/tree navigation, and video file queries
 * into a single, high-performance, non-redundant pipeline.
 */
object MediaFileRepository {
  private const val TAG = "MediaFileRepository"

  // In-memory cache for discovered folders
  @Volatile
  private var cachedFolders: List<VideoFolder>? = null

  @Volatile
  private var cachedFolderCounts: Map<String, Int> = emptyMap()

  @Volatile
  private var cachedRecursiveCounts: Map<String, Int> = emptyMap()

  @Volatile
  private var cachedRecursiveSizes: Map<String, Long> = emptyMap()

  @Volatile
  private var cachedRecursiveDurations: Map<String, Long> = emptyMap()

  private var cacheTimestamp: Long = 0L
  private const val CACHE_TTL_MS = 15_000L // 15 seconds TTL
  private val scanMutex = Mutex()

  /**
   * Clears all discovery caches.
   */
  fun clearCache() {
    Log.d(TAG, "Clearing media file caches")
    cachedFolders = null
    cachedFolderCounts = emptyMap()
    cachedRecursiveCounts = emptyMap()
    cachedRecursiveSizes = emptyMap()
    cachedRecursiveDurations = emptyMap()
    cacheTimestamp = 0L
  }

  // =============================================================================
  // FOLDER OPERATIONS (Album View & Discovery)
  // =============================================================================

  private data class FolderAccumulator(
    val path: String,
    val name: String,
    var videoCount: Int = 0,
    var totalSize: Long = 0L,
    var totalDuration: Long = 0L,
    var lastModified: Long = 0L,
    var hasSubfolders: Boolean = false,
  )

  /**
   * Scans storage to find all folders containing videos (fast MediaStore + shallow external fallback).
   */
  suspend fun getAllVideoFolders(context: Context): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      cachedFolders?.let { cached ->
        if (now - cacheTimestamp < CACHE_TTL_MS) {
          return@withContext cached
        }
      }

      scanMutex.withLock {
        // Double-check cache after acquiring mutex
        cachedFolders?.let { cached ->
          if (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return@withContext cached
          }
        }

        val foldersMap = mutableMapOf<String, FolderAccumulator>()

      // 1. Single-pass query over MediaStore.Video
      try {
        val projection = arrayOf(
          MediaStore.Video.Media.DATA,
          MediaStore.Video.Media.SIZE,
          MediaStore.Video.Media.DURATION,
          MediaStore.Video.Media.DATE_MODIFIED,
        )

        context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          null,
          null,
          null,
        )?.use { cursor ->
          val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
          val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
          val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
          val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

          val folderSkipCache = mutableMapOf<String, Boolean>()

          while (cursor.moveToNext()) {
            val path = cursor.getString(dataCol) ?: continue
            val file = File(path)
            if (!file.exists()) continue

            val folderPath = file.parent ?: continue
            val shouldSkip = folderSkipCache.getOrPut(folderPath) {
              FileFilterUtils.shouldSkipFolder(File(folderPath))
            }
            if (shouldSkip) continue

            val folderFile = File(folderPath)
            val size = cursor.getLong(sizeCol)
            val duration = cursor.getLong(durationCol)
            val dateModified = cursor.getLong(dateCol)

            val acc = foldersMap.getOrPut(folderPath) {
              FolderAccumulator(
                path = folderPath,
                name = folderFile.name,
              )
            }

            acc.videoCount++
            acc.totalSize += size
            acc.totalDuration += duration
            if (dateModified > acc.lastModified) {
              acc.lastModified = dateModified
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error querying MediaStore for folders", e)
      }

      // 2. Check external storage volumes (SD cards, USB OTG) that MediaStore might miss
      try {
        val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
        for (volume in externalVolumes) {
          val volumePath = StorageVolumeUtils.getVolumePath(volume) ?: continue
          val volumeDir = File(volumePath)
          if (volumeDir.exists() && volumeDir.canRead()) {
            scanExternalVolumeShallow(volumeDir, foldersMap, maxDepth = 4)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning external volumes", e)
      }

      val result = foldersMap.values.map { acc ->
        VideoFolder(
          bucketId = acc.path,
          name = acc.name,
          path = acc.path,
          videoCount = acc.videoCount,
          totalSize = acc.totalSize,
          totalDuration = acc.totalDuration,
          lastModified = acc.lastModified,
        )
      }.sortedBy { it.name.lowercase(Locale.getDefault()) }

      val directCounts = mutableMapOf<String, Int>()
      val recursiveCounts = mutableMapOf<String, Int>()
      val recursiveSizes = mutableMapOf<String, Long>()
      val recursiveDurations = mutableMapOf<String, Long>()

      for ((folderPath, acc) in foldersMap) {
        directCounts[folderPath] = acc.videoCount

        var current: File? = File(folderPath)
        while (current != null) {
          val p = current.absolutePath
          recursiveCounts[p] = (recursiveCounts[p] ?: 0) + acc.videoCount
          recursiveSizes[p] = (recursiveSizes[p] ?: 0L) + acc.totalSize
          recursiveDurations[p] = (recursiveDurations[p] ?: 0L) + acc.totalDuration
          current = current.parentFile
        }
      }

      cachedFolders = result
      cachedFolderCounts = directCounts
      cachedRecursiveCounts = recursiveCounts
      cachedRecursiveSizes = recursiveSizes
      cachedRecursiveDurations = recursiveDurations
      cacheTimestamp = now

      result
    }
  }

  /**
   * Shallow recursive scan for external volumes (limits depth to prevent stutter).
   */
  private fun scanExternalVolumeShallow(
    dir: File,
    foldersMap: MutableMap<String, FolderAccumulator>,
    maxDepth: Int,
    currentDepth: Int = 0,
  ) {
    if (currentDepth >= maxDepth || !dir.exists() || !dir.canRead() || !dir.isDirectory) return
    if (FileFilterUtils.shouldSkipFolder(dir)) return

    val children = dir.listFiles() ?: return
    val subdirs = mutableListOf<File>()
    var videoCount = 0
    var totalSize = 0L
    var lastModified = 0L

    for (child in children) {
      if (child.isDirectory) {
        if (!FileFilterUtils.shouldSkipFolder(child)) {
          subdirs.add(child)
        }
      } else if (child.isFile) {
        if (FileTypeUtils.isVideoFile(child)) {
          videoCount++
          totalSize += child.length()
          val mod = child.lastModified() / 1000
          if (mod > lastModified) lastModified = mod
        }
      }
    }

    if (videoCount > 0 && !foldersMap.containsKey(dir.absolutePath)) {
      foldersMap[dir.absolutePath] = FolderAccumulator(
        path = dir.absolutePath,
        name = dir.name,
        videoCount = videoCount,
        totalSize = totalSize,
        totalDuration = 0L,
        lastModified = lastModified,
        hasSubfolders = subdirs.isNotEmpty(),
      )
    }

    for (subdir in subdirs) {
      scanExternalVolumeShallow(subdir, foldersMap, maxDepth, currentDepth + 1)
    }
  }

  suspend fun getAllVideoFoldersFast(
    context: Context,
    onProgress: ((Int) -> Unit)? = null,
  ): List<VideoFolder> = getAllVideoFolders(context)

  suspend fun enrichVideoFolders(
    context: Context,
    folders: List<VideoFolder>,
    onProgress: ((Int, Int) -> Unit)? = null,
  ): List<VideoFolder> = folders

  // =============================================================================
  // VIDEO FILE OPERATIONS
  // =============================================================================

  suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      try {
        VideoScanUtils.getVideosInFolder(context, bucketId)
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for folder $bucketId", e)
        emptyList()
      }
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

  suspend fun getVideosFromFiles(
    context: Context,
    files: List<File>,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      files.mapNotNull { file ->
        try {
          val folderPath = file.parent ?: ""
          val folderName = file.parentFile?.name ?: ""
          createVideoFromFile(context, file, folderPath, folderName)
        } catch (e: Exception) {
          Log.w(TAG, "Error creating video from file: ${file.absolutePath}", e)
          null
        }
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

    val extension = file.extension.lowercase(Locale.getDefault())
    val mimeType = FileTypeUtils.getMimeTypeFromExtension(extension)
    val uri = Uri.fromFile(file)

    var size = file.length()
    var duration = 0L
    var width = 0
    var height = 0
    var fps = 0f
    var hasEmbeddedSubtitles = false
    var subtitleCodec = ""

    MediaInfoOps.extractBasicMetadata(context, uri, displayName).onSuccess { metadata ->
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

  // =============================================================================
  // FILE SYSTEM BROWSING (Directory / Tree View)
  // =============================================================================

  fun getDefaultRootPath(): String = Environment.getExternalStorageDirectory().absolutePath

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
   * Scans a specific directory and returns its immediate child folders and video files.
   * Runs instantly without scanning the rest of the device.
   */
  suspend fun scanDirectory(
    context: Context,
    path: String,
    showAllFileTypes: Boolean = false,
    useFastCount: Boolean = false,
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

        // Ensure index is populated for fast recursive counts and filtering
        if (cachedFolders == null) {
          getAllVideoFolders(context)
        }

        val items = mutableListOf<FileSystemItem>()

        // 1. Direct subdirectories that contain video files (only show folders with videos)
        val childFiles = directory.listFiles() ?: emptyArray()
        for (file in childFiles) {
          if (file.isDirectory && !FileFilterUtils.shouldSkipFolder(file)) {
            val subfolderPath = file.absolutePath

            // Recursive count from index
            var recursiveCount = cachedRecursiveCounts[subfolderPath] ?: 0

            // Fallback for unindexed storage (e.g. OTG/new file not yet indexed by MediaStore)
            if (recursiveCount == 0) {
              val directVideoCount = file.listFiles { f -> f.isFile && FileTypeUtils.isVideoFile(f) }?.size ?: 0
              if (directVideoCount > 0) {
                recursiveCount = directVideoCount
              }
            }

            // In tree view, ONLY show folders which contain video files
            if (recursiveCount <= 0) {
              continue
            }

            // Check if this subfolder has child subdirectories that also contain videos
            val hasSubfoldersWithVideos = file.listFiles()?.any { child ->
              child.isDirectory && !FileFilterUtils.shouldSkipFolder(child) &&
                ((cachedRecursiveCounts[child.absolutePath] ?: 0) > 0 ||
                  child.listFiles { f -> f.isFile && FileTypeUtils.isVideoFile(f) }?.isNotEmpty() == true)
            } == true

            val totalSize = cachedRecursiveSizes[subfolderPath] ?: 0L
            val totalDuration = cachedRecursiveDurations[subfolderPath] ?: 0L

            items.add(
              FileSystemItem.Folder(
                name = file.name,
                path = subfolderPath,
                lastModified = file.lastModified(),
                videoCount = recursiveCount,
                totalSize = totalSize,
                totalDuration = totalDuration,
                hasSubfolders = hasSubfoldersWithVideos,
              ),
            )
          }
        }

        // Sort folders alphabetically
        items.sortBy { it.name.lowercase(Locale.getDefault()) }

        // 2. Videos in current directory
        val videos = VideoScanUtils.getVideosInFolder(context, path)
        for (video in videos) {
          items.add(
            FileSystemItem.VideoFile(
              name = video.displayName,
              path = video.path,
              lastModified = File(video.path).lastModified(),
              video = video,
            ),
          )
        }

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
   * Gets all storage volume roots.
   */
  suspend fun getStorageRoots(context: Context): List<FileSystemItem.Folder> =
    withContext(Dispatchers.IO) {
      if (cachedFolders == null) {
        getAllVideoFolders(context)
      }
      val roots = mutableListOf<FileSystemItem.Folder>()

      try {
        // Primary internal storage
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage.exists() && primaryStorage.canRead()) {
          val primaryPath = primaryStorage.absolutePath
          val primaryCount = cachedRecursiveCounts[primaryPath] ?: cachedFolderCounts.values.sum()
          roots.add(
            FileSystemItem.Folder(
              name = "Internal Storage",
              path = primaryPath,
              lastModified = primaryStorage.lastModified(),
              videoCount = primaryCount,
              totalSize = cachedRecursiveSizes[primaryPath] ?: 0L,
              totalDuration = cachedRecursiveDurations[primaryPath] ?: 0L,
              hasSubfolders = true,
            ),
          )
        }

        // External volumes (SD cards, USB OTG)
        val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
        for (volume in externalVolumes) {
          val volumePath = StorageVolumeUtils.getVolumePath(volume)
          if (volumePath != null) {
            val volumeDir = File(volumePath)
            if (volumeDir.exists() && volumeDir.canRead()) {
              val volumeName = volume.getDescription(context)
              val volumeCount = cachedRecursiveCounts[volumeDir.absolutePath] ?: 0
              roots.add(
                FileSystemItem.Folder(
                  name = volumeName,
                  path = volumeDir.absolutePath,
                  lastModified = volumeDir.lastModified(),
                  videoCount = volumeCount,
                  totalSize = cachedRecursiveSizes[volumeDir.absolutePath] ?: 0L,
                  totalDuration = cachedRecursiveDurations[volumeDir.absolutePath] ?: 0L,
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

  // =============================================================================
  // FORMATTING UTILITIES (Forwarded to MediaFormatUtils)
  // =============================================================================

  fun formatDuration(durationMs: Long): String = MediaFormatUtils.formatDuration(durationMs)
  fun formatFileSize(bytes: Long): String = MediaFormatUtils.formatFileSize(bytes)
  fun formatResolution(width: Int, height: Int): String = MediaFormatUtils.formatResolution(width, height)
  fun formatResolutionWithFps(width: Int, height: Int, fps: Float): String =
    MediaFormatUtils.formatResolutionWithFps(width, height, fps)
}
