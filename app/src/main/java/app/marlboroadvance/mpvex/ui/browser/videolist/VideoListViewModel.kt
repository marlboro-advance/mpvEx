package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.repository.VideoRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.media.MediaStoreObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class VideoWithPlaybackInfo(
  val video: Video,
  val timeRemaining: Long? = null, // in seconds
  val timeRemainingFormatted: String? = null,
)

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // MediaStore observer for external changes
  private val mediaStoreObserver = MediaStoreObserver(application, viewModelScope)

  private val tag = "VideoListViewModel"

  init {
    loadVideos()
    // Start observing MediaStore for external changes
    viewModelScope.launch {
      mediaStoreObserver.startObserving()
    }
    // Listen for global media library changes and refresh this list when they occur
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadVideos()
      }
    }
  }

  override fun refresh() {
    loadVideos()
  }

  override fun onCleared() {
    super.onCleared()
    // Stop observing when ViewModel is destroyed
    viewModelScope.launch {
      mediaStoreObserver.stopObserving()
    }
  }

  private fun loadVideos() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true

        // First attempt to load videos
        val videoList = VideoRepository.getVideosInFolder(getApplication(), bucketId)

        if (videoList.isEmpty()) {
          Log.d(tag, "No videos found for bucket $bucketId - attempting media rescan")
          // Trigger a media scan to refresh MediaStore
          triggerMediaScan()
          // Wait longer for MediaStore to update
          delay(1000)
          val retryVideoList = VideoRepository.getVideosInFolder(getApplication(), bucketId)
          _videos.value = retryVideoList
          loadPlaybackInfo(retryVideoList)
        } else {
          _videos.value = videoList
          loadPlaybackInfo(videoList)
        }
      } catch (e: Exception) {
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        _videos.value = emptyList()
        _videosWithPlaybackInfo.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val videosWithInfo =
      videos.map { video ->
        val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)
        // Only show time remaining if it's more than 3 minutes (180 seconds)
        val timeRemaining = playbackState?.timeRemaining?.takeIf { it > 180 }?.toLong()

        VideoWithPlaybackInfo(
          video = video,
          timeRemaining = timeRemaining,
          timeRemainingFormatted = timeRemaining?.let { formatTimeRemaining(it) },
        )
      }
    _videosWithPlaybackInfo.value = videosWithInfo
  }

  private fun formatTimeRemaining(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
      hours > 0 -> "${hours}h ${minutes}m remaining"
      minutes > 0 -> "${minutes}m remaining"
      else -> "${seconds}s remaining"
    }
  }

  private fun triggerMediaScan() {
    try {
      // Trigger a comprehensive media scan
      val externalStorage = android.os.Environment.getExternalStorageDirectory()

      android.media.MediaScannerConnection.scanFile(
        getApplication(),
        arrayOf(externalStorage.absolutePath),
        arrayOf("video/*"),
      ) { path, uri ->
        Log.d(tag, "Media scan completed for: $path -> $uri")
      }

      Log.d(tag, "Triggered media scan for: ${externalStorage.absolutePath}")
    } catch (e: Exception) {
      Log.e(tag, "Failed to trigger media scan", e)
    }
  }

  companion object {
    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }
}
