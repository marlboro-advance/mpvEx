package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.repository.VideoRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.media.MediaStoreObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val foldersPreferences: FoldersPreferences by inject()

  private val _allVideoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  // MediaStore observer for external changes
  private val mediaStoreObserver = MediaStoreObserver(application, viewModelScope)

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Load folders asynchronously on initialization
    loadVideoFolders()

    // Start observing MediaStore for external changes
    viewModelScope.launch {
      mediaStoreObserver.startObserving()
    }

    // Refresh folders on global media library changes
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadVideoFolders()
      }
    }

    // Filter folders based on blacklist
    viewModelScope.launch {
      combine(_allVideoFolders, foldersPreferences.blacklistedFolders.changes()) { folders, blacklist ->
        folders.filter { folder -> folder.path !in blacklist }
      }.collectLatest { filteredFolders ->
        _videoFolders.value = filteredFolders
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    // Stop observing when ViewModel is destroyed
    viewModelScope.launch {
      mediaStoreObserver.stopObserving()
    }
  }

  override fun refresh() {
    loadVideoFolders()
  }

  private fun loadVideoFolders() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val folders = VideoFolderRepository.getVideoFolders(getApplication())
        _allVideoFolders.value = folders
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _allVideoFolders.value = emptyList()
      }
    }
  }
}
