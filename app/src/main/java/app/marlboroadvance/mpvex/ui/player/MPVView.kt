package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.os.Environment
import android.util.AttributeSet

import android.view.KeyCharacterMap
import android.view.KeyEvent
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.domain.anime4k.Anime4KManager
import app.marlboroadvance.mpvex.ui.player.PlayerActivity.Companion.TAG
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.toColorHexString
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPVLib
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KProperty

class MPVView(
  context: Context,
  attributes: AttributeSet,
) : BaseMPVView(context, attributes),
  KoinComponent {
  private val audioPreferences: AudioPreferences by inject()
  private val playerPreferences: PlayerPreferences by inject()
  private val decoderPreferences: DecoderPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val anime4kManager: Anime4KManager by inject()

  var isExiting = false

  fun getVideoOutAspect(): Double? {
    // Try to get aspect from video-params/aspect first
    val rawAspect = MPVLib.getPropertyDouble("video-params/aspect")
    val rotate = MPVLib.getPropertyInt("video-params/rotate") ?: 0

    // If aspect is not available or 0, calculate from width and height
    val finalAspect = if (rawAspect == null || rawAspect < 0.001) {
      val width = runCatching {
        MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
      }.getOrDefault(0)

      val height = runCatching {
        MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
      }.getOrDefault(0)

      if (width > 0 && height > 0) {
        width.toDouble() / height.toDouble()
      } else {
        null
      }
    } else {
      rawAspect
    }

    return finalAspect?.let { aspect ->
      if (aspect <= 0.001) {
        return null
      }
      val isRotated = (rotate % 180 == 90)
      val correctedAspect = if (isRotated) 1.0 / aspect else aspect
      correctedAspect
    }
  }

  class TrackDelegate(
    private val name: String,
  ) {
    operator fun getValue(
      thisRef: Any?,
      property: KProperty<*>,
    ): Int {
      val v = MPVLib.getPropertyString(name)
      // we can get null here for "no" or other invalid value
      return v?.toIntOrNull() ?: -1
    }

    operator fun setValue(
      thisRef: Any?,
      property: KProperty<*>,
      value: Int,
    ) {
      if (value == -1) MPVLib.setPropertyString(name, "no") else MPVLib.setPropertyInt(name, value)
    }
  }

  var sid: Int by TrackDelegate("sid")
  var secondarySid: Int by TrackDelegate("secondary-sid")
  var aid: Int by TrackDelegate("aid")

  override fun initOptions() {
    // Note: Anime4K and gpu-next are mutually exclusive in settings
    val useGpuNext = decoderPreferences.gpuNext.get()
    setVo(if (useGpuNext) "gpu-next" else "gpu")
    // Removed profile=fast for better video quality

    // Set hwdec with fallback order: HW+ (mediacodec) -> HW (mediacodec-copy) -> SW (no)
    MPVLib.setOptionString(
      "hwdec",
      if (decoderPreferences.tryHWDecoding.get()) "mediacodec,mediacodec-copy,no" else "no",
    )
    MPVLib.setOptionString("hwdec-codecs", "all")

    if (decoderPreferences.useYUV420P.get()) {
      MPVLib.setOptionString("vf", "format=yuv420p")
    }
    val logLevel = if (advancedPreferences.verboseLogging.get()) "v" else "warn"
    MPVLib.setOptionString("msg-level", "all=$logLevel")

    MPVLib.setPropertyBoolean("keep-open", true)
    MPVLib.setPropertyBoolean("input-default-bindings", true)

    MPVLib.setOptionString("tls-verify", "yes")
    MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

    val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    screenshotDir.mkdirs()
    MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

    VideoFilters.entries.forEach {
      MPVLib.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
    }

    // Apply interpolation settings
    MPVLib.setOptionString("video-sync", decoderPreferences.videoSync.get().value)
    if (decoderPreferences.videoInterpolation.get()) {
      MPVLib.setOptionString("interpolation", "yes")
    }

    // Apply scaler settings
    // Spatial upscaling
    MPVLib.setOptionString("scale", decoderPreferences.videoScale.get().value)
    if (decoderPreferences.videoScaleParam1.get().isNotBlank()) {
      MPVLib.setOptionString("scale-param1", decoderPreferences.videoScaleParam1.get())
    }
    if (decoderPreferences.videoScaleParam2.get().isNotBlank()) {
      MPVLib.setOptionString("scale-param2", decoderPreferences.videoScaleParam2.get())
    }

    // Spatial downscaling
    MPVLib.setOptionString("dscale", decoderPreferences.videoDownscale.get().value)
    if (decoderPreferences.videoDownscaleParam1.get().isNotBlank()) {
      MPVLib.setOptionString("dscale-param1", decoderPreferences.videoDownscaleParam1.get())
    }
    if (decoderPreferences.videoDownscaleParam2.get().isNotBlank()) {
      MPVLib.setOptionString("dscale-param2", decoderPreferences.videoDownscaleParam2.get())
    }

    // Chroma upscaling (improves color clarity)
    MPVLib.setOptionString("cscale", decoderPreferences.videoScale.get().value)

    // Quality enhancements for better video rendering
    MPVLib.setOptionString("correct-downscaling", "yes")
    MPVLib.setOptionString("linear-downscaling", "yes")
    MPVLib.setOptionString("sigmoid-upscaling", "yes")
    MPVLib.setOptionString("dither-depth", "auto")

    // Temporal scaling (for interpolation)
    MPVLib.setOptionString("tscale", decoderPreferences.videoTscale.get().value)
    if (decoderPreferences.videoTscaleParam1.get().isNotBlank()) {
      MPVLib.setOptionString("tscale-param1", decoderPreferences.videoTscaleParam1.get())
    }
    if (decoderPreferences.videoTscaleParam2.get().isNotBlank()) {
      MPVLib.setOptionString("tscale-param2", decoderPreferences.videoTscaleParam2.get())
    }

    MPVLib.setOptionString("speed", playerPreferences.defaultSpeed.get().toString())
    MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

    val preciseSeek = playerPreferences.usePreciseSeeking.get()
    MPVLib.setOptionString("hr-seek", if (preciseSeek) "yes" else "no")
    MPVLib.setOptionString("hr-seek-framedrop", if (preciseSeek) "no" else "yes")

    // Anime4K shader initialization (MUST be in initOptions, not after file load!)
    applyAnime4KShaders()

    setupSubtitlesOptions()
    setupAudioOptions()
  }

  override fun observeProperties() {
    for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
  }

  override fun postInitOptions() {
    when (decoderPreferences.debanding.get()) {
      Debanding.None -> {}
      Debanding.CPU -> MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
      Debanding.GPU -> MPVLib.setOptionString("deband", "yes")
    }

    advancedPreferences.enabledStatisticsPage.get().let {
      if (it != 0) {
        MPVLib.command("script-binding", "stats/display-stats-toggle")
        MPVLib.command("script-binding", "stats/display-page-$it")
      }
    }
  }

  @Suppress("ReturnCount", "DEPRECATION")
  fun onKey(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
      return false
    }

    var mapped = KeyMapping[event.keyCode]
    if (mapped == null) {
      // Fallback to produced glyph
      if (!event.isPrintingKey) {
        return false
      }

      val ch = event.unicodeChar
      if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
        return false // dead key
      }
      mapped = ch.toChar().toString()
    }

    if (event.repeatCount > 0) {
      return true
    }

    val mod: MutableList<String> = mutableListOf()
    event.isShiftPressed && mod.add("shift")
    event.isCtrlPressed && mod.add("ctrl")
    event.isAltPressed && mod.add("alt")
    event.isMetaPressed && mod.add("meta")

    val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
    mod.add(mapped)
    MPVLib.command(action, mod.joinToString("+"))

    return true
  }

  private val observedProps =
    mapOf(
      "pause" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "paused-for-cache" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "video-params/aspect" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
      "video-params/w" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "video-params/h" to MPVLib.MpvFormat.MPV_FORMAT_INT64,
      "eof-reached" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "user-data/mpvex/show_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/toggle_ui" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/show_panel" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/set_button_title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/reset_button_title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/toggle_button" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_by" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_to" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_by_with_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/seek_to_with_text" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "user-data/mpvex/software_keyboard" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
    )

  private fun setupAudioOptions() {
    // Disable MPV's automatic audio selection
    // App will handle track selection manually via TrackSelector to respect user choices
    MPVLib.setOptionString("alang", "")
    MPVLib.setOptionString("audio-delay", (audioPreferences.defaultAudioDelay.get() / 1000.0).toString())
    MPVLib.setOptionString("audio-pitch-correction", audioPreferences.audioPitchCorrection.get().toString())
    MPVLib.setOptionString("volume-max", (audioPreferences.volumeBoostCap.get() + 100).toString())
    
    // Volume normalization using dynamic audio normalization filter
    if (audioPreferences.volumeNormalization.get()) {
      MPVLib.setOptionString("af", "dynaudnorm")
    }
  }

  // Setup
  private fun setupSubtitlesOptions() {
    // Disable MPV's automatic subtitle selection
    // App will handle track selection manually via TrackSelector to respect user choices
    MPVLib.setOptionString("slang", "")
    MPVLib.setOptionString("sub-auto", "no")
    MPVLib.setOptionString("sub-file-paths", "")
    MPVLib.setOptionString("subs-fallback", "no")

    val fontsDirPath = "${context.filesDir.path}/fonts/"
    MPVLib.setOptionString("sub-fonts-dir", fontsDirPath)
    
    // Delay and speed for both primary and secondary
    val subDelay = (subtitlesPreferences.defaultSubDelay.get() / 1000.0).toString()
    val subSpeed = subtitlesPreferences.defaultSubSpeed.get().toString()
    MPVLib.setOptionString("sub-delay", subDelay)
    MPVLib.setOptionString("sub-speed", subSpeed)
    MPVLib.setOptionString("secondary-sub-delay", subDelay)
    MPVLib.setOptionString("secondary-sub-speed", subSpeed)

    val preferredFont = subtitlesPreferences.font.get()
    if (preferredFont.isNotBlank()) {
      MPVLib.setOptionString("sub-font", preferredFont)
      MPVLib.setOptionString("secondary-sub-font", preferredFont)
    }
    // If blank, MPV uses its default font

    if (subtitlesPreferences.overrideAssSubs.get()) {
      MPVLib.setOptionString("sub-ass-override", "force")
      MPVLib.setOptionString("sub-ass-justify", "yes")
      MPVLib.setOptionString("secondary-sub-ass-override", "force")
    } else {
      MPVLib.setOptionString("sub-ass-override", "no")
      MPVLib.setOptionString("secondary-sub-ass-override", "no")
    }

    // Typography and styling for both primary and secondary
    val fontSize = subtitlesPreferences.fontSize.get().toString()
    val bold = if (subtitlesPreferences.bold.get()) "yes" else "no"
    val italic = if (subtitlesPreferences.italic.get()) "yes" else "no"
    val justify = subtitlesPreferences.justification.get().value
    val textColor = subtitlesPreferences.textColor.get().toColorHexString()
    val backgroundColor = subtitlesPreferences.backgroundColor.get().toColorHexString()
    val borderColor = subtitlesPreferences.borderColor.get().toColorHexString()
    val borderSize = subtitlesPreferences.borderSize.get().toString()
    val borderStyle = subtitlesPreferences.borderStyle.get().value
    val shadowOffset = subtitlesPreferences.shadowOffset.get().toString()
    val subPos = subtitlesPreferences.subPos.get().toString()
    val subScale = subtitlesPreferences.subScale.get().toString()

    MPVLib.setOptionString("sub-font-size", fontSize)
    MPVLib.setOptionString("sub-bold", bold)
    MPVLib.setOptionString("sub-italic", italic)
    MPVLib.setOptionString("sub-justify", justify)
    MPVLib.setOptionString("sub-color", textColor)
    MPVLib.setOptionString("sub-back-color", backgroundColor)
    MPVLib.setOptionString("sub-border-color", borderColor)
    MPVLib.setOptionString("sub-border-size", borderSize)
    MPVLib.setOptionString("sub-border-style", borderStyle)
    MPVLib.setOptionString("sub-shadow-offset", shadowOffset)
    MPVLib.setOptionString("sub-scale", subScale)
    MPVLib.setOptionString("sub-pos", subPos)
    
    MPVLib.setOptionString("secondary-sub-font-size", fontSize)
    MPVLib.setOptionString("secondary-sub-bold", bold)
    MPVLib.setOptionString("secondary-sub-italic", italic)
    MPVLib.setOptionString("secondary-sub-justify", justify)
    MPVLib.setOptionString("secondary-sub-color", textColor)
    MPVLib.setOptionString("secondary-sub-back-color", backgroundColor)
    MPVLib.setOptionString("secondary-sub-border-color", borderColor)
    MPVLib.setOptionString("secondary-sub-border-size", borderSize)
    MPVLib.setOptionString("secondary-sub-border-style", borderStyle)
    MPVLib.setOptionString("secondary-sub-shadow-offset", shadowOffset)
    MPVLib.setOptionString("secondary-sub-scale", subScale)
    MPVLib.setOptionString("secondary-sub-pos", subPos)

    val scaleByWindow = if (subtitlesPreferences.scaleByWindow.get()) "yes" else "no"
    MPVLib.setOptionString("sub-scale-by-window", scaleByWindow)
    MPVLib.setOptionString("sub-use-margins", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-scale-by-window", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-use-margins", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-scale-by-window", scaleByWindow)
    MPVLib.setOptionString("secondary-sub-use-margins", scaleByWindow)
  }


  private fun applyAnime4KShaders() {
    runCatching {
      val enabled = decoderPreferences.enableAnime4K.get()
      if (!enabled) {
        return
      }
      
      // DEFENSIVE CHECK: Ensure mutual exclusion at initialization time
      val gpuNextActive = decoderPreferences.gpuNext.get()
      if (gpuNextActive) {
        return  // Abort shader loading to prevent incompatible state
      }
      
      // Initialize shader files if needed - THIS IS CRITICAL!
      if (!anime4kManager.initialize()) {
        return
      }
      
      // Get preferences
      val modeStr = decoderPreferences.anime4kMode.get()
      
      // Check if mode is OFF - if so, don't apply any shaders
      if (modeStr == "OFF") {
        return  // Exit early - user wants it OFF
      }
      
      // Parse user's selected mode
      val mode = try {
          Anime4KManager.Mode.valueOf(modeStr)
      } catch (e: IllegalArgumentException) {
          Anime4KManager.Mode.OFF
      }
      
      val qualityStr = decoderPreferences.anime4kQuality.get()
      val quality = try {
        Anime4KManager.Quality.valueOf(qualityStr)
      } catch (e: IllegalArgumentException) {
        Anime4KManager.Quality.BALANCED
      }
      
      // Get shader chain from manager
      val shaderChain = anime4kManager.getShaderChain(mode, quality)
      
      if (shaderChain.isNotEmpty()) {
        // GPU optimizations for better performance
        MPVLib.setOptionString("opengl-pbo", "yes")
        MPVLib.setOptionString("vd-lavc-dr", "yes")
        MPVLib.setOptionString("opengl-early-flush", "no")
        
        // Apply shaders (MUST use setOptionString in initOptions!)
        MPVLib.setOptionString("glsl-shaders", shaderChain)
      }
    }.onFailure {
      // Don't crash - just continue without shaders
    }
  }
}
