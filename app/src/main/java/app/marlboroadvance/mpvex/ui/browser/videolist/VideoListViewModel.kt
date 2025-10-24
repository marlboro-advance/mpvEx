package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.VideoRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class VideoListViewModel(
  private val application: Application,
  private val bucketId: String,
) : ViewModel() {
  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  // Use Flow to observe recently played changes instead of querying
  val recentlyPlayedFilePath: StateFlow<String?> =
    recentlyPlayedRepository.observeLastPlayedForHighlight()
      .map { it?.filePath }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  private val tag = "VideoListViewModel"

  init {
    loadVideos()
  }

  fun refresh() {
    loadVideos()
  }

  private fun loadVideos() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        val videoList = VideoRepository.getVideosInFolder(application, bucketId)
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
    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }
}
