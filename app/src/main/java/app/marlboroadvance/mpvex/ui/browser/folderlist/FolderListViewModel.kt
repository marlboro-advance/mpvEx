package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Show MediaStore folders immediately (no DB dependency)
    try {
      val mediaStoreFolders = runBlocking(Dispatchers.IO) { VideoFolderRepository.getVideoFolders(getApplication()) }
      _videoFolders.value = mediaStoreFolders
    } catch (_: Exception) {
    }
    loadVideoFolders()

    // Refresh folders on global media library changes (cache already updated by repository)
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        val refreshed = VideoFolderRepository.getVideoFolders(getApplication())
        _videoFolders.value = refreshed
      }
    }
  }

  override fun refresh() {
    loadVideoFolders()
  }

  private fun loadVideoFolders() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val folders = VideoFolderRepository.getVideoFolders(getApplication())
        _videoFolders.value = folders
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _videoFolders.value = emptyList()
      }
    }
  }
}
