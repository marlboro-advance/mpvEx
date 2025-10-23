package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class FolderListViewModel(
  private val application: Application,
) : ViewModel() {
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val _recentlyPlayedFilePath = MutableStateFlow<String?>(null)
  val recentlyPlayedFilePath: StateFlow<String?> = _recentlyPlayedFilePath.asStateFlow()

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    loadVideoFolders()
    loadRecentlyPlayed()
  }

  fun refresh() {
    loadVideoFolders()
    loadRecentlyPlayed()
  }

  private fun loadVideoFolders() {
    viewModelScope.launch {
      try {
        val folders = VideoFolderRepository.getVideoFolders(application)
        _videoFolders.value = folders
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _videoFolders.value = emptyList()
      }
    }
  }

  private fun loadRecentlyPlayed() {
    viewModelScope.launch {
      try {
        val recentlyPlayed = recentlyPlayedRepository.getLastPlayedForHighlight()
        _recentlyPlayedFilePath.value = recentlyPlayed?.filePath
      } catch (e: Exception) {
        Log.e(TAG, "Error loading recently played", e)
        _recentlyPlayedFilePath.value = null
      }
    }
  }
}
