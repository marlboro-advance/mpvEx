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
   * Repository for managing playlists.
   */
  private val playlistRepository: app.marlboroadvance.mpvex.database.repository.PlaylistRepository by inject()

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
   * Unique identifier for the current media, used for saving/loading playback state.
   * For network streams, this includes a hash of the URI to ensure uniqueness.
   */
  private var mediaIdentifier = ""

  /**
   * Playlist of URIs for sequential playback
   */
  internal var playlist: List<Uri> = emptyList()

  /**
   * Current index in the playlist
   */
  internal var playlistIndex: Int = 0

  /**
   * Playlist ID for tracking play history (optional, only for custom playlists)
   */
  private var playlistId: Int? = null

  /**
   * Helper for managing Picture-in-Picture mode.
   */
  private lateinit var pipHelper: MPVPipHelper

  private var isReady = false // Single flag: true when video loaded and ready
  private var isUserFinishing = false
  private var noisyReceiverRegistered = false
  private var mpvInitialized = false // Track MPV initialization state

  // ==================== Background Playback ====================

  /**
   * Reference to the background playback service.
   */
  private var mediaPlaybackService: MediaPlaybackService? = null

  /**
   * Tracks whether we're currently bound to the background playback service.
   */
  private var serviceBound = false

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
          viewModel.pause()
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    setupMPV()
    MediaPlaybackService.createNotificationChannel(this)
    setupAudio()
    setupBackPressHandler()
    setupPlayerControls()
    setupPipHelper()
    setupMediaSession()

    playlist = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }
    playlistIndex = intent.getIntExtra("playlist_index", 0)
    playlistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }

    // Extract fileName early so it's available when video loads
    fileName = getFileName(intent)
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)

    getPlayableUri(intent)?.let(player::playFile)
    setOrientation()

    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }

  override fun attachBaseContext(newBase: Context?) {
    if (newBase == null) {
      super.attachBaseContext(null)
      return
    }

    val originalConfiguration = newBase.resources.configuration
    val contextToUse =
      if (originalConfiguration.fontScale == 1f) {
        newBase
      } else {
        val updatedConfiguration = Configuration(originalConfiguration).apply { fontScale = 1f }
        val configurationContext = newBase.createConfigurationContext(updatedConfiguration)
        val configurationDisplayMetrics = configurationContext.resources.displayMetrics
        configurationDisplayMetrics.scaledDensity = updatedConfiguration.fontScale * configurationDisplayMetrics.density
        configurationContext
      }

    super.attachBaseContext(contextToUse)
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
    // Dismiss overlays first
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

    if (!viewModel.controlsShown.value) {
      viewModel.showControls()
      return
    }

    isUserFinishing = true
    finish()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun setupPlayerControls() {
    binding.controls.setContent {
      MpvexTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = {
            isUserFinishing = true
            finish()
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

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let {
      MPVLib.setPropertyString(it.property, it.value)
    }

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

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onDestroy() {
    Log.d(TAG, "PlayerActivity onDestroy")

    runCatching {
      if (isUserFinishing || isFinishing) {
        if (serviceBound) {
          runCatching { unbindService(serviceConnection) }
          serviceBound = false
        }
        stopService(Intent(this, MediaPlaybackService::class.java))
        mediaPlaybackService = null
      }

      isReady = false
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
    if (!mpvInitialized) return

    player.isExiting = true

    if (!isFinishing) return

    // Cleanup on main thread - it's fast enough and safer
    runCatching {
      MPVLib.removeObserver(playerObserver)
      if (isReady) {
        MPVLib.command("quit")
      }
      MPVLib.destroy()
      mpvInitialized = false
    }.onFailure { e ->
      Log.e(TAG, "Error cleaning up MPV", e)
    }
  }

  private fun cleanupAudio() {
    if (restoreAudioFocus != {}) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      restoreAudioFocus = {}
    }
  }

  private fun cleanupReceivers() {
    if (noisyReceiverRegistered) {
      runCatching {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onPause() {
    runCatching {
      val isInPip = isInPictureInPictureMode
      val shouldPause = !playerPreferences.automaticBackgroundPlayback.get() || isUserFinishing

      if (!isInPip && shouldPause) {
        viewModel.pause()
      }

      // Restore UI immediately when user is finishing for instant feedback
      if (isUserFinishing && !isInPip) {
        restoreSystemUI()
      }

      saveVideoPlaybackState(fileName)
    }.onFailure { e ->
      Log.e(TAG, "Error during onPause", e)
    }

    super.onPause()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun finish() {
    runCatching {
      // Restore UI immediately for responsive exit
      if (!isInPictureInPictureMode) {
        restoreSystemUI()
      }
      isReady = false
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

      if (noisyReceiverRegistered) {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }

      if (!serviceBound && playerPreferences.automaticBackgroundPlayback.get() && !isUserFinishing) {
        startBackgroundPlayback()
      } else {
        if (!playerPreferences.automaticBackgroundPlayback.get() || isUserFinishing) {
          viewModel.pause()
        }
        if (serviceBound) {
          runCatching {
            unbindService(serviceConnection)
            serviceBound = false
          }
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onStop", e)
    }

    super.onStop()
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onStart() {
    super.onStart()

    runCatching {
      setupWindowFlags()
      setupSystemUI()

      if (!noisyReceiverRegistered) {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
        noisyReceiverRegistered = true
      }

      if (playerPreferences.rememberBrightness.get()) {
        val brightness = playerPreferences.defaultBrightness.get()
        if (brightness != BRIGHTNESS_NOT_SET) {
          viewModel.changeBrightnessTo(brightness)
        }
      }

      if (serviceBound) {
        endBackgroundPlayback()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error during onStart", e)
    }
  }

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
    @Suppress("DEPRECATION")
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE

    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    windowInsetsController.apply {
      hide(WindowInsetsCompat.Type.systemBars())
      hide(WindowInsetsCompat.Type.navigationBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun restoreSystemUI() {
    // Clear flags first for immediate effect
    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Set cutout mode before showing bars for smoother transition
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT

    // Update window insets configuration
    WindowCompat.setDecorFitsSystemWindows(window, true)

    // Restore default behavior and show bars in one go
    windowInsetsController.apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
      show(WindowInsetsCompat.Type.systemBars())
      show(WindowInsetsCompat.Type.navigationBars())
    }
  }

  /**
   * Initializes the MPV player with the necessary paths and observers.
   * Fast synchronous initialization - assets are copied asynchronously.
   */
  private fun setupMPV() {
    // Initialize player first (this is fast)
    player.initialize(filesDir.path, cacheDir.path)
    mpvInitialized = true

    // Add observer after initialization
    MPVLib.addObserver(playerObserver)

    // Copy assets asynchronously (non-blocking)
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        Utils.copyAssets(this@PlayerActivity)
        copyMPVConfigFiles()
        copyMPVScripts()
        copyMPVFonts()
      }.onFailure { e ->
        Log.e(TAG, "Error copying MPV assets", e)
      }
    }
  }

  /**
   * Copies or creates the MPV configuration files.
   */
  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    runCatching {
      // Create mpv.conf
      File("$applicationPath/mpv.conf").apply {
        if (!exists()) createNewFile()
        val content = advancedPreferences.mpvConf.get()
        if (content.isNotBlank()) writeText(content)
      }

      // Create input.conf
      File("$applicationPath/input.conf").apply {
        if (!exists()) createNewFile()
        val content = advancedPreferences.inputConf.get()
        if (content.isNotBlank()) writeText(content)
      }
    }.onFailure { e ->
      Log.e(TAG, "Error creating config files", e)
    }
  }

  private fun copyMPVScripts() {
    runCatching {
      val applicationPath = filesDir.path
      val scriptsDir =
        fileManager.createDir(
          fileManager.fromPath(applicationPath),
          "scripts",
        ) ?: error("Failed to create scripts directory")

      fileManager.deleteContent(scriptsDir)

      assets.open("mpvex.lua").use { input ->
        File("$scriptsDir/mpvex.lua").apply {
          if (!exists()) createNewFile()
          writeText(input.bufferedReader().readText())
        }
      }
    }.onFailure { e ->
      Log.e(TAG, "Error copying MPV scripts", e)
    }
  }

  private fun copyMPVFonts() {
    runCatching {
      val persistentPath = filesDir.path
      val destDir = ensureFontsDirectory(persistentPath)

      val fontsFolderUri = subtitlesPreferences.fontsFolder.get().toUri()
      val fontsDir = fileManager.fromUri(fontsFolderUri) ?: return@runCatching

      if (fileManager.exists(fontsDir)) {
        fileManager.copyDirectoryWithContent(fontsDir, destDir, false)
      }
    }.onFailure { e ->
      Log.e(TAG, "Error copying fonts: ${e.message}")
    }
  }

  private fun ensureFontsDirectory(basePath: String): com.github.k1rakishou.fsaf.file.AbstractFile {
    val destDir = fileManager.fromPath("$basePath/fonts")
    if (!fileManager.exists(destDir)) {
      fileManager.createDir(fileManager.fromPath(basePath), "fonts")
    }
    return destDir
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
      val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      uri?.resolveUri(this@PlayerActivity)
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
    // First check if a custom title/filename was provided via intent extras
    intent.getStringExtra("title")?.let { return it }
    intent.getStringExtra("filename")?.let { return it }

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
  private fun extractUriFromIntent(intent: Intent): Uri? =
    if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
    } else {
      intent.data ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
      }
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
    if (isReady) {
      handleConfigurationChange()
    }
  }

  /**
   * Handles configuration changes by updating video aspect ratio.
   */
  private fun handleConfigurationChange() {
    if (!isInPictureInPictureMode) {
      viewModel.changeVideoAspect(playerPreferences.videoAspect.get(), showUpdate = false)
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
        isReady = true
      }

      MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
        player.isExiting = false
        if (!isReady) {
          isReady = true
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
    // Extract fileName from intent only if not already set
    // This preserves fileName set in onNewIntent or onCreate
    if (fileName.isBlank()) {
      fileName = getFileName(intent)
      // Ensure fileName is not blank - use a fallback if necessary
      if (fileName.isBlank()) {
        fileName = intent.data?.lastPathSegment ?: "Unknown Video"
      }
      mediaIdentifier = getMediaIdentifier(intent, fileName)
    } else if (mediaIdentifier.isBlank()) {
      // If fileName was already set, but mediaIdentifier is missing, set it for safety
      mediaIdentifier = getMediaIdentifier(intent, fileName)
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
    viewModel.changeVideoAspect(playerPreferences.videoAspect.get(), showUpdate = false)
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
    if (mediaIdentifier.isBlank()) return

    GlobalScope.launch(Dispatchers.IO) {
      runCatching {
        val oldState = playbackStateRepository.getVideoDataByTitle(mediaIdentifier)
        Log.d(TAG, "Saving playback state for: $mediaTitle (identifier: $mediaIdentifier)")

        val lastPosition = calculateSavePosition(oldState)
        val duration = viewModel.duration ?: 0
        val timeRemaining = if (duration > lastPosition) duration - lastPosition else 0

        playbackStateRepository.upsert(
          PlaybackStateEntity(
            mediaTitle = mediaIdentifier,
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
    if (mediaIdentifier.isBlank()) return

    runCatching {
      val state = playbackStateRepository.getVideoDataByTitle(mediaIdentifier)

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
    
    // Update the intent first so getFileName uses the new intent data
    setIntent(intent)
    
    // Extract the new fileName before loading the file
    fileName = getFileName(intent)
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)
    
    // Load the new file
    getPlayableUri(intent)?.let { uri ->
      MPVLib.command("loadfile", uri)
    }
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
    windowInsetsController.apply {
      show(WindowInsetsCompat.Type.systemBars())
      show(WindowInsetsCompat.Type.navigationBars())
    }
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  private fun exitPipUIMode() {
    setupWindowFlags()
    setupSystemUI()
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
    if (fileName.isBlank() || !isReady) return

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
    // Generate new media identifier for playback state
    mediaIdentifier = getMediaIdentifierFromUri(uri, fileName)

    // Update playlist play history if this is a custom playlist
    playlistId?.let { id ->
      lifecycleScope.launch(Dispatchers.IO) {
        val filePath = when (uri.scheme) {
          "file" -> uri.path ?: uri.toString()
          "content" -> {
            contentResolver.query(
              uri,
              arrayOf(MediaStore.MediaColumns.DATA),
              null,
              null,
              null,
            )?.use { cursor ->
              if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex != -1) cursor.getString(columnIndex) else null
              } else null
            } ?: uri.toString()
          }

          else -> uri.toString()
        }

        runCatching {
          playlistRepository.updatePlayHistory(id, filePath)
          Log.d(TAG, "Updated playlist history for: $filePath in playlist $id")
        }.onFailure { e ->
          Log.e(TAG, "Error updating playlist history", e)
        }
      }
    }

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
   * Get the current video title for controls display.
   * Used as a fallback when MPV hasn't set the media-title property yet.
   */
  fun getTitleForControls(): String = fileName

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
        playlistId = playlistId,
      )

      Log.d(TAG, "Saved recently played: $filePath (source: playlist, playlistId: $playlistId)")
    }.onFailure { e ->
      Log.e(TAG, "Error saving recently played for playlist item", e)
    }
  }

  /**
   * Generate a unique identifier for this media for playback state/history.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  private fun getMediaIdentifier(intent: Intent, fileName: String): String {
    val uri = extractUriFromIntent(intent)
    return if (uri != null && (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms")) {
      // For remoting protocols: hash the URI so position is per-episode or per-stream.
      "${fileName}_${uri.toString().hashCode()}"
    } else {
      // For local/file uris and unknown: just use fileName.
      fileName
    }
  }

  /**
   * Generate a unique identifier for this media from a URI and name.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  private fun getMediaIdentifierFromUri(uri: Uri, fileName: String): String {
    return if (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms") {
      "${fileName}_${uri.toString().hashCode()}"
    } else {
      fileName
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
}
