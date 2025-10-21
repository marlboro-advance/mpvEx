package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.data.media.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoListViewModel(
  private val application: Application,
  private val bucketId: String,
  folderName: String,
) : ViewModel() {

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val tag = "VideoListViewModel"

  init {
    Log.d(tag, "ViewModel initialized for bucket: $bucketId, folder: $folderName")
    loadVideos()
  }

  fun refresh() {
    Log.d(tag, "Refresh called for bucket: $bucketId")
    loadVideos()
  }

  private fun loadVideos() {
    viewModelScope.launch {
      try {
        _isLoading.value = true
        Log.d(tag, "Starting to load videos for bucket: $bucketId")
        val videoList = VideoRepository.getVideosInFolder(application, bucketId)
        Log.d(tag, "Loaded ${videoList.size} videos")
        _videos.value = videoList
      } catch (e: Exception) {
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        _videos.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  companion object {
    fun factory(application: Application, bucketId: String, folderName: String) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return VideoListViewModel(application, bucketId, folderName) as T
        }
      }
  }
}
