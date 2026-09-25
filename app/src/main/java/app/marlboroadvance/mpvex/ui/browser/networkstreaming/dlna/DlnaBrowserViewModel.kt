package app.marlboroadvance.mpvex.ui.browser.networkstreaming.dlna

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import app.marlboroadvance.mpvex.repository.DlnaRepository
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel for browsing the ContentDirectory of a discovered DLNA media server.
 * The DLNA res URL is plain HTTP, so playback goes straight to the player
 * without the streaming proxy.
 */
class DlnaBrowserViewModel(
  private val application: Application,
  private val deviceUdn: String,
  private val objectId: String,
) : AndroidViewModel(application),
  KoinComponent {
  private val repository: DlnaRepository by inject()

  private val _files = MutableStateFlow<List<NetworkFile>>(emptyList())
  val files: StateFlow<List<NetworkFile>> = _files.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  /**
   * Load the contents of the current container
   */
  fun loadFiles() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null

      repository
        .browse(deviceUdn, objectId)
        .onSuccess { fileList ->
          _files.value =
            fileList.sortedWith(
              compareBy<NetworkFile> { !it.isDirectory }
                .thenBy { it.name.lowercase() },
            )
        }
        .onFailure { e ->
          _error.value = e.message ?: "Unknown error"
        }

      _isLoading.value = false
    }
  }

  /**
   * Play a video item using its direct HTTP resource URL
   */
  fun playVideo(file: NetworkFile) {
    viewModelScope.launch {
      val remoteUri = file.remoteUri
      if (remoteUri == null) {
        _error.value = "No playable resource found for this item"
        return@launch
      }

      val uri = Uri.parse(remoteUri)
      val intent = Intent(Intent.ACTION_VIEW, uri)
      intent.setClass(application, PlayerActivity::class.java)
      intent.putExtra("internal_launch", true)
      intent.putExtra("launch_source", "network_stream")
      intent.putExtra("title", file.name)
      intent.putExtra("filename", file.name)
      intent.setDataAndType(uri, file.mimeType ?: "video/*")
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      application.startActivity(intent)
    }
  }

  companion object {
    private const val TAG = "DlnaBrowserVM"

    fun factory(
      application: Application,
      deviceUdn: String,
      objectId: String,
    ): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          DlnaBrowserViewModel(application, deviceUdn, objectId)
        }
      }
  }
}
