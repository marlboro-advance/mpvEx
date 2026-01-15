package app.marlboroadvance.mpvex.utils.history

import android.annotation.SuppressLint
import android.net.Uri
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

object RecentlyPlayedOps {
  private val repository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)
  private val preferences: AdvancedPreferences by inject(AdvancedPreferences::class.java)

  suspend fun addRecentlyPlayed(
    filePath: String,
    fileName: String,
    videoTitle: String? = null,
    duration: Long = 0,
    fileSize: Long = 0,
    width: Int = 0,
    height: Int = 0,
    launchSource: String? = null,
    playlistId: Int? = null,
  ) {
    // Check if recently played feature is enabled
    if (!preferences.enableRecentlyPlayed.get()) return

    val uri = Uri.parse(filePath)

    if (uri.scheme in listOf("smb", "ftp", "ftps", "webdav", "webdavs")) return
    if (uri.host?.lowercase() in listOf("127.0.0.1", "localhost", "0.0.0.0")) return

    repository.addRecentlyPlayed(
      filePath,
      fileName,
      videoTitle,
      duration,
      fileSize,
      width,
      height,
      launchSource,
      playlistId,
    )
  }

  suspend fun clearAll() {
    repository.clearAll()
  }

  suspend fun updateVideoTitle(
    filePath: String,
    videoTitle: String,
  ) {
    repository.updateVideoTitle(filePath, videoTitle)
  }

  suspend fun updateVideoMetadata(
    filePath: String,
    videoTitle: String?,
    duration: Long,
    fileSize: Long,
    width: Int,
    height: Int,
  ) {
    repository.updateVideoMetadata(filePath, videoTitle, duration, fileSize, width, height)
  }

  suspend fun getLastPlayed(): String? {
    return withContext(Dispatchers.IO) {
      val recent = kotlin.runCatching { repository.getRecentlyPlayed(limit = 50) }.getOrDefault(emptyList())
      for (entity in recent) {
        val path = entity.filePath
        if (isNonFileUri(path)) {
          return@withContext path
        }
        if (fileExists(path)) {
          return@withContext path
        } else {
          kotlin.runCatching { repository.deleteByFilePath(path) }
        }
      }
      null
    }
  }

  suspend fun getLastPlayedEntity(): app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity? {
    return withContext(Dispatchers.IO) {
      val recent = kotlin.runCatching { repository.getRecentlyPlayed(limit = 50) }.getOrDefault(emptyList())
      for (entity in recent) {
        val path = entity.filePath
        if (isNonFileUri(path)) {
          return@withContext entity
        }
        if (fileExists(path)) {
          return@withContext entity
        } else {
          kotlin.runCatching { repository.deleteByFilePath(path) }
        }
      }
      null
    }
  }

  suspend fun hasRecentlyPlayed(): Boolean = withContext(Dispatchers.IO) {
    if (!preferences.enableRecentlyPlayed.get()) return@withContext false
    getLastPlayed() != null
  }
  suspend fun getRecentlyPlayed(limit: Int = 50): List<RecentlyPlayedEntity> {
    return repository.getRecentlyPlayed(limit)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeLastPlayedPath(): Flow<String?> =
    repository
      .observeLastPlayedForHighlight()
      .mapLatest { entity ->
        val path = entity?.filePath
        if (path.isNullOrEmpty()) {
          null
        } else if (fileExists(path)) {
          path
        } else {
          null
        }
      }.distinctUntilChanged()
      .flowOn(Dispatchers.IO)

  suspend fun onVideoDeleted(filePath: String) {
    if (filePath.isBlank()) return
    withContext(Dispatchers.IO) {
      kotlin.runCatching { repository.deleteByFilePath(filePath) }
    }
  }

  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return

    val newFileName = java.io.File(newPath).name
    kotlin
      .runCatching {
        repository.updateFilePath(oldPath, newPath, newFileName)
        android.util.Log.d("RecentlyPlayedOps", "Updated history: $oldPath -> $newPath")
      }.onFailure { e ->
        android.util.Log.w("RecentlyPlayedOps", "Failed to update history path: ${e.message}")
      }
  }

  @SuppressLint("UseKtx")
  private fun fileExists(path: String): Boolean =
    kotlin.runCatching {
      val uri = Uri.parse(path)
      val scheme = uri.scheme
      if (scheme == null || scheme.equals("file", ignoreCase = true)) {
        java.io.File(path).exists()
      } else {
          true
        }
      }.getOrDefault(false)

  @SuppressLint("UseKtx")
  private fun isNonFileUri(path: String): Boolean =
    kotlin.runCatching {
      val scheme = Uri.parse(path).scheme
      scheme != null && !scheme.equals("file", ignoreCase = true)
    }.getOrDefault(false)
}
