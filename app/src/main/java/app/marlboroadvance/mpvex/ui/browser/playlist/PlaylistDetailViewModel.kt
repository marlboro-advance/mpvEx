package app.marlboroadvance.mpvex.ui.browser.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistItemEntity
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.repository.VideoRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

data class PlaylistVideoItem(
  val playlistItem: PlaylistItemEntity,
  val video: Video,
)

class PlaylistDetailViewModel(
  application: Application,
  private val playlistId: Int,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playlistRepository: PlaylistRepository by inject()
  private val videoRepository: VideoRepository by inject()

  private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
  val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

  private val _videoItems = MutableStateFlow<List<PlaylistVideoItem>>(emptyList())
  val videoItems: StateFlow<List<PlaylistVideoItem>> = _videoItems.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  companion object {
    private const val TAG = "PlaylistDetailViewModel"

    fun factory(
      application: Application,
      playlistId: Int,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlaylistDetailViewModel(application, playlistId) as T
    }
  }

  init {
    // Observe playlist info
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.observePlaylistById(playlistId).collectLatest { playlist ->
        _playlist.value = playlist
      }
    }

    // Observe playlist items and load video metadata
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.observePlaylistItems(playlistId).collectLatest { items ->
        if (items.isEmpty()) {
          _videoItems.value = emptyList()
        } else {
          // Get unique bucket IDs from playlist items' parent folders
          val bucketIds = items.map { item ->
            File(item.filePath).parent ?: ""
          }.toSet()

          // Get all videos from those folders (uses cache)
          val allVideos = videoRepository.getVideosForBuckets(getApplication(), bucketIds)

          // Match videos by path, maintaining playlist order
          val videoItems = items.mapNotNull { item ->
            val matchedVideo = allVideos.find { video -> video.path == item.filePath }
            if (matchedVideo != null) {
              PlaylistVideoItem(item, matchedVideo)
            } else {
              Log.w(TAG, "Video not found in MediaStore for path: ${item.filePath}")
              null
            }
          }

          Log.d(TAG, "Loaded ${videoItems.size} videos out of ${items.size} playlist items")
          _videoItems.value = videoItems
        }
      }
    }
  }

  override fun refresh() {
    // Refresh is handled automatically through Flow observation
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        // Trigger a refresh by reloading playlist items
        val items = playlistRepository.getPlaylistItems(playlistId)
        if (items.isNotEmpty()) {
          val bucketIds = items.map { item ->
            File(item.filePath).parent ?: ""
          }.toSet()
          val allVideos = videoRepository.getVideosForBuckets(getApplication(), bucketIds)
          val videoItems = items.mapNotNull { item ->
            allVideos.find { video -> video.path == item.filePath }?.let { video ->
              PlaylistVideoItem(item, video)
            }
          }
          _videoItems.value = videoItems
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error refreshing playlist videos", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  suspend fun updatePlaylistName(newName: String) {
    _playlist.value?.let { playlist ->
      playlistRepository.updatePlaylist(playlist.copy(name = newName))
    }
  }

  suspend fun removeVideoFromPlaylist(video: Video) {
    val items = playlistRepository.getPlaylistItems(playlistId)
    val itemToRemove = items.find { it.filePath == video.path }
    itemToRemove?.let {
      playlistRepository.removeItemFromPlaylist(it)
    }
  }

  suspend fun updatePlayHistory(filePath: String, position: Long = 0) {
    playlistRepository.updatePlayHistory(playlistId, filePath, position)
  }
}
