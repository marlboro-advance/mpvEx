package app.marlboroadvance.mpvex.ui.player.managers

import android.util.Log
import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.DEFAULT_PLAYBACK_SPEED
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.DEFAULT_SUB_SPEED
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.DELAY_DIVISOR
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.MILLISECONDS_TO_SECONDS
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.TAG
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

/**
 * Manages saving and restoring playback state (position, speed, tracks, delays).
 */
class PlaybackStateManager(
  private val player: MPVView,
  private val playbackStateRepository: PlaybackStateRepository,
  private val playerPreferences: PlayerPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
) {

  /**
   * Saves the current playback state for a video.
   */
  suspend fun savePlaybackState(mediaTitle: String, currentPosition: Int?, duration: Int?) {
    if (mediaTitle.isBlank()) return

    runCatching {
      val oldState = playbackStateRepository.getVideoDataByTitle(mediaTitle)
      val positionToSave = calculateSavePosition(oldState, currentPosition, duration)
      Log.d(
        TAG,
        "Saving playback state for: $mediaTitle, position: $positionToSave, currentPosition: $currentPosition, duration: $duration",
      )

      playbackStateRepository.upsert(
        PlaybackStateEntity(
          mediaTitle = mediaTitle,
          lastPosition = positionToSave,
          playbackSpeed = MPVLib.getPropertyDouble("speed") ?: DEFAULT_PLAYBACK_SPEED,
          sid = player.sid,
          subDelay = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS).toInt(),
          subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: DEFAULT_SUB_SPEED,
          secondarySid = player.secondarySid,
          secondarySubDelay = (
            (MPVLib.getPropertyDouble("secondary-sub-delay") ?: 0.0) *
              MILLISECONDS_TO_SECONDS
            ).toInt(),
          aid = player.aid,
          audioDelay = (
            (MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS
            ).toInt(),
        ),
      )
    }.onFailure { e ->
      Log.e(TAG, "Error saving playback state", e)
    }
  }

  /**
   * Gets the saved playback state for a video.
   */
  suspend fun getPlaybackState(mediaTitle: String): PlaybackStateEntity? {
    if (mediaTitle.isBlank()) return null
    return runCatching {
      playbackStateRepository.getVideoDataByTitle(mediaTitle)
    }.getOrNull()
  }

  /**
   * Loads and applies saved playback state for a video.
   */
  suspend fun loadPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return

    runCatching {
      val state = playbackStateRepository.getVideoDataByTitle(mediaTitle)
      Log.d(TAG, "Loading playback state for: $mediaTitle, saved position: ${state?.lastPosition}")

      applyPlaybackState(state)
      applyDefaultSettings(state)
    }.onFailure { e ->
      Log.e(TAG, "Error loading playback state", e)
    }
  }

  private fun calculateSavePosition(
    oldState: PlaybackStateEntity?,
    currentPosition: Int?,
    duration: Int?,
  ): Int {
    if (!playerPreferences.savePositionOnQuit.get()) {
      // When save position is disabled, always save 0 (beginning of video)
      return 0
    }

    val pos = currentPosition ?: 0
    val dur = duration ?: 0
    // Save position if not near the end (within 1 second of end)
    return if (pos < dur - 1) pos else 0
  }

  private fun applyPlaybackState(state: PlaybackStateEntity?) {
    if (state == null) {
      Log.d(TAG, "No saved playback state found")
      return
    }

    val subDelay = state.subDelay / DELAY_DIVISOR
    val secondarySubDelay = state.secondarySubDelay / DELAY_DIVISOR
    val audioDelay = state.audioDelay / DELAY_DIVISOR

    player.sid = state.sid
    player.secondarySid = state.secondarySid
    player.aid = state.aid

    MPVLib.setPropertyDouble("sub-delay", subDelay)
    MPVLib.setPropertyDouble("secondary-sub-delay", secondarySubDelay)
    MPVLib.setPropertyDouble("speed", state.playbackSpeed)
    MPVLib.setPropertyDouble("audio-delay", audioDelay)
    MPVLib.setPropertyDouble("sub-speed", state.subSpeed)

    // Position is restored in MPVEventDispatcher.handlePlaybackRestart()
    Log.d(TAG, "Applied playback state (position will be restored on playback restart)")
  }

  private fun applyDefaultSettings(state: PlaybackStateEntity?) {
    // Set default sub speed if no state exists
    if (state == null) {
      val defaultSubSpeed = subtitlesPreferences.defaultSubSpeed.get().toDouble()
      MPVLib.setPropertyDouble("sub-speed", defaultSubSpeed)
    }
  }
}
