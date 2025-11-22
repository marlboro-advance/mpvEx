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
 * - Subtitle tracks respect user preference (only selected if configured)
 * - Preferred languages work correctly
 * - Manual selections are preserved
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
   * 3. If no language match, keep existing selection or select first track
   * 4. Audio must ALWAYS be selected
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

        Log.d(TAG, "No preferred language found in available tracks")
      }

      // Check if a track is already selected
      val currentAid = MPVLib.getPropertyInt("aid")
      if (currentAid != null && currentAid > 0) {
        Log.d(TAG, "Audio track already selected: $currentAid (no preferred language to override)")
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
   * 3. If no preferred languages, disable subtitles
   * 4. If no language match, disable subtitles
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

      // If no preferred languages configured, disable subtitles
      if (preferredLangs.isEmpty()) {
        Log.d(TAG, "No preferred subtitle languages configured")
        val currentSid = MPVLib.getPropertyInt("sid")
        if (currentSid != null && currentSid > 0) {
          MPVLib.setPropertyBoolean("sid", false)
          Log.d(TAG, "Disabled subtitles (no preferred language)")
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

      // No matching subtitle found for any preferred language, disable subtitles
      Log.d(TAG, "No matching subtitle tracks found for any preferred language")
      val currentSid = MPVLib.getPropertyInt("sid")
      if (currentSid != null && currentSid > 0) {
        MPVLib.setPropertyBoolean("sid", false)
        Log.d(TAG, "Disabled subtitles (preferred language not found)")
      }

    } catch (e: Exception) {
      Log.e(TAG, "Error in ensureSubtitleTrackSelected", e)
    }
  }
}
