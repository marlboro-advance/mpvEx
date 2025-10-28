package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
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
import org.koin.core.component.inject

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val repository: FileSystemVideoRepository by inject()

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Prime cached folders synchronously to avoid initial empty flicker
    try {
      val cached =
        runBlocking(Dispatchers.IO) {
          repository.getCachedFolders(getApplication())
        }
      _videoFolders.value = cached
    } catch (_: Exception) {
      // Ignore, fallback to async load
    }
    loadVideoFolders()

    // Refresh folders on global media library changes (cache already updated by repository)
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        val refreshed = repository.getCachedFolders(getApplication())
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
        // 1) Show cached instantly (no blocking)
        val cached = repository.getCachedFolders(getApplication())
        _videoFolders.value = cached

        // 2) Refresh index in background, then update if changed
        repository.runIndexUpdate(getApplication())
        val refreshed = repository.getCachedFolders(getApplication())
        if (refreshed != _videoFolders.value) {
          _videoFolders.value = refreshed
        }
        // Signal repository that UI has now applied the indexed data
        repository.markInitialIndexApplied()
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _videoFolders.value = emptyList()
      }
    }
  }
}
