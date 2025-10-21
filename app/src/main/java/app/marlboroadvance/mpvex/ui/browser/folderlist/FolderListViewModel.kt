package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.data.media.repository.VideoFolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderListViewModel(private val application: Application) : ViewModel() {

  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val tag = "FolderListViewModel"

  init {
    Log.d(tag, "ViewModel initialized, loading folders")
    loadVideoFolders()
  }

  fun refresh() {
    Log.d(tag, "Refresh called, reloading folders")
    loadVideoFolders()
  }

  private fun loadVideoFolders() {
    viewModelScope.launch {
      try {
        Log.d(tag, "Starting to load video folders")
        val folders = VideoFolderRepository.getVideoFolders(application)
        Log.d(tag, "Loaded ${folders.size} folders: ${folders.map { it.name }}")
        _videoFolders.value = folders
      } catch (e: Exception) {
        Log.e(tag, "Error loading video folders", e)
        _videoFolders.value = emptyList()
      }
    }
  }

  companion object {
    fun factory(application: Application) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FolderListViewModel(application) as T
      }
    }
  }
}
