package app.marlboroadvance.mpvex.utils.history

import android.annotation.SuppressLint
import android.net.Uri
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

/**
 * This object consolidates all recently played logic including:
 * - Recording playback history
 * - Retrieving recently played items
 * - File validation and pruning
 * - Cleanup on delete/rename
 * - UI state observing
 *
 * All components should use this instead of directly accessing the repository.
 */
object RecentlyPlayedOps {
  private val repository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  // ========== WRITE OPERATIONS ==========

  /**
   * Record a video playback
   *
   * @param filePath Full path to the video file
   * @param fileName Display name of the file
   * @param launchSource Optional source identifier (e.g., "folder_list", "open_file")
   */
  suspend fun addRecentlyPlayed(
    filePath: String,
    fileName: String,
    launchSource: String? = null,
  ) {
    repository.addRecentlyPlayed(filePath, fileName, launchSource)
  }

  /**
   * Clear all recently played history
   */
  suspend fun clearAll() {
    repository.clearAll()
  }

  // ========== READ OPERATIONS ==========

  /**
   * Get the most recently played file path (if it still exists)
   *
   * @return File path string, or null if no recently played or file doesn't exist
   */
  suspend fun getLastPlayed(): String? {
    return withContext(Dispatchers.IO) {
      // Fetch a batch of recent entries to find the first playable one
      val recent = kotlin.runCatching { repository.getRecentlyPlayed(limit = 50) }.getOrDefault(emptyList())
      for (entity in recent) {
        val path = entity.filePath
        if (isNonFileUri(path)) {
          return@withContext path
        }
        if (fileExists(path)) {
          return@withContext path
        } else {
          // Prune missing local file entries as we encounter them
          kotlin.runCatching { repository.deleteByFilePath(path) }
        }
      }
      null
    }
  }

  /**
   * Check if there's a valid recently played file
   * Automatically prunes invalid entries
   *
   * @return True if there's a recently played file that exists
   */
  suspend fun hasRecentlyPlayed(): Boolean = withContext(Dispatchers.IO) { getLastPlayed() != null }

  // ========== FLOW OPERATIONS (for UI observing) ==========

  /**
   * Observe the most recently played file path for UI highlighting
   * Returns null if file doesn't exist (for hiding highlights)
   *
   * @return Flow of file path (or null)
   */
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

  // ========== MAINTENANCE OPERATIONS ==========

  // ========== EVENT HANDLERS ==========

  /**
   * Called when a video is deleted
   * Removes it from recently played history
   */
  suspend fun onVideoDeleted(filePath: String) {
    if (filePath.isBlank()) return
    withContext(Dispatchers.IO) {
      kotlin.runCatching { repository.deleteByFilePath(filePath) }
    }
  }

  /**
   * Called when a video is renamed
   * Updates the path and filename in recently played history
   *
   * @param oldPath The original file path
   * @param newPath The new file path after renaming
   */
  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return

    // Extract the new filename from the new path
    val newFileName = java.io.File(newPath).name
    kotlin
      .runCatching {
        repository.updateFilePath(oldPath, newPath, newFileName)
        android.util.Log.d("RecentlyPlayedOps", "✓ Updated history: $oldPath -> $newPath")
      }.onFailure { e ->
        android.util.Log.w("RecentlyPlayedOps", "Failed to update history path: ${e.message}")
      }
  }

  // ========== INTERNAL HELPERS ==========

  /**
   * Check if a file exists on the filesystem
   */
  @SuppressLint("UseKtx")
  private fun fileExists(path: String): Boolean =
    kotlin
      .runCatching {
        val uri = Uri.parse(path)
        val scheme = uri.scheme
        // Treat non-file schemes (http, https, rtsp, rtmp, content, etc.) as valid and non-prunable
        if (scheme == null || scheme.equals("file", ignoreCase = true)) {
          java.io.File(path).exists()
        } else {
          // Non-file schemes are not checked against filesystem
          true
        }
      }.getOrDefault(false)

  @SuppressLint("UseKtx")
  private fun isNonFileUri(path: String): Boolean =
    kotlin
      .runCatching {
        val scheme = Uri.parse(path).scheme
        scheme != null && !scheme.equals("file", ignoreCase = true)
      }.getOrDefault(false)
}
