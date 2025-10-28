package app.marlboroadvance.mpvex.database.repository

import android.content.Context
import android.util.Log
import app.marlboroadvance.mpvex.database.dao.PrivateVideoDao
import app.marlboroadvance.mpvex.database.entities.PrivateVideoEntity
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.PrivateStorageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PrivateVideoRepository(
  private val dao: PrivateVideoDao,
  private val context: Context,
) {
  private companion object {
    const val TAG = "PrivateVideoRepository"
  }

  suspend fun getAll(): List<PrivateVideoEntity> = dao.getAll()

  /**
   * Sync database with persistent JSON metadata
   * Handles app reinstallation and data recovery
   */
  suspend fun syncFromPersistentStorage() =
    withContext(Dispatchers.IO) {
      runCatching {
        val jsonMetadata = PrivateStorageOps.loadMetadata(context)
        val dbEntities = dao.getAll()

        val jsonVideoIds = jsonMetadata.map { it.videoId }.toSet()
        val dbVideoIds = dbEntities.map { it.videoId }.toSet()

        // Add missing entries from JSON
        jsonMetadata
          .filterNot { it.videoId in dbVideoIds }
          .forEach { metadata ->
            dao.insert(
              PrivateVideoEntity(
                videoId = metadata.videoId,
                originalPath = metadata.originalPath,
                privateFilePath = metadata.privateFilePath,
                addedAt = metadata.addedAt,
              ),
            )
          }

        // Remove stale entries (not in JSON or file missing)
        dbEntities
          .filter { it.videoId !in jsonVideoIds || !File(it.privateFilePath).exists() }
          .forEach { dao.delete(it.videoId) }
      }.onFailure { e ->
        Log.e(TAG, "Error syncing from persistent storage", e)
      }
    }

  /**
   * Add a video to private list
   */
  suspend fun addToPrivateList(video: Video): Result<String> =
    PrivateStorageOps.moveToPrivateStorage(context, video).onSuccess { privateFilePath ->
      dao.insert(
        PrivateVideoEntity(
          videoId = video.id,
          originalPath = video.path,
          privateFilePath = privateFilePath,
        ),
      )
    }

  /**
   * Add multiple videos to private list
   */
  suspend fun addMultipleToPrivateList(videos: List<Video>): Pair<Int, Int> =
    videos.fold(0 to 0) { (success, fail), video ->
      addToPrivateList(video).fold(
        onSuccess = { (success + 1) to fail },
        onFailure = { success to (fail + 1) },
      )
    }

  /**
   * Delete video from private storage
   */
  suspend fun deleteFromPrivateStorage(videoId: Long): Boolean =
    dao.getAll().find { it.videoId == videoId }?.let { entity ->
      if (PrivateStorageOps.deleteFromPrivateStorage(context, entity.privateFilePath)) {
        dao.delete(videoId)
        true
      } else {
        false
      }
    } ?: false

  /**
   * Delete multiple videos from private storage
   */
  suspend fun deleteMultipleFromPrivateStorage(videoIds: List<Long>): Pair<Int, Int> =
    videoIds.fold(0 to 0) { (success, fail), videoId ->
      if (deleteFromPrivateStorage(videoId)) (success + 1) to fail else success to (fail + 1)
    }

  /**
   * Restore video to original location
   */
  suspend fun restoreFromPrivateStorage(videoId: Long): Result<String> =
    dao.getAll().find { it.videoId == videoId }?.let { entity ->
      PrivateStorageOps
        .restoreToOriginalLocation(
          context,
          entity.privateFilePath,
          entity.originalPath,
        ).onSuccess {
          dao.delete(videoId)
        }
    } ?: Result.failure(Exception("Video not found"))

  /**
   * Restore multiple videos
   */
  suspend fun restoreMultipleFromPrivateStorage(videoIds: List<Long>): Pair<Int, Int> =
    videoIds.fold(0 to 0) { (success, fail), videoId ->
      restoreFromPrivateStorage(videoId).fold(
        onSuccess = { (success + 1) to fail },
        onFailure = { success to (fail + 1) },
      )
    }
}
