package app.marlboroadvance.mpvex.ui.player

import android.util.Log
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import `is`.xyz.mpv.MPVLib

/**
 * Handles automatic track selection based on user preferences.
 *
 * This class ensures:
 * - Audio tracks are ALWAYS selected (never silent playback)
 * - Preferred languages override user selections when configured
 * - User manual selections and saved states are respected when no preferred language is set
 * - User manual selections and saved states are preserved when preferred language doesn't match
 * - Subtitle tracks are optional and only enabled when explicitly selected or matching preferences
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
   */
  fun onFileLoaded() {
    Log.d(TAG, "File loaded, checking track selection...")

    // Give MPV a moment to process the alang/slang options
    // This is necessary because MPV processes options asynchronously
    Thread.sleep(100)

    ensureAudioTrackSelected()
    ensureSubtitleTrackSelected()
  }

  /**
   * Ensures an audio track is selected.
   *
   * Strategy:
   * 1. If preferred languages configured, ALWAYS select based on preference
   * 2. Override any existing selection to respect user preference
   * 3. If no preferred languages configured, respect any existing selection (user choice or saved state)
   * 4. If no selection exists and no preference, select first track
   * 5. Audio must ALWAYS be selected
   */
  private fun ensureAudioTrackSelected() {
    try {
      // Get audio track count
      val trackCount = MPVLib.getPropertyInt("audio-track-count") ?: 0
      if (trackCount == 0) {
        Log.w(TAG, "No audio tracks available")
        return
      }

      Log.d(TAG, "Found $trackCount audio tracks")

      // Get preferred languages
      val preferredLangs = audioPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

      // If preferred languages are configured, respect them at any cost
      if (preferredLangs.isNotEmpty()) {
        Log.d(TAG, "Preferred audio languages: $preferredLangs")

        // Try to match preferred languages in order
        for (preferredLang in preferredLangs) {
          for (i in 1..trackCount) {
            val lang = MPVLib.getPropertyString("audio/$i/lang") ?: continue

            if (lang.equals(preferredLang, ignoreCase = true)) {
              val currentAid = MPVLib.getPropertyInt("aid")
              if (currentAid != i) {
                MPVLib.setPropertyInt("aid", i)
                Log.d(TAG, "Selected audio track $i (language: $lang) - preferred language override")
              } else {
                Log.d(TAG, "Audio track $i already selected (language: $lang)")
              }
              return
            }
          }
        }

        Log.d(TAG, "No preferred language found in available tracks - keeping existing selection")
        // When preferred language is set but not found, keep existing selection
        // This respects user's manual selection or saved state
      }

      // Check if a track is already selected (user choice, saved state, or MPV's alang)
      val currentAid = MPVLib.getPropertyInt("aid")
      if (currentAid != null && currentAid > 0) {
        Log.d(TAG, "Audio track already selected: $currentAid (respecting existing selection)")
        return
      }

      // No track selected and no preferred language match, select first track
      MPVLib.setPropertyInt("aid", 1)
      Log.d(TAG, "Selected first audio track (fallback)")

    } catch (e: Exception) {
      Log.e(TAG, "Error in ensureAudioTrackSelected", e)
    }
  }

  /**
   * Ensures subtitle track selection respects user preference.
   *
   * Strategy:
   * 1. If preferred languages configured, ALWAYS select based on preference
   * 2. Override any existing selection to respect user preference
   * 3. If no preferred languages configured, respect any existing selection (user choice or saved state)
   * 4. If preferred language set but not found, keep existing selection
   * 5. Subtitles are optional - only enabled if preference matches or user/saved state selects them
   */
  private fun ensureSubtitleTrackSelected() {
    try {
      // Get subtitle track count
      val trackCount = MPVLib.getPropertyInt("sub-track-count") ?: 0
      if (trackCount == 0) {
        Log.d(TAG, "No subtitle tracks available")
        return
      }

      Log.d(TAG, "Found $trackCount subtitle tracks")

      // Get preferred languages
      val preferredLangs = subtitlesPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

      // If no preferred languages configured, respect any existing selection
      if (preferredLangs.isEmpty()) {
        Log.d(TAG, "No preferred subtitle languages configured - respecting existing selection")
        val currentSid = MPVLib.getPropertyInt("sid")
        if (currentSid != null && currentSid > 0) {
          Log.d(TAG, "Subtitle track $currentSid is selected (user choice or saved state)")
        } else {
          Log.d(TAG, "No subtitle track selected (respecting user preference for no subtitles)")
        }
        return
      }

      Log.d(TAG, "Preferred subtitle languages: $preferredLangs")

      // Try each preferred language in order
      for (preferredLang in preferredLangs) {
        Log.d(TAG, "Trying language: $preferredLang")

        // Find first track with this language
        for (i in 1..trackCount) {
          val lang = MPVLib.getPropertyString("sub/$i/lang") ?: continue
          val title = MPVLib.getPropertyString("sub/$i/title") ?: ""

          if (lang.equals(preferredLang, ignoreCase = true)) {
            val currentSid = MPVLib.getPropertyInt("sid")
            if (currentSid != i) {
              MPVLib.setPropertyInt("sid", i)
              Log.d(TAG, "Selected subtitle track $i (lang: $lang, title: '$title') - preferred language override")
            } else {
              Log.d(TAG, "Subtitle track $i already selected (lang: $lang, title: '$title')")
            }
            return
          }
        }

        Log.d(TAG, "No tracks found for language: $preferredLang")
      }

      // No matching subtitle found for any preferred language
      // Keep existing selection (user choice or saved state) instead of disabling
      Log.d(TAG, "No matching subtitle tracks found for any preferred language - keeping existing selection")
      val currentSid = MPVLib.getPropertyInt("sid")
      if (currentSid != null && currentSid > 0) {
        Log.d(TAG, "Keeping subtitle track $currentSid (user choice or saved state)")
      } else {
        Log.d(TAG, "No subtitle track selected")
      }

    } catch (e: Exception) {
      Log.e(TAG, "Error in ensureSubtitleTrackSelected", e)
    }
  }
}
