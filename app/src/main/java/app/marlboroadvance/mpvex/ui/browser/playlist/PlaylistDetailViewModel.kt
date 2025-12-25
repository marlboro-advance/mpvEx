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
import app.marlboroadvance.mpvex.repository.MediaFileRepository
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
  // Using MediaFileRepository singleton directly

  private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
  val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

  private val _videoItems = MutableStateFlow<List<PlaylistVideoItem>>(emptyList())
  val videoItems: StateFlow<List<PlaylistVideoItem>> = _videoItems.asStateFlow()

  // Start as loading to avoid briefly showing "No videos in playlist" before the first DB emission arrives,
  // especially noticeable for very large playlists.
  private val _isLoading = MutableStateFlow(true)
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
        _isLoading.value = true
        try {
          if (items.isEmpty()) {
            _videoItems.value = emptyList()
          } else {
            // Check if this is an M3U playlist
            val playlist = _playlist.value
            val isM3uPlaylist = playlist?.isM3uPlaylist == true

            if (isM3uPlaylist) {
              // For M3U playlists, create Video objects directly from URLs
              val videoItems = items.mapNotNull { item ->
                try {
                  // Create a Video object for streaming URLs
                  val video = Video(
                    id = item.id.toLong(),
                    title = item.fileName,
                    displayName = item.fileName,
                    path = item.filePath,
                    uri = android.net.Uri.parse(item.filePath),
                    duration = 0L, // Duration unknown for streams
                    durationFormatted = "00:00",
                    size = 0L, // Size unknown for streams
                    sizeFormatted = "0 B",
                    dateModified = item.addedAt,
                    dateAdded = item.addedAt,
                    mimeType = "video/*",
                    bucketId = "m3u_playlist_$playlistId",
                    bucketDisplayName = playlist?.name ?: "M3U Playlist",
                    width = 0,
                    height = 0,
                    fps = 0f,
                    resolution = "Unknown"
                  )
                  PlaylistVideoItem(item, video)
                } catch (e: Exception) {
                  Log.w(TAG, "Failed to create video item for URL: ${item.filePath}", e)
                  null
                }
              }

              Log.d(TAG, "Loaded ${videoItems.size} M3U playlist items")
              _videoItems.value = videoItems
            } else {
              // For regular playlists, use the existing logic with MediaFileRepository
              // Get unique bucket IDs from playlist items' parent folders
              val bucketIds = items.map { item ->
                File(item.filePath).parent ?: ""
              }.toSet()

              // Get all videos from those folders (uses cache)
              val allVideos = MediaFileRepository.getVideosForBuckets(getApplication(), bucketIds)

              // Match videos by path, maintaining playlist order
              val videoItems = items.mapNotNull { item ->
                val matchedVideo = allVideos.find { video -> video.path == item.filePath }
                if (matchedVideo != null) {
                  PlaylistVideoItem(item, matchedVideo)
                } else {
                  Log.w(TAG, "Video not found for path: ${item.filePath}")
                  null
                }
              }

              Log.d(TAG, "Loaded ${videoItems.size} videos out of ${items.size} playlist items")
              _videoItems.value = videoItems
            }
          }
        } finally {
          _isLoading.value = false
        }
      }
    }
  }

  override fun refresh() {
    // Refresh is handled automatically through Flow observation
    viewModelScope.launch(Dispatchers.IO) { refreshNow() }
  }

  /**
   * Refresh playlist items and associated [Video] metadata, awaiting completion.
   *
   * This is useful for UI gestures like pull-to-refresh that need to know when refreshing is done.
   */
  suspend fun refreshNow() {
    try {
      _isLoading.value = true
      // Trigger a refresh by reloading playlist items
      val items = playlistRepository.getPlaylistItems(playlistId)
      val playlist = _playlist.value
      val isM3uPlaylist = playlist?.isM3uPlaylist == true

      if (items.isNotEmpty()) {
        if (isM3uPlaylist) {
          // For M3U playlists, create Video objects directly from URLs
          val videoItems = items.mapNotNull { item ->
            try {
              val video = Video(
                id = item.id.toLong(),
                title = item.fileName,
                displayName = item.fileName,
                path = item.filePath,
                uri = android.net.Uri.parse(item.filePath),
                duration = 0L,
                durationFormatted = "00:00",
                size = 0L,
                sizeFormatted = "0 B",
                dateModified = item.addedAt,
                dateAdded = item.addedAt,
                mimeType = "video/*",
                bucketId = "m3u_playlist_$playlistId",
                bucketDisplayName = playlist?.name ?: "M3U Playlist",
                width = 0,
                height = 0,
                fps = 0f,
                resolution = "Unknown"
              )
              PlaylistVideoItem(item, video)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to create video item for URL: ${item.filePath}", e)
              null
            }
          }
          _videoItems.value = videoItems
        } else {
          // For regular playlists, use existing logic
          val bucketIds = items.map { item ->
            File(item.filePath).parent ?: ""
          }.toSet()
          val allVideos = MediaFileRepository.getVideosForBuckets(getApplication(), bucketIds)
          val videoItems = items.mapNotNull { item ->
            allVideos.find { video -> video.path == item.filePath }?.let { video ->
              PlaylistVideoItem(item, video)
            }
          }
          _videoItems.value = videoItems
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error refreshing playlist videos", e)
    } finally {
      _isLoading.value = false
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

  suspend fun removeVideosFromPlaylist(videos: List<Video>) {
    val items = playlistRepository.getPlaylistItems(playlistId)
    val videoPaths = videos.map { it.path }.toSet()
    val itemsToRemove = items.filter { it.filePath in videoPaths }
    playlistRepository.removeItemsFromPlaylist(itemsToRemove)
  }

  suspend fun updatePlayHistory(filePath: String, position: Long = 0) {
    playlistRepository.updatePlayHistory(playlistId, filePath, position)
  }

  suspend fun reorderPlaylistItems(fromIndex: Int, toIndex: Int) {
    val currentItems = _videoItems.value.toMutableList()
    if (fromIndex < 0 || fromIndex >= currentItems.size || toIndex < 0 || toIndex >= currentItems.size) {
      return
    }

    // Reorder the list locally first
    val item = currentItems.removeAt(fromIndex)
    currentItems.add(toIndex, item)
    _videoItems.value = currentItems

    // Persist to database
    val newOrder = currentItems.map { it.playlistItem.id }
    playlistRepository.reorderPlaylistItems(playlistId, newOrder)
  }

  suspend fun refreshM3UPlaylist(): Result<Unit> {
    return try {
      _isLoading.value = true
      playlistRepository.refreshM3UPlaylist(playlistId)
    } finally {
      _isLoading.value = false
    }
  }
}
