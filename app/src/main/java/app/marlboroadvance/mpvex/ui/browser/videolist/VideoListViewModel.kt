package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
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
  val progressPercentage: Float? = null, // 0.0 to 1.0
  val isOldAndUnplayed: Boolean = false, // true if video is older than threshold and never played
)

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val appearancePreferences: app.marlboroadvance.mpvex.preferences.AppearancePreferences by inject()
  // Using MediaFileRepository singleton directly

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Track if items were deleted/moved leaving folder empty
  private val _videosWereDeletedOrMoved = MutableStateFlow(false)
  val videosWereDeletedOrMoved: StateFlow<Boolean> = _videosWereDeletedOrMoved.asStateFlow()

  // Track previous video count to detect if folder became empty
  private var previousVideoCount = 0

  private val tag = "VideoListViewModel"

  init {
    loadVideos()

    // Listen for global media library changes and refresh this list when they occur
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        // Clear cache when media library changes
        MediaFileRepository.clearCacheForFolder(bucketId)
        loadVideos()
      }
    }
  }

  override fun refresh() {
    // Clear cache for this folder to force fresh data
    MediaFileRepository.clearCacheForFolder(bucketId)
    loadVideos()
  }

  private fun loadVideos() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Get the hidden files preference
        val showHiddenFiles = appearancePreferences.showHiddenFiles.get()

        // First attempt to load videos
        val videoList = MediaFileRepository.getVideosInFolder(getApplication(), bucketId, showHiddenFiles)

        // Check if folder became empty after having videos
        if (previousVideoCount > 0 && videoList.isEmpty()) {
          _videosWereDeletedOrMoved.value = true
          Log.d(tag, "Folder became empty (had $previousVideoCount videos before)")
        } else if (videoList.isNotEmpty()) {
          // Reset flag if folder now has videos
          _videosWereDeletedOrMoved.value = false
        }

        // Update previous count
        previousVideoCount = videoList.size

        if (videoList.isEmpty()) {
          Log.d(tag, "No videos found for bucket $bucketId - attempting media rescan")
          triggerMediaScan()
          delay(1000)
          val retryVideoList = MediaFileRepository.getVideosInFolder(getApplication(), bucketId, showHiddenFiles)

          // Update count after retry
          if (previousVideoCount > 0 && retryVideoList.isEmpty()) {
            _videosWereDeletedOrMoved.value = true
          } else if (retryVideoList.isNotEmpty()) {
            _videosWereDeletedOrMoved.value = false
          }
          previousVideoCount = retryVideoList.size

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

  /**
   * Set flag indicating videos were deleted or moved
   */
  fun setVideosWereDeletedOrMoved() {
    _videosWereDeletedOrMoved.value = true
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val videosWithInfo =
      videos.map { video ->
        val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)

        // Calculate watch progress (0.0 to 1.0)
        val progress = if (playbackState != null && video.duration > 0) {
          // Duration is in milliseconds, convert to seconds
          val durationSeconds = video.duration / 1000
          val timeRemaining = playbackState.timeRemaining.toLong()
          val watched = durationSeconds - timeRemaining
          val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)

          // Only show progress for videos that are 1-99% complete
          if (progressValue in 0.01f..0.99f) progressValue else null
        } else {
          null
        }

        // Check if video is old and unplayed
        // Video is old if it's been more than threshold days since it was added/modified
        // Video is unplayed if there's no playback state record
        val isOldAndUnplayed = playbackState == null

        VideoWithPlaybackInfo(
          video = video,
          timeRemaining = playbackState?.timeRemaining?.toLong(),
          progressPercentage = progress,
          isOldAndUnplayed = isOldAndUnplayed,
        )
      }
    _videosWithPlaybackInfo.value = videosWithInfo
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
