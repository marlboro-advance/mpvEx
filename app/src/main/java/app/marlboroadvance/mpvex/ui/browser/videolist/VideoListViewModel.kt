package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.repository.VideoRepository
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

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val tag = "VideoListViewModel"

  init {
    loadVideos()
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

  private fun loadVideos() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true

        // Trigger media scan for the directory if no videos found
        // This helps when MediaStore is out of sync
        val videoList = VideoRepository.getVideosInFolder(getApplication(), bucketId)

        if (videoList.isEmpty()) {
          Log.d(tag, "No videos found for bucket $bucketId - MediaStore might be out of sync")
          // Trigger a media scan to refresh MediaStore
          triggerMediaScan()
          // Try loading again after a short delay
          delay(500)
          val retryVideoList = VideoRepository.getVideosInFolder(getApplication(), bucketId)
          _videos.value = retryVideoList
        } else {
          _videos.value = videoList
        }
      } catch (e: Exception) {
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        _videos.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  private fun triggerMediaScan() {
    try {
      // Trigger a media scan for all video directories
      val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
      android.media.MediaScannerConnection.scanFile(
        getApplication(),
        arrayOf(
          android.os.Environment
            .getExternalStorageDirectory()
            .absolutePath,
        ),
        null,
      ) { path, uri ->
        Log.d(tag, "Media scan completed for: $path")
      }
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
