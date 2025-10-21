package app.marlboroadvance.mpvex.ui.player.managers

import android.util.Log
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.BRIGHTNESS_NOT_SET
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the lifecycle of the player and coordinates all managers.
 */
class PlayerLifecycleManager(
  private val audioFocusManager: AudioFocusManager,
  private val mpvConfigurationManager: MPVConfigurationManager,
  private val systemUIManager: SystemUIManager,
  private val playbackStateManager: PlaybackStateManager,
  private val mpvEventDispatcher: MPVEventDispatcher,
  private val pipCoordinator: PipCoordinator,
  private val playerPreferences: PlayerPreferences,
  private val scope: CoroutineScope,
) {

  /**
   * Called when the activity is starting.
   */
  fun onStart() {
    runCatching {
      systemUIManager.setupWindowFlags()
      systemUIManager.setupSystemUI()
      audioFocusManager.registerNoisyReceiver()
      restoreBrightness()
      pipCoordinator.updatePictureInPictureParams()
    }.onFailure { e ->
      Log.e(TAG, "Error during onStart", e)
    }
  }

  /**
   * Called when the activity is stopping.
   */
  fun onStop(fileName: String, currentPosition: Int?, duration: Int?) {
    runCatching {
      pipCoordinator.onStop()
      savePlaybackState(fileName, currentPosition, duration)
      audioFocusManager.unregisterNoisyReceiver()
    }.onFailure { e ->
      Log.e(TAG, "Error during onStop", e)
    }
  }

  /**
   * Called when the activity is pausing.
   */
  fun onPause(
    fileName: String,
    currentPosition: Int?,
    duration: Int?,
    isInPictureInPictureMode: Boolean,
    isFinishing: Boolean,
  ) {
    runCatching {
      savePlaybackState(fileName, currentPosition, duration)

      if (isFinishing && !isInPictureInPictureMode && !systemUIManager.isSystemUIRestored()) {
        systemUIManager.restoreSystemUI()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onPause", e)
    }
  }

  /**
   * Called when the activity is being destroyed.
   */
  fun onDestroy(isFinishing: Boolean) {
    Log.d(TAG, "Exiting PlayerActivity")

    runCatching {
      if (isFinishing && !systemUIManager.isSystemUIRestored()) {
        systemUIManager.restoreSystemUI()
      }

      if (isFinishing) {
        mpvConfigurationManager.destroy()
      }

      audioFocusManager.cleanup()
    }.onFailure { e ->
      Log.e(TAG, "Error during onDestroy", e)
    }
  }

  /**
   * Called when finishing the activity.
   */
  fun onFinish() {
    runCatching {
      if (!systemUIManager.isSystemUIRestored()) {
        systemUIManager.restoreSystemUI()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during finish", e)
    }
  }

  private fun restoreBrightness() {
    if (playerPreferences.rememberBrightness.get()) {
      val brightness = playerPreferences.defaultBrightness.get()
      if (brightness != BRIGHTNESS_NOT_SET) {
        systemUIManager.setBrightness(brightness)
      }
    }
  }

  private fun savePlaybackState(fileName: String, currentPosition: Int?, duration: Int?) {
    scope.launch(Dispatchers.IO) {
      playbackStateManager.savePlaybackState(fileName, currentPosition, duration)
    }
  }
}
