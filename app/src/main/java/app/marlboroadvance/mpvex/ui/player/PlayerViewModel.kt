package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PlayerViewModelProviderFactory(
  private val host: PlayerHost,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(
    modelClass: Class<T>,
    extras: CreationExtras,
  ): T {
    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PlayerViewModel(host) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private val host: PlayerHost,
) : ViewModel(),
  KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val externalSubtitleRepository: ExternalSubtitleRepository by inject()
  private val json: Json by inject()

  // MPV properties with efficient collection
  val paused by MPVLib.propBoolean["pause"].collectAsState(viewModelScope)
  val pos by MPVLib.propInt["time-pos"].collectAsState(viewModelScope)
  val duration by MPVLib.propInt["duration"].collectAsState(viewModelScope)

  // Audio state
  val currentVolume = MutableStateFlow(host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)
  val maxVolume = host.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

  // Subtitle and audio tracks using StateFlow for better performance
  private val _externalSubtitleMetadata = MutableStateFlow<Map<String, String>>(emptyMap())
  val externalSubtitleMetadata: StateFlow<Map<String, String>> = _externalSubtitleMetadata.asStateFlow()

  val subtitleTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isSubtitle }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val audioTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isAudio }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val chapters: StateFlow<List<dev.vivvvek.seeker.Segment>> =
    MPVLib.propNode["chapter-list"]
      .map { node ->
        node?.toObject<List<ChapterNode>>(json)?.map { it.toSegment() }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  // UI state
  private val _controlsShown = MutableStateFlow(false)
  val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()

  private val _seekBarShown = MutableStateFlow(false)
  val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()

  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val currentBrightness =
    MutableStateFlow(
      runCatching {
        Settings.System
          .getFloat(host.hostContentResolver, Settings.System.SCREEN_BRIGHTNESS)
          .normalize(0f, 255f, 0f, 1f)
      }.getOrElse { 0f },
    )

  val sheetShown = MutableStateFlow(Sheets.None)
  val panelShown = MutableStateFlow(Panels.None)

  // Seek state
  val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)
  private val _seekText = MutableStateFlow<String?>(null)
  val seekText: StateFlow<String?> = _seekText.asStateFlow()

  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount: StateFlow<Int> = _doubleTapSeekAmount.asStateFlow()

  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards: StateFlow<Boolean> = _isSeekingForwards.asStateFlow()

  // Frame navigation
  private val _currentFrame = MutableStateFlow(0)
  val currentFrame: StateFlow<Int> = _currentFrame.asStateFlow()

  private val _totalFrames = MutableStateFlow(0)
  val totalFrames: StateFlow<Int> = _totalFrames.asStateFlow()

  // Video zoom
  private val _videoZoom = MutableStateFlow(0f)
  val videoZoom: StateFlow<Float> = _videoZoom.asStateFlow()

  // Timer
  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

  // Media title for subtitle association
  private var currentMediaTitle: String = ""

  // Cached values
  private val showStatusBar = playerPreferences.showSystemStatusBar.get()
  private val doubleTapToSeekDuration by lazy { gesturePreferences.doubleTapToSeekDuration.get() }
  private val inputMethodManager by lazy {
    host.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  }

  // Seek coalescing for smooth performance
  private var pendingSeekOffset: Int = 0
  private var seekCoalesceJob: Job? = null

  private companion object {
    const val SEEK_COALESCE_DELAY_MS = 60L
    val VALID_SUBTITLE_EXTENSIONS =
      setOf("srt", "ass", "ssa", "sub", "idx", "vtt", "sup", "txt", "pgs")
  }

  // ==================== Timer ====================

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return

    timerJob =
      viewModelScope.launch {
        for (time in seconds downTo 0) {
          _remainingTime.value = time
          delay(1000)
        }
        MPVLib.setPropertyBoolean("pause", true)
        showToast(host.context.getString(R.string.toast_sleep_timer_ended))
      }
  }

  // ==================== Decoder ====================

  fun cycleDecoders() {
    val currentDecoder = MPVLib.getPropertyString("hwdec-current") ?: return
    val nextDecoder =
      when (Decoder.getDecoderFromValue(currentDecoder)) {
        Decoder.HWPlus -> Decoder.SW
        Decoder.SW -> Decoder.HW
        Decoder.HW -> Decoder.HWPlus
        Decoder.AutoCopy, Decoder.Auto -> Decoder.HWPlus
      }
    MPVLib.setPropertyString("hwdec", nextDecoder.value)
  }

  // ==================== Audio/Subtitle Management ====================

  fun addAudio(uri: Uri) {
    viewModelScope.launch {
      runCatching {
        val path =
          uri.resolveUri(host.context)
            ?: return@launch showToast("Failed to load audio file: Invalid URI")

        MPVLib.command("audio-add", path, "cached")
        showToast("Audio track added")
      }.onFailure { e ->
        showToast("Failed to load audio: ${e.message}")
        android.util.Log.e("PlayerViewModel", "Error adding audio", e)
      }
    }
  }

  fun addSubtitle(uri: Uri) {
    viewModelScope.launch {
      runCatching {
        val fileName = getFileNameFromUri(uri) ?: "subtitle.srt"

        if (!isValidSubtitleFile(fileName)) {
          return@launch showToast("Invalid subtitle file format")
        }

        val cachedPath =
          externalSubtitleRepository
            .cacheSubtitle(uri, fileName, currentMediaTitle)
            .getOrElse { return@launch showToast("Failed to cache subtitle") }

        MPVLib.command("sub-add", cachedPath, "select")
        _externalSubtitleMetadata.update { it + (cachedPath to fileName) }

        val displayName = fileName.take(30).let { if (fileName.length > 30) "$it..." else it }
        showToast("$displayName added")
      }.onFailure {
        showToast("Failed to load subtitle")
      }
    }
  }

  fun setMediaTitle(mediaTitle: String) {
    currentMediaTitle = mediaTitle
    viewModelScope.launch {
      delay(100) // Allow MPV to set media title first
      restoreCachedSubtitles(mediaTitle)
    }
  }

  private suspend fun restoreCachedSubtitles(mediaTitle: String) {
    val subtitles = externalSubtitleRepository.getSubtitlesForMedia(mediaTitle)
    val metadata = mutableMapOf<String, String>()

    withContext(Dispatchers.IO) {
      subtitles.forEach { subtitle ->
        if (File(subtitle.cachedFilePath).exists()) {
          MPVLib.command("sub-add", subtitle.cachedFilePath, "select")
          metadata[subtitle.cachedFilePath] = subtitle.originalFileName
        } else {
          externalSubtitleRepository.deleteSubtitle(subtitle.cachedFilePath)
        }
      }
    }

    _externalSubtitleMetadata.update { metadata }
  }

  fun removeSubtitle(id: Int) {
    viewModelScope.launch {
      val tracks = MPVLib.propNode["track-list"].value?.toObject<List<TrackNode>>(json) ?: return@launch
      val track = tracks.find { it.id == id && it.isSubtitle && it.external == true }

      track?.externalFilename?.let { filename ->
        externalSubtitleRepository.deleteSubtitle(filename)
        _externalSubtitleMetadata.update { it - filename }
      }

      MPVLib.command("sub-remove", id.toString())
    }
  }

  fun addDownloadedSubtitle(
    filePath: String,
    fileName: String,
  ) {
    viewModelScope.launch {
      runCatching {
        MPVLib.command("sub-add", filePath, "select")
        _externalSubtitleMetadata.update { it + (filePath to fileName) }

        val displayName = fileName.take(30).let { if (fileName.length > 30) "$it..." else it }
        showToast("$displayName added")
      }.onFailure {
        showToast("Failed to add subtitle")
      }
    }
  }

  fun selectSub(id: Int) {
    val (primarySid, secondarySid) =
      Pair(
        MPVLib.getPropertyInt("sid"),
        MPVLib.getPropertyInt("secondary-sid"),
      )

    val (newPrimary, newSecondary) =
      when (id) {
        primarySid -> Pair(secondarySid, null)
        secondarySid -> Pair(primarySid, null)
        else -> if (primarySid != null) Pair(primarySid, id) else Pair(id, null)
      }

    newSecondary?.let { MPVLib.setPropertyInt("secondary-sid", it) }
      ?: MPVLib.setPropertyBoolean("secondary-sid", false)
    newPrimary?.let { MPVLib.setPropertyInt("sid", it) }
      ?: MPVLib.setPropertyBoolean("sid", false)
  }

  private fun getFileNameFromUri(uri: Uri): String? =
    when (uri.scheme) {
      "content" ->
        host.context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }

      "file" -> uri.lastPathSegment
      else -> uri.lastPathSegment
    }

  private fun isValidSubtitleFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in VALID_SUBTITLE_EXTENSIONS

  // ==================== Playback Control ====================

  fun pauseUnpause() = MPVLib.command("cycle", "pause")

  fun pause() = MPVLib.setPropertyBoolean("pause", true)

  fun unpause() = MPVLib.setPropertyBoolean("pause", false)

  // ==================== UI Control ====================

  fun showControls() {
    if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
    if (showStatusBar) {
      host.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
      host.windowInsetsController.isAppearanceLightStatusBars = false
    }
    _controlsShown.value = true
  }

  fun hideControls() {
    host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    _controlsShown.value = false
  }

  fun showSeekBar() {
    if (sheetShown.value == Sheets.None) {
      _seekBarShown.value = true
    }
  }

  fun hideSeekBar() {
    _seekBarShown.value = false
  }

  fun lockControls() {
    _areControlsLocked.value = true
  }

  fun unlockControls() {
    _areControlsLocked.value = false
  }

  // ==================== Seeking ====================

  fun seekBy(offset: Int) {
    coalesceSeek(offset)
  }

  fun seekTo(position: Int) {
    val maxDuration = MPVLib.getPropertyInt("duration") ?: 0
    if (position !in 0..maxDuration) return

    // Cancel pending relative seek before absolute seek
    seekCoalesceJob?.cancel()
    pendingSeekOffset = 0
    val seekMode = if (playerPreferences.usePreciseSeeking.get()) "absolute+exact" else "absolute+keyframes"
    MPVLib.command("seek", position.toString(), seekMode)
  }

  private fun coalesceSeek(offset: Int) {
    pendingSeekOffset += offset
    seekCoalesceJob?.cancel()
    seekCoalesceJob =
      viewModelScope.launch {
        delay(SEEK_COALESCE_DELAY_MS)
        val toApply = pendingSeekOffset
        pendingSeekOffset = 0
        if (toApply != 0) {
          val seekMode = if (playerPreferences.usePreciseSeeking.get()) "relative+exact" else "relative+keyframes"
          MPVLib.command("seek", toApply.toString(), seekMode)
        }
      }
  }

  fun leftSeek() {
    if ((pos ?: 0) > 0) {
      _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    }
    _isSeekingForwards.value = false
    seekBy(-doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun rightSeek() {
    if ((pos ?: 0) < (duration ?: 0)) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    seekBy(doubleTapToSeekDuration)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.value = amount
  }

  fun updateSeekText(text: String?) {
    _seekText.value = text
  }

  private fun seekToWithText(
    seekValue: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    _isSeekingForwards.value = seekValue > currentPos
    _doubleTapSeekAmount.value = seekValue - currentPos
    _seekText.value = text
    seekTo(seekValue)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  private fun seekByWithText(
    value: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    val maxDuration = duration ?: return

    _doubleTapSeekAmount.update {
      if ((value < 0 && it < 0) || currentPos + value > maxDuration) 0 else it + value
    }
    _seekText.value = text
    _isSeekingForwards.value = value > 0
    seekBy(value)
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  // ==================== Brightness & Volume ====================

  fun changeBrightnessTo(brightness: Float) {
    val coercedBrightness = brightness.coerceIn(0f, 1f)
    host.hostWindow.attributes =
      host.hostWindow.attributes.apply {
        screenBrightness = coercedBrightness
      }
    currentBrightness.value = coercedBrightness

    // Save brightness to preferences if enabled
    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.set(coercedBrightness)
    }
  }

  fun displayBrightnessSlider() {
    isBrightnessSliderShown.value = true
  }

  fun changeVolumeBy(change: Int) {
    val mpvVolume = MPVLib.getPropertyInt("volume")
    val boostCap = volumeBoostCap ?: audioPreferences.volumeBoostCap.get()

    if (boostCap > 0 && currentVolume.value == maxVolume) {
      if (mpvVolume == 100 && change < 0) {
        changeVolumeTo(currentVolume.value + change)
      }
      val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
      if (finalMPVVolume in 100..(boostCap + 100)) {
        return changeMPVVolumeTo(finalMPVVolume)
      }
    }
    changeVolumeTo(currentVolume.value + change)
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    currentVolume.value = newVolume
  }

  fun changeMPVVolumeTo(volume: Int) {
    MPVLib.setPropertyInt("volume", volume)
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.value = true
  }

  // ==================== Video Aspect ====================

  fun changeVideoAspect(
    aspect: VideoAspect,
    showUpdate: Boolean = true,
  ) {
    when (aspect) {
      VideoAspect.Fit -> {
        // To FIT: Reset both properties to their defaults.
        MPVLib.setPropertyDouble("panscan", 0.0)
        MPVLib.setPropertyDouble("video-aspect-override", -1.0)
      }
      VideoAspect.Crop -> {
        // To CROP: Set panscan. MPV will auto-reset video-aspect-override.
        MPVLib.setPropertyDouble("panscan", 1.0)
      }
      VideoAspect.Stretch -> {
        // To STRETCH: Calculate ratio and set it. MPV will auto-reset panscan.
        @Suppress("DEPRECATION")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        host.hostWindowManager.defaultDisplay.getRealMetrics(dm)
        val ratio = dm.widthPixels / dm.heightPixels.toDouble()

        MPVLib.setPropertyDouble("video-aspect-override", ratio)
      }
    }

    // Save the preference
    playerPreferences.videoAspect.set(aspect)

    // Notify the UI
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  fun setCustomAspectRatio(ratio: Double) {
    MPVLib.setPropertyDouble("panscan", 0.0)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    playerPreferences.currentAspectRatio.set(ratio.toFloat())
    playerUpdate.value = PlayerUpdates.AspectRatio
  }

  fun restoreCustomAspectRatio() {
    val savedRatio = playerPreferences.currentAspectRatio.get()
    if (savedRatio > 0) {
      MPVLib.setPropertyDouble("panscan", 0.0)
      MPVLib.setPropertyDouble("video-aspect-override", savedRatio.toDouble())
    }
  }

  // ==================== Screen Rotation ====================

  fun cycleScreenRotations() {
    host.hostRequestedOrientation =
      when (host.hostRequestedOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        -> {
          playerPreferences.orientation.set(PlayerOrientation.SensorPortrait)
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        else -> {
          playerPreferences.orientation.set(PlayerOrientation.SensorLandscape)
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
      }
  }

  // ==================== Lua Invocation Handling ====================

  fun handleLuaInvocation(
    property: String,
    value: String,
  ) {
    val data = value.removeSurrounding("\"").ifEmpty { return }

    when (property.substringAfterLast("/")) {
      "show_text" -> playerUpdate.value = PlayerUpdates.ShowText(data)
      "toggle_ui" -> handleToggleUI(data)
      "show_panel" -> handleShowPanel(data)
      "seek_to_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekToWithText(seekValue.toInt(), text)
      }
      "seek_by_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekByWithText(seekValue.toInt(), text)
      }
      "seek_by" -> seekByWithText(data.toInt(), null)
      "seek_to" -> seekToWithText(data.toInt(), null)
      "software_keyboard" -> handleSoftwareKeyboard(data)
    }

    MPVLib.setPropertyString(property, "")
  }

  private fun handleToggleUI(data: String) {
    when (data) {
      "show" -> showControls()
      "toggle" -> if (controlsShown.value) hideControls() else showControls()
      "hide" -> {
        sheetShown.value = Sheets.None
        panelShown.value = Panels.None
        hideControls()
      }
    }
  }

  private fun handleShowPanel(data: String) {
    when (data) {
      "frame_navigation" -> {
        sheetShown.value = Sheets.FrameNavigation
      }
      else -> {
        panelShown.value =
          when (data) {
            "subtitle_settings" -> Panels.SubtitleSettings
            "subtitle_delay" -> Panels.SubtitleDelay
            "audio_delay" -> Panels.AudioDelay
            "video_filters" -> Panels.VideoFilters
            else -> Panels.None
          }
      }
    }
  }

  private fun handleSoftwareKeyboard(data: String) {
    when (data) {
      "show" -> forceShowSoftwareKeyboard()
      "hide" -> forceHideSoftwareKeyboard()
      "toggle" ->
        if (!inputMethodManager.isActive) {
          forceShowSoftwareKeyboard()
        } else {
          forceHideSoftwareKeyboard()
        }
    }
  }

  @Suppress("DEPRECATION")
  private fun forceShowSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
  }

  @Suppress("DEPRECATION")
  private fun forceHideSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
  }

  // ==================== Gesture Handling ====================

  fun handleLeftDoubleTap() {
    when (gesturePreferences.leftSingleActionGesture.get()) {
      SingleActionGesture.Seek -> leftSeek()
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> MPVLib.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
      SingleActionGesture.None -> {}
    }
  }

  fun handleCenterDoubleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      SingleActionGesture.Seek, SingleActionGesture.None -> {}
    }
  }

  fun handleCenterSingleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      SingleActionGesture.Seek, SingleActionGesture.None -> {}
    }
  }

  fun handleRightDoubleTap() {
    when (gesturePreferences.rightSingleActionGesture.get()) {
      SingleActionGesture.Seek -> rightSeek()
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> MPVLib.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
      SingleActionGesture.None -> {}
    }
  }

  // ==================== Video Zoom ====================

  fun setVideoZoom(zoom: Float) {
    _videoZoom.value = zoom
    MPVLib.setPropertyDouble("video-zoom", zoom.toDouble())
  }

  // ==================== Frame Navigation ====================

  fun updateFrameInfo() {
    _currentFrame.value = MPVLib.getPropertyInt("estimated-frame-number") ?: 0

    val durationValue = MPVLib.getPropertyDouble("duration") ?: 0.0
    val fps =
      MPVLib.getPropertyDouble("container-fps")
        ?: MPVLib.getPropertyDouble("estimated-vf-fps")
        ?: 0.0

    _totalFrames.value =
      if (durationValue > 0 && fps > 0) {
        (durationValue * fps).toInt()
      } else {
        0
      }
  }

  // ==================== Playlist Management ====================

  fun hasPlaylistSupport(): Boolean = (host as? PlayerActivity)?.playlist?.isNotEmpty() ?: false

  fun getPlaylistInfo(): String? {
    val activity = host as? PlayerActivity ?: return null
    if (activity.playlist.isEmpty()) return null
    return "${activity.playlistIndex + 1}/${activity.playlist.size}"
  }

  fun hasNext(): Boolean = (host as? PlayerActivity)?.hasNext() ?: false

  fun hasPrevious(): Boolean = (host as? PlayerActivity)?.hasPrevious() ?: false

  fun playNext() {
    (host as? PlayerActivity)?.playNext()
  }

  fun playPrevious() {
    (host as? PlayerActivity)?.playPrevious()
  }

  fun getCurrentMediaTitle(): String = currentMediaTitle

  // ==================== Utility ====================

  private fun showToast(message: String) {
    Toast.makeText(host.context, message, Toast.LENGTH_SHORT).show()
  }
}

// Extension functions
fun Float.normalize(
  inMin: Float,
  inMax: Float,
  outMin: Float,
  outMax: Float,
): Float = (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

fun <T> Flow<T>.collectAsState(
  scope: CoroutineScope,
  initialValue: T? = null,
) = object : ReadOnlyProperty<Any?, T?> {
  private var value: T? = initialValue

  init {
    scope.launch { collect { value = it } }
  }

  override fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = value
}
