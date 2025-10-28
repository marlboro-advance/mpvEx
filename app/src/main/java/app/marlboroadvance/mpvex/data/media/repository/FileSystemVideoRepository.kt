@file:Suppress("DEPRECATION")

package app.marlboroadvance.mpvex.data.media.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.dao.VideoIndexDao
import app.marlboroadvance.mpvex.database.entities.VideoIndexEntity
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * File-system based video repository with database caching.
 * Requires MANAGE_EXTERNAL_STORAGE permission (All Files Access).
 * Does NOT depend on MediaStore.
 *
 * Uses Room database for fast subsequent loads:
 * - First scan: Indexes all videos to database (slow)
 * - Subsequent scans: Loads from database and only scans for changes (fast)
 */
class FileSystemVideoRepository(
  private val database: MpvExDatabase,
  private val advancedPreferences: AdvancedPreferences,
) {
  data class IndexingProgress(
    val isIndexing: Boolean = false,
    val currentFile: String = "",
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val phase: IndexingPhase = IndexingPhase.IDLE,
    val isInitialIndex: Boolean = false,
  )

  enum class IndexingPhase {
    IDLE,
    SCANNING_FILES, // Collecting file paths
    EXTRACTING_METADATA, // Extracting video metadata
    SAVING_TO_DB, // Batch saving to database
    COMPLETE,
  }

  private val _indexingProgress = MutableStateFlow(IndexingProgress())
  val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()

  companion object {
    private const val TAG = "FileSystemVideoRepository"

    // Video file extensions to scan for
    private val VIDEO_EXTENSIONS =
      setOf(
        "mp4",
        "mkv",
        "avi",
        "mov",
        "wmv",
        "flv",
        "webm",
        "m4v",
        "mpg",
        "mpeg",
        "3gp",
        "3g2",
        "ts",
        "m2ts",
        "mts",
        "vob",
        "ogv",
        "m4p",
        "qt",
        "yuv",
        "rm",
        "rmvb",
        "asf",
        "amv",
        "mp2",
        "mpe",
        "mpv",
        "m2v",
        "svi",
        "mxf",
        "roq",
        "nsv",
        "f4v",
        "f4p",
        "f4a",
        "f4b",
      )

    // MIME type mappings
    private val EXTENSION_TO_MIME =
      mapOf(
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "webm" to "video/webm",
        "m4v" to "video/x-m4v",
        "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg",
        "3gp" to "video/3gpp",
        "3g2" to "video/3gpp2",
        "ts" to "video/mp2ts",
        "m2ts" to "video/mp2t",
        "mts" to "video/mp2t",
        "vob" to "video/dvd",
      )

    // Directories to exclude from scanning
    private val EXCLUDED_DIRS =
      setOf(
        ".",
        "..",
        ".thumbnails",
        ".cache",
        ".trash",
        ".nomedia",
        "Android/data",
        "Android/obb",
      )

    // Check if a file is a video file based on extension
    fun isVideoFile(file: File): Boolean {
      val extension = file.extension.lowercase(Locale.getDefault())
      return VIDEO_EXTENSIONS.contains(extension)
    }

    // Check if a directory should be skipped
    fun shouldSkipDirectory(directory: File): Boolean {
      val name = directory.name

      // Skip hidden directories
      if (name.startsWith(".")) return true

      // Skip excluded directories
      if (EXCLUDED_DIRS.contains(name)) return true

      // Skip Android system directories
      val path = directory.absolutePath
      return path.contains("/Android/data") || path.contains("/Android/obb")
    }

    // Get root directories to scan
    fun getRootDirectories(context: Context): List<File> {
      val roots = mutableListOf<File>()

      // Primary external storage
      val externalStorage = Environment.getExternalStorageDirectory()
      if (externalStorage.exists()) {
        roots.add(externalStorage)
      }

      // Try to get secondary storage (SD card, etc.)
      try {
        val secondaryStorages = System.getenv("SECONDARY_STORAGE")?.split(":")
        secondaryStorages?.forEach { path ->
          val file = File(path)
          if (file.exists() && file.canRead()) {
            roots.add(file)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not get secondary storage paths", e)
      }

      // API 30+: reliable volume directories via StorageManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
          val sm = context.getSystemService(StorageManager::class.java)
          sm?.storageVolumes?.forEach { vol ->
            val dir = vol.directory
            if (dir != null && dir.exists() && dir.canRead()) {
              val name = dir.name.lowercase(Locale.getDefault())
              val skip = name in setOf("emulated", "self", "enc_emulated")
              if (!skip) roots.add(dir)
            }
          }
        }.onFailure { e -> Log.w(TAG, "Could not get storage volumes from StorageManager", e) }
      } else {
        // Pre-30: discover mounted volumes under /storage (e.g., /storage/XXXX-XXXX)
        try {
          val storageRoot = File("/storage")
          if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { vol ->
              // Skip virtual or internal wrappers
              val name = vol.name.lowercase(Locale.getDefault())
              val skip = name in setOf("emulated", "self", "enc_emulated")
              if (!skip && vol.isDirectory && vol.canRead()) {
                roots.add(vol)
              }
            }
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not enumerate /storage volumes", e)
        }
      }

      // De-duplicate by absolute path
      return roots
        .distinctBy { file -> runCatching { file.canonicalPath }.getOrElse { file.absolutePath } }
        .toList()
    }

    // Extract video duration using MediaMetadataRetriever
    fun extractVideoDuration(filePath: String): Long =
      try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        durationStr?.toLongOrNull() ?: 0L
      } catch (e: Exception) {
        Log.w(TAG, "Could not extract duration for: $filePath", e)
        0L
      }

    // Format duration in milliseconds to human-readable string
    fun formatDuration(durationMs: Long): String {
      if (durationMs <= 0) return "Unknown"

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

    // Format file size to human-readable string
    fun formatFileSize(bytes: Long): String {
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

  /**
   * Get videos by bucket ID from database
   */
  suspend fun getVideosByBucketId(
    context: Context,
    bucketId: String,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val dao = database.videoIndexDao()
      val entities = dao.getByBucketId(bucketId)

      entities
        .map { entity ->
          entityToVideo(entity)
        }.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }

  /**
   * Get videos for multiple bucket IDs from database
   */
  suspend fun getVideosForBuckets(
    context: Context,
    bucketIds: Set<String>,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      val dao = database.videoIndexDao()
      val result = mutableListOf<Video>()

      for (bucketId in bucketIds) {
        val entities = dao.getByBucketId(bucketId)
        result += entities.map { entityToVideo(it) }
      }

      result
    }

  /**
   * Update database cache for specific files (after copy/move operations)
   * This is faster than full rescan - only indexes the new/modified files
   */
  suspend fun updateCacheForFiles(
    context: Context,
    filePaths: List<String>,
  ) = withContext(Dispatchers.IO) {
    if (filePaths.isEmpty()) return@withContext

    Log.d(TAG, "Updating cache for ${filePaths.size} files")
    val dao = database.videoIndexDao()

    val videosToUpdate = mutableListOf<VideoIndexEntity>()

    for (filePath in filePaths) {
      val file = File(filePath)
      if (file.exists() && isVideoFile(file)) {
        createVideoIndexEntity(file)?.let { videosToUpdate.add(it) }
      }
    }

    if (videosToUpdate.isNotEmpty()) {
      dao.insertAll(videosToUpdate)
      Log.d(TAG, "Updated ${videosToUpdate.size} entries in cache")
      MediaLibraryEvents.notifyChanged()
    }
  }

  /**
   * Remove files from database cache (after delete operations)
   */
  suspend fun removeCacheForFiles(
    context: Context,
    filePaths: List<String>,
  ) = withContext(Dispatchers.IO) {
    if (filePaths.isEmpty()) return@withContext

    Log.d(TAG, "Removing ${filePaths.size} files from cache")
    val dao = database.videoIndexDao()
    dao.deleteByPaths(filePaths)
    MediaLibraryEvents.notifyChanged()
  }

  /**
   * Perform full file system scan and index to database
   */
  private suspend fun performFullScan(
    context: Context,
    dao: VideoIndexDao,
  ) {
    Log.d(TAG, "Starting full file system scan")
    val startTime = System.currentTimeMillis()

    try {
      // Phase 1: Scan for files
      _indexingProgress.value =
        IndexingProgress(
          isIndexing = true,
          phase = IndexingPhase.SCANNING_FILES,
          isInitialIndex = true,
        )

      val allFilePaths = mutableListOf<String>()
      val rootDirs = getRootDirectories(context)

      for (rootDir in rootDirs) {
        if (rootDir.exists() && rootDir.canRead()) {
          Log.d(TAG, "Scanning root directory: ${rootDir.absolutePath}")
          collectVideoFiles(rootDir, allFilePaths)
        }
      }

      val totalFiles = allFilePaths.size
      Log.d(TAG, "Found $totalFiles video files to index")

      // Phase 2: Extract metadata
      _indexingProgress.value =
        _indexingProgress.value.copy(
          phase = IndexingPhase.EXTRACTING_METADATA,
          totalFiles = totalFiles,
        )

      val videosToIndex = mutableListOf<VideoIndexEntity>()

      allFilePaths.forEachIndexed { index, filePath ->
        val file = File(filePath)
        createVideoIndexEntity(file)?.let { entity ->
          videosToIndex.add(entity)

          // Update progress every 10 files to avoid too many updates
          if (index % 10 == 0 || index == totalFiles - 1) {
            _indexingProgress.value =
              _indexingProgress.value.copy(
                currentFile = file.name,
                processedFiles = index + 1,
              )
          }
        }
      }

      // Phase 3: Save to database
      _indexingProgress.value =
        _indexingProgress.value.copy(
          phase = IndexingPhase.SAVING_TO_DB,
          currentFile = "",
        )

      if (videosToIndex.isNotEmpty()) {
        Log.d(TAG, "Indexing ${videosToIndex.size} videos to database")
        dao.insertAll(videosToIndex)
      }

      // Complete
      _indexingProgress.value =
        _indexingProgress.value.copy(
          phase = IndexingPhase.COMPLETE,
        )

      val elapsed = System.currentTimeMillis() - startTime
      Log.d(TAG, "Full scan complete: ${videosToIndex.size} videos indexed in ${elapsed}ms")
    } finally {
      // Keep dialog open (isIndexing = true) until UI confirms folders are rendered
      // via markInitialIndexApplied()
    }
  }

  /**
   * Perform incremental scan - check for new/modified/deleted files
   */
  private suspend fun performIncrementalScan(
    context: Context,
    dao: VideoIndexDao,
  ) {
    Log.d(TAG, "Starting incremental scan")
    val startTime = System.currentTimeMillis()

    val currentFiles = mutableSetOf<String>()
    val newVideos = mutableListOf<VideoIndexEntity>()
    val modifiedVideos = mutableListOf<VideoIndexEntity>()

    val rootDirs = getRootDirectories(context)
    val maxDepth = advancedPreferences.folderScanDepth.get().coerceAtLeast(0)

    // Scan file system quickly (without extracting metadata)
    for (rootDir in rootDirs) {
      if (rootDir.exists() && rootDir.canRead()) {
        scanDirectoryQuick(rootDir, currentFiles, 0, maxDepth)
      }
    }

    // Get cached entries
    val cachedEntries = dao.getAll().associateBy { it.path }

    // Find new and modified files
    for (filePath in currentFiles) {
      val cached = cachedEntries[filePath]
      val file = File(filePath)
      val fileLastModified = file.lastModified() / 1000

      if (cached == null) {
        // New file - need to index it
        createVideoIndexEntity(file)?.let { newVideos.add(it) }
      } else if (cached.lastModified != fileLastModified || cached.size != file.length()) {
        // File modified - re-index it
        createVideoIndexEntity(file)?.let { modifiedVideos.add(it) }
      }
    }

    // Find deleted files
    val deletedPaths = cachedEntries.keys.filter { it !in currentFiles }

    // Update database
    if (newVideos.isNotEmpty()) {
      Log.d(TAG, "Adding ${newVideos.size} new videos to index")
      dao.insertAll(newVideos)
    }

    if (modifiedVideos.isNotEmpty()) {
      Log.d(TAG, "Updating ${modifiedVideos.size} modified videos")
      dao.insertAll(modifiedVideos)
    }

    if (deletedPaths.isNotEmpty()) {
      Log.d(TAG, "Removing ${deletedPaths.size} deleted videos from index")
      dao.deleteByPaths(deletedPaths)
    }

    val elapsed = System.currentTimeMillis() - startTime
    Log.d(
      TAG,
      "Incremental scan complete in ${elapsed}ms (${newVideos.size} new, ${modifiedVideos.size} modified, ${deletedPaths.size} deleted)",
    )
  }

  /**
   * Quick scan to collect file paths without extracting metadata
   */
  private fun scanDirectoryQuick(
    directory: File,
    filePaths: MutableSet<String>,
    depth: Int,
    maxDepth: Int,
  ) {
    try {
      if (depth > maxDepth) return
      if (shouldSkipDirectory(directory)) return

      val files = directory.listFiles() ?: return

      for (file in files) {
        try {
          when {
            file.isDirectory -> scanDirectoryQuick(file, filePaths, depth + 1, maxDepth)
            file.isFile && isVideoFile(file) -> filePaths.add(file.absolutePath)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Error scanning file: ${file.absolutePath}", e)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
    }
  }

  /**
   * Collect all video file paths without extracting metadata (fast)
   */
  private fun collectVideoFiles(
    directory: File,
    filePaths: MutableList<String>,
  ) {
    collectVideoFiles(directory, filePaths, 0, advancedPreferences.folderScanDepth.get().coerceAtLeast(0))
  }

  private fun collectVideoFiles(
    directory: File,
    filePaths: MutableList<String>,
    depth: Int,
    maxDepth: Int,
  ) {
    try {
      if (depth > maxDepth) return
      if (shouldSkipDirectory(directory)) return

      val files = directory.listFiles() ?: return

      for (file in files) {
        try {
          when {
            file.isDirectory -> collectVideoFiles(file, filePaths, depth + 1, maxDepth)
            file.isFile && isVideoFile(file) -> filePaths.add(file.absolutePath)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Error collecting file: ${file.absolutePath}", e)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
    }
  }

  /**
   * Create VideoIndexEntity from a file
   */
  private fun createVideoIndexEntity(file: File): VideoIndexEntity? =
    try {
      val extension = file.extension.lowercase(Locale.getDefault())
      val mimeType = EXTENSION_TO_MIME[extension] ?: "video/*"
      val parent = file.parentFile
      val bucketId = parent?.absolutePath?.hashCode()?.toString() ?: "0"
      val bucketDisplayName = parent?.name ?: "Unknown"
      val duration = extractVideoDuration(file.absolutePath)

      VideoIndexEntity(
        path = file.absolutePath,
        displayName = file.name,
        title = file.nameWithoutExtension,
        size = file.length(),
        duration = duration,
        dateModified = file.lastModified() / 1000,
        dateAdded = file.lastModified() / 1000,
        lastModified = file.lastModified() / 1000,
        mimeType = mimeType,
        bucketId = bucketId,
        bucketDisplayName = bucketDisplayName,
        lastIndexed = System.currentTimeMillis(),
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error creating index entity for: ${file.absolutePath}", e)
      null
    }

  /**
   * Convert VideoIndexEntity to Video domain model
   */
  private fun entityToVideo(entity: VideoIndexEntity): Video {
    val uri = Uri.fromFile(File(entity.path))

    return Video(
      id = entity.path.hashCode().toLong(),
      title = entity.title,
      displayName = entity.displayName,
      path = entity.path,
      uri = uri,
      duration = entity.duration,
      durationFormatted = formatDuration(entity.duration),
      size = entity.size,
      sizeFormatted = formatFileSize(entity.size),
      dateModified = entity.dateModified,
      dateAdded = entity.dateAdded,
      mimeType = entity.mimeType,
      bucketId = entity.bucketId,
      bucketDisplayName = entity.bucketDisplayName,
    )
  }

  /**
   * Return folders from cache instantly without triggering a scan.
   */
  suspend fun getCachedFolders(context: Context): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      val dao = database.videoIndexDao()
      val buckets = runCatching { dao.getAllBuckets() }.getOrDefault(emptyList())
      buckets
        .map { bucketInfo ->
          val videoCount = runCatching { dao.getVideoCountForBucket(bucketInfo.bucketId) }.getOrDefault(0)
          val totalSize = runCatching { dao.getTotalSizeForBucket(bucketInfo.bucketId) }.getOrNull() ?: 0L
          val totalDuration = runCatching { dao.getTotalDurationForBucket(bucketInfo.bucketId) }.getOrNull() ?: 0L
          val lastModified = runCatching { dao.getLastModifiedForBucket(bucketInfo.bucketId) }.getOrNull() ?: 0L

          val cachedVideos = runCatching { dao.getByBucketId(bucketInfo.bucketId) }.getOrDefault(emptyList())
          val folderPath = cachedVideos.firstOrNull()?.let { File(it.path).parent } ?: ""

          VideoFolder(
            bucketId = bucketInfo.bucketId,
            name = bucketInfo.bucketDisplayName,
            path = folderPath,
            videoCount = videoCount,
            totalSize = totalSize,
            totalDuration = totalDuration,
            lastModified = lastModified,
          )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

  /**
   * Trigger index update (full or incremental). Callers can refresh cached folders after this.
   */
  suspend fun runIndexUpdate(context: Context) =
    withContext(Dispatchers.IO) {
      if (isScanning) return@withContext
      isScanning = true
      try {
        val dao = database.videoIndexDao()
        val cachedCount = dao.getAll().size
        if (cachedCount == 0) {
          performFullScan(context, dao)
        } else {
          performIncrementalScan(context, dao)
        }
        // Notify once per completed run
        MediaLibraryEvents.notifyChanged()
      } finally {
        isScanning = false
      }
    }

  /**
   * Call this after the UI has applied the initial index (folders shown)
   * to hide the indexing dialog.
   */
  fun markInitialIndexApplied() {
    val current = _indexingProgress.value
    if (current.isInitialIndex) {
      _indexingProgress.value =
        current.copy(
          isIndexing = false,
          isInitialIndex = false,
          phase = IndexingPhase.IDLE,
          currentFile = "",
          processedFiles = 0,
          totalFiles = 0,
        )
    }
  }

  @Volatile
  private var isScanning: Boolean = false
}
