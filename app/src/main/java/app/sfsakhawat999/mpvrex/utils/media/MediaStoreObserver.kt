package app.sfsakhawat999.mpvrex.utils.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes changes in MediaStore for video files and notifies listeners.
 * This detects external changes like file renames, moves, or deletions done outside the app.
 *
 * Thread-safe and handles multiple storage volumes (internal, SD card, USB drives).
 */
class MediaStoreObserver(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private val tag = "MediaStoreObserver"

  // Thread-safe state management
  private val isObserving = AtomicBoolean(false)
  private val mutex = Mutex()

  // Dedicated background thread for content observation
  private var handlerThread: HandlerThread? = null
  private var handler: Handler? = null

  // Track all registered observers for cleanup
  private val registeredObservers = mutableListOf<Pair<Uri, ContentObserver>>()

  // Debounce job to avoid excessive refreshes
  private var debounceJob: Job? = null
  private val debounceDelayMs = 500L

  // Track last change time to avoid duplicate notifications
  private var lastChangeTimeMs = 0L
  private val minChangeIntervalMs = 100L

  /**
   * Creates a ContentObserver for a specific URI
   */
  private fun createObserver(volumeName: String): ContentObserver {
    return object : ContentObserver(handler) {
      override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
      }

      override fun onChange(selfChange: Boolean, uri: Uri?) {
        // Filter out rapid duplicate changes
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastChangeTimeMs < minChangeIntervalMs) {
          Log.v(tag, "Ignoring rapid duplicate change for $volumeName")
          return
        }
        lastChangeTimeMs = currentTime

        Log.d(tag, "MediaStore change detected on $volumeName: selfChange=$selfChange, uri=$uri")

        // Debounce the refresh to avoid multiple rapid calls
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
          // Double-check scope is still active before processing
          if (!isActive) {
            Log.d(tag, "Scope cancelled, skipping refresh notification")
            return@launch
          }

          delay(debounceDelayMs)

          // Triple-check after delay
          if (!isActive) {
            Log.d(tag, "Scope cancelled after delay, skipping refresh notification")
            return@launch
          }

          Log.d(tag, "Notifying MediaLibraryEvents of external change from $volumeName")
          MediaLibraryEvents.notifyChanged()
        }
      }
    }
  }

  /**
   * Start observing MediaStore for video changes across all storage volumes
   */
  suspend fun startObserving() {
    mutex.withLock {
      if (isObserving.get()) {
        Log.w(tag, "Already observing MediaStore, skipping duplicate registration")
        return@withLock
      }

      try {
        // Create dedicated background thread for content observation
        val thread = HandlerThread("MediaStoreObserver-${System.currentTimeMillis()}").apply {
          start()
        }
        handlerThread = thread
        handler = Handler(thread.looper)

        // Register observers for all storage volumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          registerObserversForAllVolumes()
        } else {
          // Pre-Android 10: only external storage
          registerObserverForUri(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "external",
          )
        }

        if (registeredObservers.isEmpty()) {
          Log.w(tag, "No observers registered, cleanup and exit")
          cleanup()
          return@withLock
        }

        isObserving.set(true)
        Log.d(tag, "Started observing MediaStore with ${registeredObservers.size} observer(s)")
      } catch (e: Exception) {
        Log.e(tag, "Failed to start observing MediaStore", e)
        cleanup()
      }
      Unit
    }
  }

  /**
   * Register observers for all available storage volumes (Android 10+)
   */
  private fun registerObserversForAllVolumes() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Get all available MediaStore volumes
        val volumeNames = MediaStore.getExternalVolumeNames(context)
        Log.d(tag, "Found ${volumeNames.size} MediaStore volumes: $volumeNames")

        for (volumeName in volumeNames) {
          try {
            val contentUri = MediaStore.Video.Media.getContentUri(volumeName)
            registerObserverForUri(contentUri, volumeName)
          } catch (e: Exception) {
            Log.e(tag, "Failed to register observer for volume: $volumeName", e)
          }
        }

        // Also check for physical storage volumes
        registerObserversForPhysicalVolumes()
      }
    } catch (e: Exception) {
      Log.e(tag, "Error registering observers for all volumes", e)
      // Fallback to external storage
      registerObserverForUri(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        "external-fallback",
      )
    }
  }

  /**
   * Register observers for physical storage volumes (SD cards, USB drives)
   */
  private fun registerObserversForPhysicalVolumes() {
    try {
      val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
      storageManager?.storageVolumes?.forEach { volume ->
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val volumeName = volume.mediaStoreVolumeName
            if (volumeName != null && !registeredObservers.any { it.first.toString().contains(volumeName) }) {
              val contentUri = MediaStore.Video.Media.getContentUri(volumeName)
              registerObserverForUri(contentUri, "physical-$volumeName")
            }
          }
        } catch (e: Exception) {
          Log.w(tag, "Failed to register observer for physical volume: ${volume.getDescription(context)}", e)
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error accessing storage manager", e)
    }
  }

  /**
   * Register a ContentObserver for a specific URI
   */
  private fun registerObserverForUri(uri: Uri, volumeName: String) {
    try {
      val observer = createObserver(volumeName)
      context.contentResolver.registerContentObserver(
        uri,
        true, // notify for descendants
        observer,
      )
      registeredObservers.add(uri to observer)
      Log.d(tag, "Registered observer for: $volumeName ($uri)")
    } catch (e: Exception) {
      Log.e(tag, "Failed to register observer for $volumeName", e)
    }
  }

  /**
   * Stop observing MediaStore changes
   */
  suspend fun stopObserving() {
    mutex.withLock {
      if (!isObserving.get()) {
        Log.d(tag, "Not currently observing, nothing to stop")
        return@withLock
      }

      try {
        Log.d(tag, "Stopping MediaStore observation")
        cleanup()
        isObserving.set(false)
        Log.d(tag, "Stopped observing MediaStore")
      } catch (e: Exception) {
        Log.e(tag, "Error while stopping observation", e)
      }
      Unit
    }
  }

  /**
   * Clean up all resources
   */
  private fun cleanup() {
    try {
      // Cancel any pending debounce job
      debounceJob?.cancel()
      debounceJob = null

      // Unregister all content observers
      registeredObservers.forEach { (uri, observer) ->
        try {
          context.contentResolver.unregisterContentObserver(observer)
          Log.v(tag, "Unregistered observer for: $uri")
        } catch (e: Exception) {
          Log.w(tag, "Failed to unregister observer for $uri", e)
        }
      }
      registeredObservers.clear()

      // Clean up handler and thread
      handler?.removeCallbacksAndMessages(null)
      handler = null

      handlerThread?.quitSafely()
      handlerThread = null

      lastChangeTimeMs = 0L
    } catch (e: Exception) {
      Log.e(tag, "Error during cleanup", e)
    }
  }

  /**
   * Check if currently observing
   */
  fun isObserving(): Boolean = isObserving.get()

  /**
   * Get count of registered observers
   */
  fun getObserverCount(): Int = registeredObservers.size
}
