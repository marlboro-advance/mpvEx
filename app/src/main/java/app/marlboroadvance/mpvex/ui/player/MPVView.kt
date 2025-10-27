package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
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

  var isExiting = false

  /**
   * Returns the video aspect ratio. Rotation is taken into account.
   */
  fun getVideoOutAspect(): Double? {
    return MPVLib.getPropertyDouble("video-params/aspect")?.let {
      if (it < 0.001) return 0.0
      if ((MPVLib.getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
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
    setVo(if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu")
    MPVLib.setOptionString("profile", "fast")
    // Set hwdec with fallback order: HW+ (mediacodec) -> SW (no) -> HW (mediacodec-copy)
    MPVLib.setOptionString(
      "hwdec",
      if (decoderPreferences.tryHWDecoding.get()) "mediacodec,no,mediacodec-copy" else "no",
    )
    MPVLib.setOptionString("hwdec-codecs", "all")

    if (decoderPreferences.useYUV420P.get()) {
      MPVLib.setOptionString("vf", "format=yuv420p")
    }
    MPVLib.setOptionString("msg-level", "all=" + if (advancedPreferences.verboseLogging.get()) "v" else "warn")

    MPVLib.setPropertyBoolean("keep-open", true)
    MPVLib.setPropertyBoolean("input-default-bindings", true)

    MPVLib.setOptionString("tls-verify", "yes")
    MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

    // Limit demuxer cache since the defaults are too high for mobile devices
    val cacheMegs = 64
    MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
    MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    //
    val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    screenshotDir.mkdirs()
    MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

    VideoFilters.entries.forEach {
      MPVLib.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
    }

    MPVLib.setOptionString("speed", playerPreferences.defaultSpeed.get().toString())
    // workaround for <https://github.com/mpv-player/mpv/issues/14651>
    MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

    // Improve seek responsiveness/smoothness on mobile
    MPVLib.setOptionString("hr-seek", "yes")
    MPVLib.setOptionString("hr-seek-framedrop", "yes")
    MPVLib.setOptionString("demuxer-readahead-secs", "8")
    MPVLib.setOptionString("demuxer-seekable-cache", "yes")
    MPVLib.setOptionString("cache", "yes")
    MPVLib.setOptionString("cache-secs", "8")

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
        if (event.repeatCount == 0) {
          Log.d(TAG, "Unmapped non-printable key ${event.keyCode}")
        }
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
      "video-params/aspect" to MPVLib.MpvFormat.MPV_FORMAT_DOUBLE,
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
    MPVLib.setOptionString("alang", audioPreferences.preferredLanguages.get())
    MPVLib.setOptionString("audio-delay", (audioPreferences.defaultAudioDelay.get() / 1000.0).toString())
    MPVLib.setOptionString("audio-pitch-correction", audioPreferences.audioPitchCorrection.get().toString())
    MPVLib.setOptionString("volume-max", (audioPreferences.volumeBoostCap.get() + 100).toString())
  }

  // Setup
  private fun setupSubtitlesOptions() {
    MPVLib.setOptionString("slang", subtitlesPreferences.preferredLanguages.get())

    // Disable automatic subtitle loading from video directory
    // Users must manually add subtitles via the subtitle picker
    MPVLib.setOptionString("sub-auto", "no")

    // Load external subtitle files from the same directory as the video
    MPVLib.setOptionString("sub-file-paths", "")

    val fontsDirPath = context.filesDir.path + "/fonts/"
    MPVLib.setOptionString("sub-fonts-dir", fontsDirPath)
    MPVLib.setOptionString("sub-delay", (subtitlesPreferences.defaultSubDelay.get() / 1000.0).toString())
    MPVLib.setOptionString("sub-speed", subtitlesPreferences.defaultSubSpeed.get().toString())
    MPVLib.setOptionString(
      "secondary-sub-delay",
      (subtitlesPreferences.defaultSecondarySubDelay.get() / 1000.0).toString(),
    )

    // With fonts cached persistently in filesDir/fonts, just set preferred or fallback
    val preferredFontFamily = subtitlesPreferences.font.get()
    MPVLib.setOptionString("sub-font", preferredFontFamily.ifBlank { "sans-serif" })

    // Removed SSA/ASS global override; rely on track styling and per-justify settings only
    MPVLib.setOptionString("sub-font-size", subtitlesPreferences.fontSize.get().toString())
    MPVLib.setOptionString("sub-bold", if (subtitlesPreferences.bold.get()) "yes" else "no")
    MPVLib.setOptionString("sub-italic", if (subtitlesPreferences.italic.get()) "yes" else "no")
    MPVLib.setOptionString("sub-justify", subtitlesPreferences.justification.get().value)
    MPVLib.setOptionString("sub-color", subtitlesPreferences.textColor.get().toColorHexString())
    MPVLib.setOptionString("sub-back-color", subtitlesPreferences.backgroundColor.get().toColorHexString())
    MPVLib.setOptionString("sub-border-color", subtitlesPreferences.borderColor.get().toColorHexString())
    MPVLib.setOptionString("sub-border-size", subtitlesPreferences.borderSize.get().toString())
    MPVLib.setOptionString("sub-border-style", subtitlesPreferences.borderStyle.get().value)
    MPVLib.setOptionString("sub-shadow-offset", subtitlesPreferences.shadowOffset.get().toString())
    MPVLib.setOptionString("sub-pos", subtitlesPreferences.subPos.get().toString())
    MPVLib.setOptionString("sub-scale", subtitlesPreferences.subScale.get().toString())
  }
}
