package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.usb.UsbOtgManager
import app.marlboroadvance.mpvex.utils.usb.UsbVideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val _usbOtgFolders = MutableStateFlow<List<UsbVideoFolder>>(emptyList())
  val usbOtgFolders: StateFlow<List<UsbVideoFolder>> = _usbOtgFolders.asStateFlow()

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
    scanUsbDevices()

    // Refresh folders on global media library changes
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadVideoFolders()
      }
    }

    // Listen for USB OTG device changes
    viewModelScope.launch(Dispatchers.IO) {
      UsbOtgManager.usbOtgDevices.collectLatest { devices ->
        Log.d(TAG, "USB devices changed: ${devices.size} device(s) detected")
        loadUsbVideoFolders()
      }
    }
  }

  override fun refresh() {
    loadVideoFolders()
    scanUsbDevices()
  }

  fun scanUsbDevices() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        UsbOtgManager.scanForUsbDevices(getApplication())
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning USB devices", e)
      }
    }
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

  private fun loadUsbVideoFolders() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val allUsbFolders = mutableListOf<UsbVideoFolder>()
        val devices = UsbOtgManager.usbOtgDevices.value

        for (device in devices) {
          val folders = UsbOtgManager.getUsbVideoFolders(device)
          allUsbFolders.addAll(folders)
          Log.d(TAG, "Loaded ${folders.size} video folders from USB device: ${device.name}")
        }

        _usbOtgFolders.value = allUsbFolders
        Log.d(TAG, "Total USB video folders: ${allUsbFolders.size}")
      } catch (e: Exception) {
        Log.e(TAG, "Error loading USB video folders", e)
        _usbOtgFolders.value = emptyList()
      }
    }
  }
}
