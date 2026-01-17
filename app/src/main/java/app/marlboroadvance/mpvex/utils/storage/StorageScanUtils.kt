package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

/**
 * Unified utility for scanning all storage volumes (internal, SD card, USB OTG)
 * Provides common functionality for both video folders and individual videos
 */
object StorageScanUtils {
  private const val TAG = "StorageScanUtils"

  // Video file extensions to scan for
  val VIDEO_EXTENSIONS =
    setOf(
      "mp4",
      "mkv",
      "avi",
      "mov",
      "wmv",
      "flv",
      "webm",
      "m4v",
      "3gp",
      "3g2",
      "mpg",
      "mpeg",
      "m2v",
      "ogv",
      "ts",
      "mts",
      "m2ts",
      "vob",
      "divx",
      "xvid",
      "f4v",
      "rm",
      "rmvb",
      "asf",
    )

  // Audio file extensions
  val AUDIO_EXTENSIONS =
    setOf(
      "mp3",
      "m4a",
      "aac",
      "ogg",
      "oga",
      "opus",
      "flac",
      "wav",
      "wma",
      "ape",
      "wv",
      "mpc",
      "tta",
      "tak",
      "dsd",
      "dsf",
      "dff",
      "aiff",
      "aif",
      "aifc",
    )

  // Image file extensions
  val IMAGE_EXTENSIONS =
    setOf(
      "jpg",
      "jpeg",
      "png",
      "gif",
      "bmp",
      "webp",
      "heic",
      "heif",
      "svg",
      "tiff",
      "tif",
      "ico",
      "avif",
      "jxl",
    )

  // Document file extensions
  val DOCUMENT_EXTENSIONS =
    setOf(
      "pdf",
      "doc",
      "docx",
      "xls",
      "xlsx",
      "ppt",
      "pptx",
      "txt",
      "rtf",
      "odt",
      "ods",
      "odp",
      "csv",
      "log",
      "md",
      "epub",
      "mobi",
    )

  // Archive file extensions
  val ARCHIVE_EXTENSIONS =
    setOf(
      "zip",
      "rar",
      "7z",
      "tar",
      "gz",
      "bz2",
      "xz",
      "iso",
      "dmg",
      "apk",
      "jar",
    )

  // Code file extensions
  val CODE_EXTENSIONS =
    setOf(
      "java",
      "kt",
      "kts",
      "xml",
      "json",
      "html",
      "css",
      "js",
      "ts",
      "py",
      "c",
      "cpp",
      "h",
      "hpp",
      "swift",
      "go",
      "rs",
      "sh",
      "bat",
      "gradle",
    )

  /**
   * Gets all mounted storage volumes
   * Note: Uses a lenient check as volume.state can sometimes be unreliable
   */
  fun getAllStorageVolumes(context: Context): List<StorageVolume> =
    try {
      val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
      // Filter volumes more leniently - include volumes even if state is not MEDIA_MOUNTED
      // as the state can be unreliable, especially for SD cards
      storageManager.storageVolumes.filter { volume ->
        // Consider a volume available if:
        // 1. It's reported as mounted, OR
        // 2. We can get a valid path for it and it exists
        volume.state == Environment.MEDIA_MOUNTED ||
          (getVolumePath(volume)?.let { path -> File(path).exists() } == true)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting storage volumes", e)
      emptyList()
    }



  /**
   * Determines which storage volume a given path belongs to
   */
  fun getVolumeForPath(
    context: Context,
    path: String,
  ): StorageVolume? {
    try {
      val volumes = getAllStorageVolumes(context)

      for (volume in volumes) {
        val volumePath = getVolumePath(volume)
        if (volumePath != null && path.startsWith(volumePath)) {
          return volume
        }
      }

      return volumes.firstOrNull { it.isPrimary }
    } catch (e: Exception) {
      Log.w(TAG, "Error determining volume for path: $path", e)
      return null
    }
  }

  /**
   * Gets the physical path of a storage volume
   */
  fun getVolumePath(volume: StorageVolume): String? {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val directory = volume.directory
        if (directory != null) {
          return directory.absolutePath
        }
      }

      val method = volume.javaClass.getMethod("getPath")
      val path = method.invoke(volume) as? String
      if (path != null) {
        return path
      }

      volume.uuid?.let { uuid ->
        val possiblePaths =
          listOf(
            "/storage/$uuid",
            "/mnt/media_rw/$uuid",
          )
        for (possiblePath in possiblePaths) {
          if (File(possiblePath).exists()) {
            return possiblePath
          }
        }
      }

      return null
    } catch (e: Exception) {
      Log.w(TAG, "Could not get volume path", e)
      return null
    }
  }

  /**
   * Checks if a file is a video based on extension
   */
  fun isVideoFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return VIDEO_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if a file is audio based on extension
   */
  fun isAudioFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return AUDIO_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if a file is an image based on extension
   */
  fun isImageFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return IMAGE_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if a file is a document based on extension
   */
  fun isDocumentFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return DOCUMENT_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if a file is an archive based on extension
   */
  fun isArchiveFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return ARCHIVE_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if a file is a code file based on extension
   */
  fun isCodeFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return CODE_EXTENSIONS.contains(extension)
  }

  /**
   * Gets the file type category
   */
  fun getFileType(file: File): FileType {
    return when {
      isVideoFile(file) -> FileType.VIDEO
      isAudioFile(file) -> FileType.AUDIO
      isImageFile(file) -> FileType.IMAGE
      isDocumentFile(file) -> FileType.DOCUMENT
      isArchiveFile(file) -> FileType.ARCHIVE
      isCodeFile(file) -> FileType.CODE
      else -> FileType.OTHER
    }
  }

  /**
   * File type categories
   */
  enum class FileType {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    ARCHIVE,
    CODE,
    OTHER
  }

  // Folders to skip during scanning (system/cache folders)
private val SKIP_FOLDERS = setOf(
    // System & OS Junk
    "android", "data", "obb", "system", "lost.dir", ".android_secure", "android_secure",
    
    // Hidden & Temp Files
    ".thumbnails", "thumbnails", "thumbs", ".thumbs",
    ".cache", "cache", "temp", "tmp", ".temp", ".tmp",
    
    // Trash & Recycle Bins
    ".trash", "trash", ".trashbin", ".trashed", "recycle", "recycler",
    
    // App Clutters
    "log", "logs", "backup", "backups",
    "stickers", "whatsapp stickers", "telegram stickers",
)

  /**
   * Checks if a folder contains a .nomedia file
   * @param folder The folder to check
   * @return true if the folder contains a .nomedia file, false otherwise
   */
  fun hasNoMediaFile(folder: File): Boolean {
    if (!folder.isDirectory || !folder.canRead()) {
      return false
    }

    return try {
      val noMediaFile = File(folder, ".nomedia")
      noMediaFile.exists()
    } catch (e: Exception) {
      Log.w(TAG, "Error checking for .nomedia file in: ${folder.absolutePath}", e)
      false
    }
  }

  /**
   * Checks if a folder should be skipped during scanning
   * @param folder The folder to check
   * @param showHiddenFiles Whether to show hidden files/folders
   * @return true if the folder should be skipped, false otherwise
   *
   * When showHiddenFiles = true: Shows everything including .nomedia folders
   * When showHiddenFiles = false: Hides .nomedia folders, hidden folders, and system folders
   */
  fun shouldSkipFolder(folder: File, showHiddenFiles: Boolean): Boolean {
    // If showHiddenFiles is enabled, don't skip anything (show .nomedia folders too)
    if (showHiddenFiles) {
      return false
    }

    // If showHiddenFiles is disabled, check for .nomedia, hidden folders, and system folders
    if (hasNoMediaFile(folder)) {
      return true
    }

    val name = folder.name.lowercase()
    val isHidden = name.startsWith(".")
    return isHidden || SKIP_FOLDERS.contains(name)
  }

  /**
   * Checks if a file should be skipped during file listing
   * @param file The file to check
   * @param showHiddenFiles Whether to show hidden files
   * @return true if the file should be skipped (hidden), false otherwise
   *
   * When showHiddenFiles = true: Shows all files including hidden ones
   * When showHiddenFiles = false: Hides files starting with a dot (.)
   */
  fun shouldSkipFile(file: File, showHiddenFiles: Boolean): Boolean {
    // If showHiddenFiles is enabled, don't skip anything
    if (showHiddenFiles) {
      return false
    }

    // Skip files that start with a dot (hidden files)
    return file.name.startsWith(".")
  }

  /**
   * Recursively scans a directory for video files
   * @param directory The directory to scan
   * @param onFolderFound Callback when a folder with videos is found
   * @param maxDepth Maximum recursion depth
   * @param currentDepth Current recursion depth
   */
  fun scanDirectoryForVideos(
    directory: File,
    onFolderFound: (folder: File, videoFiles: List<File>) -> Unit,
    maxDepth: Int = 5,
    currentDepth: Int = 0,
  ) {
    if (currentDepth >= maxDepth) return

    try {
      if (!directory.exists() || !directory.canRead()) {
        return
      }

      val files = directory.listFiles() ?: return
      val videoFiles = files.filter { it.isFile && isVideoFile(it) }

      // If this directory contains videos, notify
      if (videoFiles.isNotEmpty()) {
        onFolderFound(directory, videoFiles)
      }

      // Recursively scan subdirectories (skip hidden and system folders)
      files
        .filter {
          it.isDirectory &&
            it.canRead() &&
            !it.name.startsWith(".") &&
            !SKIP_FOLDERS.contains(it.name.lowercase())
        }
        .forEach { subDir ->
          scanDirectoryForVideos(subDir, onFolderFound, maxDepth, currentDepth + 1)
        }
    } catch (e: SecurityException) {
      Log.w(TAG, "Security exception scanning: ${directory.path}", e)
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.path}", e)
    }
  }

  /**
   * Finds a specific directory by matching its bucket ID (path hash)
   * @param rootDirectory The root directory to start searching from
   * @param targetBucketId The bucket ID to match
   * @param maxDepth Maximum recursion depth
   * @return The matching directory or null
   */
  fun findDirectoryByBucketId(
    rootDirectory: File,
    targetBucketId: String,
    maxDepth: Int = 5,
  ): File? = findDirectoryByBucketIdRecursive(rootDirectory, targetBucketId, maxDepth, 0)

  private fun findDirectoryByBucketIdRecursive(
    directory: File,
    targetBucketId: String,
    maxDepth: Int,
    currentDepth: Int,
  ): File? {
    if (currentDepth >= maxDepth) return null

    try {
      if (!directory.exists() || !directory.canRead()) return null

      val currentBucketId = directory.absolutePath.hashCode().toString()
      if (currentBucketId == targetBucketId) {
        return directory
      }

      // Search subdirectories
      val subdirs =
        directory.listFiles()?.filter {
          it.isDirectory && it.canRead() && !it.name.startsWith(".")
        } ?: return null

      for (subdir in subdirs) {
        val found = findDirectoryByBucketIdRecursive(subdir, targetBucketId, maxDepth, currentDepth + 1)
        if (found != null) return found
      }

      return null
    } catch (e: Exception) {
      Log.w(TAG, "Error searching directory: ${directory.path}", e)
      return null
    }
  }

  /**
   * Extracts video metadata using MediaInfo library
   * @return VideoMetadata object with duration, mime type, width, and height
   */
  fun extractVideoMetadata(
    context: Context,
    file: File,
  ): VideoMetadata {
    var duration = 0L
    var mimeType = "video/*"
    var width = 0
    var height = 0

    try {
      // Use MediaInfo library for better accuracy and performance
      val uri = Uri.fromFile(file)
      val result =
        runBlocking {
          MediaInfoOps.extractBasicMetadata(context, uri, file.name)
        }

      result.onSuccess { metadata ->
        duration = metadata.durationMs
        width = metadata.width
        height = metadata.height
        // Get mime type from extension since MediaInfo doesn't return it directly
        mimeType = getMimeTypeFromExtension(file.extension.lowercase())
      }.onFailure { e ->
        Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
        // Fallback to mime type based on extension
        mimeType = getMimeTypeFromExtension(file.extension.lowercase())
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
      // Fallback to mime type based on extension
      mimeType = getMimeTypeFromExtension(file.extension.lowercase())
    }

    return VideoMetadata(duration, mimeType, width, height)
  }

  /**
   * Gets MIME type from file extension
   */
  fun getMimeTypeFromExtension(extension: String): String =
    when (extension.lowercase()) {
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "webm" -> "video/webm"
      "flv" -> "video/x-flv"
      "wmv" -> "video/x-ms-wmv"
      "m4v" -> "video/x-m4v"
      "3gp" -> "video/3gpp"
      "mpg", "mpeg" -> "video/mpeg"
      else -> "video/*"
    }

  /**
   * Gets non-primary (external) storage volumes (SD cards, USB OTG)
   */
  fun getExternalStorageVolumes(context: Context): List<StorageVolume> =
    getAllStorageVolumes(context).filter { !it.isPrimary }

  /**
   * Data class to hold video metadata
   */
  data class VideoMetadata(
    val duration: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
  )
}
