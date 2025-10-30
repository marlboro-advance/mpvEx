package app.marlboroadvance.mpvex.utils.media

import android.util.Log
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import org.koin.java.KoinJavaComponent.inject
import java.io.File

/**
 * Utility for managing playback state when files are renamed or deleted.
 */
object PlaybackStateOps {
  private const val TAG = "PlaybackStateOps"
  private val repository: PlaybackStateRepository by inject(PlaybackStateRepository::class.java)

  /**
   * Called when a video file is renamed
   * Updates the playback state entry to use the new filename
   *
   * @param oldPath The original file path
   * @param newPath The new file path after renaming
   */
  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return

    try {
      val oldFileName = File(oldPath).name
      val newFileName = File(newPath).name

      // Only update if the filename actually changed
      if (oldFileName != newFileName) {
        repository.updateMediaTitle(oldFileName, newFileName)
        Log.d(TAG, "✓ Updated playback state: $oldFileName -> $newFileName")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update playback state: ${e.message}")
    }
  }

  /**
   * Called when a video file is deleted
   * Removes its playback state entry
   *
   * @param filePath The path of the deleted file
   */
  suspend fun onVideoDeleted(filePath: String) {
    if (filePath.isBlank()) return

    try {
      val fileName = File(filePath).name
      repository.deleteByTitle(fileName)
      Log.d(TAG, "✓ Deleted playback state for: $fileName")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete playback state: ${e.message}")
    }
  }
}
