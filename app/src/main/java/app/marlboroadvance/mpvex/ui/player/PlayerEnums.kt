package app.marlboroadvance.mpvex.ui.player

import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.Preference

enum class PlayerOrientation(
  @StringRes val titleRes: Int,
) {
  Free(R.string.pref_player_orientation_free),
  Video(R.string.pref_player_orientation_video),
  Portrait(R.string.pref_player_orientation_portrait),
  ReversePortrait(R.string.pref_player_orientation_reverse_portrait),
  SensorPortrait(R.string.pref_player_orientation_sensor_portrait),
  Landscape(R.string.pref_player_orientation_landscape),
  ReverseLandscape(R.string.pref_player_orientation_reverse_landscape),
  SensorLandscape(R.string.pref_player_orientation_sensor_landscape),
}

enum class VideoAspect(
  @StringRes val titleRes: Int,
) {
  Crop(R.string.player_aspect_crop),
  Fit(R.string.player_aspect_fit),
  Stretch(R.string.player_aspect_stretch),
}

enum class SingleActionGesture(
  @StringRes val titleRes: Int,
) {
  None(R.string.pref_gesture_double_tap_none),
  Seek(R.string.pref_gesture_double_tap_seek),
  PlayPause(R.string.pref_gesture_double_tap_play),
  Custom(R.string.pref_gesture_double_tap_custom),
}

enum class CustomKeyCodes(
  val keyCode: String,
) {
  DoubleTapLeft("MBTN_LEFT_DBL"),
  DoubleTapCenter("MBTN_MID_DBL"),
  DoubleTapRight("MBTN_RIGHT_DBL"),
  MediaPrevious("PREV"),
  MediaPlay("PLAYPAUSE"),
  MediaNext("NEXT"),
}

enum class Decoder(
  val title: String,
  val value: String,
) {
  AutoCopy("Auto", "auto-copy"),
  Auto("Auto", "auto"),
  SW("SW", "no"),
  HW("HW", "mediacodec-copy"),
  HWPlus("HW+", "mediacodec"),
  ;

  companion object {
    fun getDecoderFromValue(value: String): Decoder = Decoder.entries.first { it.value == value }
  }
}

enum class Debanding(
  @StringRes val titleRes: Int,
) {
  None(R.string.player_sheets_deband_none),
  CPU(R.string.player_sheets_deband_cpu),
  GPU(R.string.player_sheets_deband_gpu),
}

enum class Sheets {
  None,
  PlaybackSpeed,
  SubtitleTracks,
  SubtitleSearch,
  AudioTracks,
  Chapters,
  Decoders,
  More,
  VideoZoom,
  AspectRatios,
  FrameNavigation,
}

enum class Panels {
  None,
  SubtitleSettings,
  SubtitleDelay,
  AudioDelay,
  VideoFilters,
}

sealed class PlayerUpdates {
  data object None : PlayerUpdates()

  data object MultipleSpeed : PlayerUpdates()

  data class DynamicSpeedControl(
    val speed: Float,
    val showFullOverlay: Boolean = true,
  ) : PlayerUpdates()

  data object AspectRatio : PlayerUpdates()

  data object VideoZoom : PlayerUpdates()

  data class ShowText(
    val value: String,
  ) : PlayerUpdates()

  data class RepeatMode(
    val mode: app.marlboroadvance.mpvex.ui.player.RepeatMode,
  ) : PlayerUpdates()

  data class Shuffle(
    val enabled: Boolean,
  ) : PlayerUpdates()

  data class FrameInfo(
    val currentFrame: Int,
    val totalFrames: Int,
  ) : PlayerUpdates()
}

enum class VideoFilters(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
) {
  BRIGHTNESS(
    R.string.player_sheets_filters_brightness,
    { it.brightnessFilter },
    "brightness",
  ),
  SATURATION(
    R.string.player_sheets_filters_Saturation,
    { it.saturationFilter },
    "saturation",
  ),
  CONTRAST(
    R.string.player_sheets_filters_contrast,
    { it.contrastFilter },
    "contrast",
  ),
  GAMMA(
    R.string.player_sheets_filters_gamma,
    { it.gammaFilter },
    "gamma",
  ),
  HUE(
    R.string.player_sheets_filters_hue,
    { it.hueFilter },
    "hue",
  ),
}

enum class DebandSettings(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
  val start: Int,
  val end: Int,
) {
  Iterations(
    R.string.player_sheets_deband_iterations,
    { it.debandIterations },
    "deband-iterations",
    0,
    16,
  ),
  Threshold(
    R.string.player_sheets_deband_threshold,
    { it.debandThreshold },
    "deband-threshold",
    0,
    200,
  ),
  Range(
    R.string.player_sheets_deband_range,
    { it.debandRange },
    "deband-range",
    1,
    64,
  ),
  Grain(
    R.string.player_sheets_deband_grain,
    { it.debandGrain },
    "deband-grain",
    0,
    200,
  ),
}

enum class VideoSync(
  val displayName: String,
  val value: String,
) {
  Audio("Sync to audio", "audio"),
  DisplayResample("Display resample", "display-resample"),
  DisplayResampleVdrop("Display resample (video drop)", "display-resample-vdrop"),
  DisplayVdrop("Display (video drop)", "display-vdrop"),
  DisplayAdrop("Display (audio drop)", "display-adrop"),
  ;

  companion object {
    fun getDisplayResampleModes(): List<VideoSync> =
      listOf(
        DisplayResample,
        DisplayResampleVdrop,
        DisplayVdrop,
        DisplayAdrop,
      )
  }
}

enum class Scaler(
  val displayName: String,
  val value: String,
) {
  Bilinear("Bilinear", "bilinear"),
  BicubicFast("Bicubic Fast", "bicubic_fast"),
  Oversample("Oversample", "oversample"),
  Spline16("Spline 16", "spline16"),
  Spline36("Spline 36", "spline36"),
  Spline64("Spline 64", "spline64"),
  Sinc("Sinc", "sinc"),
  Lanczos("Lanczos", "lanczos"),
  Ginseng("Ginseng", "ginseng"),
  Jinc("Jinc", "jinc"),
  EwaLanczos("EWA Lanczos", "ewa_lanczos"),
  EwaHanning("EWA Hanning", "ewa_hanning"),
  EwaGinseng("EWA Ginseng", "ewa_ginseng"),
  EwaLanczossharp("EWA Lanczos Sharp", "ewa_lanczossharp"),
  EwaLanczos4sharpest("EWA Lanczos 4 Sharpest", "ewa_lanczos4sharpest"),
  EwaLanczossoft("EWA Lanczos Soft", "ewa_lanczossoft"),
  Haasnsoft("Haasnsoft", "haasnsoft"),
  Bicubic("Bicubic", "bicubic"),
  Hermite("Hermite", "hermite"),
  CatmullRom("Catmull-Rom", "catmull_rom"),
  Mitchell("Mitchell", "mitchell"),
  Robidoux("Robidoux", "robidoux"),
  Robidouxsharp("Robidoux Sharp", "robidouxsharp"),
  EwaRobidoux("EWA Robidoux", "ewa_robidoux"),
  EwaRobidouxsharp("EWA Robidoux Sharp", "ewa_robidouxsharp"),
  Box("Box", "box"),
  Nearest("Nearest", "nearest"),
  Triangle("Triangle", "triangle"),
  Gaussian("Gaussian", "gaussian"),
  Bartlett("Bartlett", "bartlett"),
  Cosine("Cosine", "cosine"),
  Tukey("Tukey", "tukey"),
  Hamming("Hamming", "hamming"),
  Quadric("Quadric", "quadric"),
  Welch("Welch", "welch"),
  Kaiser("Kaiser", "kaiser"),
  Blackman("Blackman", "blackman"),
  Sphinx("Sphinx", "sphinx"),
}

enum class TemporalScaler(
  val displayName: String,
  val value: String,
) {
  Oversample("Oversample", "oversample"),
  Linear("Linear", "linear"),
  CatmullRom("Catmull-Rom", "catmull_rom"),
  Mitchell("Mitchell", "mitchell"),
  Gaussian("Gaussian", "gaussian"),
  Bicubic("Bicubic", "bicubic"),
}
