package app.marlboroadvance.mpvex.ui.player.managers

import android.content.Intent
import android.util.Log
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dispatches and handles MPV events and property changes.
 */
class MPVEventDispatcher(
  private val player: MPVView,
  private val viewModel: PlayerViewModel,
  private val playerPreferences: PlayerPreferences,
  private val intentHandler: IntentHandler,
  private val playbackStateManager: PlaybackStateManager,
  private val orientationManager: OrientationManager,
  private val systemUIManager: SystemUIManager,
  private val audioFocusManager: AudioFocusManager,
  private val scope: CoroutineScope,
  private val onFinishActivity: () -> Unit,
) {
  private var fileName: String = ""
  private var currentIntent: Intent? = null
  private var shouldRestorePosition = false

  /**
   * Handles generic property change events.
   */
  fun onObserverEvent() {
    // Generic event handling if needed
  }

  /**
   * Handles boolean property changes.
   */
  fun onObserverEvent(property: String, value: Boolean) {
    if (player.isExiting) return

    when (property) {
      "pause" -> handlePauseStateChange(value)
      "eof-reached" -> handleEndOfFile(value)
    }
  }

  /**
   * Handles string property changes.
   */
  fun onObserverEvent(property: String, value: String) {
    if (player.isExiting) return

    when (property.substringBeforeLast("/")) {
      "user-data/mpvex" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  /**
   * Handles other property changes.
   */
  fun onObserverEvent(property: String) {
    if (player.isExiting) return

    when (property) {
      "video-params/aspect" -> {
        // PiP params will be updated by the caller if needed
      }
    }
  }

  /**
   * Handles MPV events by ID.
   */
  fun event(eventId: Int) {
    if (player.isExiting) return

    when (eventId) {
      MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> handleFileLoaded()
      MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> handlePlaybackRestart()
    }
  }

  /**
   * Sets the current file name for state management.
   */
  fun setFileName(name: String) {
    fileName = name
  }

  /**
   * Gets the current file name.
   */
  fun getFileName(): String = fileName

  /**
   * Handles file loaded event - applies settings and restores state.
   * This is called from the activity with the intent.
   */
  fun handleFileLoaded(intent: Intent) {
    currentIntent = intent
    // The actual loading will be handled by the event callback
  }

  /**
   * Handles file loaded event - applies settings.
   * This is called from the MPV event system when the file is loaded.
   */
  private fun handleFileLoaded() {
    player.isExiting = false

    Log.d(PlayerConstants.TAG, "File loaded: $fileName")
    // force-media-title is now set in PlayerActivity before loading the file

    currentIntent?.let { intent ->
      intentHandler.applyIntentExtras(intent)
    }

    scope.launch(Dispatchers.IO) {
      // Load playback state (tracks, speed, delays, etc.)
      playbackStateManager.loadPlaybackState(fileName)

      // Get saved position and set it immediately via time-pos
      val state = playbackStateManager.getPlaybackState(fileName)
      if (state != null && playerPreferences.savePositionOnQuit.get() && state.lastPosition != 0) {
        Log.d(PlayerConstants.TAG, "Setting initial position: ${state.lastPosition} seconds")
        // Set time-pos to position the video before playback starts
        MPVLib.setPropertyInt("time-pos", state.lastPosition)
      }
    }

    orientationManager.setOrientation()
    viewModel.changeVideoAspect(playerPreferences.videoAspect.get())

    val defaultZoom = playerPreferences.defaultVideoZoom.get()
    MPVLib.setPropertyDouble("video-zoom", defaultZoom.toDouble())
    viewModel.setVideoZoom(defaultZoom)

    audioFocusManager.requestAudioFocus()
    viewModel.unpause()
  }

  /**
   * Handles playback restart event - restores saved position.
   * This fires when playback actually starts and the video is seekable.
   */
  private fun handlePlaybackRestart() {
    player.isExiting = false

    if (shouldRestorePosition) {
      shouldRestorePosition = false
      scope.launch(Dispatchers.IO) {
        val state = playbackStateManager.getPlaybackState(fileName)
        if (state != null && playerPreferences.savePositionOnQuit.get() && state.lastPosition != 0) {
          Log.d(PlayerConstants.TAG, "Restoring position to: ${state.lastPosition} seconds")
          // Small delay to ensure video is ready
          delay(50)
          MPVLib.setPropertyInt("time-pos", state.lastPosition)

          // Verify
          delay(100)
          val actualPosition = MPVLib.getPropertyInt("time-pos")
          Log.d(PlayerConstants.TAG, "Position after restore: $actualPosition (target was: ${state.lastPosition})")
        }
      }
    }
  }

  private fun handlePauseStateChange(isPaused: Boolean) {
    if (isPaused) {
      systemUIManager.allowScreenOff()
      audioFocusManager.abandonAudioFocus()
    } else {
      systemUIManager.keepScreenOn()
      audioFocusManager.requestAudioFocus()
    }
  }

  private fun handleEndOfFile(isEof: Boolean) {
    if (isEof) {
      audioFocusManager.abandonAudioFocus()

      if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
        onFinishActivity()
      }
    }
  }
}
