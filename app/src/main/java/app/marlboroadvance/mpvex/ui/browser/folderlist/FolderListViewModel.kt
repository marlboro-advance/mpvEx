package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.data.media.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderListViewModel(
    application: Application,
) : BaseBrowserViewModel(application) {
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
        loadVideoFolders()
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
