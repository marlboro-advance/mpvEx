package app.marlboroadvance.mpvex.ui.browser.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

data class PlaylistWithCount(
  val playlist: PlaylistEntity,
  val itemCount: Int,
)

class PlaylistViewModel(
  application: Application,
) : androidx.lifecycle.AndroidViewModel(application),
  KoinComponent {
  private val repository: PlaylistRepository by inject()
  private val videoRepository: VideoRepository by inject()

  private val _playlistsWithCount = MutableStateFlow<List<PlaylistWithCount>>(emptyList())
  val playlistsWithCount: StateFlow<List<PlaylistWithCount>> = _playlistsWithCount.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  companion object {
    private const val TAG = "PlaylistViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistViewModel(application) as T
      }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      repository.observeAllPlaylists().collectLatest { playlistsFromDb ->
        val sortedPlaylists = playlistsFromDb.sortedBy { it.name.lowercase() }

        val playlistsWithCounts = sortedPlaylists.map { playlist ->
          val count = getActualVideoCount(playlist.id)
          PlaylistWithCount(playlist, count)
        }

        _playlistsWithCount.value = playlistsWithCounts
      }
    }
  }

  /**
   * Get the actual count of videos that exist for a playlist
   */
  private suspend fun getActualVideoCount(playlistId: Int): Int {
    val items = repository.getPlaylistItems(playlistId)
    if (items.isEmpty()) return 0

    val bucketIds = items.map { item ->
      File(item.filePath).parent ?: ""
    }.toSet()

    val allVideos = videoRepository.getVideosForBuckets(getApplication(), bucketIds)

    return items.count { item ->
      allVideos.any { video -> video.path == item.filePath }
    }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        val playlistsFromDb = repository.getAllPlaylists()
        val sortedPlaylists = playlistsFromDb.sortedBy { it.name.lowercase() }

        val playlistsWithCounts = sortedPlaylists.map { playlist ->
          val count = getActualVideoCount(playlist.id)
          PlaylistWithCount(playlist, count)
        }

        _playlistsWithCount.value = playlistsWithCounts
      } catch (e: Exception) {
        Log.e(TAG, "Error refreshing playlists", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  suspend fun createPlaylist(name: String): Long {
    return repository.createPlaylist(name)
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    repository.deletePlaylist(playlist)
  }
}
