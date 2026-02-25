package app.marlboroadvance.mpvex.ui.player

import android.util.Log
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

/**
 * Handles automatic track selection based on user preferences.
 *
 * Priority hierarchy for SUBTITLES (highest to lowest):
 * 1. User manual selection (saved state) - ALWAYS respected, never overridden
 * 2. Preferred language (from settings) - Applied only when no saved selection exists
 * 3. Default track (from container metadata) - Used when no preference and no saved state
 * 4. No selection (disabled) - Subtitles are optional
 *
 * Priority hierarchy for AUDIO (highest to lowest):
 * 1. User manual selection (saved state) - ALWAYS respected, never overridden
 * 2. Preferred language (from settings) - Applied only when no saved selection exists
 * 3. Default track (from container metadata) - Used as fallback
 * 4. First available track - Final fallback (audio is mandatory)
 *
 * This ensures:
 * - User choices are ALWAYS preserved across app restarts
 * - Audio tracks are ALWAYS selected (never silent playback)
 * - Subtitle default tracks are respected on first-time playback
 * - Preferred languages serve as defaults for first-time playback only
 */
class TrackSelector(
  private val audioPreferences: AudioPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
) {
  companion object {
    private const val TAG = "TrackSelector"
  }

  /**
   * Called after a file loads in MPV.
   * Ensures proper track selection based on preferences.
   * This is a suspend function to avoid blocking threads.
   *
   * @param hasState Whether saved playback state exists for this video
   */
  suspend fun onFileLoaded(hasState: Boolean = false) {
    // Wait for MPV to finish demuxing and detecting tracks
    var attempts = 0
    val maxAttempts = 20 // 20 attempts * 50ms = 1 second max wait
    
    while (attempts < maxAttempts) {
      val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
      if (trackCount > 0) break
      delay(50)
      attempts++
    }

    ensureAudioTrackSelected(hasState)
    ensureSubtitleTrackSelected(hasState)
  }

  /**
   * Ensures an audio track is selected.
   *
   * NEW Strategy (User selection has highest priority):
   * 1. If a track is already selected (from saved state or previous selection), keep it
   * 2. If no track selected AND preferred languages configured, select based on preference
   * 3. If no track selected AND no preferred languages, select default track or first track
   * 4. User manual selections always have highest priority and are never overridden
   * 5. Audio is mandatory - always ensure something is selected
   *
   * @param hasState Whether saved playback state exists for this video
   */
  private fun ensureAudioTrackSelected(hasState: Boolean) {
    try {
      val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
      
      // Check if track already selected (from saved state)
      // Only respect existing selection if we have a saved state.
      // Otherwise, we want to enforce preferences even if mpv picked a default.
      val currentAid = MPVLib.getPropertyInt("aid")
      if (hasState && currentAid != null && currentAid > 0) return

      // Get preferred languages
      val preferredLangs = audioPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

      // Try to match preferred language
      if (preferredLangs.isNotEmpty()) {
        for (i in 0 until totalTrackCount) {
          if (MPVLib.getPropertyString("track-list/$i/type") != "audio") continue
          
          val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
          
          for (preferredLang in preferredLangs) {
            if (lang.equals(preferredLang, ignoreCase = true)) {
              MPVLib.setPropertyInt("aid", trackId)
              return
            }
          }
        }
      }

      // Try to find default track
      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") != "audio") continue
        
        val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
        val isDefault = MPVLib.getPropertyBoolean("track-list/$i/default") ?: false
        
        if (isDefault) {
          MPVLib.setPropertyInt("aid", trackId)
          return
        }
      }

      // Select first audio track (audio is mandatory)
      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") == "audio") {
          val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          MPVLib.setPropertyInt("aid", trackId)
          return
        }
      }

    } catch (e: Exception) {
      Log.e(TAG, "Error selecting audio track", e)
    }
  }

  /**
   * Ensures subtitle track selection respects user preference.
   *
   * NEW Strategy (User selection has highest priority):
   * 1. If a track is already selected (from saved state or previous selection), keep it
   * 2. If saved state exists but subtitles were disabled, respect that choice (don't enable defaults)
   * 3. If NO saved state AND preferred languages configured, select based on preference
   * 4. If NO saved state AND no preferred languages, select default track (if marked)
   * 5. If no default track, keep subtitles disabled (subtitles are optional)
   * 6. User manual selections always have highest priority and are never overridden
   *
   * @param hasState Whether saved playback state exists for this video
   */
  private fun ensureSubtitleTrackSelected(hasState: Boolean) {
    try {
      val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
      
      // Check if track already selected (from saved state)
      val currentSid = MPVLib.getPropertyInt("sid")
      // Only return if hasState. If !hasState, we want to check preferences.
      if (hasState && currentSid != null && currentSid > 0) return

      // If saved state exists with subtitles disabled, respect that choice
      if (hasState && (currentSid == null || currentSid <= 0)) return

      // Get preferred languages
      val preferredLangs = subtitlesPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

      // Try to match preferred language
      if (preferredLangs.isNotEmpty()) {
        for (i in 0 until totalTrackCount) {
          if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
          
          val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
          
          for (preferredLang in preferredLangs) {
            if (lang.equals(preferredLang, ignoreCase = true)) {
              MPVLib.setPropertyInt("sid", trackId)
              return
            }
          }
        }
        return // No match found, keep disabled
      }

      // No preferred language - try to find default track
      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
        
        val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
        val isDefault = MPVLib.getPropertyBoolean("track-list/$i/default") ?: false
        
        if (isDefault) {
          MPVLib.setPropertyInt("sid", trackId)
          return
        }
      }

    } catch (e: Exception) {
      Log.e(TAG, "Error selecting subtitle track", e)
    }
  }
}
