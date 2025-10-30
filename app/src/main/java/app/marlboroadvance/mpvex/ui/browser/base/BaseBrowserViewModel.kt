package app.marlboroadvance.mpvex.ui.browser.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent

/**
 * Base ViewModel for browser screens with shared functionality
 */
abstract class BaseBrowserViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  /**
   * Observable recently played file path for highlighting
   * Automatically filters out non-existent files
   */
  val recentlyPlayedFilePath: StateFlow<String?> =
    RecentlyPlayedOps
      .observeLastPlayedPath()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  /**
   * Refresh the data (to be implemented by subclasses)
   */
  abstract fun refresh()

  /**
   * Delete videos from storage
   * Automatically removes from recently played history
   *
   * @return Pair of (deletedCount, failedCount)
   */
  open suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> = StorageOps.deleteVideos(videos)

  /**
   * Rename a video
   * Automatically updates recently played history
   *
   * @param video Video to rename
   * @param newDisplayName New display name (including extension)
   * @return Result indicating success or failure
   */
  open suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> = StorageOps.renameVideo(getApplication(), video, newDisplayName)
}
