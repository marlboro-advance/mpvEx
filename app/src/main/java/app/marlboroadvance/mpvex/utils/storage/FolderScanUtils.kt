package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import java.io.File
import java.util.Locale

/**
 * Unified utility for scanning folders and analyzing video content
 * Consolidates logic from FolderListViewModel, FileSystemRepository, and FoldersPreferencesScreen
 */
object FolderScanUtils {
  private const val TAG = "FolderScanUtils"

  /**
   * Data class representing folder information during scanning
   */
  data class FolderData(
    val path: String,
    val name: String,
    val videoCount: Int,
    val totalSize: Long,
    val totalDuration: Long,
    val lastModified: Long,
    val hasSubfolders: Boolean = false,
  )

  /**
   * Gets all storage roots (internal + external SD/OTG)
   */
  fun getStorageRoots(context: Context): List<File> {
    val roots = mutableListOf<File>()

    // Primary storage
    val primaryStorage = Environment.getExternalStorageDirectory()
    if (primaryStorage.exists() && primaryStorage.canRead()) {
      roots.add(primaryStorage)
    }

    // External volumes (SD cards, USB OTG)
    val externalVolumes = StorageScanUtils.getExternalStorageVolumes(context)
    for (volume in externalVolumes) {
      val volumePath = StorageScanUtils.getVolumePath(volume)
      if (volumePath != null) {
        val volumeDir = File(volumePath)
        if (volumeDir.exists() && volumeDir.canRead()) {
          roots.add(volumeDir)
        }
      }
    }

    return roots
  }

  /**
   * Scans all storage volumes recursively to find all folders containing videos
   * This is the main unified scanning function
   *
   * @param context Application context
   * @param showHiddenFiles Whether to show hidden files/folders
   * @param metadataCache Cache for video metadata
   * @param maxDepth Maximum recursion depth (default 20)
   * @return Map of folder paths to FolderData
   */
  suspend fun scanAllStorageForVideoFolders(
    context: Context,
    showHiddenFiles: Boolean,
    metadataCache: VideoMetadataCacheRepository,
    maxDepth: Int = 20,
  ): Map<String, FolderData> {
    val folders = mutableMapOf<String, FolderData>()
    val storageRoots = getStorageRoots(context)

    Log.d(TAG, "Scanning ${storageRoots.size} storage volumes for video folders")

    for (root in storageRoots) {
      scanDirectoryRecursively(
        directory = root,
        folders = folders,
        showHiddenFiles = showHiddenFiles,
        metadataCache = metadataCache,
        maxDepth = maxDepth,
        currentDepth = 0,
      )
    }

    Log.d(TAG, "Found ${folders.size} folders containing videos")
    return folders
  }

  /**
   * Recursively scans a directory for folders containing videos
   * Only adds folders that directly contain video files
   *
   * @param directory Directory to scan
   * @param folders Output map to populate
   * @param showHiddenFiles Whether to show hidden files/folders
   * @param metadataCache Cache for extracting video metadata
   * @param maxDepth Maximum recursion depth
   * @param currentDepth Current recursion depth
   */
  suspend fun scanDirectoryRecursively(
    directory: File,
    folders: MutableMap<String, FolderData>,
    showHiddenFiles: Boolean,
    metadataCache: VideoMetadataCacheRepository,
    maxDepth: Int = 20,
    currentDepth: Int = 0,
  ) {
    if (currentDepth >= maxDepth) return
    if (!directory.exists() || !directory.canRead() || !directory.isDirectory) return

    try {
      val files = directory.listFiles() ?: return

      // Separate files into videos and subdirectories
      val videoFiles = mutableListOf<File>()
      val subdirectories = mutableListOf<File>()

      for (file in files) {
        when {
          // Skip hidden files/folders if needed
          !showHiddenFiles && file.name.startsWith(".") -> continue

          // Skip system folders and folders with .nomedia
          file.isDirectory && StorageScanUtils.shouldSkipFolder(file, showHiddenFiles) -> continue

          // Collect video files
          file.isFile && StorageScanUtils.isVideoFile(file) -> {
            videoFiles.add(file)
          }

          // Collect subdirectories
          file.isDirectory -> {
            subdirectories.add(file)
          }
        }
      }

      // If this directory contains videos, add it to the list
      if (videoFiles.isNotEmpty()) {
        val folderPath = directory.absolutePath
        val folderName = directory.name
        val totalSize = videoFiles.sumOf { it.length() }
        val lastModified = videoFiles.maxOfOrNull { it.lastModified() }?.div(1000) ?: 0L

        // Calculate total duration by loading video metadata
        var totalDuration = 0L
        for (videoFile in videoFiles) {
          try {
            val uri = Uri.fromFile(videoFile)
            val metadata = metadataCache.getOrExtractMetadata(videoFile, uri, videoFile.name)
            if (metadata != null && metadata.durationMs > 0) {
              totalDuration += metadata.durationMs
            }
          } catch (e: Exception) {
            Log.w(TAG, "Failed to extract duration for ${videoFile.name}", e)
          }
        }

        folders[folderPath] = FolderData(
          path = folderPath,
          name = folderName,
          videoCount = videoFiles.size,
          totalSize = totalSize,
          totalDuration = totalDuration,
          lastModified = lastModified,
          hasSubfolders = subdirectories.isNotEmpty(),
        )

        Log.d(TAG, "Found folder with videos: $folderPath (${videoFiles.size} videos)")
      }

      // Recursively scan subdirectories
      for (subdir in subdirectories) {
        scanDirectoryRecursively(
          directory = subdir,
          folders = folders,
          showHiddenFiles = showHiddenFiles,
          metadataCache = metadataCache,
          maxDepth = maxDepth,
          currentDepth = currentDepth + 1,
        )
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "Permission denied scanning: ${directory.absolutePath}", e)
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
    }
  }

  /**
   * Converts FolderData map to sorted VideoFolder list
   */
  fun convertToVideoFolders(folders: Map<String, FolderData>): List<VideoFolder> {
    return folders.values.map { folderData ->
      VideoFolder(
        bucketId = folderData.path,
        name = folderData.name,
        path = folderData.path,
        videoCount = folderData.videoCount,
        totalSize = folderData.totalSize,
        totalDuration = folderData.totalDuration,
        lastModified = folderData.lastModified,
      )
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }
  }

  /**
   * Counts files recursively in a folder hierarchy
   * Used by FileSystemRepository for folder counting
   *
   * @param folder Folder to analyze
   * @param showHiddenFiles Whether to show hidden files/folders
   * @param showAllFileTypes If true, counts all files. If false, counts only videos.
   * @param maxDepth Maximum recursion depth
   * @param currentDepth Current recursion depth
   * @return FolderData with counts
   */
  fun countFilesRecursive(
    folder: File,
    showHiddenFiles: Boolean,
    showAllFileTypes: Boolean = false,
    maxDepth: Int = 10,
    currentDepth: Int = 0,
  ): FolderData {
    if (currentDepth >= maxDepth) {
      return FolderData(
        path = folder.absolutePath,
        name = folder.name,
        videoCount = 0,
        totalSize = 0L,
        totalDuration = 0L,
        lastModified = 0L,
        hasSubfolders = false,
      )
    }

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
            file.isDirectory && StorageScanUtils.shouldSkipFolder(file, showHiddenFiles) -> continue

            // Count files
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

            // Recursively count subdirectories
            file.isDirectory -> {
              hasSubfolders = true
              val subInfo = countFilesRecursive(
                folder = file,
                showHiddenFiles = showHiddenFiles,
                showAllFileTypes = showAllFileTypes,
                maxDepth = maxDepth,
                currentDepth = currentDepth + 1,
              )
              videoCount += subInfo.videoCount
              totalSize += subInfo.totalSize
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error counting files in: ${folder.absolutePath}", e)
    }

    return FolderData(
      path = folder.absolutePath,
      name = folder.name,
      videoCount = videoCount,
      totalSize = totalSize,
      totalDuration = 0L, // Duration not calculated in counting mode
      lastModified = folder.lastModified() / 1000,
      hasSubfolders = hasSubfolders,
    )
  }

  /**
   * Gets direct children count for a folder (not recursive)
   * Includes recursive subfolder analysis up to maxDepth
   */
  fun getDirectChildrenCount(
    folder: File,
    showHiddenFiles: Boolean,
    showAllFileTypes: Boolean = false,
  ): FolderData {
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
            file.isDirectory && StorageScanUtils.shouldSkipFolder(file, showHiddenFiles) -> continue

            // Count subdirectories
            file.isDirectory -> {
              hasSubfolders = true
              // Recursively count files in subfolders (up to depth 10)
              val subInfo = countFilesRecursive(
                folder = file,
                showHiddenFiles = showHiddenFiles,
                showAllFileTypes = showAllFileTypes,
                maxDepth = 10,
                currentDepth = 0,
              )
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

    return FolderData(
      path = folder.absolutePath,
      name = folder.name,
      videoCount = videoCount,
      totalSize = totalSize,
      totalDuration = 0L,
      lastModified = folder.lastModified() / 1000,
      hasSubfolders = hasSubfolders,
    )
  }
}
