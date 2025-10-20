package app.marlboroadvance.mpvex.ui.home.data.repository

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.ui.home.data.model.VideoFolder
import app.marlboroadvance.mpvex.ui.utils.TVUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object VideoFolderRepository {

  private const val tag = "VideoFolderRepository"

  fun getVideoFolders(context: Context): List<VideoFolder> {
    Log.d(tag, "Starting video folder scan")
    val folders = mutableMapOf<String, VideoFolderInfo>()
    val isTV = TVUtils.isAndroidTV(context)

    val projection = getProjection()
    val cursor: Cursor? = context.contentResolver.query(
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      projection,
      null,
      null,
      "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
    )

    cursor?.use { c ->
      processFolderCursor(c, folders)
    }

    // If no folders found and we're on Android TV, scan file system directly
    if (folders.isEmpty() && isTV) {
      Log.d(tag, "No folders found via MediaStore on Android TV, scanning file system")
      scanCommonTVDirectories(folders)
    }

    // On Android TV, always scan for external USB/SD mounts even if MediaStore found something
    // This ensures USB drives are detected even if internal storage has videos
    if (isTV) {
      Log.d(tag, "Android TV detected, scanning for external USB/SD mounts")
      scanExternalMounts(folders)
    }

    Log.d(tag, "Finished video folder scan")
    val result = convertToVideoFolderList(folders)
    Log.d(tag, "Found ${result.size} folders")
    return result
  }

  private fun normalizePath(path: String): String {
    // Normalize paths to avoid duplicates
    return when {
      path.startsWith("/sdcard") -> path.replace("/sdcard", TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT)
      else -> {
        try {
          // Try to resolve canonical path to handle symlinks
          File(path).canonicalPath
        } catch (e: Exception) {
          path
        }
      }
    }
  }

  private fun scanCommonTVDirectories(folders: MutableMap<String, VideoFolderInfo>) {
    // Scan root of external storage
    scanDirectory(File(TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT), folders, maxDepth = 2)

    // Scan common paths
    TVUtils.FileSystemPaths.COMMON_VIDEO_DIRECTORIES.forEach { path ->
      val dir = File(path)
      if (dir.exists() && dir.isDirectory) {
        Log.d(tag, "Scanning directory: $path")
        scanDirectory(dir, folders, maxDepth = TVUtils.FileSystemPaths.MAX_SCAN_DEPTH)
      }
    }

    // Scan for external USB/SD card mounts
    val mediaRwDir = File(TVUtils.FileSystemPaths.MEDIA_RW_DIR)
    if (mediaRwDir.exists() && mediaRwDir.isDirectory) {
      mediaRwDir.listFiles()?.forEach { mountPoint ->
        if (mountPoint.isDirectory) {
          Log.d(tag, "Scanning external mount: ${mountPoint.absolutePath}")
          scanDirectory(mountPoint, folders, maxDepth = TVUtils.FileSystemPaths.MAX_SCAN_DEPTH)
        }
      }
    }
  }

  @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
  private fun scanExternalMounts(folders: MutableMap<String, VideoFolderInfo>) {
    val scannedPaths = mutableSetOf<String>()

    // First, try to parse /proc/mounts to find all mounted filesystems
    try {
      val mountsFile = File("/proc/mounts")
      if (mountsFile.exists() && mountsFile.canRead()) {
        Log.d(tag, "Reading /proc/mounts for USB/SD card detection")
        BufferedReader(FileReader(mountsFile)).use { reader ->
          reader.lineSequence().forEach { line ->
            parseMountLine(line, folders, scannedPaths)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error reading /proc/mounts", e)
    }

    // Fallback: Scan common USB mount locations that might not be in /proc/mounts
    TVUtils.FileSystemPaths.COMMON_USB_MOUNT_PATHS.forEach { basePath ->
      try {
        val dir = File(basePath)
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
          // For directories that contain subdirectories (like /mnt/media_rw or /storage)
          dir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory && subDir.canRead()) {
              val normalizedPath = normalizePath(subDir.absolutePath)
              if (!scannedPaths.contains(normalizedPath)) {
                Log.d(tag, "Scanning fallback USB location: ${subDir.absolutePath}")
                scannedPaths.add(normalizedPath)
                scanDirectory(subDir, folders, maxDepth = TVUtils.FileSystemPaths.MAX_SCAN_DEPTH)
              }
            }
          }
        }
      } catch (e: Exception) {
        Log.w(tag, "Error scanning common USB path: $basePath", e)
      }
    }
  }

  private fun parseMountLine(
    line: String,
    folders: MutableMap<String, VideoFolderInfo>,
    scannedPaths: MutableSet<String>,
  ) {
    try {
      // Mount line format: device mountpoint filesystem options ...
      val parts = line.split("\\s+".toRegex())
      if (parts.size < 3) return

      val device = parts[0]
      val mountPoint = parts[1]
      val fsType = parts[2]

      // Check if this looks like a USB/SD mount using TVUtils
      if (TVUtils.isExternalMount(device, mountPoint, fsType)) {
        val mountDir = File(mountPoint)
        if (mountDir.exists() && mountDir.isDirectory && mountDir.canRead()) {
          val normalizedPath = normalizePath(mountPoint)
          if (!scannedPaths.contains(normalizedPath)) {
            Log.d(tag, "Found external mount: $mountPoint (fs: $fsType, device: $device)")
            scannedPaths.add(normalizedPath)
            scanDirectory(mountDir, folders, maxDepth = TVUtils.FileSystemPaths.MAX_SCAN_DEPTH)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "Error parsing mount line: $line", e)
    }
  }

  @Suppress("ReturnCount")
  private fun scanDirectory(
    directory: File,
    folders: MutableMap<String, VideoFolderInfo>,
    currentDepth: Int = 0,
    maxDepth: Int = TVUtils.FileSystemPaths.MAX_SCAN_DEPTH,
  ) {
    if (currentDepth > maxDepth) return
    if (!directory.canRead()) return

    try {
      val files = directory.listFiles() ?: return

      for (file in files) {
        try {
          if (file.isDirectory && currentDepth < maxDepth) {
            scanDirectory(file, folders, currentDepth + 1, maxDepth)
          } else if (file.isFile && isVideoFile(file)) {
            val folderPath = normalizePath(file.parent ?: continue)
            val bucketId = folderPath.hashCode().toString()
            val bucketName = File(folderPath).name

            Log.d(tag, "Found video via direct scan: ${file.absolutePath}")
            updateFolderInfo(
              bucketId = bucketId,
              bucketName = bucketName,
              folderPath = folderPath,
              dateModified = file.lastModified() / 1000,
              size = file.length(),
              filePath = file.absolutePath,
              folders = folders,
            )
          }
        } catch (e: SecurityException) {
          Log.w(tag, "Security exception accessing: ${file.absolutePath}", e)
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error scanning directory: ${directory.absolutePath}", e)
    }
  }

  private fun isVideoFile(file: File): Boolean {
    val videoExtensions = setOf(
      "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
      "mpg", "mpeg", "3gp", "3g2", "ts", "m2ts", "mts", "vob",
    )
    return videoExtensions.contains(file.extension.lowercase())
  }

  private fun getProjection(): Array<String> {
    return arrayOf(
      MediaStore.Video.Media.BUCKET_ID,
      MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.SIZE,
    )
  }

  private fun processFolderCursor(
    cursor: Cursor,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
    val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

    while (cursor.moveToNext()) {
      val bucketId = cursor.getString(bucketIdColumn)
      val bucketName = cursor.getString(bucketNameColumn)
      val filePath = cursor.getString(dataColumn)
      val dateModified = cursor.getLong(dateModifiedColumn)
      val size = cursor.getLong(sizeColumn)

      processVideoFile(filePath, bucketId, bucketName, dateModified, size, folders)
    }
  }

  private fun processVideoFile(
    filePath: String?,
    bucketId: String?,
    bucketName: String?,
    dateModified: Long,
    size: Long,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    if (filePath == null) return

    val normalizedPath = normalizePath(File(filePath).parent ?: return)
    val (finalBucketId, finalBucketName) = getFinalBucketInfo(bucketId, bucketName, normalizedPath)

    Log.d(tag, "Found video: $filePath, bucketId: $finalBucketId, bucketName: $finalBucketName")

    updateFolderInfo(finalBucketId, finalBucketName, normalizedPath, dateModified, size, filePath, folders)
  }

  private fun getFinalBucketInfo(
    bucketId: String?,
    bucketName: String?,
    folderPath: String,
  ): Pair<String, String> {
    // Use normalized folder path for consistent bucket ID
    val finalBucketId = bucketId?.takeIf { it.isNotBlank() } ?: folderPath.hashCode().toString()
    val finalBucketName = when {
      !bucketName.isNullOrBlank() -> bucketName
      folderPath.contains(TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT) &&
        folderPath == TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT -> "Internal Storage"

      folderPath.contains(TVUtils.FileSystemPaths.EXTERNAL_STORAGE_ROOT) -> File(folderPath).name
      else -> "Unknown Folder"
    }
    return Pair(finalBucketId, finalBucketName)
  }

  @Suppress("LongParameterList")
  private fun updateFolderInfo(
    bucketId: String,
    bucketName: String,
    folderPath: String,
    dateModified: Long,
    size: Long,
    filePath: String,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    // Normalize the file path to handle duplicates from different sources
    val normalizedFilePath = normalizePath(filePath)

    val folderInfo = folders[bucketId] ?: VideoFolderInfo(
      bucketId = bucketId,
      name = bucketName,
      path = folderPath,
      videoCount = 0,
      totalSize = 0L,
      lastModified = 0L,
      processedVideos = mutableSetOf(),
    )

    // Only count this video if we haven't seen it before
    if (folderInfo.processedVideos.add(normalizedFilePath)) {
      folders[bucketId] = folderInfo.copy(
        videoCount = folderInfo.videoCount + 1,
        totalSize = folderInfo.totalSize + size,
        lastModified = maxOf(folderInfo.lastModified, dateModified),
        processedVideos = folderInfo.processedVideos, // Keep the same set reference
      )
      Log.d(tag, "Counted video: $normalizedFilePath in folder $bucketName (total: ${folderInfo.videoCount + 1})")
    } else {
      Log.d(tag, "Skipping duplicate video: $normalizedFilePath in folder $bucketName")
    }
  }

  private fun convertToVideoFolderList(
    folders: Map<String, VideoFolderInfo>,
  ): List<VideoFolder> {
    return folders.values
      .map { info ->
        VideoFolder(
          bucketId = info.bucketId,
          name = info.name,
          path = info.path,
          videoCount = info.videoCount,
          totalSize = info.totalSize,
          lastModified = info.lastModified,
        )
      }
      .sortedByDescending { it.lastModified }
  }

  private data class VideoFolderInfo(
    val bucketId: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSize: Long,
    val lastModified: Long,
    val processedVideos: MutableSet<String> = mutableSetOf(), // Track which videos we've already counted
  )
}
