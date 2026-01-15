package app.marlboroadvance.mpvex.ui.preferences

import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen

/**
 * Represents a searchable preference item.
 * Used to index all preferences for the settings search feature.
 */
data class SearchablePreference(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    val keywords: List<String> = emptyList(),
    val category: String,
    val screen: Screen,
)

/**
 * All searchable preferences indexed for settings search.
 */
object SearchablePreferences {
    val allPreferences: List<SearchablePreference> by lazy {
        buildList {
            // Appearance preferences
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_title,
                summaryRes = R.string.pref_appearance_summary,
                keywords = listOf("theme", "dark", "light", "amoled", "material you", "color"),
                category = "UI & Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_amoled_mode_title,
                summaryRes = R.string.pref_appearance_amoled_mode_summary,
                keywords = listOf("amoled", "black", "dark", "oled", "pure black"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_unlimited_name_lines_title,
                summaryRes = R.string.pref_appearance_unlimited_name_lines_summary,
                keywords = listOf("name", "full", "truncate", "lines"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_hidden_files_title,
                summaryRes = R.string.pref_appearance_show_hidden_files_summary,
                keywords = listOf("hidden", "files", "dot", "nomedia"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_network_thumbnails_title,
                summaryRes = R.string.pref_appearance_show_network_thumbnails_summary,
                keywords = listOf("network", "thumbnail", "stream"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            
            // Motion/Animation preferences
            add(SearchablePreference(
                titleRes = R.string.pref_motion_quality_title,
                summaryRes = R.string.pref_motion_high_quality_desc,
                keywords = listOf("animation", "motion", "transition", "reduce motion", "performance", "120fps", "spring"),
                category = "Animations",
                screen = AppearancePreferencesScreen,
            ))
            
            // Layout preferences
            add(SearchablePreference(
                titleRes = R.string.pref_layout_title,
                summaryRes = R.string.pref_layout_summary,
                keywords = listOf("layout", "controls", "buttons", "player"),
                category = "UI & Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            
            // Player preferences
            add(SearchablePreference(
                titleRes = R.string.pref_player,
                summaryRes = R.string.pref_player_summary,
                keywords = listOf("player", "orientation", "gestures", "controls"),
                category = "Playback & Controls",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_save_position_on_quit,
                keywords = listOf("save", "position", "resume", "remember"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_close_after_eof,
                keywords = listOf("close", "end", "playback", "quit"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_remember_brightness,
                keywords = listOf("brightness", "remember", "display"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            
            // Gesture preferences
            add(SearchablePreference(
                titleRes = R.string.pref_gesture,
                summaryRes = R.string.pref_gesture_summary,
                keywords = listOf("gesture", "double tap", "swipe", "media controls"),
                category = "Playback & Controls",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_tap_thumbnail_to_select_title,
                summaryRes = R.string.pref_gesture_tap_thumbnail_to_select_summary,
                keywords = listOf("thumbnail", "tap", "select", "play"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            
            // Folder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_folders_title,
                summaryRes = R.string.pref_folders_summary,
                keywords = listOf("folders", "blacklist", "hide", "exclude"),
                category = "File Management",
                screen = FoldersPreferencesScreen,
            ))
            
            // Decoder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_decoder,
                summaryRes = R.string.pref_decoder_summary,
                keywords = listOf("decoder", "hardware", "gpu", "debanding"),
                category = "Media Settings",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_try_hw_dec_title,
                keywords = listOf("hardware", "decoding", "hw", "acceleration"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_gpu_next_title,
                summaryRes = R.string.pref_decoder_gpu_next_summary,
                keywords = listOf("gpu", "next", "rendering", "backend"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_debanding_title,
                keywords = listOf("deband", "banding", "gradient"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            
            // Subtitle preferences
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles,
                summaryRes = R.string.pref_subtitles_summary,
                keywords = listOf("subtitles", "subs", "language", "fonts"),
                category = "Media Settings",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_fonts_dir,
                keywords = listOf("fonts", "directory", "subtitle", "custom"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_autoload_title,
                summaryRes = R.string.pref_subtitles_autoload_summary,
                keywords = listOf("autoload", "automatic", "subtitles", "external"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            
            // Audio preferences
            add(SearchablePreference(
                titleRes = R.string.pref_audio,
                summaryRes = R.string.pref_audio_summary,
                keywords = listOf("audio", "language", "channels", "pitch"),
                category = "Media Settings",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_pitch_correction_title,
                summaryRes = R.string.pref_audio_pitch_correction_summary,
                keywords = listOf("pitch", "correction", "speed", "audio"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_normalization_title,
                summaryRes = R.string.pref_audio_volume_normalization_summary,
                keywords = listOf("volume", "normalization", "loudness", "audio"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_boost_cap,
                keywords = listOf("volume", "boost", "cap", "maximum"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            
            // Advanced preferences
            add(SearchablePreference(
                titleRes = R.string.pref_advanced,
                summaryRes = R.string.pref_advanced_summary,
                keywords = listOf("advanced", "mpv", "config", "logs"),
                category = "Advanced & About",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_mpv_conf,
                keywords = listOf("mpv", "conf", "config", "configuration"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_input_conf,
                keywords = listOf("input", "conf", "keybindings", "shortcuts"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_dump_logs_title,
                summaryRes = R.string.pref_advanced_dump_logs_summary,
                keywords = listOf("logs", "debug", "dump", "share"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_verbose_logging_title,
                summaryRes = R.string.pref_advanced_verbose_logging_summary,
                keywords = listOf("verbose", "logging", "debug"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_playback_history,
                keywords = listOf("clear", "history", "playback", "reset"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_fonts_cache,
                keywords = listOf("clear", "fonts", "cache"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_anime4k_title,
                summaryRes = R.string.pref_anime4k_summary,
                keywords = listOf("anime4k", "upscale", "shader", "anime"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            
            // About
            add(SearchablePreference(
                titleRes = R.string.pref_about_title,
                summaryRes = R.string.pref_about_summary,
                keywords = listOf("about", "version", "licenses", "acknowledgments"),
                category = "Advanced & About",
                screen = AboutScreen,
            ))
        }
    }

    /**
     * Search preferences by query with fuzzy matching support.
     * 
     * Matches against title, summary, keywords, and category.
     * Supports typo tolerance using fuzzy string matching.
     */
    fun search(query: String, getStringRes: (Int) -> String): List<SearchablePreference> {
        if (query.isBlank()) return emptyList()
        
        val normalizedQuery = query.lowercase().trim()
        
        // Score each preference by match quality
        return allPreferences
            .map { pref ->
                val title = getStringRes(pref.titleRes).lowercase()
                val summary = pref.summaryRes?.let { getStringRes(it).lowercase() } ?: ""
                val keywords = pref.keywords.joinToString(" ").lowercase()
                val category = pref.category.lowercase()
                
                // Calculate match score (higher = better match)
                val score = calculateMatchScore(normalizedQuery, title, summary, keywords, category)
                pref to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.titleRes }
    }
    
    /**
     * Calculate match score for a preference.
     * Returns a score > 0 for matches, 0 for no match.
     * 
     * Exact matches score highest, fuzzy matches score lower.
     */
    private fun calculateMatchScore(
        query: String,
        title: String,
        summary: String,
        keywords: String,
        category: String
    ): Int {
        var score = 0
        
        // Exact contains matches (highest priority)
        if (title.contains(query)) score += 100
        if (summary.contains(query)) score += 50
        if (keywords.contains(query)) score += 75
        if (category.contains(query)) score += 25
        
        // Word-level matching
        val queryWords = query.split(" ").filter { it.isNotBlank() }
        for (word in queryWords) {
            if (title.contains(word)) score += 30
            if (summary.contains(word)) score += 15
            if (keywords.contains(word)) score += 20
            if (category.contains(word)) score += 10
        }
        
        // Fuzzy matching for typo tolerance (only if no exact matches)
        if (score == 0) {
            val allText = "$title $summary $keywords $category"
            val words = allText.split(" ", ",").filter { it.length >= 3 }
            
            for (word in words) {
                if (fuzzyMatch(query, word)) {
                    score += 20
                    break
                }
            }
            
            // Try matching each query word fuzzily
            for (queryWord in queryWords) {
                if (queryWord.length >= 2) {
                    for (word in words) {
                        if (fuzzyMatch(queryWord, word)) {
                            score += 10
                        }
                    }
                }
            }
        }
        
        return score
    }
    
    /**
     * Simple fuzzy matching using Levenshtein distance.
     * Returns true if the distance is within tolerance.
     */
    private fun fuzzyMatch(query: String, target: String): Boolean {
        if (query.length < 2 || target.length < 2) return false
        
        // Allow more tolerance for longer words
        val maxDistance = when {
            query.length <= 3 -> 1
            query.length <= 5 -> 2
            else -> 3
        }
        
        return levenshteinDistance(query, target) <= maxDistance
    }
    
    /**
     * Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Quick exit for identical or empty strings
        if (s1 == s2) return 0
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        // Use shorter string for columns to save space
        val (shorter, longer) = if (len1 < len2) s1 to s2 else s2 to s1
        val shortLen = shorter.length
        val longLen = longer.length
        
        // Only need two rows of the matrix
        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)
        
        for (i in 1..longLen) {
            currRow[0] = i
            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                currRow[j] = minOf(
                    currRow[j - 1] + 1,      // insertion
                    prevRow[j] + 1,          // deletion
                    prevRow[j - 1] + cost    // substitution
                )
            }
            // Swap rows
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        
        return prevRow[shortLen]
    }
}
