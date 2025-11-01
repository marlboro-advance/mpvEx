package app.marlboroadvance.mpvex.ui.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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
import android.os.IBinder
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
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.controls.PlayerControls
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.SubtitleOps
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Main player activity that handles video playback using the MPV library.
 *
 * This activity manages:
 * - Video playback using MPV library
 * - System UI visibility (immersive mode)
 * - Audio focus management
 * - Picture-in-Picture (PiP) mode
 * - Background playback service
 * - MediaSession for external controls (Android Auto, Bluetooth, etc.)
 * - Playback state persistence and restoration
 * - Subtitle and audio track management
 * - Hardware key event handling
 *
 * @see PlayerViewModel for UI state management
 * @see MediaPlaybackService for background playback functionality
 */
@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity :
  AppCompatActivity(),
  PlayerHost {
  // ==================== ViewModels and Bindings ====================

  /**
   * View model for managing player UI state.
   */
  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> {
    PlayerViewModelProviderFactory(this)
  }

  /**
   * Binding for the player layout.
   */
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }

  /**
   * Observer for MPV events.
   */
  private val playerObserver by lazy { PlayerObserver(this) }

  // ==================== Dependency Injection ====================

  /**
   * Repository for managing playback state.
   */
  private val playbackStateRepository: PlaybackStateRepository by inject()

  /**
   * Preferences for player settings.
   */
  private val playerPreferences: PlayerPreferences by inject()

  /**
   * Preferences for audio settings.
   */
  private val audioPreferences: AudioPreferences by inject()

  /**
   * Preferences for subtitle settings.
   */
  private val subtitlesPreferences: SubtitlesPreferences by inject()

  /**
   * Preferences for advanced settings.
   */
  private val advancedPreferences: AdvancedPreferences by inject()

  /**
   * Manager for file operations.
   */
  private val fileManager: FileManager by inject()

  // ==================== Views ====================

  /**
   * The MPV player view.
   */
  val player by lazy { binding.player }

  // ==================== State Management ====================

  /**
   * Current video file name being played.
   */
  private var fileName = ""

  /**
   * Playlist of URIs for sequential playback
   */
  internal var playlist: List<Uri> = emptyList()

  /**
   * Current index in the playlist
   */
  internal var playlistIndex: Int = 0

  /**
   * Helper for managing Picture-in-Picture mode.
   */
  private lateinit var pipHelper: MPVPipHelper

  /**
   * Tracks whether system UI has been restored to prevent multiple restoration attempts.
   */
  private var systemUIRestored = false

  /**
   * Tracks whether the noisy audio receiver is currently registered.
   */
  private var noisyReceiverRegistered = false

  /**
   * Guard flag to prevent operations during initialization that could cause crashes.
   * Set to true during onCreate, set to false when video loads.
   */
  private var isInitializing = false

  /**
   * Tracks whether video has been loaded successfully.
   * Used to prevent operations before playback is ready.
   */
  private var hasVideoLoaded = false

  // ==================== Background Playback ====================

  /**
   * Reference to the background playback service.
   */
  private var mediaPlaybackService: MediaPlaybackService? = null

  /**
   * Tracks whether we're currently bound to the background playback service.
   */
  private var serviceBound = false

  /**
   * Tracks if user explicitly wants to exit (e.g., via back button).
   * When true, background playback service will be stopped.
   */
  private var isUserFinishing = false

  // ==================== MediaSession ====================

  /**
   * MediaSession for integration with system media controls, Android Auto, and Wear OS.
   */
  private lateinit var mediaSession: MediaSession

  /**
   * Tracks whether MediaSession has been successfully initialized.
   */
  private var mediaSessionInitialized = false

  /**
   * Builder for MediaSession playback states.
   */
  private lateinit var playbackStateBuilder: PlaybackState.Builder

  // ==================== Audio Focus ====================

  /**
   * Audio focus request for API 26+.
   */
  private var audioFocusRequest: AudioFocusRequest? = null

  /**
   * Callback to restore audio focus after it's been lost and regained.
   */
  private var restoreAudioFocus: () -> Unit = {}

  // ==================== Broadcast Receivers ====================

  /**
   * Receiver for handling noisy audio events.
   */
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

  /**
   * Listener for audio focus changes.
   */
  private val audioFocusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        -> {
          // Save current state to restore later
          val oldRestore = restoreAudioFocus
          val wasPlayerPaused = viewModel.paused ?: false
          viewModel.pause()
          restoreAudioFocus = {
            oldRestore()
            if (!wasPlayerPaused) viewModel.unpause()
          }
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // Lower volume temporarily
          MPVLib.command("multiply", "volume", "0.5")
          restoreAudioFocus = {
            MPVLib.command("multiply", "volume", "2")
          }
        }

        AudioManager.AUDIOFOCUS_GAIN -> {
          // Restore previous audio state
          restoreAudioFocus()
          restoreAudioFocus = {}
        }

        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
          Log.d(TAG, "Audio focus request failed")
        }
      }
    }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    // Prevent operations during initialization
    isInitializing = true
    hasVideoLoaded = false

    // Initialize components
    setupMPV()
    MediaPlaybackService.createNotificationChannel(this)
    setupAudio()
    setupBackPressHandler()
    setupPlayerControls()
    setupPipHelper()
    setupMediaSession()

    // Extract playlist info from intent
    playlist = intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    playlistIndex = intent.getIntExtra("playlist_index", 0)

    // Start playback
    getPlayableUri(intent)?.let(player::playFile)
    setOrientation()

    // Configure display cutout handling for devices with notches
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  /**
   * Enforces a default font scale to maintain consistent UI across different system settings.
   *
   * @param newBase The new base context
   */
  override fun attachBaseContext(newBase: Context?) {
    if (newBase == null) {
      super.attachBaseContext(null)
      return
    }

    val contextWithDefaultFontScale = enforceDefaultFontScale(newBase)
    super.attachBaseContext(contextWithDefaultFontScale)
  }

  /**
   * Creates a context with default font scale to prevent UI layout issues.
   *
   * @param baseContext The original context
   * @return Context with enforced font scale of 1.0
   */
  private fun enforceDefaultFontScale(baseContext: Context): Context {
    val originalConfiguration = baseContext.resources.configuration
    if (originalConfiguration.fontScale == 1f) {
      return baseContext
    }

    val updatedConfiguration =
      Configuration(originalConfiguration).apply {
        fontScale = 1f
      }

    val configurationContext = baseContext.createConfigurationContext(updatedConfiguration)
    val configurationResources = configurationContext.resources
    val configurationDisplayMetrics = configurationResources.displayMetrics
    val density = configurationDisplayMetrics.density
    configurationDisplayMetrics.scaledDensity = updatedConfiguration.fontScale * density

    return configurationContext
  }

  /**
   * Sets up the back press handler to manage navigation and cleanup.
   */
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
    // Allow exit during initialization if video hasn't loaded yet
    if (isInitializing && !hasVideoLoaded) {
      finishSafely()
      return
    }

    // Dismiss overlays first (sheets)
    if (viewModel.sheetShown.value != Sheets.None) {
      viewModel.sheetShown.update { Sheets.None }
      viewModel.showControls()
      return
    }

    // Dismiss panels
    if (viewModel.panelShown.value != Panels.None) {
      viewModel.panelShown.update { Panels.None }
      viewModel.showControls()
      return
    }

    // Show controls if hidden
    if (!viewModel.controlsShown.value) {
      viewModel.showControls()
      return
    }

    // Restore system UI before finishing
    restoreSystemUI()

    isUserFinishing = true
    finishSafely()
  }

  /**
   * Initializes the Compose-based player controls UI.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupPlayerControls() {
    binding.controls.setContent {
      MpvexTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = {
            isUserFinishing = true
            finishSafely()
          },
          modifier = Modifier,
        )
      }
    }
  }

  /**
   * Initializes the Picture-in-Picture helper.
   */
  private fun setupPipHelper() {
    pipHelper = MPVPipHelper(activity = this, mpvView = player)
  }

  /**
   * Configures audio settings and requests audio focus.
   * Audio focus is only requested if not bound to background service.
   */
  private fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      MPVLib.setPropertyString(it.property, it.value)
    }
    // Only request focus if background service is not bound
    if (!serviceBound) {
      audioFocusRequest =
        AudioFocusRequest
          .Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes
              .Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
              .build(),
          ).setOnAudioFocusChangeListener(audioFocusChangeListener)
          .setAcceptsDelayedFocusGain(true)
          .setWillPauseWhenDucked(true)
          .build()
      requestAudioFocusForPlayback()
    }
  }

  /**
   * Requests audio focus for media playback.
   *
   * @return true if audio focus was granted immediately, false otherwise
   */
  private fun requestAudioFocusForPlayback(): Boolean {
    val req = audioFocusRequest ?: return false
    val result = audioManager.requestAudioFocus(req)
    return when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        restoreAudioFocus = {}
        true
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        restoreAudioFocus = { requestAudioFocusForPlayback() }
        false
      }

      else -> {
        restoreAudioFocus = {}
        false
      }
    }
  }

  /**
   * Abandons audio focus if currently held.
   */
  private fun abandonAudioFocusIfHeld() {
    if (restoreAudioFocus != {}) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      restoreAudioFocus = {}
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onDestroy() {
    Log.d(TAG, "PlayerActivity onDestroy - isUserFinishing: $isUserFinishing")

    runCatching {
      // Stop background service if user explicitly finished
      if (isUserFinishing || isFinishing) {
        if (serviceBound) {
          try {
            unbindService(serviceConnection)
          } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service in onDestroy", e)
          }
          serviceBound = false
        }
        stopService(Intent(this, MediaPlaybackService::class.java))
        mediaPlaybackService = null
      }

      // Restore system UI at the start of cleanup
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

  /**
   * Cleans up MPV resources.
   * Must be done on a background thread to avoid blocking the main thread.
   */
  private fun cleanupMPV() {
    player.isExiting = true

    if (!isFinishing) {
      return
    }

    // Cleanup MPV on background thread to prevent blocking
    Thread {
      try {
        if (hasVideoLoaded) {
          MPVLib.setPropertyString("pause", "yes")
        }
      } catch (_: Throwable) {
        // MPV may already be destroyed
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
        Log.e(TAG, "Error destroying MPV", e)
      }
    }.start()
  }

  /**
   * Cleans up audio focus resources.
   */
  private fun cleanupAudio() {
    abandonAudioFocusIfHeld()
  }

  /**
   * Unregisters broadcast receivers.
   */
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

  /**
   * Called when activity is paused.
   * Pauses playback (unless in PiP or background playback enabled) and saves state.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPause() {
    runCatching {
      val isInPip = isInPictureInPictureMode

      // Only pause if not in background playback mode or user is finishing
      val shouldPause = !playerPreferences.automaticBackgroundPlayback.get() || isUserFinishing
      if (!isInPip && shouldPause) {
        viewModel.pause()
      }

      saveVideoPlaybackState(fileName)

      // Restore UI if finishing and not in PiP
      if (isFinishing && !isInPip && !systemUIRestored) {
        restoreSystemUI()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onPause", e)
    }

    super.onPause()
  }

  /**
   * Called when activity is finishing.
   * Restores system UI and sets return intent with playback position.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  override fun finish() {
    runCatching {
      // Restore UI if not already restored
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

  /**
   * Called when activity is stopped.
   * Manages background playback service and saves state.
   */
  override fun onStop() {
    runCatching {
      pipHelper.onStop()
      saveVideoPlaybackState(fileName)
      unregisterNoisyReceiver()

      // Start background playback if enabled, not already bound, and not user finishing
      if (!serviceBound && playerPreferences.automaticBackgroundPlayback.get() && !isUserFinishing) {
        startBackgroundPlayback()
      } else {
        // Only pause if NOT background playback and not resuming
        if (!playerPreferences.automaticBackgroundPlayback.get() || isUserFinishing) {
          viewModel.pause()
        }
        if (serviceBound) {
          try {
            unbindService(serviceConnection)
            serviceBound = false
          } catch (e: IllegalArgumentException) {
            // Service was not registered
            Log.w(TAG, "Service was not bound when trying to unbind", e)
            serviceBound = false
          }
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onStop", e)
    }

    super.onStop()
  }

  /**
   * Unregisters the noisy audio receiver if it's currently registered.
   */
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

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onStart() {
    super.onStart()
    runCatching {
      setupWindowFlags()
      setupSystemUI()
      registerNoisyReceiver()
      restoreBrightness()
      // End background playback if service is bound
      if (serviceBound) {
        endBackgroundPlayback()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onStart", e)
    }
  }

  /**
   * Configures window flags for immersive playback.
   */
  private fun setupWindowFlags() {
    pipHelper.updatePictureInPictureParams()

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

  /**
   * Copies MPV assets to the app's storage.
   *
   * This method is responsible for copying the necessary MPV assets,
   * including fonts, scripts, and configuration files, to the app's
   * storage directory. This is done asynchronously on app startup.
   */
  private fun copyMPVAssets() {
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

  /**
   * Initializes the MPV player with the necessary paths and observers.
   *
   * This method sets up the MPV player by providing it with the
   * necessary paths and observers. It also copies the MPV assets
   * to the app's storage directory.
   */
  private fun setupMPV() {
    copyMPVAssets()
    player.initialize(filesDir.path, cacheDir.path)
    MPVLib.addObserver(playerObserver)
  }

  /**
   * Copies or creates the MPV configuration files.
   *
   * This method copies or creates the necessary MPV configuration
   * files, including mpv.conf and input.conf, to the app's storage
   * directory.
   */
  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    runCatching {
      createDefaultConfigFiles(applicationPath)
    }.onFailure { e ->
      Log.e(TAG, "Error ensuring config files exist: ${e.message}")
    }
  }

  /**
   * Creates default MPV configuration files with user preferences.
   *
   * @param applicationPath Path to the application's file directory
   */
  private fun createDefaultConfigFiles(applicationPath: String) {
    runCatching {
      val mpvConfFile = File("$applicationPath/mpv.conf")
      if (!mpvConfFile.exists()) {
        mpvConfFile.createNewFile()
      }
      val mpvConfContent = advancedPreferences.mpvConf.get()
      if (mpvConfContent.isNotBlank()) {
        mpvConfFile.writeText(mpvConfContent)
      }

      val inputConfFile = File("$applicationPath/input.conf")
      if (!inputConfFile.exists()) {
        inputConfFile.createNewFile()
      }
      val inputConfContent = advancedPreferences.inputConf.get()
      if (inputConfContent.isNotBlank()) {
        inputConfFile.writeText(inputConfContent)
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

  /**
   * Updates the volume level to match the system volume.
   *
   * This method updates the current volume level by getting the current system volume
   * and adjusting the MPV volume accordingly. It ensures that the MPV volume is set
   * to the maximum allowed value if the system volume is lower than the maximum.
   */
  private fun updateVolume() {
    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also { volume ->
        if (volume < viewModel.maxVolume) {
          viewModel.changeMPVVolumeTo(MAX_MPV_VOLUME)
        }
      }
    }
  }

  /**
   * Processes intent extras to set initial playback position, subtitles, and HTTP headers.
   *
   * This method checks the intent extras for the following keys:
   * - "position": The initial playback position in seconds.
   * - "subs": A list of subtitle URIs to add.
   * - "subs.enable": A list of subtitle URIs to enable.
   * - "headers": A list of HTTP headers to set for network playback.
   *
   * @param extras Bundle containing intent extras
   */
  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    extras.getInt("position", POSITION_NOT_SET).takeIf { it != POSITION_NOT_SET }?.let {
      MPVLib.setPropertyInt("time-pos", it / MILLISECONDS_TO_SECONDS)
    }

    addSubtitlesFromExtras(extras)
    setHttpHeadersFromExtras(extras)
  }

  /**
   * Adds subtitle tracks from intent extras.
   *
   * This method checks the intent extras for the "subs" key, which contains a list
   * of subtitle URIs to add. It also checks for the "subs.enable" key, which contains
   * a list of subtitle URIs to enable.
   *
   * @param extras Bundle containing subtitle URIs
   */
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

  /**
   * Sets HTTP headers from intent extras for network playback.
   *
   * This method checks the intent extras for the "headers" key, which contains a list
   * of HTTP headers to set. It sets the User-Agent header and any additional headers
   * specified in the list.
   *
   * @param extras Bundle containing HTTP headers
   */
  private fun setHttpHeadersFromExtras(extras: Bundle) {
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

  /**
   * Parses the file path from the intent.
   *
   * This method checks the intent action and data to determine the file path.
   * It supports the following actions:
   * - ACTION_VIEW: The file path is contained in the intent data.
   * - ACTION_SEND: The file path is contained in the intent extras.
   *
   * @param intent The intent containing the file URI
   * @return The resolved file path, or null if not found
   */
  private fun parsePathFromIntent(intent: Intent): String? =
    when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
      Intent.ACTION_SEND -> parsePathFromSendIntent(intent)
      else -> intent.getStringExtra("uri")
    }

  /**
   * Parses the file path from a SEND intent.
   *
   * This method checks the intent extras for the file path.
   *
   * @param intent The SEND intent
   * @return The resolved file path, or null if not found
   */
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

  /**
   * Extracts and resolves the file name from the intent.
   *
   * @param intent The intent containing the file URI
   * @return The display name of the file, or empty string if not found
   */
  private fun getFileName(intent: Intent): String {
    val uri = extractUriFromIntent(intent) ?: return ""

    getDisplayNameFromUri(uri)?.let { return it }

    return uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: ""
  }

  /**
   * Extracts the URI from the intent based on intent type.
   *
   * @param intent The intent to extract URI from
   * @return The extracted URI, or null if not found
   */
  @Suppress("DEPRECATION")
  private fun extractUriFromIntent(intent: Intent): Uri? =
    if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
    } else {
      intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

  /**
   * Queries the content resolver to get the display name for a URI.
   *
   * @param uri The URI to query
   * @return The display name, or null if not found
   */
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

  /**
   * Converts the intent URI to a playable URI string for MPV.
   *
   * @param intent The intent containing the file URI
   * @return A playable URI string, or null if unable to resolve
   */
  private fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent) ?: return null
    return if (uri.startsWith("content://")) {
      uri.toUri().openContentFd(this)
    } else {
      uri
    }
  }

  /**
   * Handles device configuration changes.
   *
   * @param newConfig The new configuration
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (!isInitializing && hasVideoLoaded) {
      handleConfigurationChange()
    }
  }

  /**
   * Handles configuration changes by updating video aspect ratio.
   */
  private fun handleConfigurationChange() {
    if (!isInPictureInPictureMode) {
      viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
    } else {
      viewModel.hideControls()
    }
  }

  // ==================== MPV Event Observers ====================

  /**
   * Observer callback for MPV property changes (Long values).
   *
   * This method is called when an MPV property (with Long value) changes.
   * Extend this method to handle properties as needed.
   *
   * @param property The property name that changed
   * @param value The new Long value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: Long,
  ) {
    // Currently no Long properties are handled
  }

  /**
   * Observer callback for MPV property changes (Boolean values).
   * Handles pause state and end-of-file events.
   *
   * @param property The property name that changed
   * @param value The new Boolean value
   */
  internal fun onObserverEvent(
    property: String,
    value: Boolean,
  ) {
    when (property) {
      "pause" -> handlePauseStateChange(value)
      "eof-reached" -> handleEndOfFile(value)
    }
  }

  /**
   * Handles pause state changes by managing screen-on flag and MediaSession state.
   *
   * @param isPaused true if playback is paused, false if playing
   */
  private fun handlePauseStateChange(isPaused: Boolean) {
    if (isPaused) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    updateMediaSessionPlaybackState(!isPaused)
    runCatching {
      if (isInPictureInPictureMode) {
        pipHelper.updatePictureInPictureParams()
      }
    }.onFailure { /* Silently ignore PiP update failures */ }
  }

  /**
   * Handles end-of-file event by playing next in playlist if available, otherwise finishing activity if configured.
   *
   * @param isEof true if end of file reached
   */
  private fun handleEndOfFile(isEof: Boolean) {
    if (isEof) {
      // If there's a next video in the playlist, play it
      if (hasNext()) {
        playNext()
      } else if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
        // Only close if no next video and setting is enabled
        finishAndRemoveTask()
      }
    }
  }

  /**
   * Observer callback for MPV property changes (String values).
   * Handles Lua script invocations.
   *
   * @param property The property name that changed
   * @param value The new String value
   */
  internal fun onObserverEvent(
    property: String,
    value: String,
  ) {
    when (property.substringBeforeLast("/")) {
      "user-data/mpvex" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  /**
   * Observer callback for MPV property changes (MPVNode values).
   *
   * This method is called when an MPV property (with MPVNode value) changes.
   * Extend this method to handle properties as needed.
   *
   * @param property The property name that changed
   * @param value The new MPVNode value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: MPVNode,
  ) {
    // Currently no MPVNode properties are handled
  }

  /**
   * Observer callback for MPV property changes (Double values).
   *
   * This method is called when an MPV property (with Double value) changes.
   * Extend this method to handle properties as needed.
   *
   * @param property The property name that changed
   * @param value The new Double value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: Double,
  ) {
    // Currently no Double properties are handled
  }

  /**
   * Observer callback for MPV property changes (no value parameter).
   * Handles video aspect ratio changes.
   *
   * @param property The property name that changed
   */
  internal fun onObserverEvent(property: String) {
    when (property) {
      "video-params/aspect" -> {
        pipHelper.updatePictureInPictureParams()
      }
    }
  }

  /**
   * Handles MPV core events such as file loaded and playback restart.
   *
   * Called by the player when critical playback events occur.
   *
   * @param eventId The MPV event ID
   */
  internal fun event(eventId: Int) {
    when (eventId) {
      MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
        handleFileLoaded()
        isInitializing = false
        hasVideoLoaded = true
      }

      MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
        player.isExiting = false
        if (!hasVideoLoaded) {
          isInitializing = false
          hasVideoLoaded = true
        }
      }
    }
  }

  /**
   * Handles the file loaded event from MPV.
   * Initializes playback state, loads saved playback data, restores custom settings,
   * applies user preferences, and sets up metadata and media session.
   */
  @OptIn(DelicateCoroutinesApi::class)
  private fun handleFileLoaded() {
    // Only extract fileName from intent if not already set (i.e., not from playlist navigation)
    if (fileName.isBlank()) {
      fileName = getFileName(intent)
    }

    setIntentExtras(intent.extras)

    lifecycleScope.launch(Dispatchers.IO) {
      loadVideoPlaybackState(fileName)
    }

    // Save to recently played when video actually loads and plays
    GlobalScope.launch(Dispatchers.IO) {
      if (playlist.isNotEmpty()) {
        // For playlist items, save using the current URI
        saveRecentlyPlayedForUri(playlist[playlistIndex], fileName)
      } else {
        // For non-playlist videos, use the original saveRecentlyPlayed
        saveRecentlyPlayed()
      }
    }

    setOrientation()
    viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
    viewModel.restoreCustomAspectRatio()

    val zoomPreference = playerPreferences.defaultVideoZoom.get()
    MPVLib.setPropertyDouble("video-zoom", zoomPreference.toDouble())
    viewModel.setVideoZoom(zoomPreference)

    MPVLib.setPropertyString("force-media-title", fileName)
    viewModel.setMediaTitle(fileName)

    viewModel.unpause()

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

    updateMediaSessionMetadata(
      title = fileName,
      durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L,
    )
    updateMediaSessionPlaybackState(isPlaying = true)
  }

  /**
   * Saves the current playback state to the database.
   *
   * Uses GlobalScope to ensure save completes even if activity is destroyed.
   *
   * @param mediaTitle The title of the media being played
   */
  @OptIn(DelicateCoroutinesApi::class)
  private fun saveVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return

    GlobalScope.launch(Dispatchers.IO) {
      runCatching {
        val oldState = playbackStateRepository.getVideoDataByTitle(fileName)
        Log.d(TAG, "Saving playback state for: $mediaTitle")

        val lastPosition = calculateSavePosition(oldState)
        val duration = viewModel.duration ?: 0
        val timeRemaining = if (duration > lastPosition) duration - lastPosition else 0

        playbackStateRepository.upsert(
          PlaybackStateEntity(
            mediaTitle = mediaTitle,
            lastPosition = lastPosition,
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
            timeRemaining = timeRemaining,
          ),
        )
      }.onFailure { e ->
        Log.e(TAG, "Error saving playback state", e)
      }
    }
  }

  /**
   * Calculates the position to save based on user preferences.
   *
   * If "savePositionOnQuit" is not enabled, returns the previous saved position or 0.
   * If enabled, saves the current playback position unless at end of video.
   *
   * @param oldState Previous playback state if it exists
   * @return Position in seconds to save
   */
  private fun calculateSavePosition(oldState: PlaybackStateEntity?): Int {
    if (!playerPreferences.savePositionOnQuit.get()) {
      return oldState?.lastPosition ?: 0
    }

    val pos = viewModel.pos ?: 0
    val duration = viewModel.duration ?: 0
    return if (pos < duration - 1) pos else 0
  }

  /**
   * Loads and applies saved playback state from the database.
   *
   * @param mediaTitle The title of the media being played
   */
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

  /**
   * Applies saved playback state to MPV.
   *
   * Restores subtitle delay, audio delay, audio and track selections, and playback speed.
   * Also restores saved time position if enabled.
   *
   * @param state The saved playback state entity
   */
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

  /**
   * Applies default settings when no saved state exists.
   *
   * Sets subtitle speed to user default if not present in saved state.
   *
   * @param state The saved playback state entity (null if no saved state)
   */
  private fun applyDefaultSettings(state: PlaybackStateEntity?) {
    if (state == null) {
      val defaultSubSpeed = subtitlesPreferences.defaultSubSpeed.get().toDouble()
      MPVLib.setPropertyDouble("sub-speed", defaultSubSpeed)
    }
  }

  /**
   * Saves the currently playing file to recently played history.
   *
   * Uses GlobalScope to ensure save completes even if activity is destroyed.
   * Handles various URI schemes and infers launch source.
   */
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

      val filePath =
        when (uri.scheme) {
          "file" -> {
            uri.path ?: uri.toString()
          }

          "content" -> {
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

  /**
   * Sets the result intent with current playback position and duration.
   * Called when activity is finishing to return data to caller.
   */
  private fun setReturnIntent() {
    Log.d(TAG, "Setting return intent")

    val resultIntent =
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
        viewModel.duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
      }

    setResult(RESULT_OK, resultIntent)
  }

  /**
   * Handles new intents to load a different file without recreating the activity.
   *
   * @param intent The new intent
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    getPlayableUri(intent)?.let { uri ->
      MPVLib.command("loadfile", uri)
    }
    setIntent(intent)
  }

  // ==================== Picture-in-Picture Management ====================

  /**
   * Called when Picture-in-Picture mode changes.
   * Updates UI visibility and window configuration.
   *
   * @param isInPictureInPictureMode true if entering PiP, false if exiting
   * @param newConfig The new configuration
   */
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

  /**
   * Configures window for Picture-in-Picture mode.
   * Shows system UI and navigation bars.
   */
  private fun enterPipUIMode() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    WindowCompat.setDecorFitsSystemWindows(window, true)
    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
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

  /**
   * Enters Picture-in-Picture mode and hides all overlay controls.
   */
  fun enterPipModeHidingOverlay() {
    runCatching {
      enterPipUIMode()
    }.onFailure { e ->
      Log.e(TAG, "Error entering PiP mode with hidden overlay", e)
    }

    binding.controls.alpha = 0f

    pipHelper.enterPipMode()
  }

  // ==================== Orientation Management ====================

  /**
   * Sets the screen orientation based on user preferences.
   */
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

  /**
   * Determines the appropriate orientation based on video aspect ratio.
   *
   * @return Orientation constant for landscape (if aspect > 1.0) or portrait
   */
  private fun determineVideoOrientation(): Int {
    val aspect = player.getVideoOutAspect() ?: 0.0
    return if (aspect > 1.0) {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
  }

  // ==================== Key Event Handling ====================

  /**
   * Handles hardware key down events for player control.
   * Supports D-pad navigation, media keys, and volume controls.
   *
   * @param keyCode The key code
   * @param event The key event
   * @return true if event was handled, false otherwise
   */
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

  /**
   * Handles hardware key up events for player control.
   *
   * @param keyCode The key code
   * @param event The key event
   * @return true if event was handled, false otherwise
   */
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

  /**
   * Restores system UI to normal state (shows status and navigation bars).
   * Called when finishing the activity to return to normal Android UI.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun restoreSystemUI() {
    if (systemUIRestored || isFinishing || isDestroyed) {
      systemUIRestored = true
      return
    }

    runCatching {
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
      systemUIRestored = true
    }
  }

  // ==================== MediaSession ====================

  /**
   * Initializes MediaSession for integration with system media controls.
   * Supports Android Auto, Wear OS, Bluetooth controls, and notification controls.
   */
  private fun setupMediaSession() {
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

  /**
   * Updates MediaSession playback state (playing/paused).
   *
   * @param isPlaying true if currently playing, false if paused
   */
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

  /**
   * Updates MediaSession metadata (title, duration, etc.).
   *
   * @param title The media title
   * @param durationMs The media duration in milliseconds
   */
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

  /**
   * Releases MediaSession resources.
   * Called during activity cleanup.
   */
  private fun releaseMediaSession() {
    if (!mediaSessionInitialized) return
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }.onFailure { e -> Log.e(TAG, "Error releasing MediaSession", e) }
    mediaSessionInitialized = false
  }

  // ==================== Background Playback Service ====================

  /**
   * Service connection for binding to background playback service.
   */
  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?,
      ) {
        val binder = service as? MediaPlaybackService.MediaPlaybackBinder ?: return
        mediaPlaybackService = binder.getService()
        serviceBound = true

        Log.d(TAG, "Service connected, setting media info for: $fileName")

        if (fileName.isNotBlank()) {
          val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""
          val thumbnail = runCatching { MPVLib.grabThumbnail(1080) }.getOrNull()
          mediaPlaybackService?.setMediaInfo(title = fileName, artist = artist, thumbnail = thumbnail)
        }
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "Service disconnected")
        mediaPlaybackService = null
        serviceBound = false
      }
    }

  /**
   * Starts the background playback service and binds to it.
   *
   * This should only be called if a video is loaded and playback is initialized.
   * Responsible for starting and binding to the MediaPlaybackService, which
   * handles background playback.
   */
  private fun startBackgroundPlayback() {
    if (fileName.isBlank() || !hasVideoLoaded) return

    Log.d(TAG, "Starting background playback")
    val intent = Intent(this, MediaPlaybackService::class.java)
    startForegroundService(intent)
    bindService(intent, serviceConnection, BIND_AUTO_CREATE)
  }

  /**
   * Stops the background playback service and unbinds from it.
   *
   * Ensures that the background service is properly stopped when no longer needed,
   * such as when playback ends or the user leaves the activity.
   */
  private fun endBackgroundPlayback() {
    if (serviceBound) {
      try {
        unbindService(serviceConnection)
      } catch (e: Exception) {
        Log.e(TAG, "Error unbinding service", e)
      }
      serviceBound = false
    }
    stopService(Intent(this, MediaPlaybackService::class.java))
    mediaPlaybackService = null
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

  // ==================== Playlist Management ====================

  /**
   * Check if there's a next video in the playlist
   */
  fun hasNext(): Boolean = playlist.isNotEmpty() && playlistIndex < playlist.size - 1

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean = playlist.isNotEmpty() && playlistIndex > 0

  /**
   * Play the next video in the playlist
   */
  fun playNext() {
    if (!hasNext()) return

    playlistIndex++
    loadPlaylistItem(playlistIndex)
  }

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() {
    if (!hasPrevious()) return

    playlistIndex--
    loadPlaylistItem(playlistIndex)
  }

  /**
   * Load a playlist item by index
   */
  private fun loadPlaylistItem(index: Int) {
    if (index < 0 || index >= playlist.size) return

    // Save current video's playback state before switching
    if (fileName.isNotBlank()) {
      saveVideoPlaybackState(fileName)
    }

    val uri = playlist[index]
    val playableUri = uri.openContentFd(this) ?: uri.toString()

    // Update playlist index
    playlistIndex = index

    // Extract and set the new file name
    fileName = getFileNameFromUri(uri)

    // Load the new video
    MPVLib.command("loadfile", playableUri)

    // Update media title (this will trigger UI update)
    MPVLib.setPropertyString("force-media-title", fileName)
    viewModel.setMediaTitle(fileName)

    // Update media session metadata
    lifecycleScope.launch {
      kotlinx.coroutines.delay(100) // Wait for MPV to load the file
      val durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L
      updateMediaSessionMetadata(
        title = fileName,
        durationMs = durationMs,
      )
    }
  }

  /**
   * Get file name from URI
   */
  private fun getFileNameFromUri(uri: Uri): String {
    getDisplayNameFromUri(uri)?.let { return it }
    return uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: ""
  }

  /**
   * Save recently played for a specific URI
   */
  private suspend fun saveRecentlyPlayedForUri(
    uri: Uri,
    name: String,
  ) {
    runCatching {
      val filePath =
        when (uri.scheme) {
          "file" -> {
            uri.path ?: uri.toString()
          }

          "content" -> {
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

      RecentlyPlayedOps.addRecentlyPlayed(
        filePath = filePath,
        fileName = name,
        launchSource = "playlist",
      )

      Log.d(TAG, "Saved recently played: $filePath (source: playlist)")
    }.onFailure { e ->
      Log.e(TAG, "Error saving recently played for playlist item", e)
    }
  }

  companion object {
    /**
     * Intent action used to return playback result data to the calling activity.
     */
    private const val RESULT_INTENT = "app.marlboroadvance.mpvex.ui.player.PlayerActivity.result"

    /**
     * Constant for "brightness not set".
     */
    private const val BRIGHTNESS_NOT_SET = -1f

    /**
     * Constant used when playback position is not set.
     */
    private const val POSITION_NOT_SET = 0

    /**
     * Maximum volume for MPV in percent.
     */
    private const val MAX_MPV_VOLUME = 100

    /**
     * Milliseconds-to-seconds conversion factor.
     */
    private const val MILLISECONDS_TO_SECONDS = 1000

    /**
     * Factor to divide subtitle and audio delays to convert from ms to seconds.
     */
    private const val DELAY_DIVISOR = 1000.0

    /**
     * Default playback speed (1.0 = normal).
     */
    private const val DEFAULT_PLAYBACK_SPEED = 1.0

    /**
     * Default subtitle speed (1.0 = normal).
     */
    private const val DEFAULT_SUB_SPEED = 1.0

    /**
     * General tag for logging from PlayerActivity.
     */
    const val TAG = "mpvex"
  }

  /**
   * Safely finishes the activity with proper cleanup.
   *
   * Handles rapid back presses, initialization states, and ensures:
   * - System UI is restored
   * - Background playback service is stopped if user is finishing
   * - Return intent is set with playback data
   * - All flags are reset
   *
   * Multiple guards and try-catch blocks prevent crashes during edge cases.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun finishSafely() {
    runCatching {
      if (isFinishing) return

      if (!systemUIRestored) {
        restoreSystemUI()
      }

      isInitializing = false
      hasVideoLoaded = false

      if (isUserFinishing) {
        endBackgroundPlayback()
      }

      setReturnIntent()
      finish()
    }.onFailure { e ->
      Log.e(TAG, "Error during finishSafely", e)
      // Attempt basic cleanup even if error occurred
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
