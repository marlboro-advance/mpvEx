package app.marlboroadvance.mpvex.ui.browser.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
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
  // Using MediaFileRepository singleton directly

  private val _playlistsWithCount = MutableStateFlow<List<PlaylistWithCount>>(emptyList())
  val playlistsWithCount: StateFlow<List<PlaylistWithCount>> = _playlistsWithCount.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Track if initial load has completed to prevent empty state flicker
  private val _hasCompletedInitialLoad = MutableStateFlow(false)
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  companion object {
    private const val TAG = "PlaylistViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistViewModel(application) as T
      }
  }

  init {
    // Load cached playlists instantly for immediate display
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Get initial cached data synchronously
        val cachedPlaylists = repository.getAllPlaylists()
        if (cachedPlaylists.isNotEmpty()) {
          // Show cached data immediately (without video counts for speed)
          val quickLoad = cachedPlaylists.sortedBy { it.name.lowercase() }.map { playlist ->
            PlaylistWithCount(playlist, 0) // Show 0 count initially
          }
          _playlistsWithCount.value = quickLoad
          _hasCompletedInitialLoad.value = true
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading cached playlists", e)
      }
    }

    // Then observe for updates with actual counts
    viewModelScope.launch(Dispatchers.IO) {
      repository.observeAllPlaylists().collectLatest { playlistsFromDb ->
        val sortedPlaylists = playlistsFromDb.sortedBy { it.name.lowercase() }

        val playlistsWithCounts = sortedPlaylists.map { playlist ->
          val count = getActualVideoCount(playlist.id)
          PlaylistWithCount(playlist, count)
        }

        _playlistsWithCount.value = playlistsWithCounts
        _hasCompletedInitialLoad.value = true
      }
    }
  }

  /**
   * Get the actual count of videos that exist for a playlist
   */
  private suspend fun getActualVideoCount(playlistId: Int): Int {
    val playlist = repository.getPlaylistById(playlistId)
    val items = repository.getPlaylistItems(playlistId)
    if (items.isEmpty()) return 0

    // For M3U playlists, return item count directly (URLs don't need file system check)
    if (playlist?.isM3uPlaylist == true) {
      return items.size
    }

    // For regular playlists, check if files still exist
    val bucketIds = items.map { item ->
      File(item.filePath).parent ?: ""
    }.toSet()

    val allVideos = MediaFileRepository.getVideosForBuckets(getApplication(), bucketIds)

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

  suspend fun createM3UPlaylist(url: String): Result<Long> {
    return repository.createM3UPlaylist(url)
  }

  suspend fun refreshM3UPlaylist(playlistId: Int): Result<Unit> {
    return repository.refreshM3UPlaylist(playlistId)
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    repository.deletePlaylist(playlist)
  }
}
