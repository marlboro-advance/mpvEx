package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.player.PlayerOrientation
import app.marlboroadvance.mpvex.ui.player.VideoAspect

class PlayerPreferences(
    preferenceStore: PreferenceStore,
) {
    val orientation = preferenceStore.getEnum("player_orientation", PlayerOrientation.SensorLandscape)
    val invertDuration = preferenceStore.getBoolean("invert_duration")
    val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
    val horizontalSeekGesture = preferenceStore.getBoolean("horizontal_seek_gesture", true)
    val showSeekBarWhenSeeking = preferenceStore.getBoolean("show_seekbar_when_seeking")
    val showDoubleTapOvals = preferenceStore.getBoolean("show_double_tap_ovals", true)
    val showSeekTimeWhileSeeking = preferenceStore.getBoolean("show_seek_time_while_seeking", true)

    val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
    val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)
    val pinchToZoomGesture = preferenceStore.getBoolean("pinch_to_zoom_gesture", true)

    val videoAspect = preferenceStore.getEnum("video_aspect", VideoAspect.Fit)
    val customAspectRatios = preferenceStore.getStringSet("custom_aspect_ratios", emptySet())
    val currentAspectRatio = preferenceStore.getFloat("current_aspect_ratio", -1f)
    val currentChaptersIndicator = preferenceStore.getBoolean("show_video_chapter_indicator", true)
    val showChaptersButton = preferenceStore.getBoolean("show_video_chapters_button")

    val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
    val speedPresets =
        preferenceStore.getStringSet(
            "default_speed_presets",
            setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
        )
    val displayVolumeAsPercentage = preferenceStore.getBoolean("display_volume_as_percentage", true)
    val swapVolumeAndBrightness = preferenceStore.getBoolean("display_volume_on_right")
    val showLoadingCircle = preferenceStore.getBoolean("show_loading_circle", true)
    val savePositionOnQuit = preferenceStore.getBoolean("save_position", true)

    val automaticallyEnterPip = preferenceStore.getBoolean("automatic_pip")
    val closeAfterReachingEndOfVideo = preferenceStore.getBoolean("close_after_eof", true)

    val rememberBrightness = preferenceStore.getBoolean("remember_brightness")
    val defaultBrightness = preferenceStore.getFloat("default_brightness", -1f)

    val allowGesturesInPanels = preferenceStore.getBoolean("allow_gestures_in_panels")
    val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar")
    val reduceMotion = preferenceStore.getBoolean("reduce_motion", true)
    val playerTimeToDisappear = preferenceStore.getInt("player_time_to_disappear", 4000)

    val panelTransparency = preferenceStore.getFloat("panel_transparency", 0.6f)

    val defaultVideoZoom = preferenceStore.getFloat("default_video_zoom", 0f)
}
