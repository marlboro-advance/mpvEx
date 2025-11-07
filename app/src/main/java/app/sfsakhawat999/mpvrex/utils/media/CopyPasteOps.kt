package app.sfsakhawat999.mpvrex.utils.media

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import app.sfsakhawat999.mpvrex.domain.media.model.Video
import app.sfsakhawat999.mpvrex.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles copy and move operations for video files with progress tracking.
 * Requires MANAGE_EXTERNAL_STORAGE permission on Android 11+.
 */
object CopyPasteOps {
  private const val TAG = "CopyPasteOps"
  private const val BUFFER_SIZE = 8 * 1024 // 8KB buffer for file operations
  private const val MAX_FILENAME_ATTEMPTS = 1000

  // ============================================================================
  // Data Classes
  // ============================================================================

  data class FileOperationProgress(
    val currentFile: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val error: String? = null,
  )

  sealed class OperationType {
    object Copy : OperationType()

    object Move : OperationType()
  }

  // ============================================================================
  // State Management
  // ============================================================================

  private val _operationProgress = MutableStateFlow(FileOperationProgress())
  val operationProgress: StateFlow<FileOperationProgress> = _operationProgress.asStateFlow()

  private val isCancelled = AtomicBoolean(false)

  // ============================================================================
  // Permission Check
  // ============================================================================

  /**
   * Check if MANAGE_EXTERNAL_STORAGE permission is granted
   */
  fun hasManageStoragePermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      true // Pre-Android 11 devices don't need this permission
    }

  // ============================================================================
  // Operation Control
  // ============================================================================

  /**
   * Cancel the current operation
   */
  fun cancelOperation() {
    isCancelled.set(true)
  }

  /**
   * Reset the cancellation flag and progress
   */
  fun resetOperation() {
    isCancelled.set(false)
    _operationProgress.value = FileOperationProgress()
  }

  // ============================================================================
  // Public API - Copy Files
  // ============================================================================

  /**
   * Copy files to a destination folder
   */
  suspend fun copyFiles(
    context: Context,
    videos: List<Video>,
    destinationPath: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        // Validate inputs
        if (videos.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("No files to copy"))
        }

        if (!hasManageStoragePermission()) {
          return@withContext Result.failure(
            SecurityException("MANAGE_EXTERNAL_STORAGE permission not granted"),
          )
        }

        resetOperation()

        // Validate and prepare destination
        val destDir =
          prepareDestinationDirectory(destinationPath)
            ?: return@withContext Result.failure(
              IOException("Failed to create destination directory: $destinationPath"),
            )

        // Filter valid source files
        val validVideos =
          videos.filter { video ->
            val sourceFile = File(video.path)
            if (!sourceFile.exists()) {
              Log.w(TAG, "Source file does not exist, skipping: ${video.path}")
              false
            } else if (sourceFile.parent == destDir.absolutePath) {
              Log.w(TAG, "Source and destination are the same, skipping: ${video.displayName}")
              false
            } else {
              true
            }
          }

        if (validVideos.isEmpty()) {
          return@withContext Result.failure(
            IllegalArgumentException("No valid files to copy"),
          )
        }

        // Check available disk space
        val totalBytes = validVideos.sumOf { it.size }
        if (!hasEnoughDiskSpace(destDir, totalBytes)) {
          return@withContext Result.failure(
            IOException("Not enough disk space. Required: ${formatBytes(totalBytes)}"),
          )
        }

        // Perform copy operation
        val copiedFilePaths = performCopyOperation(validVideos, destDir, totalBytes)

        // Notify that media library has changed
        MediaLibraryEvents.notifyChanged()

        // Trigger media scan
        triggerMediaScan(context, copiedFilePaths)

        Log.d(TAG, "Copy operation completed successfully. Copied ${copiedFilePaths.size} files")
        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Copy operation failed: ${e.message}", e)
        _operationProgress.value =
          _operationProgress.value.copy(
            error = e.message ?: "Unknown error occurred",
          )
        Result.failure(e)
      }
    }

  // ============================================================================
  // Public API - Move Files
  // ============================================================================

  /**
   * Move files to a destination folder
   */
  suspend fun moveFiles(
    context: Context,
    videos: List<Video>,
    destinationPath: String,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        // Validate inputs
        if (videos.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("No files to move"))
        }

        if (!hasManageStoragePermission()) {
          return@withContext Result.failure(
            SecurityException("MANAGE_EXTERNAL_STORAGE permission not granted"),
          )
        }

        resetOperation()

        // Validate and prepare destination
        val destDir =
          prepareDestinationDirectory(destinationPath)
            ?: return@withContext Result.failure(
              IOException("Failed to create destination directory: $destinationPath"),
            )

        // Filter valid source files
        val validVideos =
          videos.filter { video ->
            val sourceFile = File(video.path)
            if (!sourceFile.exists()) {
              Log.w(TAG, "Source file does not exist, skipping: ${video.path}")
              false
            } else if (sourceFile.parent == destDir.absolutePath) {
              Log.w(TAG, "Source and destination are the same, skipping: ${video.displayName}")
              false
            } else {
              true
            }
          }

        if (validVideos.isEmpty()) {
          return@withContext Result.failure(
            IllegalArgumentException("No valid files to move"),
          )
        }

        // Check available disk space (only needed if move crosses filesystems)
        val totalBytes = validVideos.sumOf { it.size }

        // Perform move operation
        val (movedFilePaths, historyUpdates) = performMoveOperation(validVideos, destDir, totalBytes)

        // Update history for moved files
        historyUpdates.forEach { (oldPath, newPath) ->
          RecentlyPlayedOps.onVideoRenamed(oldPath, newPath)
          PlaybackStateOps.onVideoRenamed(oldPath, newPath)
        }

        // Trigger media scan
        triggerMediaScan(context, movedFilePaths)

        Log.d(TAG, "Move operation completed successfully. Moved ${movedFilePaths.size} files")
        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Move operation failed: ${e.message}", e)
        _operationProgress.value =
          _operationProgress.value.copy(
            error = e.message ?: "Unknown error occurred",
          )
        Result.failure(e)
      }
    }

  // ============================================================================
  // Private Helper - Directory Operations
  // ============================================================================

  private fun prepareDestinationDirectory(path: String): File? {
    return try {
      val dir = File(path)

      if (dir.exists()) {
        if (!dir.isDirectory) {
          Log.e(TAG, "Destination exists but is not a directory: $path")
          return null
        }
        if (!dir.canWrite()) {
          Log.e(TAG, "Destination directory is not writable: $path")
          return null
        }
      } else {
        val created = dir.mkdirs()
        Log.d(TAG, "Created destination directory: $created at $path")
        if (!created) {
          return null
        }
      }

      dir
    } catch (e: Exception) {
      Log.e(TAG, "Error preparing destination directory: ${e.message}", e)
      null
    }
  }

  private fun hasEnoughDiskSpace(
    directory: File,
    requiredBytes: Long,
  ): Boolean =
    try {
      val statFs = StatFs(directory.absolutePath)
      val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
      val hasSpace = availableBytes >= requiredBytes

      if (!hasSpace) {
        Log.w(
          TAG,
          "Insufficient disk space. Required: ${formatBytes(requiredBytes)}, Available: ${formatBytes(availableBytes)}",
        )
      }

      hasSpace
    } catch (e: Exception) {
      Log.w(TAG, "Could not check disk space: ${e.message}")
      true // Assume space is available if we can't check
    }

  // ============================================================================
  // Private Helper - Copy Operation
  // ============================================================================

  private fun performCopyOperation(
    videos: List<Video>,
    destDir: File,
    totalBytes: Long,
  ): List<String> {
    val copiedFilePaths = mutableListOf<String>()
    var processedBytes = 0L

    _operationProgress.value =
      FileOperationProgress(
        totalFiles = videos.size,
        totalBytes = totalBytes,
      )

    videos.forEachIndexed { index, video ->
      checkCancellation()

      val sourceFile = File(video.path)
      val finalDestFile = getUniqueFileName(File(destDir, video.displayName))

      updateProgress(
        currentFile = video.displayName,
        currentFileIndex = index + 1,
        totalFiles = videos.size,
        currentFileProgress = 0f,
        bytesProcessed = processedBytes,
        totalBytes = totalBytes,
      )

      // Copy the file with progress tracking
      copyFileWithProgress(sourceFile, finalDestFile, video.size) { progress ->
        updateProgress(
          currentFile = video.displayName,
          currentFileIndex = index + 1,
          totalFiles = videos.size,
          currentFileProgress = progress,
          bytesProcessed = processedBytes + (video.size * progress).toLong(),
          totalBytes = totalBytes,
        )
      }

      copiedFilePaths.add(finalDestFile.absolutePath)
      processedBytes += video.size

      Log.d(TAG, "✓ Copied: ${video.displayName} -> ${finalDestFile.name}")
    }

    _operationProgress.value =
      _operationProgress.value.copy(
        isComplete = true,
        overallProgress = 1f,
        bytesProcessed = totalBytes,
      )

    return copiedFilePaths
  }

  // ============================================================================
  // Private Helper - Move Operation
  // ============================================================================

  private fun performMoveOperation(
    videos: List<Video>,
    destDir: File,
    totalBytes: Long,
  ): Pair<List<String>, List<Pair<String, String>>> {
    val movedFilePaths = mutableListOf<String>()
    val historyUpdates = mutableListOf<Pair<String, String>>()
    var processedBytes = 0L

    _operationProgress.value =
      FileOperationProgress(
        totalFiles = videos.size,
        totalBytes = totalBytes,
      )

    videos.forEachIndexed { index, video ->
      checkCancellation()

      val sourceFile = File(video.path)
      val finalDestFile = getUniqueFileName(File(destDir, video.displayName))

      updateProgress(
        currentFile = video.displayName,
        currentFileIndex = index + 1,
        totalFiles = videos.size,
        currentFileProgress = 0f,
        bytesProcessed = processedBytes,
        totalBytes = totalBytes,
      )

      // Try direct move first (faster if same filesystem)
      val moved = tryDirectMove(sourceFile, finalDestFile)

      if (!moved) {
        // Fall back to copy + delete
        moveViaCopyAndDelete(
          sourceFile,
          finalDestFile,
          video.size,
          processedBytes,
          totalBytes,
          video.displayName,
          index + 1,
          videos.size,
        )
      }

      // Verify move success
      if (!finalDestFile.exists()) {
        throw IOException("Move failed: destination file not found for ${video.displayName}")
      }

      movedFilePaths.add(finalDestFile.absolutePath)
      historyUpdates.add(sourceFile.absolutePath to finalDestFile.absolutePath)
      processedBytes += video.size

      updateProgress(
        currentFile = video.displayName,
        currentFileIndex = index + 1,
        totalFiles = videos.size,
        currentFileProgress = 1f,
        bytesProcessed = processedBytes,
        totalBytes = totalBytes,
      )

      Log.d(TAG, "✓ Moved: ${video.displayName} -> ${finalDestFile.name}")
    }

    _operationProgress.value =
      _operationProgress.value.copy(
        isComplete = true,
        overallProgress = 1f,
        bytesProcessed = totalBytes,
      )

    return Pair(movedFilePaths, historyUpdates)
  }

  private fun tryDirectMove(
    source: File,
    destination: File,
  ): Boolean =
    try {
      val success = source.renameTo(destination)
      if (success && destination.exists()) {
        Log.d(TAG, "✓ Direct move successful: ${source.name}")
        true
      } else {
        Log.d(TAG, "Direct move failed, will use copy+delete: ${source.name}")
        false
      }
    } catch (e: Exception) {
      Log.w(TAG, "Direct move threw exception: ${e.message}")
      false
    }

  private fun moveViaCopyAndDelete(
    source: File,
    destination: File,
    fileSize: Long,
    processedBytes: Long,
    totalBytes: Long,
    fileName: String,
    currentIndex: Int,
    totalFiles: Int,
  ) {
    // Copy with progress
    copyFileWithProgress(source, destination, fileSize) { progress ->
      updateProgress(
        currentFile = fileName,
        currentFileIndex = currentIndex,
        totalFiles = totalFiles,
        currentFileProgress = progress,
        bytesProcessed = processedBytes + (fileSize * progress).toLong(),
        totalBytes = totalBytes,
      )
    }

    // Verify copy
    if (!destination.exists() || destination.length() != source.length()) {
      destination.delete() // Clean up partial copy
      throw IOException("Copy verification failed for: $fileName")
    }

    // Delete source
    if (!source.delete()) {
      Log.w(TAG, "Failed to delete source file after copy: ${source.absolutePath}")
      // Don't throw - the file was successfully copied
    }
  }

  // ============================================================================
  // Private Helper - File Operations
  // ============================================================================

  private fun copyFileWithProgress(
    source: File,
    destination: File,
    fileSize: Long,
    onProgress: (Float) -> Unit,
  ) {
    if (!source.exists()) {
      throw IOException("Source file does not exist: ${source.path}")
    }

    if (!source.canRead()) {
      throw IOException("Source file is not readable: ${source.path}")
    }

    try {
      FileInputStream(source).use { input ->
        FileOutputStream(destination).use { output ->
          val buffer = ByteArray(BUFFER_SIZE)
          var bytesCopied = 0L
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            checkCancellation()

            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            val progress = if (fileSize > 0) bytesCopied.toFloat() / fileSize else 1f
            onProgress(progress.coerceIn(0f, 1f))
          }

          output.flush()
        }
      }

      // Preserve file timestamp
      destination.setLastModified(source.lastModified())
    } catch (e: Exception) {
      destination.delete() // Clean up on error
      throw e
    }
  }

  private fun getUniqueFileName(file: File): File {
    if (!file.exists()) return file

    val name = file.nameWithoutExtension
    val extension = file.extension
    val parent = file.parentFile ?: return file

    for (counter in 1..MAX_FILENAME_ATTEMPTS) {
      val newName =
        if (extension.isNotEmpty()) {
          "${name}_$counter.$extension"
        } else {
          "${name}_$counter"
        }
      val newFile = File(parent, newName)
      if (!newFile.exists()) {
        return newFile
      }
    }

    throw IOException("Could not generate unique filename after $MAX_FILENAME_ATTEMPTS attempts")
  }

  // ============================================================================
  // Private Helper - Progress & State
  // ============================================================================

  private fun updateProgress(
    currentFile: String,
    currentFileIndex: Int,
    totalFiles: Int,
    currentFileProgress: Float,
    bytesProcessed: Long,
    totalBytes: Long,
  ) {
    val overallProgress = if (totalBytes > 0) bytesProcessed.toFloat() / totalBytes else 0f

    _operationProgress.value =
      _operationProgress.value.copy(
        currentFile = currentFile,
        currentFileIndex = currentFileIndex,
        totalFiles = totalFiles,
        currentFileProgress = currentFileProgress.coerceIn(0f, 1f),
        overallProgress = overallProgress.coerceIn(0f, 1f),
        bytesProcessed = bytesProcessed,
        totalBytes = totalBytes,
      )
  }

  private fun checkCancellation() {
    if (isCancelled.get()) {
      _operationProgress.value =
        _operationProgress.value.copy(
          isCancelled = true,
          error = "Operation cancelled by user",
        )
      throw IOException("Operation cancelled by user")
    }
  }

  // ============================================================================
  // Private Helper - Media Scanning
  // ============================================================================

  private fun triggerMediaScan(
    context: Context,
    filePaths: List<String>,
  ) {
    if (filePaths.isEmpty()) return

    try {
      Log.d(TAG, "Triggering media scan for ${filePaths.size} files...")
      android.media.MediaScannerConnection.scanFile(
        context,
        filePaths.toTypedArray(),
        null,
        null,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Media scan failed: ${e.message}")
      // Don't throw - the file operation succeeded
    }
  }

  // ============================================================================
  // Utility Functions
  // ============================================================================

  /**
   * Format bytes to human-readable string
   */
  fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.2f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.2f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
  }
}
