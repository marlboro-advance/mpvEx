package app.marlboroadvance.mpvex.ui.browser.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base ViewModel for browser screens with shared functionality
 */
abstract class BaseBrowserViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  private val metadataCache: VideoMetadataCacheRepository by inject()
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
   * Automatically removes from recently played history and invalidates cache
   *
   * @return Pair of (deletedCount, failedCount)
   */
  open suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    val result = StorageOps.deleteVideos(videos)

    // Invalidate cache for deleted videos
    val paths = videos.map { it.path }
    metadataCache.invalidateVideos(paths)

    return result
  }

  /**
   * Rename a video
   * Automatically updates recently played history and invalidates old cache entry
   *
   * @param video Video to rename
   * @param newDisplayName New display name (including extension)
   * @return Result indicating success or failure
   */
  open suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> {
    val oldPath = video.path
    val result = StorageOps.renameVideo(getApplication(), video, newDisplayName)

    // Invalidate cache for old path (new path will be re-cached on next access)
    result.onSuccess {
      metadataCache.invalidateVideo(oldPath)
    }

    return result
  }
}
