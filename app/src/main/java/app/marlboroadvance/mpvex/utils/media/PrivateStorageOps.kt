package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Handles operations related to private video storage.
 * Videos added to private space are moved to persistent storage and deleted from original location.
 * Storage and metadata persist after app uninstallation.
 */
object PrivateStorageOps {
  private const val TAG = "PrivateStorageOps"
  private const val APP_FOLDER = "mpvEx"
  private const val PRIVATE_VIDEOS_DIR = ".private"
  private const val METADATA_FILE = "video_metadata.json"
  private const val MIN_FREE_SPACE_MB = 100L // Minimum free space required (100 MB)

  private val json =
    Json {
      prettyPrint = true
      ignoreUnknownKeys = true
    }

  /**
   * Get the directory where private videos are stored
   * Uses mpvEx/.private directory in external storage which persists after app uninstallation
   */
  fun getPrivateVideosDir(context: Context): File {
    // Use custom mpvEx folder in external storage root
    val externalStorageDir = Environment.getExternalStorageDirectory()
    val appDir = File(externalStorageDir, APP_FOLDER)
    val privateDir = File(appDir, PRIVATE_VIDEOS_DIR)

    if (!privateDir.exists()) {
      privateDir.mkdirs()
      // Create .nomedia file to hide from gallery
      runCatching { File(privateDir, ".nomedia").createNewFile() }
    }
    return privateDir
  }

  /**
   * Metadata for a private video stored in JSON
   */
  @Serializable
  data class PrivateVideoMetadata(
    val videoId: Long,
    val originalPath: String,
    val privateFilePath: String,
    val addedAt: Long,
    val displayName: String,
  )

  /**
   * Load all metadata from JSON file with corruption handling
   */
  suspend fun loadMetadata(context: Context): List<PrivateVideoMetadata> =
    withContext(Dispatchers.IO) {
      runCatching {
        val metadataFile = File(getPrivateVideosDir(context), METADATA_FILE)
        if (!metadataFile.exists() || metadataFile.length() == 0L) {
          return@runCatching emptyList()
        }

        json.decodeFromString<List<PrivateVideoMetadata>>(metadataFile.readText())
      }.getOrElse { e ->
        when (e) {
          is SerializationException -> {
            Log.e(TAG, "Corrupted metadata file, resetting", e)
            saveMetadata(context, emptyList()) // Reset corrupted file
          }

          else -> Log.e(TAG, "Error loading metadata", e)
        }
        emptyList()
      }
    }

  /**
   * Save metadata to JSON file atomically to prevent corruption
   */
  private suspend fun saveMetadata(
    context: Context,
    metadata: List<PrivateVideoMetadata>,
  ) = withContext(Dispatchers.IO) {
    runCatching {
      val metadataFile = File(getPrivateVideosDir(context), METADATA_FILE)
      val tempFile = File(metadataFile.parent, "${METADATA_FILE}.tmp")

      // Write to temp file first
      tempFile.writeText(json.encodeToString(metadata))

      // Atomic rename
      if (metadataFile.exists()) metadataFile.delete()
      tempFile.renameTo(metadataFile)

      Log.d(TAG, "Saved metadata for ${metadata.size} videos")
    }.onFailure { e ->
      Log.e(TAG, "Error saving metadata", e)
    }
  }

  /**
   * Add metadata for a video
   */
  suspend fun addMetadata(
    context: Context,
    videoId: Long,
    originalPath: String,
    privateFilePath: String,
    displayName: String,
  ) {
    val metadata = loadMetadata(context).toMutableList()
    metadata.add(
      PrivateVideoMetadata(
        videoId = videoId,
        originalPath = originalPath,
        privateFilePath = privateFilePath,
        addedAt = System.currentTimeMillis(),
        displayName = displayName,
      ),
    )
    saveMetadata(context, metadata)
  }

  /**
   * Remove metadata for a video
   */
  suspend fun removeMetadata(
    context: Context,
    videoId: Long,
  ) {
    val metadata = loadMetadata(context).filterNot { it.videoId == videoId }
    saveMetadata(context, metadata)
  }

  /**
   * Update metadata by adding or updating a video entry
   */
  private suspend fun updateMetadata(
    context: Context,
    videoId: Long,
    originalPath: String,
    privateFilePath: String,
    displayName: String,
  ) {
    val metadata = loadMetadata(context).toMutableList()
    metadata.removeAll { it.videoId == videoId } // Remove existing if any
    metadata.add(
      PrivateVideoMetadata(
        videoId = videoId,
        originalPath = originalPath,
        privateFilePath = privateFilePath,
        addedAt = System.currentTimeMillis(),
        displayName = displayName,
      ),
    )
    saveMetadata(context, metadata)
  }

  /**
   * Get metadata for a specific video
   */
  suspend fun getMetadata(
    context: Context,
    videoId: Long,
  ): PrivateVideoMetadata? = loadMetadata(context).find { it.videoId == videoId }

  /**
   * Check if there's enough storage space
   */
  private fun hasEnoughSpace(
    directory: File,
    requiredBytes: Long,
  ): Boolean {
    val freeSpace = directory.usableSpace
    val requiredSpace = requiredBytes + (MIN_FREE_SPACE_MB * 1024 * 1024)
    return freeSpace >= requiredSpace
  }

  /**
   * Move a video to private storage with validation
   */
  suspend fun moveToPrivateStorage(
    context: Context,
    video: Video,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalFile = File(video.path)
        if (!originalFile.exists()) {
          throw IOException("Source file not found: ${video.path}")
        }

        val privateDir = getPrivateVideosDir(context)
        if (!hasEnoughSpace(privateDir, originalFile.length())) {
          throw IOException("Not enough storage space")
        }

        val privateFile = File(privateDir, "${video.id}_${video.displayName}")

        // Skip if already exists
        if (privateFile.exists() && privateFile.length() == originalFile.length()) {
          Log.d(TAG, "Video already in private storage")
          return@runCatching privateFile.absolutePath
        }

        // Copy file
        originalFile.copyTo(privateFile, overwrite = true)

        // Verify copy
        if (privateFile.length() != originalFile.length()) {
          privateFile.delete()
          throw IOException("File copy verification failed")
        }

        // Delete original
        runCatching {
          context.contentResolver.delete(
            video.uri,
            null,
            null,
          )
        }.onFailure {
          originalFile.delete()
        }

        // Clean up recently played
        RecentlyPlayedOps.onVideoDeleted(video.path)

        // Update metadata
        updateMetadata(context, video.id, video.path, privateFile.absolutePath, video.displayName)

        Log.d(TAG, "Moved video to private storage: ${privateFile.name}")
        privateFile.absolutePath
      }
    }

  /**
   * Move multiple videos to private storage
   */
  suspend fun moveMultipleToPrivateStorage(
    context: Context,
    videos: List<Video>,
  ): Pair<Int, Int> =
    withContext(Dispatchers.IO) {
      videos.fold(0 to 0) { (success, fail), video ->
        moveToPrivateStorage(context, video)
          .fold(
            onSuccess = { (success + 1) to fail },
            onFailure = { success to (fail + 1) },
          )
      }
    }

  /**
   * Restore a video from private storage back to public storage
   * @return Result with the new public file path, or error
   */
  suspend fun restoreFromPrivateStorage(
    context: Context,
    privateFilePath: String,
    destinationDir: File,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val privateFile = File(privateFilePath)
        if (!privateFile.exists()) {
          throw IOException("Private file not found: $privateFilePath")
        }

        // Extract original filename (remove video ID prefix)
        val originalFilename =
          privateFile.name.substringAfter("_", privateFile.name)
        val publicFile = File(destinationDir, originalFilename)

        // Make sure destination directory exists
        if (!destinationDir.exists()) {
          destinationDir.mkdirs()
        }

        // Handle filename collision
        var finalPublicFile = publicFile
        var counter = 1
        while (finalPublicFile.exists()) {
          val nameWithoutExt = originalFilename.substringBeforeLast('.')
          val extension = originalFilename.substringAfterLast('.', "")
          val newName =
            if (extension.isNotEmpty()) {
              "${nameWithoutExt}_$counter.$extension"
            } else {
              "${nameWithoutExt}_$counter"
            }
          finalPublicFile = File(destinationDir, newName)
          counter++
        }

        // Copy file back to public storage
        privateFile.copyTo(finalPublicFile, overwrite = false)

        // Verify the copy
        if (!finalPublicFile.exists() || finalPublicFile.length() != privateFile.length()) {
          finalPublicFile.delete()
          throw IOException("File restore verification failed")
        }

        // Delete from private storage
        privateFile.delete()
        removeMetadata(context, privateFile.name.substringBefore("_").toLong())

        // Trigger media scan to add back to MediaStore
        val scanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        scanIntent.data = Uri.fromFile(File(finalPublicFile.absolutePath))
        context.sendBroadcast(scanIntent)

        Log.d(TAG, "Successfully restored video to public storage: ${finalPublicFile.absolutePath}")
        finalPublicFile.absolutePath
      }
    }

  /**
   * Restore a video from private storage back to its original location
   * @param privateFilePath The path to the private video file
   * @param originalPath The original path where the video was located, or "Unknown" for imported videos
   * @return Result with the new public file path, or error
   */
  suspend fun restoreToOriginalLocation(
    context: Context,
    privateFilePath: String,
    originalPath: String,
  ): Result<String> {
    // Determine the destination directory
    val destinationDir =
      when {
        originalPath == "Unknown" -> {
          // If original path is unknown (e.g., video was imported after reinstall),
          // restore to a default location in Movies/mpvEx
          val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
          File(moviesDir, APP_FOLDER)
        }

        else -> {
          // Use the original parent directory
          File(originalPath).parentFile
        }
      }

    if (destinationDir == null) {
      return Result.failure(Exception("Cannot determine destination directory"))
    }

    // Create the directory if it doesn't exist
    if (!destinationDir.exists()) {
      val created = destinationDir.mkdirs()
      if (!created && !destinationDir.exists()) {
        return Result.failure(Exception("Failed to create directory: ${destinationDir.absolutePath}"))
      }
      Log.d(TAG, "Created directory: ${destinationDir.absolutePath}")
    }

    return restoreFromPrivateStorage(context, privateFilePath, destinationDir)
  }

  /**
   * Restore multiple videos
   */
  suspend fun restoreMultipleFromPrivateStorage(
    context: Context,
    entries: List<Pair<String, String>>, // (privateFilePath, originalPath)
  ): Pair<Int, Int> =
    withContext(Dispatchers.IO) {
      entries.fold(0 to 0) { (success, fail), (privatePath, originalPath) ->
        restoreToOriginalLocation(context, privatePath, originalPath)
          .fold(
            onSuccess = { (success + 1) to fail },
            onFailure = { success to (fail + 1) },
          )
      }
    }

  /**
   * Delete a video from private storage
   */
  suspend fun deleteFromPrivateStorage(
    context: Context,
    privateFilePath: String,
  ): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(privateFilePath)
        val deleted = !file.exists() || file.delete()
        if (deleted) {
          file.name.substringBefore("_").toLongOrNull()?.let { videoId ->
            removeMetadata(context, videoId)
          }
        }
        deleted
      }.getOrDefault(false)
    }

  /**
   * Check if a file path is in private storage
   */
  fun isPrivateStoragePath(
    context: Context,
    filePath: String,
  ): Boolean = filePath.startsWith(getPrivateVideosDir(context).absolutePath)
}
