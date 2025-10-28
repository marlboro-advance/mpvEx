package app.marlboroadvance.mpvex.utils.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Observes storage volume changes (e.g., USB OTG mount/unmount) and triggers re-indexing.
 */
class StorageMonitor(
  private val context: Context,
  private val repository: FileSystemVideoRepository,
) {
  private val tag = "StorageMonitor"
  private val storageManager: StorageManager? = context.getSystemService(StorageManager::class.java)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val bgExecutor = Executors.newSingleThreadExecutor()

  private var isStarted = false

  private val mediaReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        ctx: Context?,
        intent: Intent?,
      ) {
        when (intent?.action) {
          Intent.ACTION_MEDIA_MOUNTED,
          Intent.ACTION_MEDIA_UNMOUNTED,
          Intent.ACTION_MEDIA_REMOVED,
          Intent.ACTION_MEDIA_EJECT,
          -> {
            Log.d(tag, "Storage media event: ${intent.action}")
            triggerReindex()
          }
        }
      }
    }

  private val volumeCallback: Any? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      object : StorageManager.StorageVolumeCallback() {
        override fun onStateChanged(volume: StorageVolume) {
          Log.d(tag, "Volume state changed: ${volume.getDescription(context)}")
          triggerReindex()
        }
      }
    } else {
      null
    }

  fun start() {
    if (isStarted) return
    isStarted = true
    runCatching {
      // Runtime-registered receiver for legacy media intents
      val filter =
        IntentFilter().apply {
          addAction(Intent.ACTION_MEDIA_MOUNTED)
          addAction(Intent.ACTION_MEDIA_UNMOUNTED)
          addAction(Intent.ACTION_MEDIA_REMOVED)
          addAction(Intent.ACTION_MEDIA_EJECT)
          addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
          addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
          addDataScheme("file")
        }
      context.registerReceiver(mediaReceiver, filter)
    }.onFailure { e -> Log.w(tag, "Failed to register media receiver", e) }

    // StorageVolumeCallback for API 30+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching {
        storageManager?.registerStorageVolumeCallback(
          bgExecutor,
          volumeCallback as StorageManager.StorageVolumeCallback,
        )
      }.onFailure { e -> Log.w(tag, "Failed to register StorageVolumeCallback", e) }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun triggerReindex() {
    // Debounce on main thread to avoid too frequent triggers
    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.postDelayed(
      {
        // Fire and forget; repository serializes scans internally
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
          repository.runIndexUpdate(context)
        }
      },
      500,
    )
  }

  // Eagerly start monitoring when constructed via DI
  init {
    try {
      start()
    } catch (_: Throwable) {
    }
  }
}
