package app.marlboroadvance.mpvex.ui.preferences

import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen

/**
 * Represents a searchable preference item.
 * Used to index all preferences for the settings search feature.
 */
data class SearchablePreference(
    @StringRes val titleRes: Int? = null,
    val title: String? = null,
    @StringRes val summaryRes: Int? = null,
    val summary: String? = null,
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
                keywords = listOf("theme", "dark", "light", "amoled", "material you", "color", "appearance"),
                category = "Appearance",
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
                keywords = listOf("name", "full", "truncate", "lines", "display"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_hidden_files_title,
                summaryRes = R.string.pref_appearance_show_hidden_files_summary,
                keywords = listOf("hidden", "files", "dot", "nomedia", "show"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_unplayed_old_video_label_title,
                summaryRes = R.string.pref_appearance_show_unplayed_old_video_label_summary,
                keywords = listOf("unplayed", "old", "label", "video", "new", "indicator"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_unplayed_old_video_days_title,
                keywords = listOf("days", "old", "video", "threshold", "time"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_auto_scroll_title,
                summaryRes = R.string.pref_appearance_auto_scroll_summary,
                keywords = listOf("scroll", "auto", "last played", "resume", "position"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_show_network_thumbnails_title,
                summaryRes = R.string.pref_appearance_show_network_thumbnails_summary,
                keywords = listOf("network", "thumbnail", "stream", "preview", "images"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))

            // Layout preferences
            add(SearchablePreference(
                titleRes = R.string.pref_layout_title,
                summaryRes = R.string.pref_layout_summary,
                keywords = listOf("layout", "controls", "buttons", "player", "customize", "arrange"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_top_right_controls,
                keywords = listOf("controls", "top", "right", "landscape", "buttons"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_bottom_right_controls,
                keywords = listOf("controls", "bottom", "right", "landscape", "buttons"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_bottom_left_controls,
                keywords = listOf("controls", "bottom", "left", "landscape", "buttons"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_layout_portrait_bottom_controls,
                keywords = listOf("controls", "portrait", "bottom", "buttons"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_appearance_hide_player_buttons_background_title,
                summaryRes = R.string.pref_appearance_hide_player_buttons_background_summary,
                keywords = listOf("hide", "background", "buttons", "transparent", "player"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_hide_player_control_time,
                keywords = listOf("time", "hide", "controls", "disappear", "timeout", "ms"),
                category = "Appearance",
                screen = PlayerControlsPreferencesScreen,
            ))

            // Player preferences
            add(SearchablePreference(
                titleRes = R.string.pref_player,
                summaryRes = R.string.pref_player_summary,
                keywords = listOf("player", "orientation", "gestures", "controls", "playback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_orientation,
                keywords = listOf("orientation", "landscape", "portrait", "rotate", "screen"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_save_position_on_quit,
                keywords = listOf("save", "position", "resume", "remember", "progress"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_close_after_eof,
                keywords = listOf("close", "end", "playback", "quit", "finish"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_remember_brightness,
                keywords = listOf("brightness", "remember", "display", "screen"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_autoplay_title,
                summaryRes = R.string.pref_autoplay_summary,
                keywords = listOf("autoplay", "playlist", "next", "previous", "folder", "navigation"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_autoplay_next_video_title,
                summaryRes = R.string.pref_autoplay_next_video_summary,
                keywords = listOf("autoplay", "next", "video", "auto", "advance", "continuous"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_auto_pip_title,
                summaryRes = R.string.pref_auto_pip_summary,
                keywords = listOf("pip", "picture", "auto", "navigation", "home", "back"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.show_splash_ovals_on_double_tap_to_seek,
                keywords = listOf("oval", "circle", "double tap", "seek", "visual", "feedback"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.show_time_on_double_tap_to_seek,
                keywords = listOf("time", "double tap", "seek", "overlay", "timestamp"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_use_precise_seeking,
                keywords = listOf("precise", "seek", "keyframes", "accurate", "navigation"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_brightness,
                keywords = listOf("brightness", "gesture", "swipe", "display", "control"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_volume,
                keywords = listOf("volume", "gesture", "swipe", "audio", "sound"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_pinch_to_zoom,
                keywords = listOf("zoom", "pinch", "gesture", "scale", "crop", "video"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_horizontal_swipe_to_seek,
                keywords = listOf("horizontal", "swipe", "seek", "gesture", "left", "right"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_horizontal_swipe_sensitivity,
                summaryRes = R.string.pref_player_gestures_horizontal_swipe_sensitivity_summary,
                keywords = listOf("horizontal", "swipe", "sensitivity", "seek", "distance", "speed"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_gestures_hold_for_multiple_speed,
                keywords = listOf("hold", "speed", "multiple", "playback", "tempo", "rate"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_dynamic_speed_overlay_title,
                summaryRes = R.string.pref_dynamic_speed_overlay_summary,
                keywords = listOf("dynamic", "speed", "overlay", "control", "hold", "swipe"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_controls_allow_gestures_in_panels,
                keywords = listOf("gestures", "panels", "controls", "overlay", "enable"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.swap_the_volume_and_brightness_slider,
                keywords = listOf("swap", "volume", "brightness", "slider", "left", "right"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_controls_show_loading_circle,
                keywords = listOf("loading", "circle", "indicator", "buffer", "progress"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_show_status_bar,
                keywords = listOf("status bar", "navigation", "system", "show", "hide", "immersive"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_show_navigation_bar_title,
                keywords = listOf("navigation bar", "controls", "system", "show", "hide"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_display_reduce_player_animation,
                keywords = listOf("reduce", "animation", "motion", "performance", "smooth"),
                category = "Player",
                screen = PlayerPreferencesScreen,
            ))

            // Gesture preferences
            add(SearchablePreference(
                titleRes = R.string.pref_gesture,
                summaryRes = R.string.pref_gesture_summary,
                keywords = listOf("gesture", "double tap", "swipe", "media controls", "touch"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_player_double_tap_seek_duration,
                keywords = listOf("seek", "duration", "double tap", "time", "seconds"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_double_tap_seek_area_width_title,
                summaryRes = R.string.pref_double_tap_seek_area_width_summary,
                keywords = listOf("area", "width", "double tap", "seek", "region", "percent"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_left_title,
                keywords = listOf("double tap", "left", "seek", "backward", "rewind"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_center_title,
                keywords = listOf("double tap", "center", "play", "pause", "action"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_double_tap_right_title,
                keywords = listOf("double tap", "right", "seek", "forward", "advance"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_use_single_tap_for_center_title,
                summaryRes = R.string.pref_gesture_use_single_tap_for_center_summary,
                keywords = listOf("single", "tap", "center", "play", "pause"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_previous,
                keywords = listOf("media", "previous", "gesture", "control", "backward"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_play,
                keywords = listOf("media", "play", "pause", "gesture", "control"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_media_next,
                keywords = listOf("media", "next", "gesture", "control", "forward"),
                category = "Gestures",
                screen = GesturePreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_gesture_tap_thumbnail_to_select_title,
                summaryRes = R.string.pref_gesture_tap_thumbnail_to_select_summary,
                keywords = listOf("thumbnail", "tap", "select", "play", "preview"),
                category = "Appearance",
                screen = AppearancePreferencesScreen,
            ))

            // Folder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_folders_title,
                summaryRes = R.string.pref_folders_summary,
                keywords = listOf("folders", "blacklist", "hide", "exclude", "manage"),
                category = "Folders",
                screen = FoldersPreferencesScreen,
            ))

            // Decoder preferences
            add(SearchablePreference(
                titleRes = R.string.pref_decoder,
                summaryRes = R.string.pref_decoder_summary,
                keywords = listOf("decoder", "hardware", "gpu", "debanding", "video"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_try_hw_dec_title,
                keywords = listOf("hardware", "decoding", "hw", "acceleration", "gpu"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_gpu_next_title,
                summaryRes = R.string.pref_decoder_gpu_next_summary,
                keywords = listOf("gpu", "next", "rendering", "backend", "vulkan", "opengl"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_debanding_title,
                keywords = listOf("deband", "banding", "gradient", "visual", "quality"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_decoder_yuv420p_title,
                summaryRes = R.string.pref_decoder_yuv420p_summary,
                keywords = listOf("yuv420p", "chroma", "subsampling", "format", "compatibility"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_anime4k_title,
                summaryRes = R.string.pref_anime4k_summary,
                keywords = listOf("anime4k", "upscale", "shader", "anime", "upscale"),
                category = "Decoder",
                screen = DecoderPreferencesScreen,
            ))

            // Subtitle preferences
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles,
                summaryRes = R.string.pref_subtitles_summary,
                keywords = listOf("subtitles", "subs", "language", "fonts", "text"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_preferred_languages,
                keywords = listOf("language", "preferred", "subtitle", "audio", "locale", "code"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_autoload_title,
                summaryRes = R.string.pref_subtitles_autoload_summary,
                keywords = listOf("autoload", "automatic", "subtitles", "external", "load"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.player_sheets_sub_override_ass,
                summaryRes = R.string.player_sheets_sub_override_ass_subtitle,
                keywords = listOf("ass", "override", "subtitle", "ssa", "format", "style"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.player_sheets_sub_scale_by_window,
                summaryRes = R.string.player_sheets_sub_scale_by_window_summary,
                keywords = listOf("scale", "window", "subtitle", "size", "resize", "fit"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_subtitles_fonts_dir,
                keywords = listOf("fonts", "directory", "subtitle", "custom", "folder"),
                category = "Subtitles",
                screen = SubtitlesPreferencesScreen,
            ))

            // Audio preferences
            add(SearchablePreference(
                titleRes = R.string.pref_audio,
                summaryRes = R.string.pref_audio_summary,
                keywords = listOf("audio", "language", "channels", "pitch", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_preferred_languages,
                keywords = listOf("language", "preferred", "subtitle", "audio", "locale", "code"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_pitch_correction_title,
                summaryRes = R.string.pref_audio_pitch_correction_summary,
                keywords = listOf("pitch", "correction", "speed", "audio", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_normalization_title,
                summaryRes = R.string.pref_audio_volume_normalization_summary,
                keywords = listOf("volume", "normalization", "loudness", "audio", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.background_playback_title,
                keywords = listOf("background", "playback", "audio", "service", "music"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_channels,
                keywords = listOf("channels", "audio", "stereo", "surround", "output", "sound"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_audio_volume_boost_cap,
                keywords = listOf("volume", "boost", "cap", "maximum", "amplify"),
                category = "Audio",
                screen = AudioPreferencesScreen,
            ))

            // Advanced preferences
            add(SearchablePreference(
                titleRes = R.string.pref_advanced,
                summaryRes = R.string.pref_advanced_summary,
                keywords = listOf("advanced", "mpv", "config", "logs", "debug"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_export_settings_title,
                summaryRes = R.string.pref_export_settings_summary,
                keywords = listOf("export", "backup", "settings", "xml", "save"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_import_settings_title,
                summaryRes = R.string.pref_import_settings_summary,
                keywords = listOf("import", "restore", "settings", "xml", "load"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_mpv_conf_storage_location,
                keywords = listOf("storage", "location", "directory", "folder", "config"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_mpv_conf,
                keywords = listOf("mpv", "conf", "config", "configuration", "settings"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_input_conf,
                keywords = listOf("input", "conf", "keybindings", "shortcuts", "keys", "controls"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_enable_lua_scripts_title,
                summaryRes = R.string.pref_enable_lua_scripts_summary,
                keywords = listOf("scripts", "lua", "enable", "load", "plugin"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_manage_lua_scripts_title,
                summaryRes = R.string.pref_manage_lua_scripts_summary,
                keywords = listOf("scripts", "lua", "manage", "select", "plugin"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_enable_recently_played_title,
                summaryRes = R.string.pref_advanced_enable_recently_played_summary,
                keywords = listOf("recently", "played", "history", "enable", "track"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_playback_history,
                keywords = listOf("clear", "history", "playback", "reset", "delete"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_clear_config_cache_title,
                summaryRes = R.string.pref_clear_config_cache_summary,
                keywords = listOf("clear", "config", "cache", "mpv", "settings"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_clear_thumbnail_cache_title,
                summaryRes = R.string.pref_clear_thumbnail_cache_summary,
                keywords = listOf("clear", "thumbnail", "cache", "preview", "images"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_clear_fonts_cache,
                keywords = listOf("clear", "fonts", "cache", "reset"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_verbose_logging_title,
                summaryRes = R.string.pref_advanced_verbose_logging_summary,
                keywords = listOf("verbose", "logging", "debug", "output"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))
            add(SearchablePreference(
                titleRes = R.string.pref_advanced_dump_logs_title,
                summaryRes = R.string.pref_advanced_dump_logs_summary,
                keywords = listOf("logs", "debug", "dump", "share", "export"),
                category = "Advanced",
                screen = AdvancedPreferencesScreen,
            ))

            // About
            add(SearchablePreference(
                titleRes = R.string.pref_about_title,
                summaryRes = R.string.pref_about_summary,
                keywords = listOf("about", "version", "licenses", "acknowledgments", "info", "app"),
                category = "About",
                screen = AboutScreen,
            ))
        }
    }

    /**
     * Search preferences by query.
     * Simple case-insensitive search against title, summary, keywords, and category.
     */
    fun search(query: String, getStringRes: (Int) -> String): List<SearchablePreference> {
        if (query.isBlank()) return emptyList()

        val normalizedQuery = query.lowercase().trim()

        return allPreferences.filter { pref ->
            val title = (if (pref.titleRes != null) getStringRes(pref.titleRes) else pref.title ?: "").lowercase()
            val summary = (if (pref.summaryRes != null) getStringRes(pref.summaryRes) else pref.summary ?: "").lowercase()
            val keywords = pref.keywords.joinToString(" ").lowercase()
            val category = pref.category.lowercase()

            title.contains(normalizedQuery) ||
                    summary.contains(normalizedQuery) ||
                    keywords.contains(normalizedQuery) ||
                    category.contains(normalizedQuery)
        }
    }
}
