package app.marlboroadvance.mpvex.utils.history

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
 * Single source of truth for all recently played operations.
 *
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
      val entity = repository.getLastPlayed() ?: return@withContext null
      val path = entity.filePath
      if (fileExists(path)) path else null
    }
  }

  /**
   * Check if there's a valid recently played file
   * Automatically prunes invalid entries
   *
   * @return True if there's a recently played file that exists
   */
  suspend fun hasRecentlyPlayed(): Boolean {
    return withContext(Dispatchers.IO) {
      val last = repository.getLastPlayed() ?: return@withContext false
      val path = last.filePath
      val exists = fileExists(path)
      if (!exists) {
        // Auto-prune invalid entry
        kotlin.runCatching { repository.deleteByFilePath(path) }
        false
      } else true
    }
  }

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
        if (path.isNullOrEmpty()) null else if (fileExists(path)) path else null
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)

  // ========== MAINTENANCE OPERATIONS ==========

  /**
   * Prune invalid recently played entries (files that no longer exist)
   *
   * @return True if an entry was pruned, false if no pruning needed
   */
  suspend fun pruneIfMissing(): Boolean {
    return withContext(Dispatchers.IO) {
      val last = repository.getLastPlayed() ?: return@withContext false
      val exists = fileExists(last.filePath)
      if (!exists) {
        kotlin.runCatching { repository.deleteByFilePath(last.filePath) }
        true
      } else false
    }
  }

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
  suspend fun onVideoRenamed(oldPath: String, newPath: String) {
    // Extract the new filename from the new path
    val newFileName = java.io.File(newPath).name
    kotlin.runCatching {
      repository.updateFilePath(oldPath, newPath, newFileName)
    }
  }

  // ========== INTERNAL HELPERS ==========

  /**
   * Check if a file exists on the filesystem
   */
  private fun fileExists(path: String): Boolean =
    kotlin.runCatching { java.io.File(path).exists() }.getOrDefault(false)
}
