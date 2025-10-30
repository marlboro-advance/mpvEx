@file:Suppress("DEPRECATION")

package app.marlboroadvance.mpvex.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.databinding.PlayerLayoutBinding
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.controls.PlayerControls
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.SubtitleOps
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Main player activity that handles video playback using MPV library.
 *
 * This is a 1500-line clusterfuck that manages everything because Android
 * makes it damn near impossible to separate concerns when you need to deal with:
 * - System UI that keeps trying to show itself
 * - Audio focus that other apps steal constantly
 * - PiP mode that breaks on every manufacturer differently
 * - MPV which was written for desktops and hates mobile
 * - MediaSession for Android Auto/wear that nobody uses but you HAVE to implement
 *
 * Good luck understanding this mess. I certainly don't anymore.
 */
@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity :
  AppCompatActivity(),
  PlayerHost,
  PlayerObserverCallbacks {
  // ViewModels and Bindings
  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> {
    PlayerViewModelProviderFactory(this)
  }
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
  private val playerObserver by lazy { PlayerObserver(this) }

  // Repositories
  private val playbackStateRepository: PlaybackStateRepository by inject()

  // Views and Controllers
  val player by lazy { binding.player }

  // Preferences
  private val playerPreferences: PlayerPreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val fileManager: FileManager by inject()

  // State variables - because Android lifecycle makes everything a goddamn mess
  private var fileName = ""
  private lateinit var pipHelper: MPVPipHelper
  private var systemUIRestored = false
  private var noisyReceiverRegistered = false
  private var audioFocusRequested = false
  private var audioFocusRequest: AudioFocusRequest? = null
  private var resumeOnAudioFocusGain = false
  private var isInitializing = false // Guard flag because rapid back presses crash everything
  private var hasVideoLoaded = false // Another guard flag because fuck race conditions

  // Media session - Google forces you to implement this even though nobody uses it
  private lateinit var mediaSession: MediaSession
  private var mediaSessionInitialized = false
  private lateinit var playbackStateBuilder: PlaybackState.Builder

  // Receivers and Listeners
  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        context: Context?,
        intent: Intent?,
      ) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          pausePlayback()
        }
      }
    }

  private val audioFocusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      // Because apparently EVERY app on Android fights over audio like toddlers
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        -> {
          // Some asshole app took audio focus
          resumeOnAudioFocusGain =
            (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) &&
            (viewModel.paused == false)
          pausePlayback()
          audioFocusRequested = false
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // "Can duck" but we pause anyway because fuck that noise
          resumeOnAudioFocusGain = true
          viewModel.pause()
          audioFocusRequested = false
        }

        AudioManager.AUDIOFOCUS_GAIN -> {
          // We got audio focus back, maybe we can actually play something now
          audioFocusRequested = true
          if (resumeOnAudioFocusGain) {
            resumeOnAudioFocusGain = false
            viewModel.unpause()
          }
        }
      }
    }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    // Mark as initializing because users spam back button and crash the whole thing
    isInitializing = true
    hasVideoLoaded = false

    // Setup literally everything because Android has 47 different subsystems
    setupMPV()
    setupAudio()
    setupBackPressHandler()
    setupPlayerControls()
    setupPipHelper()
    setupAudioFocus()
    setupMediaSession()

    // Finally start the damn video
    getPlayableUri(intent)?.let(player::playFile)
    setOrientation()

    // Deal with notch cutouts because manufacturers love their inconsistent designs
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun handleOnBackPressed() {
          handleBackPress()
        }
      },
    )
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun handleBackPress() {
    // If we're still loading and user is impatient, let them bail
    if (isInitializing && !hasVideoLoaded) {
      finishSafely()
      return
    }

    // Dismiss overlays first because users expect this (for some reason)
    if (viewModel.sheetShown.value != Sheets.None) {
      viewModel.sheetShown.update { Sheets.None }
      viewModel.showControls()
      return
    }
    if (viewModel.panelShown.value != Panels.None) {
      viewModel.panelShown.update { Panels.None }
      viewModel.showControls()
      return
    }

    // Show controls instead of exiting because that's "intuitive" apparently
    if (!viewModel.controlsShown.value) {
      viewModel.showControls()
      return
    }

    // Try to enter PiP if they have it enabled
    val shouldEnterPip =
      pipHelper.isPipSupported &&
        viewModel.paused != true &&
        playerPreferences.automaticallyEnterPip.get()
    if (shouldEnterPip) {
      pipHelper.enterPipMode()
      return
    }

    // Restore system UI IMMEDIATELY or users see black bars for 500ms
    restoreSystemUI()

    finishSafely()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupPlayerControls() {
    binding.controls.setContent {
      MpvexTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = ::finishSafely,
          modifier = Modifier,
        )
      }
    }
  }

  private fun setupPipHelper() {
    // Picture-in-Picture: Works perfectly on Pixel, breaks on Samsung, doesn't exist on Xiaomi
    pipHelper =
      MPVPipHelper(
        activity = this,
        mpvView = player,
        autoPipEnabled = playerPreferences.automaticallyEnterPip.get(),
        onPipModeChanged = { isInPipMode ->
          if (isInPipMode) {
            hideAllUIElements()
          }
        },
      )
  }

  private fun hideAllUIElements() {
    viewModel.hideControls()
    viewModel.hideSeekBar()
    viewModel.isBrightnessSliderShown.update { false }
    viewModel.isVolumeSliderShown.update { false }
    viewModel.sheetShown.update { Sheets.None }
    viewModel.panelShown.update { Panels.None }
  }

  private fun setupAudioFocus() {
    // Android's audio focus system: Because apps fighting over audio is "good UX"
    val audioAttributes =
      AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    audioFocusRequest =
      AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .build()
  }

  private fun requestAudioFocusForPlayback(): Boolean {
    val req = audioFocusRequest ?: return false
    val result = audioManager.requestAudioFocus(req)
    return when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        audioFocusRequested = true
        resumeOnAudioFocusGain = false
        true
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        // "Maybe later" - thanks Android, super helpful
        audioFocusRequested = false
        resumeOnAudioFocusGain = true
        false
      }

      else -> {
        // Get fucked, you can't play audio
        audioFocusRequested = false
        resumeOnAudioFocusGain = false
        false
      }
    }
  }

  private fun abandonAudioFocusIfHeld() {
    if (!audioFocusRequested) return
    runCatching {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      audioFocusRequested = false
    }.onFailure { e -> Log.e(TAG, "Error abandoning audio focus", e) }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onDestroy() {
    Log.d(TAG, "Exiting PlayerActivity")

    runCatching {
      // Restore system UI IMMEDIATELY at the start of cleanup
      if (isFinishing && !systemUIRestored) {
        restoreSystemUI()
      }

      // Mark as not initializing to prevent further operations
      isInitializing = false
      hasVideoLoaded = false

      cleanupMPV()
      cleanupAudio()
      cleanupReceivers()
      releaseMediaSession()
    }.onFailure { e ->
      Log.e(TAG, "Error during onDestroy", e)
    }

    super.onDestroy()
  }

  private fun cleanupMPV() {
    player.isExiting = true

    if (!isFinishing) {
      return
    }

    // MPV cleanup is a delicate dance - do it wrong and you get native crashes
    // But we're NOT gonna wait around with Thread.sleep() like chumps
    Thread {
      try {
        if (hasVideoLoaded) {
          MPVLib.setPropertyString("pause", "yes")
        }
      } catch (_: Throwable) {
        // MPV already died, whatever
      }
      try {
        if (hasVideoLoaded) {
          MPVLib.command("quit")
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Error quitting MPV", e)
      }
      try {
        MPVLib.removeObserver(playerObserver)
      } catch (e: Throwable) {
        Log.e(TAG, "Error removing MPV observer", e)
      }
      try {
        MPVLib.destroy()
      } catch (e: Throwable) {
        // MPV native crashes happen here sometimes, can't do shit about it
        Log.e(TAG, "Error destroying MPV (may be expected)", e)
      }
    }.start()
  }

  private fun cleanupAudio() {
    abandonAudioFocusIfHeld()
  }

  private fun cleanupReceivers() {
    if (noisyReceiverRegistered) {
      runCatching {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }.onFailure { e ->
        Log.e(TAG, "Error unregistering noisy receiver", e)
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPause() {
    runCatching {
      val isInPip = isInPictureInPictureMode

      if (!isInPip) {
        viewModel.pause()
      }

      saveVideoPlaybackState(fileName)

      // Only restore UI if we're actually finishing and not already restored
      if (isFinishing && !isInPip && !systemUIRestored) {
        restoreSystemUI()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onPause", e)
    }

    super.onPause()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun finish() {
    runCatching {
      // Don't restore UI here if it's already restored
      if (!systemUIRestored) {
        restoreSystemUI()
      }

      isInitializing = false
      hasVideoLoaded = false

      setReturnIntent()
    }.onFailure { e ->
      Log.e(TAG, "Error during finish", e)
    }

    super.finish()
  }

  override fun onStop() {
    runCatching {
      pipHelper.onStop()
      saveVideoPlaybackState(fileName)
      viewModel.pause()
      unregisterNoisyReceiver()
    }.onFailure { e ->
      Log.e(TAG, "Error during onStop", e)
    }

    super.onStop()
  }

  private fun unregisterNoisyReceiver() {
    if (noisyReceiverRegistered) {
      runCatching {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }.onFailure { e ->
        Log.e(TAG, "Error unregistering noisy receiver in onStop", e)
      }
    }
  }

  override fun onUserLeaveHint() {
    pipHelper.onUserLeaveHint()
    super.onUserLeaveHint()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onStart() {
    super.onStart()
    runCatching {
      setupWindowFlags()
      setupSystemUI()
      registerNoisyReceiver()
      restoreBrightness()
    }.onFailure { e ->
      Log.e(TAG, "Error during onStart", e)
    }
  }

  private fun setupWindowFlags() {
    if (pipHelper.isPipSupported) {
      pipHelper.updatePictureInPictureParams()
    }

    // More Android boilerplate to hide system UI
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupSystemUI() {
    // Deprecated flags that still work better than the "modern" API
    @Suppress("DEPRECATION")
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE

    // Also use new API because Android fragmentation is fun
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    // And deal with notches AGAIN
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  private fun restoreBrightness() {
    if (playerPreferences.rememberBrightness.get()) {
      val brightness = playerPreferences.defaultBrightness.get()
      if (brightness != BRIGHTNESS_NOT_SET) {
        viewModel.changeBrightnessTo(brightness)
      }
    }
  }

  private fun registerNoisyReceiver() {
    if (!noisyReceiverRegistered) {
      val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
      registerReceiver(noisyReceiver, filter)
      noisyReceiverRegistered = true
    }
  }

  private fun pausePlayback() {
    viewModel.pause()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun copyMPVAssets() {
    // Copy a bunch of files on EVERY startup because MPV needs its config files
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val fontsPath = filesDir.path
        val destDir = ensureFontsDirectory(fontsPath)
        copyDefaultSubfont(fontsPath, destDir)

        Utils.copyAssets(this@PlayerActivity)
        copyMPVScripts()
        copyMPVConfigFiles()
        copyMPVFonts()
      }.onFailure { e ->
        Log.e(TAG, "Error copying MPV assets in background", e)
      }
    }
  }

  private fun setupMPV() {
    copyMPVAssets()
    player.initialize(filesDir.path, cacheDir.path)
    MPVLib.addObserver(playerObserver)
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      MPVLib.setPropertyString(it.property, it.value)
    }
  }

  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    runCatching {
      // Simply ensure default config files exist
      // The actual content is managed by AdvancedPreferencesScreen via cache
      createDefaultConfigFiles(applicationPath)
    }.onFailure { e ->
      Log.e(TAG, "Error ensuring config files exist: ${e.message}")
    }
  }

  private fun createDefaultConfigFiles(applicationPath: String) {
    runCatching {
      val mpvConfFile = File("$applicationPath/mpv.conf")
      if (!mpvConfFile.exists()) {
        mpvConfFile.createNewFile()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error creating default config files", e)
    }
  }

  private fun copyMPVScripts() {
    runCatching {
      val mpvexLua = assets.open("mpvex.lua")
      val applicationPath = filesDir.path
      val scriptsDir =
        fileManager.createDir(
          fileManager.fromPath(applicationPath),
          "scripts",
        ) ?: error("Failed to create scripts directory")

      fileManager.deleteContent(scriptsDir)

      File("$scriptsDir/mpvex.lua")
        .apply {
          if (!exists()) createNewFile()
        }.writeText(mpvexLua.bufferedReader().readText())
    }.onFailure { e ->
      Log.e(TAG, "Error copying MPV scripts", e)
    }
  }

  private fun copyMPVFonts() {
    // Font copying: Because subtitle rendering is never simple
    runCatching {
      val persistentPath = filesDir.path
      val fontsFolderUri = subtitlesPreferences.fontsFolder.get().toUri()
      val fontsDir =
        fileManager.fromUri(fontsFolderUri)
          ?: return@runCatching

      if (!fileManager.exists(fontsDir)) {
        return@runCatching
      }

      val destDir = ensureFontsDirectory(persistentPath)
      copyDefaultSubfont(persistentPath, destDir)

      fileManager.copyDirectoryWithContent(fontsDir, destDir, false)
    }.onFailure { e ->
      Log.e(TAG, "Couldn't copy fonts to application directory: ${e.message}")
    }
  }

  private fun ensureFontsDirectory(basePath: String): com.github.k1rakishou.fsaf.file.AbstractFile {
    val destDir = fileManager.fromPath("$basePath/fonts")
    if (!fileManager.exists(destDir)) {
      fileManager.createDir(fileManager.fromPath(basePath), "fonts")
    }
    return destDir
  }

  private fun copyDefaultSubfont(
    basePath: String,
    destDir: com.github.k1rakishou.fsaf.file.AbstractFile,
  ) {
    if (fileManager.findFile(destDir, "subfont.ttf") == null) {
      resources.assets
        .open("subfont.ttf")
        .use { input ->
          File("$basePath/fonts/subfont.ttf")
            .outputStream()
            .use { output ->
              input.copyTo(output)
            }
        }
    }
  }

  override fun onResume() {
    super.onResume()
    updateVolume()
  }

  private fun updateVolume() {
    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also { volume ->
        if (volume < viewModel.maxVolume) {
          viewModel.changeMPVVolumeTo(MAX_MPV_VOLUME)
        }
      }
    }
  }

  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    // Handle intent extras because external apps send us random shit
    extras.getInt("position", POSITION_NOT_SET).takeIf { it != POSITION_NOT_SET }?.let {
      MPVLib.setPropertyInt("time-pos", it / MILLISECONDS_TO_SECONDS)
    }

    addSubtitlesFromExtras(extras)
    setHttpHeadersFromExtras(extras)
  }

  private fun addSubtitlesFromExtras(extras: Bundle) {
    if (!extras.containsKey("subs")) return

    val subList = Utils.getParcelableArray<Uri>(extras, "subs")
    val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

    for (suburi in subList) {
      val subfile = suburi.resolveUri(this) ?: continue
      val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

      Log.v(TAG, "Adding subtitles from intent extras: $subfile")
      MPVLib.command("sub-add", subfile, flag)
    }
  }

  private fun setHttpHeadersFromExtras(extras: Bundle) {
    // Because sometimes external apps want custom HTTP headers
    extras.getStringArray("headers")?.let { headers ->
      if (headers.isEmpty()) return

      if (headers[0].startsWith("User-Agent", ignoreCase = true)) {
        MPVLib.setPropertyString("user-agent", headers[1])
      }

      if (headers.size > 2) {
        val headersString =
          headers
            .asSequence()
            .drop(2)
            .chunked(2)
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }
            .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
            .joinToString(",")

        if (headersString.isNotEmpty()) {
          MPVLib.setPropertyString("http-header-fields", headersString)
        }
      }
    }
  }

  private fun parsePathFromIntent(intent: Intent): String? =
    when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
      Intent.ACTION_SEND -> parsePathFromSendIntent(intent)
      else -> intent.getStringExtra("uri")
    }

  private fun parsePathFromSendIntent(intent: Intent): String? =
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.resolveUri(this@PlayerActivity)
    } else {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
        val uri = text.trim().toUri()
        if (uri.isHierarchical && !uri.isRelative) {
          uri.resolveUri(this)
        } else {
          null
        }
      }
    }

  private fun getFileName(intent: Intent): String {
    val uri = extractUriFromIntent(intent) ?: return ""

    getDisplayNameFromUri(uri)?.let { return it }

    // Fallback: just get whatever we can from the URI
    return uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: ""
  }

  @Suppress("DEPRECATION")
  private fun extractUriFromIntent(intent: Intent): Uri? =
    if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
    } else {
      intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

  private fun getDisplayNameFromUri(uri: Uri): String? =
    runCatching {
      contentResolver
        .query(
          uri,
          arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
          null,
          null,
          null,
        )?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.onFailure { e ->
      Log.e(TAG, "Error getting display name from URI", e)
    }.getOrNull()

  private fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent) ?: return null
    return if (uri.startsWith("content://")) {
      uri.toUri().openContentFd(this)
    } else {
      uri
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Guard against rotation during initialization because shit breaks
    if (!isInitializing && hasVideoLoaded) {
      handleConfigurationChange()
    }
  }

  private fun handleConfigurationChange() {
    if (!isInPictureInPictureMode) {
      viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
    } else {
      viewModel.hideControls()
    }
  }

  // ==================== MPV Event Observers ====================

  override fun onObserverEvent() {
    if (player.isExiting) return
  }

  override fun onObserverEvent(
    property: String,
    value: Boolean,
  ) {
    if (player.isExiting) return

    when (property) {
      "pause" -> handlePauseStateChange(value)
      "eof-reached" -> handleEndOfFile(value)
    }
  }

  private fun handlePauseStateChange(isPaused: Boolean) {
    // Manage screen-on flag and audio focus because Android won't do it for us
    if (isPaused) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      abandonAudioFocusIfHeld()
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      val hasFocus = requestAudioFocusForPlayback()
      if (!hasFocus) {
        // Can't play without audio focus, fuck you very much
        viewModel.pause()
      }
    }
    updateMediaSessionPlaybackState(!isPaused)
    // Update PiP UI because it needs constant hand-holding
    runCatching {
      if (isInPictureInPictureMode && pipHelper.isPipSupported) {
        pipHelper.updatePictureInPictureParams()
      }
    }.onFailure { /* no-op */ }
  }

  private fun handleEndOfFile(isEof: Boolean) {
    if (isEof && playerPreferences.closeAfterReachingEndOfVideo.get()) {
      finishAndRemoveTask()
    }
  }

  override fun onObserverEvent(
    property: String,
    value: String,
  ) {
    if (player.isExiting) return

    when (property.substringBeforeLast("/")) {
      "user-data/mpvex" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  override fun onObserverEvent(property: String) {
    if (player.isExiting) return

    when (property) {
      "video-params/aspect" -> {
        if (pipHelper.isPipSupported) {
          pipHelper.updatePictureInPictureParams()
        }
      }
    }
  }

  override fun event(eventId: Int) {
    if (player.isExiting) return

    when (eventId) {
      MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
        handleFileLoaded()
        // Mark initialization complete when file is loaded
        isInitializing = false
        hasVideoLoaded = true
      }

      MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
        player.isExiting = false
        // Also mark as not initializing on playback restart
        if (!hasVideoLoaded) {
          isInitializing = false
          hasVideoLoaded = true
        }
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun handleFileLoaded() {
    fileName = getFileName(intent)
    setIntentExtras(intent.extras)

    lifecycleScope.launch(Dispatchers.IO) {
      loadVideoPlaybackState(fileName)
    }

    // GlobalScope because we NEED this to complete even if activity dies
    GlobalScope.launch(Dispatchers.IO) {
      saveRecentlyPlayed()
    }

    setOrientation()
    viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
    viewModel.restoreCustomAspectRatio()

    // Initialize zoom from preferences (ignore mpv.conf because it's unreliable)
    val zoomPreference = playerPreferences.defaultVideoZoom.get()
    MPVLib.setPropertyDouble("video-zoom", zoomPreference.toDouble())
    viewModel.setVideoZoom(zoomPreference)

    // Set title BEFORE unpause to avoid subtitle restoration race conditions
    MPVLib.setPropertyString("force-media-title", fileName)
    viewModel.setMediaTitle(fileName)

    viewModel.unpause()

    // Autoload subtitles if user enabled it (and pray they're named correctly)
    if (subtitlesPreferences.autoloadMatchingSubtitles.get()) {
      lifecycleScope.launch {
        val videoFilePath = parsePathFromIntent(intent)
        if (videoFilePath != null) {
          SubtitleOps.autoloadSubtitles(
            videoFilePath = videoFilePath,
            videoFileName = fileName,
          )
        }
      }
    }

    // Update MediaSession for Android Auto users (all 3 of them)
    updateMediaSessionMetadata(
      title = fileName,
      durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L,
    )
    updateMediaSessionPlaybackState(isPlaying = true)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun saveVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return

    // GlobalScope because fuck activity lifecycle, this NEEDS to save
    GlobalScope.launch(Dispatchers.IO) {
      runCatching {
        val oldState = playbackStateRepository.getVideoDataByTitle(fileName)
        Log.d(TAG, "Saving playback state for: $mediaTitle")

        playbackStateRepository.upsert(
          PlaybackStateEntity(
            mediaTitle = mediaTitle,
            lastPosition = calculateSavePosition(oldState),
            playbackSpeed = MPVLib.getPropertyDouble("speed") ?: DEFAULT_PLAYBACK_SPEED,
            sid = player.sid,
            subDelay = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS).toInt(),
            subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: DEFAULT_SUB_SPEED,
            secondarySid = player.secondarySid,
            secondarySubDelay =
              (
                (MPVLib.getPropertyDouble("secondary-sub-delay") ?: 0.0) *
                  MILLISECONDS_TO_SECONDS
              ).toInt(),
            aid = player.aid,
            audioDelay =
              (
                (MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS
              ).toInt(),
          ),
        )
      }.onFailure { e ->
        Log.e(TAG, "Error saving playback state", e)
      }
    }
  }

  private fun calculateSavePosition(oldState: PlaybackStateEntity?): Int {
    if (!playerPreferences.savePositionOnQuit.get()) {
      return oldState?.lastPosition ?: 0
    }

    val pos = viewModel.pos ?: 0
    val duration = viewModel.duration ?: 0
    return if (pos < duration - 1) pos else 0
  }

  private suspend fun loadVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return

    runCatching {
      val state = playbackStateRepository.getVideoDataByTitle(mediaTitle)

      applyPlaybackState(state)
      applyDefaultSettings(state)
    }.onFailure { e ->
      Log.e(TAG, "Error loading playback state", e)
    }
  }

  private fun applyPlaybackState(state: PlaybackStateEntity?) {
    if (state == null) return

    val subDelay = state.subDelay / DELAY_DIVISOR
    val secondarySubDelay = state.secondarySubDelay / DELAY_DIVISOR
    val audioDelay = state.audioDelay / DELAY_DIVISOR

    player.sid = state.sid
    player.secondarySid = state.secondarySid
    player.aid = state.aid

    MPVLib.setPropertyDouble("sub-delay", subDelay)
    MPVLib.setPropertyDouble("secondary-sub-delay", secondarySubDelay)
    MPVLib.setPropertyDouble("speed", state.playbackSpeed)
    MPVLib.setPropertyDouble("audio-delay", audioDelay)
    MPVLib.setPropertyDouble("sub-speed", state.subSpeed)

    if (playerPreferences.savePositionOnQuit.get() && state.lastPosition != 0) {
      MPVLib.setPropertyInt("time-pos", state.lastPosition)
    }
  }

  private fun applyDefaultSettings(state: PlaybackStateEntity?) {
    // Set default sub speed if no state exists
    if (state == null) {
      val defaultSubSpeed = subtitlesPreferences.defaultSubSpeed.get().toDouble()
      MPVLib.setPropertyDouble("sub-speed", defaultSubSpeed)
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private suspend fun saveRecentlyPlayed() {
    runCatching {
      val uri = extractUriFromIntent(intent)

      if (uri == null) {
        Log.w(TAG, "Cannot save recently played: URI is null")
        return@runCatching
      }

      if (uri.scheme == null) {
        Log.w(TAG, "Cannot save recently played: URI has null scheme: $uri")
        return@runCatching
      }

      // Figure out what the hell to save based on URI scheme
      val filePath =
        when (uri.scheme) {
          "file" -> {
            uri.path ?: uri.toString()
          }

          "content" -> {
            // Try to resolve to actual path or just save the URI
            contentResolver
              .query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
              )?.use { cursor ->
                if (cursor.moveToFirst()) {
                  val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                  if (columnIndex != -1) cursor.getString(columnIndex) else null
                } else {
                  null
                }
              } ?: uri.toString()
          }

          else -> {
            uri.toString()
          }
        }

      val launchSource =
        when {
          intent.getStringExtra("launch_source") != null -> intent.getStringExtra("launch_source")
          intent.action == Intent.ACTION_SEND -> "share"
          else -> "normal"
        }

      RecentlyPlayedOps.addRecentlyPlayed(
        filePath = filePath,
        fileName = fileName,
        launchSource = launchSource,
      )

      Log.d(TAG, "Saved recently played: $filePath (source: $launchSource)")
    }.onFailure { e ->
      Log.e(TAG, "Error saving recently played", e)
    }
  }

  // ==================== Intent and Result Management ====================

  private fun setReturnIntent() {
    Log.d(TAG, "Setting return intent")

    val resultIntent =
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
        viewModel.duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
      }

    setResult(RESULT_OK, resultIntent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    getPlayableUri(intent)?.let { uri ->
      MPVLib.command("loadfile", uri)
    }
    setIntent(intent)
  }

  // ==================== Picture-in-Picture Management ====================

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    pipHelper.onPictureInPictureModeChanged(isInPictureInPictureMode)

    binding.controls.alpha = if (isInPictureInPictureMode) 0f else 1f

    runCatching {
      if (isInPictureInPictureMode) {
        enterPipUIMode()
      } else {
        exitPipUIMode()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error handling PiP mode change", e)
    }
  }

  private fun enterPipUIMode() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    WindowCompat.setDecorFitsSystemWindows(window, true)
    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun exitPipUIMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  fun enterPipModeHidingOverlay() {
    runCatching {
      enterPipUIMode()
    }.onFailure { e ->
      Log.e(TAG, "Error entering PiP mode with hidden overlay", e)
    }

    binding.controls.alpha = 0f

    pipHelper.enterPipMode()
  }

  val isPipSupported: Boolean
    get() = pipHelper.isPipSupported

  // ==================== Orientation Management ====================

  private fun setOrientation() {
    requestedOrientation =
      when (playerPreferences.orientation.get()) {
        PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientation.Video -> determineVideoOrientation()
        PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      }
  }

  private fun determineVideoOrientation(): Int {
    val aspect = player.getVideoOutAspect() ?: 0.0
    return if (aspect > 1.0) {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
  }

  // ==================== Key Event Handling ====================

  @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
  override fun onKeyDown(
    keyCode: Int,
    event: KeyEvent?,
  ): Boolean {
    val isTrackSheetOpen =
      viewModel.sheetShown.value == Sheets.SubtitleTracks ||
        viewModel.sheetShown.value == Sheets.AudioTracks
    val isNoSheetOpen = viewModel.sheetShown.value == Sheets.None

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_DPAD_LEFT,
      -> {
        if (isTrackSheetOpen) {
          return super.onKeyDown(keyCode, event)
        }

        if (isNoSheetOpen) {
          when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
              viewModel.handleRightDoubleTap()
              return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
              viewModel.handleLeftDoubleTap()
              return true
            }
          }
        }
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
        if (isTrackSheetOpen) {
          return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
      }

      KeyEvent.KEYCODE_SPACE -> {
        viewModel.pauseUnpause()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_STOP -> {
        finishAndRemoveTask()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_REWIND -> {
        viewModel.handleLeftDoubleTap()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
        viewModel.handleRightDoubleTap()
        return true
      }

      else -> {
        event?.let { player.onKey(it) }
        return super.onKeyDown(keyCode, event)
      }
    }
  }

  override fun onKeyUp(
    keyCode: Int,
    event: KeyEvent?,
  ): Boolean {
    event?.let {
      if (player.onKey(it)) return true
    }
    return super.onKeyUp(keyCode, event)
  }

  // ==================== System UI Management ====================

  @RequiresApi(Build.VERSION_CODES.P)
  private fun restoreSystemUI() {
    // Check multiple guards because Android lifecycle is a shitshow
    if (systemUIRestored || isFinishing || isDestroyed) {
      systemUIRestored = true
      return
    }

    runCatching {
      // RESTORE IMMEDIATELY OR USERS WILL BITCH ABOUT BLACK BARS
      window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

      windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
      windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
      windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

      WindowCompat.setDecorFitsSystemWindows(window, true)

      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT

      systemUIRestored = true
    }.onFailure { e ->
      Log.e(TAG, "Error restoring system UI", e)
      systemUIRestored = true // Mark as restored even on failure to prevent loops
    }
  }

  private fun setupMediaSession() {
    // MediaSession for Android Auto/Wear - Google mandates it, users ignore it
    runCatching {
      mediaSession =
        MediaSession(this, TAG).apply {
          setCallback(
            object : MediaSession.Callback() {
              override fun onPlay() {
                viewModel.unpause()
                updateMediaSessionPlaybackState(isPlaying = true)
              }

              override fun onPause() {
                viewModel.pause()
                updateMediaSessionPlaybackState(isPlaying = false)
              }

              override fun onSeekTo(pos: Long) {
                viewModel.seekTo((pos / 1000).toInt())
                updateMediaSessionPlaybackState(isPlaying = viewModel.paused == false)
              }
            },
          )
          isActive = true
        }
      playbackStateBuilder =
        PlaybackState
          .Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_PLAY_PAUSE or
              PlaybackState.ACTION_SEEK_TO,
          )
      mediaSessionInitialized = true
    }.onFailure { e ->
      Log.e(TAG, "Failed to initialize MediaSession", e)
      mediaSessionInitialized = false
    }
  }

  private fun updateMediaSessionPlaybackState(isPlaying: Boolean) {
    if (!mediaSessionInitialized) return
    runCatching {
      val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
      val positionMs = (viewModel.pos ?: 0) * 1000L
      mediaSession.setPlaybackState(
        playbackStateBuilder
          .setState(state, positionMs, if (isPlaying) 1.0f else 0f)
          .build(),
      )
    }.onFailure { e -> Log.e(TAG, "Error updating playback state", e) }
  }

  private fun updateMediaSessionMetadata(
    title: String,
    durationMs: Long,
  ) {
    if (!mediaSessionInitialized) return
    runCatching {
      val metadata =
        MediaMetadata
          .Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, title)
          .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
          .build()
      mediaSession.setMetadata(metadata)
    }.onFailure { e -> Log.e(TAG, "Error updating metadata", e) }
  }

  private fun releaseMediaSession() {
    if (!mediaSessionInitialized) return
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }.onFailure { e -> Log.e(TAG, "Error releasing MediaSession", e) }
    mediaSessionInitialized = false
  }

  // ==================== PlayerHost ====================
  override val context: Context
    get() = this
  override val windowInsetsController: WindowInsetsControllerCompat
    get() = WindowCompat.getInsetsController(window, window.decorView)
  override val hostWindow: android.view.Window
    get() = window
  override val hostWindowManager: WindowManager
    get() = windowManager
  override val hostContentResolver: android.content.ContentResolver
    get() = contentResolver
  override val audioManager: AudioManager
    get() = getSystemService(AUDIO_SERVICE) as AudioManager
  override var hostRequestedOrientation: Int
    get() = requestedOrientation
    set(value) {
      requestedOrientation = value
    }

  companion object {
    private const val RESULT_INTENT = "app.marlboroadvance.mpvex.ui.player.PlayerActivity.result"

    // Constants for actual useful values, not for stupid sleep delays
    private const val BRIGHTNESS_NOT_SET = -1f
    private const val POSITION_NOT_SET = 0
    private const val MAX_MPV_VOLUME = 100
    private const val MILLISECONDS_TO_SECONDS = 1000
    private const val DELAY_DIVISOR = 1000.0
    private const val DEFAULT_PLAYBACK_SPEED = 1.0
    private const val DEFAULT_SUB_SPEED = 1.0
    const val TAG = "mpvex"
  }

  /**
   * Safe finish method that handles rapid back presses and initialization states.
   *
   * Called "safe" but it's really just a bunch of try-catch blocks hoping nothing explodes.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun finishSafely() {
    runCatching {
      if (isFinishing) return

      // RESTORE UI FIRST OR DIE TRYING
      if (!systemUIRestored) {
        restoreSystemUI()
      }

      isInitializing = false
      hasVideoLoaded = false

      setReturnIntent()
      finish()
    }.onFailure { e ->
      Log.e(TAG, "Error during finishSafely", e)
      // Hail Mary: just finish and hope for the best
      try {
        if (!systemUIRestored) {
          restoreSystemUI()
        }
        finish()
      } catch (e2: Exception) {
        Log.e(TAG, "Critical error: could not finish activity", e2)
      }
    }
  }
}
