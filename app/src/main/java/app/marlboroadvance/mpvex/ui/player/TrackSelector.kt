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
   * Ensures an audio track is selected based on user preferences and quality filters.
   *
   * @param hasState Whether saved playback state exists for this video
   */
  private suspend fun ensureAudioTrackSelected(hasState: Boolean) {
    try {
      // 1. RESPECT SAVED STATE
      val currentAid = MPVLib.getPropertyInt("aid")
      if (hasState && currentAid != null && currentAid > 0) {
        return
      }

      val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
      val preferredLangs = audioPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

      // Keywords that typically indicate non-main audio (commentary, ADH, etc.)
      val ignoreKeywords = listOf("commentary", "description", "adh", "comment", "extra")

      if (preferredLangs.isNotEmpty()) {
        for (prefLang in preferredLangs) {
          for (i in 0 until totalTrackCount) {
            if (MPVLib.getPropertyString("track-list/$i/type") != "audio") continue
            
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase()
            val title = (MPVLib.getPropertyString("track-list/$i/title") ?: "").lowercase()
            
            // Priority: Preferred language and NOT a commentary track
            if (lang == prefLang || lang.startsWith(prefLang)) {
              val isCommentary = ignoreKeywords.any { title.contains(it) }
              if (!isCommentary) {
                Log.d(TAG, "Selected preferred audio: $lang - $title (id=$id)")
                MPVLib.setPropertyInt("aid", id)
                return
              }
            }
          }
        }
      }

      // 3. FALLBACK TO MPV DEFAULT (mpv.conf or Metadata)
      if (currentAid != null && currentAid > 0) {
        return
      }

      // Final fallback: first available audio track (avoiding commentary if possible)
      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") == "audio") {
          val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          val title = (MPVLib.getPropertyString("track-list/$i/title") ?: "").lowercase()
          if (ignoreKeywords.any { title.contains(it) }) continue
          
          Log.d(TAG, "Falling back to main audio: id=$id")
          MPVLib.setPropertyInt("aid", id)
          return
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Audio selection failed", e)
    }
  }

  /**
   * Ensures subtitle track selection using a multi-pass intelligent strategy.
   *
   * Strategy:
   * 1. Smart Matching for Anime: Prioritizes "Dialogue"/"Full" tracks when audio is Japanese.
   * 2. Clean Language Match: Finds preferred language while stripping Signs, Songs, SDH, and Forced flags.
   * 3. Metadata Match: Respects MPV's default selection or metadata 'default' flags.
   * 4. Last Resort: Picks any matching language track.
   *
   * @param hasState Whether saved playback state exists for this video
   */
  private suspend fun ensureSubtitleTrackSelected(hasState: Boolean) {
    try {
      // 1. RESPECT SAVED STATE
      val currentSid = MPVLib.getPropertyInt("sid")
      if (hasState) return

      val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
      
      // Get currently active audio language for smart matching
      val currentAid = MPVLib.getPropertyInt("aid") ?: 0
      var audioLang = ""
      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") == "audio" && 
            MPVLib.getPropertyInt("track-list/$i/id") == currentAid) {
          audioLang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase()
          break
        }
      }
      
      // Determine if this is foreign content (Trigger: Japanese/Anime)
      val isJapaneseAudio = audioLang == "jpn" || audioLang == "ja" || audioLang == "jp"
      
      // Determine preferred subtitle languages (App Settings > MPV alang/slang > English)
      var preferredLangs = subtitlesPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

      if (preferredLangs.isEmpty()) {
        // Fallback to slang from mpv.conf
        preferredLangs = (MPVLib.getPropertyString("slang") ?: "")
          .split(",")
          .map { it.trim().lowercase() }
          .filter { it.isNotEmpty() }
      }
      
      if (preferredLangs.isEmpty()) {
        // Human fallback: English
        preferredLangs = listOf("eng", "en")
      }

      val fallbackLangs = preferredLangs // Used for anime matching if no dialogue tracks exist

      // Keywords for deprioritization (Signs, Songs, Lyrics, SDH)
      val skipKeywords = listOf("signs", "songs", "lyrics", "forced", "sdh", "colored", "karaoke")

      if (isJapaneseAudio) {
        // PASS 1: SMART ANIME DIALOGUE
        for (prefLang in fallbackLangs) {
          for (i in 0 until totalTrackCount) {
            if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
            
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase()
            val title = (MPVLib.getPropertyString("track-list/$i/title") ?: "").lowercase()
            
            if (lang == prefLang || lang.startsWith(prefLang)) {
              if (title.contains("dialogue") || title.contains("full") || title.contains("script")) {
                Log.d(TAG, "Smart Select: Dialogue found ($title, id=$id)")
                MPVLib.setPropertyInt("sid", id)
                return
              }
            }
          }
        }
      }

      // PASS 2: CLEAN LANGUAGE MATCH (Excluding Signs, SDH, and Forced)
      for (prefLang in preferredLangs) {
        for (i in 0 until totalTrackCount) {
          if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
          
          val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          val lang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase()
          val title = (MPVLib.getPropertyString("track-list/$i/title") ?: "").lowercase()
          
          // Check MPV metadata flags for forced/hearing-impaired
          val isForced = MPVLib.getPropertyBoolean("track-list/$i/forced") ?: false
          val isSDH = MPVLib.getPropertyBoolean("track-list/$i/hearing-impaired") ?: false
          
          if (lang == prefLang || lang.startsWith(prefLang)) {
            val hasSkipKeyword = skipKeywords.any { title.contains(it) }
            if (!hasSkipKeyword && !isForced && !isSDH) {
              Log.d(TAG, "Preferred Select: Clean sub found (lang=$lang, title=$title, id=$id)")
              MPVLib.setPropertyInt("sid", id)
              return
            }
          }
        }
      }
      
      // PASS 3: LAST RESORT MATCHING (Ignoring deprioritization)
      for (prefLang in preferredLangs) {
        for (i in 0 until totalTrackCount) {
          if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
          val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
          val lang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase()
          
          if (lang == prefLang || lang.startsWith(prefLang)) {
            Log.d(TAG, "Fallback Select: Language match only (id=$id)")
            MPVLib.setPropertyInt("sid", id)
            return
          }
        }
      }

      // 5. FALLBACK TO MPV/METADATA DEFAULTS
      if (currentSid != null && currentSid > 0) return

      for (i in 0 until totalTrackCount) {
        if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
        val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
        if (MPVLib.getPropertyBoolean("track-list/$i/default") == true) {
          Log.d(TAG, "Default Select: Metadata default used (id=$id)")
          MPVLib.setPropertyInt("sid", id)
          return
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Subtitle selection failed", e)
    }
  }
}
