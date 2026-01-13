package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.player.Debanding
import app.marlboroadvance.mpvex.ui.player.Scaler
import app.marlboroadvance.mpvex.ui.player.TemporalScaler
import app.marlboroadvance.mpvex.ui.player.VideoSync

class DecoderPreferences(
  preferenceStore: PreferenceStore,
) {
  val tryHWDecoding = preferenceStore.getBoolean("try_hw_dec", true)
  val gpuNext = preferenceStore.getBoolean("gpu_next")
  val useYUV420P = preferenceStore.getBoolean("use_yuv420p", false)

  val debanding = preferenceStore.getEnum("debanding", Debanding.None)
  val debandIterations = preferenceStore.getInt("deband_iterations", 1)
  val debandThreshold = preferenceStore.getInt("deband_threshold", 48)
  val debandRange = preferenceStore.getInt("deband_range", 16)
  val debandGrain = preferenceStore.getInt("deband_grain", 32)

  val brightnessFilter = preferenceStore.getInt("filter_brightness")
  val saturationFilter = preferenceStore.getInt("filter_saturation")
  val gammaFilter = preferenceStore.getInt("filter_gamma")
  val contrastFilter = preferenceStore.getInt("filter_contrast")
  val hueFilter = preferenceStore.getInt("filter_hue")
  val sharpnessFilter = preferenceStore.getInt("filter_sharpness")

  // Interpolation settings
  val videoInterpolation = preferenceStore.getBoolean("video_interpolation", false)
  val videoSync = preferenceStore.getEnum("video_sync", VideoSync.Audio)

  // Spatial scaler (upscaling)
  val videoScale = preferenceStore.getEnum("video_scale", Scaler.Bilinear)
  val videoScaleParam1 = preferenceStore.getString("video_scale_param1", "")
  val videoScaleParam2 = preferenceStore.getString("video_scale_param2", "")

  // Spatial scaler (downscaling)
  val videoDownscale = preferenceStore.getEnum("video_downscale", Scaler.Bilinear)
  val videoDownscaleParam1 = preferenceStore.getString("video_downscale_param1", "")
  val videoDownscaleParam2 = preferenceStore.getString("video_downscale_param2", "")

  // Temporal scaler (for interpolation)
  val videoTscale = preferenceStore.getEnum("video_tscale", TemporalScaler.Linear)
  val videoTscaleParam1 = preferenceStore.getString("video_tscale_param1", "")
  val videoTscaleParam2 = preferenceStore.getString("video_tscale_param2", "")

  // Anime4K Preferences
  val enableAnime4K = preferenceStore.getBoolean("enable_anime4k", false)
  val anime4kMode = preferenceStore.getString("anime4k_mode", "OFF")
  val anime4kQuality = preferenceStore.getString("anime4k_quality", "FAST")
}
