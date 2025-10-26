package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.VideoRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoListViewModel(
    application: Application,
    private val bucketId: String,
) : BaseBrowserViewModel(application) {
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val tag = "VideoListViewModel"

    init {
        loadVideos()
    }

    override fun refresh() {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val videoList = VideoRepository.getVideosInFolder(getApplication(), bucketId)
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
