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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.databinding.PlayerLayoutBinding
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.controls.PlayerControls
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.HttpUtils
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
import kotlinx.coroutines.withContext
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
   * Preferences for browser settings.
   */
  private val browserPreferences: BrowserPreferences by inject()

  /**
   * Manager for file operations.
   */
  private val fileManager: FileManager by inject()

  /**
   * Track selector for automatic audio/subtitle selection
   */
  private val trackSelector: TrackSelector by lazy {
    TrackSelector(audioPreferences, subtitlesPreferences)
  }

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
   * Shuffled order of playlist indices (when shuffle is enabled)
   */
  private var shuffledIndices: List<Int> = emptyList()

  /**
   * Current position in shuffled playlist (when shuffle is enabled)
   */
  private var shuffledPosition: Int = 0

  /**
   * Playlist ID for tracking play history (optional, only for custom playlists)
   */
  private var playlistId: Int? = null

  /**
   * Tracks the starting offset of the loaded playlist window in the full playlist.
   * Used for windowed loading to prevent ANR with large playlists.
   */
  private var playlistWindowOffset: Int = 0

  /**
   * Total count of items in the full playlist (when using windowed loading).
   * -1 means unknown or not using windowed loading.
   */
  var playlistTotalCount: Int = -1
    private set

  /**
   * Indicates whether the current playlist is an M3U playlist sourced from database.
   * Used to skip thumbnail/metadata extraction for network streams.
   */
  private var isM3uPlaylist: Boolean = false

  /**
   * Helper for managing Picture-in-Picture mode.
   */
  private lateinit var pipHelper: MPVPipHelper

  private var isReady = false // Single flag: true when video loaded and ready
  private var isUserFinishing = false
  private var noisyReceiverRegistered = false
  private var mpvInitialized = false // Track MPV initialization state
  private var savePlaybackStateJob: kotlinx.coroutines.Job? = null // Track ongoing save job
  private var wasPlayingBeforePause = false // Track if video was playing before pause

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

    playlistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
    playlistIndex = intent.getIntExtra("playlist_index", 0)

    // Load playlist from intent extras first (fast path - backward compatibility)
    playlist = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }

    // If playlist is empty but playlist_id is provided, load asynchronously from database
    // Load all items - LazyColumn handles pagination/virtualization efficiently
    if (playlist.isEmpty() && playlistId != null) {
      lifecycleScope.launch(Dispatchers.IO) {
        val pid = playlistId ?: return@launch
        try {
          // Check if this is an M3U playlist
          val playlistEntity = playlistRepository.getPlaylistById(pid)
          isM3uPlaylist = playlistEntity?.isM3uPlaylist ?: false

          // Load all items - LazyColumn will handle virtualization/pagination efficiently
          val items = playlistRepository.getPlaylistItemsAsUris(pid)
          val totalCount = items.size

          withContext(Dispatchers.Main) {
            playlist = items
            playlistWindowOffset = 0
            playlistTotalCount = totalCount
            Log.d(TAG, "Loaded all $totalCount items from playlist $pid (isM3U: $isM3uPlaylist)")
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load playlist from database", e)
        }
      }
    }

    // Only auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (playlist.isEmpty() && playlistId == null && playerPreferences.playlistMode.get()) {
      val path = parsePathFromIntent(intent)
      if (path != null) {
        generatePlaylistFromFolder(path)
      }
    }

    // Extract fileName early so it's available when video loads
    fileName = getFileName(intent)
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)

    // Set HTTP headers (including referer) BEFORE playing the file
    setHttpHeadersFromExtras(intent.extras)

    getPlayableUri(intent)?.let(player::playFile)

    // Only set orientation immediately if NOT in Video mode
    // For Video mode, wait for video-params/aspect to become available
    if (playerPreferences.orientation.get() != PlayerOrientation.Video) {
      setOrientation()
    }

    // Apply persisted shuffle state after playlist is loaded
    viewModel.applyPersistedShuffleState()

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

    // Check if auto PIP is enabled - enter PIP mode instead of finishing
    if (playerPreferences.autoPiPOnNavigation.get() && isReady) {
      pipHelper.enterPipMode()
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
      requestAudioFocus()
    }
  }

  /**
   * @return true if audio focus was granted immediately, false otherwise
   */
  override fun requestAudioFocus(): Boolean {
    val req = audioFocusRequest ?: return false
    val result = audioManager.requestAudioFocus(req)
    return when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        restoreAudioFocus = {}
        true
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        restoreAudioFocus = { requestAudioFocus() }
        false
      }

      else -> {
        restoreAudioFocus = {}
        false
      }
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // Enter PIP mode when user presses home button if auto PIP is enabled
    if (playerPreferences.autoPiPOnNavigation.get() && isReady && !isFinishing) {
      pipHelper.enterPipMode()
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

      // Wait for any pending save operation to complete before destroying MPV
      // This prevents the race condition where the save coroutine tries to access
      // MPV properties after MPVLib.destroy() has been called
      savePlaybackStateJob?.let { job ->
        Log.d(TAG, "Waiting for save playback state job to complete...")
        runCatching {
          // Use runBlocking to ensure we wait for the job to finish
          // This is safe here as onDestroy is already on the main thread
          kotlinx.coroutines.runBlocking {
            job.join()
          }
        }
        Log.d(TAG, "Save playback state job completed")
      }

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

    runCatching {
      MPVLib.removeObserver(playerObserver)

      if (isReady) {
        // Pause playback first to reduce thread activity
        MPVLib.setPropertyBoolean("pause", true)

        // Send quit command to gracefully shut down MPV
        MPVLib.command("quit")

        // Wait briefly for MPV to process quit and clean up internal threads
        // This prevents race conditions where hardware UI threads try to access
        // mutexes/queues that are destroyed by MPVLib.destroy()
        // We use a short blocking wait here as onDestroy is already on the main thread
        // and this ensures proper cleanup before activity destruction
        Thread.sleep(100)
      }

      // Now safe to destroy MPV as internal threads have had time to shut down
      MPVLib.destroy()
      mpvInitialized = false
    }.onFailure { e ->
      Log.e(TAG, "Error cleaning up MPV", e)
    }
  }

  override fun abandonAudioFocus() {
    if (restoreAudioFocus != {}) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      restoreAudioFocus = {}
    }
  }

  private fun cleanupAudio() {
    abandonAudioFocus()
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
      val shouldPause = !audioPreferences.automaticBackgroundPlayback.get() || isUserFinishing

      if (!isInPip && shouldPause) {
        wasPlayingBeforePause = !(viewModel.paused ?: true)
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

  @RequiresApi(Build.VERSION_CODES.P)
  override fun finishAndRemoveTask() {
    runCatching {
      // Restore UI immediately for responsive exit (same as finish())
      if (!isInPictureInPictureMode) {
        restoreSystemUI()
      }
      isReady = false
      isUserFinishing = true
      setReturnIntent()
    }.onFailure { e ->
      Log.e(TAG, "Error during finishAndRemoveTask", e)
    }

    super.finishAndRemoveTask()
  }

  override fun onStop() {
    runCatching {
      pipHelper.onStop()
      saveVideoPlaybackState(fileName)

      if (noisyReceiverRegistered) {
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }

      // Don't start background playback if activity is finishing or user is leaving
      if (!serviceBound && audioPreferences.automaticBackgroundPlayback.get() && !isUserFinishing && !isFinishing) {
        startBackgroundPlayback()
      } else {
        if (!audioPreferences.automaticBackgroundPlayback.get() || isUserFinishing || isFinishing) {
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
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    // Set status bar color for when it will be shown (with controls)
    if (playerPreferences.showSystemStatusBar.get()) {
      window.statusBarColor = android.graphics.Color.parseColor("#80000000") // Semi-transparent black
    }

    // Always start with status bar hidden - it will show when controls are shown
    windowInsetsController.apply {
      hide(WindowInsetsCompat.Type.statusBars())
      hide(WindowInsetsCompat.Type.navigationBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Don't use LOW_PROFILE if we plan to show status bar with controls
    // LOW_PROFILE causes only icons to show without background
    @Suppress("DEPRECATION")
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        if (playerPreferences.showSystemStatusBar.get()) 0 else View.SYSTEM_UI_FLAG_LOW_PROFILE
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
   * CRITICAL: Must copy config and scripts BEFORE initializing MPV, as MPV loads scripts during init.
   */
  private fun setupMPV() {
    // Copy essential files FIRST, before MPV initialization
    // MPV will load scripts during initialize(), so they must exist beforehand
    runCatching {
      Utils.copyAssets(this@PlayerActivity)
      copyMPVConfigFiles()
      copyMPVScripts()
      Log.d(TAG, "MPV config and scripts prepared successfully")
    }.onFailure { e ->
      Log.e(TAG, "Error copying MPV config and scripts", e)
    }

    // NOW initialize MPV - it will find and load the scripts we just copied
    player.initialize(filesDir.path, cacheDir.path)
    mpvInitialized = true
    Log.d(TAG, "MPV initialized")

    // Add observer after initialization
    MPVLib.addObserver(playerObserver)

    // Copy fonts asynchronously (not critical for initial playback)
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        copyMPVFonts()
      }.onFailure { e ->
        Log.e(TAG, "Error copying MPV fonts", e)
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
        if (content.isNotBlank()) {
          writeText(content)
        } else {
          // Default optimized config for fast stream loading
          writeText(getDefaultMpvConfig())
        }
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

  /**
   * Returns default MPV configuration.
   */
  private fun getDefaultMpvConfig(): String {
    return ""
  }

  private fun copyMPVScripts() {
    runCatching {
      val applicationPath = filesDir.path
      val scriptsDir =
        fileManager.createDir(
          fileManager.fromPath(applicationPath),
          "scripts",
        ) ?: run {
          Log.e(TAG, "Failed to create scripts directory")
          return@runCatching
        }

      // Clean existing scripts first
      fileManager.deleteContent(scriptsDir)
      Log.d(TAG, "Cleaned scripts directory")

      // Copy user-selected Lua scripts if enabled
      if (advancedPreferences.enableLuaScripts.get()) {
        val selectedScripts = advancedPreferences.selectedLuaScripts.get()
        val mpvConfStorageUri = advancedPreferences.mpvConfStorageUri.get()

        Log.d(TAG, "Lua scripts enabled: ${selectedScripts.size} script(s) selected: ${selectedScripts.joinToString()}")

        if (selectedScripts.isEmpty()) {
          Log.d(TAG, "No Lua scripts selected, skipping copy")
          return@runCatching
        }

        if (mpvConfStorageUri.isBlank()) {
          Log.w(TAG, "MPV config storage URI not set, cannot copy Lua scripts")
          return@runCatching
        }

        val tree = DocumentFile.fromTreeUri(this, mpvConfStorageUri.toUri())
        if (tree == null) {
          Log.e(TAG, "Failed to access MPV config storage directory")
          return@runCatching
        }

        if (!tree.exists() || !tree.canRead()) {
          Log.e(TAG, "MPV config storage directory does not exist or cannot be read")
          return@runCatching
        }

        var successCount = 0
        var failCount = 0

        selectedScripts.forEach { scriptName ->
          runCatching {
            val scriptFile = tree.findFile(scriptName)
            if (scriptFile != null && scriptFile.exists() && scriptFile.canRead()) {
              contentResolver.openInputStream(scriptFile.uri)?.use { input ->
                val scriptsDirPath = File(filesDir.path, "scripts")
                scriptsDirPath.mkdirs()
                val targetFile = File(scriptsDirPath, scriptName)
                targetFile.writeText(input.bufferedReader().readText())
                Log.d(TAG, "Successfully copied Lua script: $scriptName to ${targetFile.absolutePath}")
                successCount++
              } ?: run {
                Log.e(TAG, "Failed to open input stream for Lua script: $scriptName")
                failCount++
              }
            } else {
              Log.w(TAG, "Lua script not found or not readable: $scriptName")
              failCount++
            }
          }.onFailure { e ->
            Log.e(TAG, "Error copying Lua script: $scriptName", e)
            failCount++
          }
        }

        Log.d(TAG, "Lua scripts copy completed: $successCount succeeded, $failCount failed")
      } else {
        Log.d(TAG, "Lua scripts disabled in preferences")
      }
    }.onFailure { e ->
      Log.e(TAG, "Error in copyMPVScripts", e)
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

    lifecycleScope.launch(Dispatchers.Default) {
      for (suburi in subList) {
        val subfile = suburi.resolveUri(this@PlayerActivity) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }
  }

  /**
   * Sets HTTP headers from intent extras for network playback.
   *
   * This method checks the intent extras for the "headers" key, which contains a list
   * of HTTP headers to set. It sets the User-Agent header and any additional headers
   * specified in the list.
   *
   * Also automatically adds Referer header based on the URL origin if not already provided.
   *
   * @param extras Bundle containing HTTP headers
   */
  private fun setHttpHeadersFromExtras(extras: Bundle?) {
    // Build header map starting with auto-detected referer
    val headerMap = mutableMapOf<String, String>()

    // Automatically extract and set referer domain from the URL
    val uri = extractUriFromIntent(intent)
    if (uri != null && HttpUtils.isNetworkStream(uri)) {
      HttpUtils.extractRefererDomain(uri)?.let { referer ->
        headerMap["Referer"] = referer
        Log.d(TAG, "Auto-detected Referer: $referer")
      }
    }

    // Process headers from extras (these can override the auto-detected referer)
    extras?.getStringArray("headers")?.let { headers ->
      if (headers.isEmpty()) return@let

      if (headers[0].startsWith("User-Agent", ignoreCase = true)) {
        MPVLib.setPropertyString("user-agent", headers[1])
      }

      if (headers.size > 2) {
        headers
          .asSequence()
          .drop(2)
          .chunked(2)
          .filter { it.size == 2 }
          .forEach { (key, value) ->
            headerMap[key] = value
          }
      }
    }

    // Set all headers in MPV
    if (headerMap.isNotEmpty()) {
      val headersString = headerMap
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
        .joinToString(",")

      MPVLib.setPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers: $headersString")
    }
  }

  /**
   * Sets HTTP headers for a specific URI (used for playlist items).
   * Automatically extracts and sets the Referer header based on the URI origin.
   *
   * @param uri The URI to extract referer from and set headers for
   */
  private fun setHttpHeadersForUri(uri: Uri) {
    if (!HttpUtils.isNetworkStream(uri)) return

    val headerMap = mutableMapOf<String, String>()

    // Automatically extract and set referer domain from the URI
    HttpUtils.extractRefererDomain(uri)?.let { referer ->
      headerMap["Referer"] = referer
      Log.d(TAG, "Auto-detected Referer for playlist item: $referer")
    }

    // Set all headers in MPV
    if (headerMap.isNotEmpty()) {
      val headersString = headerMap
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
        .joinToString(",")

      MPVLib.setPropertyString("http-header-fields", headersString)
      Log.d(TAG, "Set HTTP headers for playlist item: $headersString")
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

    // Try content resolver first for content:// URIs
    getDisplayNameFromUri(uri)?.let { return it }

    // Extract filename from URL/URI
    return extractFileNameFromUri(uri)
  }

  /**
   * Extracts filename from URI, handling URL encoding and network URLs properly.
   * For network streams, returns a temporary name that will be updated async via HTTP headers.
   *
   * @param uri The URI to extract filename from
   * @return The extracted filename
   */
  private fun extractFileNameFromUri(uri: Uri): String {
    // For HTTP/HTTPS URLs, extract from path (will be updated async via HTTP headers)
    if (HttpUtils.isNetworkStream(uri)) {
      // Get the last path segment and decode URL encoding
      val path = uri.path ?: return uri.host ?: "Network Stream"
      val lastSegment = path.substringAfterLast("/")

      if (lastSegment.isNotBlank()) {
        // Decode URL encoding (e.g., %20 -> space)
        return try {
          java.net.URLDecoder.decode(lastSegment, "UTF-8")
            .substringBefore("?") // Remove query parameters
            .substringBefore("#") // Remove fragments
            .takeIf { it.isNotBlank() } ?: uri.host ?: "Network Stream"
        } catch (e: Exception) {
          lastSegment
            .substringBefore("?")
            .substringBefore("#")
        }
      }

      // If no filename in path, use hostname
      return uri.host ?: "Network Stream"
    }

    // For file:// and content:// URIs
    return uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: "Unknown Video"
  }

  /**
   * Gets the display title for a playlist item URI.
   *
   * @param uri The URI to get the title for
   * @return The display name/title of the file
   */
  internal fun getPlaylistItemTitle(uri: Uri): String {
    // Try content resolver first for content:// URIs
    getDisplayNameFromUri(uri)?.let { return it }

    // Extract filename from URL/URI
    return extractFileNameFromUri(uri)
  }

  /**
   * Plays a playlist item by index.
   *
   * @param index The index of the playlist item to play
   */
  internal fun playPlaylistItem(index: Int) {
    if (index in playlist.indices) {
      loadPlaylistItem(index)
    }
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
   * Handles video width and height changes.
   *
   * @param property The property name that changed
   * @param value The new Long value
   */
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(
    property: String,
    value: Long,
  ) {
    when (property) {
      "video-params/w",
      "video-params/h" -> {
        // Safety check: don't access MPV during cleanup
        if (!mpvInitialized || player.isExiting || isFinishing) return

        val aspect = player.getVideoOutAspect()
        Log.d(TAG, "Video dimension changed: $property, aspect: $aspect")
        pipHelper.updatePictureInPictureParams()
        // Update orientation when video dimensions change (fixes Video orientation mode)
        if (playerPreferences.orientation.get() == PlayerOrientation.Video && aspect != null) {
          setOrientation()
        }
      }
    }
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
      "pause" -> {
        handlePauseStateChange(value)
        // Ensure isReady is set when playback starts
        if (!value && !isReady) {
          isReady = true
        }
      }
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
      // Check if we should repeat the current file
      if (viewModel.shouldRepeatCurrentFile()) {
        MPVLib.command("seek", "0", "absolute")
        viewModel.unpause()
        return
      }

      // Handle playlist playback
      if (playlist.isNotEmpty()) {
        val hasNextItem = if (viewModel.shuffleEnabled.value) {
          shuffledPosition < shuffledIndices.size - 1
        } else {
          playlistIndex < playlist.size - 1
        }

        // Check if autoplay next video is enabled
        val autoplayEnabled = playerPreferences.autoplayNextVideo.get()

        if (hasNextItem && autoplayEnabled) {
          // Play next item in playlist
          playNext()
        } else if (viewModel.shouldRepeatPlaylist()) {
          // At end of playlist with repeat ALL: restart from beginning
          if (viewModel.shuffleEnabled.value) {
            // Regenerate shuffle order and start from beginning
            generateShuffledIndices()
            shuffledPosition = 0
            playlistIndex = shuffledIndices[0]
            loadPlaylistItem(playlistIndex)
          } else {
            // Normal mode: restart from index 0
            playlistIndex = 0
            loadPlaylistItem(0)
          }
        } else if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
          // No autoplay or no next item, end of playlist: close if setting is enabled
          finishAndRemoveTask()
        }
        // If autoplay is off and closeAfterReachingEndOfVideo is off, just stay on current video
      } else {
        // Single video playback (no playlist)
        if (playerPreferences.closeAfterReachingEndOfVideo.get()) {
          finishAndRemoveTask()
        }
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
    // Handle Double properties
    when (property) {
      "video-params/aspect" -> {
        // Safety check: don't access MPV during cleanup
        if (!mpvInitialized || player.isExiting || isFinishing) return

        val aspect = player.getVideoOutAspect()
        Log.d(TAG, "video-params/aspect changed: $aspect")
        pipHelper.updatePictureInPictureParams()
        // Update orientation when video aspect ratio changes (fixes Video orientation mode)
        if (playerPreferences.orientation.get() == PlayerOrientation.Video && aspect != null) {
          setOrientation()
        }
      }
    }
  }

  /**
   * Observer callback for MPV property changes (no value parameter).
   * Handles properties with no value parameter.
   *
   * @param property The property name that changed
   */
  internal fun onObserverEvent(property: String) {
    // Currently no properties use this signature
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
      // Load playback state (will skip track restoration if preferred language configured)
      val hasState = loadVideoPlaybackState(fileName)

      // Apply track selection logic (defaults only apply when no saved state)
      trackSelector.onFileLoaded(hasState)

      // Apply default zoom only if there's no saved state
      if (!hasState) {
        withContext(Dispatchers.Main) {
          val zoomPreference = playerPreferences.defaultVideoZoom.get()
          MPVLib.setPropertyDouble("video-zoom", zoomPreference.toDouble())
          viewModel.setVideoZoom(zoomPreference)
        }
      }
    }

    // Save to recently played when video actually loads and plays
    GlobalScope.launch(Dispatchers.IO) {
      if (playlist.isNotEmpty()) {
        // For playlist items, save using the current URI
        // All items are loaded, so playlistIndex is the direct index
        if (playlistIndex >= 0 && playlistIndex < playlist.size) {
          saveRecentlyPlayedForUri(playlist[playlistIndex], fileName)
        } else {
          Log.w(TAG, "Cannot save recently played: invalid playlist index $playlistIndex (playlist size: ${playlist.size})")
        }
      } else {
        // For non-playlist videos, use the original saveRecentlyPlayed
        saveRecentlyPlayed()
      }
    }

    // Only set orientation immediately if NOT in Video mode
    // For Video mode, wait for video-params/aspect to become available
    if (playerPreferences.orientation.get() != PlayerOrientation.Video) {
      setOrientation()
    } else {
      // For Video mode, try to set orientation after a short delay to ensure
      // video dimensions are available
      lifecycleScope.launch {
        kotlinx.coroutines.delay(100)
        if (mpvInitialized && !player.isExiting && !isFinishing) {
          val aspect = player.getVideoOutAspect()
          Log.d(TAG, "handleFileLoaded - Video mode, aspect after delay: $aspect")
          if (aspect != null && aspect > 0) {
            setOrientation()
          }
        }
      }
    }

    applySubtitlePreferences()
    viewModel.changeVideoAspect(playerPreferences.videoAspect.get(), showUpdate = false)
    viewModel.restoreCustomAspectRatio()

    // Don't force media-title for m3u/m3u8 streams - let MPV provide it
    if (!isCurrentStreamM3U()) {
      MPVLib.setPropertyString("force-media-title", fileName)
      viewModel.setMediaTitle(fileName)
    }

    viewModel.unpause()

    if (subtitlesPreferences.autoloadMatchingSubtitles.get()) {
      lifecycleScope.launch {
        // For network files played via proxy (SMB/WebDAV/FTP), use the original network file path
        val networkFilePath = intent.getStringExtra("network_file_path")
        val networkConnectionId = intent.getLongExtra("network_connection_id", -1L)

        if (networkFilePath != null && networkConnectionId != -1L) {
          // Pass network file path and connection ID for subtitle discovery
          SubtitleOps.autoloadSubtitles(
            videoFilePath = networkFilePath,
            videoFileName = fileName,
            networkConnectionId = networkConnectionId,
          )
        } else {
          // Regular file or direct network stream
          val filePath = parsePathFromIntent(intent)
          if (filePath != null) {
            SubtitleOps.autoloadSubtitles(
              videoFilePath = filePath,
              videoFileName = fileName,
            )
          }
        }
      }
    }

    updateMediaSessionMetadata(
      title = fileName,
      durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L,
    )
    updateMediaSessionPlaybackState(isPlaying = true)

    // Asynchronously fetch better filename from HTTP headers for network streams
    fetchNetworkStreamTitle()
  }

  /**
   * Fetches a better title from HTTP headers for network streams asynchronously.
   * Updates the title in UI, MPV, and media session if a better name is found.
   */
  private fun fetchNetworkStreamTitle() {
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        val uri = extractUriFromIntent(intent)
        if (uri == null || !HttpUtils.isNetworkStream(uri)) {
          return@launch
        }

        // Skip fetching for m3u/m3u8 streams - let MPV provide the title
        if (isCurrentStreamM3U()) {
          Log.d(TAG, "Skipping title fetch for m3u/m3u8 stream: $uri")
          return@launch
        }

        // Skip fetching if title was provided in intent extras (e.g. from Jellyfin or other external launchers)
        // This prevents overwriting the correct title with a generic filename from the URL (like "stream")
        if (intent.hasExtra("title") || intent.hasExtra("filename")) {
          Log.d(TAG, "Skipping title fetch because title was explicitly provided in intent: $fileName")
          return@launch
        }

        // Skip fetching for local proxy URLs (SMB/WebDAV/FTP files)
        // These already have correct filename from intent extras
        val host = uri.host?.lowercase()
        if (host == "127.0.0.1" || host == "localhost" || host == "0.0.0.0") {
          Log.d(TAG, "Skipping title fetch for local proxy URL: $uri")
          return@launch
        }

        val url = uri.toString()
        Log.d(TAG, "Fetching title from network stream: $url")

        val betterFilename = HttpUtils.extractFilenameFromUrl(url)
        if (betterFilename != null && betterFilename.isNotBlank() &&
          betterFilename != fileName &&
          betterFilename != uri.host &&
          betterFilename != "Network Stream"
        ) {

          Log.d(TAG, "Found better filename from HTTP headers: $betterFilename")

          // Update fileName
          fileName = betterFilename

          // DO NOT update mediaIdentifier - keep the original identifier for playback state consistency
          // The URI hash in mediaIdentifier ensures position is saved/loaded correctly even if filename changes

          // Update MPV title
          withContext(Dispatchers.Main) {
            MPVLib.setPropertyString("force-media-title", fileName)
            viewModel.setMediaTitle(fileName)

            // Update media session
            val durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L
            updateMediaSessionMetadata(
              title = fileName,
              durationMs = durationMs,
            )

            // Update background service if connected
            if (serviceBound && mediaPlaybackService != null) {
              val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""
              val thumbnail = runCatching { MPVLib.grabThumbnail(1080) }.getOrNull()
              mediaPlaybackService?.setMediaInfo(title = fileName, artist = artist, thumbnail = thumbnail)
            }
          }

          // Update recently played with the parsed video title, duration, and file size
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

          // Get duration and file size from MPV
          val updatedDuration = runCatching {
            (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
          }.getOrDefault(0L)

          val updatedFileSize = runCatching {
            // Try multiple properties to get file size
            MPVLib.getPropertyDouble("file-size")?.toLong()
              ?: MPVLib.getPropertyDouble("stream-end")?.toLong()
              ?: 0L
          }.getOrDefault(0L)

          // Get video resolution from MPV
          val updatedWidth = runCatching {
            MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
          }.getOrDefault(0)

          val updatedHeight = runCatching {
            MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
          }.getOrDefault(0)

          // Update metadata without thumbnail
          runCatching {
            RecentlyPlayedOps.updateVideoMetadata(
              filePath,
              fileName,
              updatedDuration,
              updatedFileSize,
              updatedWidth,
              updatedHeight,
            )
            Log.d(
              TAG,
              "Updated recently played metadata: $fileName (duration: ${updatedDuration}ms, size: ${updatedFileSize}B, resolution: ${updatedWidth}x${updatedHeight}) for $filePath",
            )
          }.onFailure { e ->
            Log.e(TAG, "Error updating video metadata in recently played", e)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching network stream title", e)
      }
    }
  }

  /**
   * Applies all saved subtitle preferences when a file is loaded.
   * This ensures subtitle customizations (font, colors, position, etc.) persist across videos.
   */
  private fun applySubtitlePreferences() {
    // Typography settings
    MPVLib.setPropertyString("sub-font", subtitlesPreferences.font.get())
    MPVLib.setPropertyString("secondary-sub-font", subtitlesPreferences.font.get())
    MPVLib.setPropertyInt("sub-font-size", subtitlesPreferences.fontSize.get())
    MPVLib.setPropertyBoolean("sub-bold", subtitlesPreferences.bold.get())
    MPVLib.setPropertyBoolean("sub-italic", subtitlesPreferences.italic.get())
    MPVLib.setPropertyString("sub-justify", subtitlesPreferences.justification.get().value)
    MPVLib.setPropertyString("sub-border-style", subtitlesPreferences.borderStyle.get().value)
    MPVLib.setPropertyInt("sub-outline-size", subtitlesPreferences.borderSize.get())
    MPVLib.setPropertyInt("sub-shadow-offset", subtitlesPreferences.shadowOffset.get())

    // Color settings
    MPVLib.setPropertyString("sub-color", subtitlesPreferences.textColor.get().toColorHexString())
    MPVLib.setPropertyString("sub-border-color", subtitlesPreferences.borderColor.get().toColorHexString())
    MPVLib.setPropertyString("sub-back-color", subtitlesPreferences.backgroundColor.get().toColorHexString())

    // Miscellaneous settings
    val overrideAssSubs = subtitlesPreferences.overrideAssSubs.get()
    MPVLib.setPropertyString("sub-ass-override", if (overrideAssSubs) "force" else "scale")
    MPVLib.setPropertyString("secondary-sub-ass-override", if (overrideAssSubs) "force" else "scale")

    val scaleByWindow = subtitlesPreferences.scaleByWindow.get()
    val scaleValue = if (scaleByWindow) "yes" else "no"
    MPVLib.setPropertyString("sub-scale-by-window", scaleValue)
    MPVLib.setPropertyString("sub-use-margins", scaleValue)

    MPVLib.setPropertyFloat("sub-scale", subtitlesPreferences.subScale.get())
    MPVLib.setPropertyInt("sub-pos", subtitlesPreferences.subPos.get())

    Log.d(TAG, "Applied subtitle preferences")
  }

  /**
   * Helper extension function to convert Int color to hex string for MPV
   */
  @OptIn(ExperimentalStdlibApi::class)
  private fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

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

    // Cancel any previous pending save operation
    savePlaybackStateJob?.cancel()

    // Launch new save job and track it
    savePlaybackStateJob = lifecycleScope.launch(Dispatchers.IO) {
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
            videoZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f,
            sid = player.sid,
            secondarySid = player.secondarySid,
            subDelay = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS).toInt(),
            subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: DEFAULT_SUB_SPEED,
            aid = player.aid,
            audioDelay =
              (
                (MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * MILLISECONDS_TO_SECONDS
                ).toInt(),
            timeRemaining = timeRemaining,
            externalSubtitles = viewModel.externalSubtitles.joinToString("|"),
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
   * @return true if saved state was found and applied, false otherwise
   */
  private suspend fun loadVideoPlaybackState(mediaTitle: String): Boolean {
    if (mediaIdentifier.isBlank()) return false

    return runCatching {
      val state = playbackStateRepository.getVideoDataByTitle(mediaIdentifier)

      applyPlaybackState(state)
      applyDefaultSettings(state)

      state != null
    }.onFailure { e ->
      Log.e(TAG, "Error loading playback state", e)
    }.getOrDefault(false)
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
    val audioDelay = state.audioDelay / DELAY_DIVISOR

    // Restore external subtitles first
    if (state.externalSubtitles.isNotBlank()) {
      val externalSubUris = state.externalSubtitles.split("|").filter { it.isNotBlank() }
      Log.d(TAG, "Restoring ${externalSubUris.size} external subtitle(s)")

      for (subUri in externalSubUris) {
        runCatching {
          MPVLib.command("sub-add", subUri, "cached")
          Log.d(TAG, "Restored external subtitle: $subUri")
        }.onFailure { e ->
          Log.e(TAG, "Failed to restore external subtitle: $subUri", e)
        }
      }

      // Update ViewModel's tracked list
      viewModel.setExternalSubtitles(externalSubUris)
    }

    // Always restore subtitle and audio tracks from saved state
    // User's manual selection has highest priority
    if (state.sid > 0) {
      player.sid = state.sid
      Log.d(TAG, "Restored primary subtitle track: ${state.sid} (user selection)")
    }

    if (state.secondarySid > 0) {
      player.secondarySid = state.secondarySid
      Log.d(TAG, "Restored secondary subtitle track: ${state.secondarySid} (user selection)")
    }

    if (state.aid > 0) {
      player.aid = state.aid
      Log.d(TAG, "Restored audio track: ${state.aid} (user selection)")
    }

    MPVLib.setPropertyDouble("sub-delay", subDelay)
    MPVLib.setPropertyDouble("speed", state.playbackSpeed)
    MPVLib.setPropertyDouble("audio-delay", audioDelay)
    MPVLib.setPropertyDouble("sub-speed", state.subSpeed)

    // Restore video zoom from saved state
    MPVLib.setPropertyDouble("video-zoom", state.videoZoom.toDouble())
    viewModel.setVideoZoom(state.videoZoom)

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

      // Get parsed video title from MPV
      val videoTitle = runCatching {
        MPVLib.getPropertyString("media-title")
      }.getOrNull()?.takeIf { it.isNotBlank() && it != fileName }

      // Get duration and file size from MPV
      val duration = runCatching {
        (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
      }.getOrDefault(0L)

      val fileSize = runCatching {
        // Try multiple properties to get file size
        MPVLib.getPropertyDouble("file-size")?.toLong()
          ?: MPVLib.getPropertyDouble("stream-end")?.toLong()
          ?: 0L
      }.getOrDefault(0L)

      // Get video resolution from MPV
      val width = runCatching {
        MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
      }.getOrDefault(0)

      val height = runCatching {
        MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
      }.getOrDefault(0)

      RecentlyPlayedOps.addRecentlyPlayed(
        filePath = filePath,
        fileName = fileName,
        videoTitle = videoTitle,
        duration = duration,
        fileSize = fileSize,
        width = width,
        height = height,
        launchSource = launchSource,
      )

      Log.d(TAG, "Saved recently played: $filePath")
      Log.d(TAG, "  - fileName: $fileName")
      Log.d(TAG, "  - videoTitle: $videoTitle")
      Log.d(TAG, "  - duration: ${duration}ms")
      Log.d(TAG, "  - size: ${fileSize}B")
      Log.d(TAG, "  - resolution: ${width}x${height}")
      Log.d(TAG, "  - source: $launchSource")
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

    // Check if this intent has playlist information
    val hasPlaylistExtras = intent.hasExtra("playlist_id") ||
      intent.hasExtra("playlist")

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableArrayListExtra("playlist") ?: emptyList()
    }

    // Only update playlist state if we have new playlist information
    // This prevents losing the playlist when coming back from notification/PiP
    if (hasPlaylistExtras || playlistFromIntent.isNotEmpty()) {
      val newPlaylistId = intent.getIntExtra("playlist_id", -1).takeIf { it != -1 }
      playlistId = newPlaylistId
      playlistIndex = intent.getIntExtra("playlist_index", 0)
      playlistWindowOffset = 0
      playlistTotalCount = -1
      playlist = playlistFromIntent
    }

    // If playlist is empty but playlist_id is provided, load from database
    if (playlist.isEmpty() && playlistId != null) {
      lifecycleScope.launch(Dispatchers.IO) {
        val pid = playlistId ?: return@launch
        try {
          val totalCount = playlistRepository.getPlaylistItemCount(pid)
          val items = playlistRepository.getPlaylistItemsAsUris(pid)
          withContext(Dispatchers.Main) {
            playlist = items
            playlistTotalCount = totalCount
            Log.d(TAG, "onNewIntent: Loaded ${items.size} items from playlist $pid")
          }
        } catch (e: Exception) {
          Log.e(TAG, "onNewIntent: Failed to load playlist from database", e)
        }
      }
    }

    // Auto-generate playlist from folder if playlist mode is enabled and no playlist_id
    if (playlist.isEmpty() && playlistId == null && playerPreferences.playlistMode.get()) {
      val path = parsePathFromIntent(intent)
      if (path != null) {
        generatePlaylistFromFolder(path)
      }
    }

    // Extract the new fileName before loading the file
    fileName = getFileName(intent)
    if (fileName.isBlank()) {
      fileName = intent.data?.lastPathSegment ?: "Unknown Video"
    }
    mediaIdentifier = getMediaIdentifier(intent, fileName)

    // Set HTTP headers (including referer) BEFORE loading the new file
    setHttpHeadersFromExtras(intent.extras)

    // Load the new file
    getPlayableUri(intent)?.let { uri ->
      // Avoid blocking UI thread while mpv opens network streams (e.g., HLS).
      lifecycleScope.launch(Dispatchers.Default) {
        MPVLib.command("loadfile", uri)
      }
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
   *
   * IMPORTANT: Preferences are the single source of truth for orientation.
   * This method applies the preference value when videos load.
   * The rotation button temporarily overrides this without changing preferences.
   *
   * For "Video" orientation mode, this will wait for video-params/aspect to update
   * to the correct orientation, starting with landscape as fallback.
   */
  private fun setOrientation() {
    val orientationPref = playerPreferences.orientation.get()

    requestedOrientation =
      when (orientationPref) {
        PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientation.Video -> {
          // For video orientation, check if aspect is available
          val aspect = runCatching { player.getVideoOutAspect() }.getOrNull()
          Log.d(TAG, "setOrientation - Video mode: aspect=$aspect")
          if (aspect == null || aspect <= 0.0) {
            // Aspect not available yet - wait for video-params/aspect update
            Log.d(TAG, "setOrientation - Aspect not available, defaulting to landscape")
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
          } else {
            // Aspect available - set correct orientation now
            val orientation = if (aspect > 1.0) {
              Log.d(TAG, "setOrientation - Aspect $aspect > 1.0, setting landscape")
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
              Log.d(TAG, "setOrientation - Aspect $aspect <= 1.0, setting portrait")
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            orientation
          }
        }
        PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
  fun hasNext(): Boolean {
    if (playlist.isEmpty()) return false

    // With repeat ALL, there's always a "next" (loops back to beginning)
    if (viewModel.shouldRepeatPlaylist()) return true

    // Use total count if we're doing windowed loading, otherwise use playlist size
    val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size

    return if (viewModel.shuffleEnabled.value) {
      shuffledPosition < shuffledIndices.size - 1
    } else {
      playlistIndex < effectiveSize - 1
    }
  }

  /**
   * Check if there's a previous video in the playlist
   */
  fun hasPrevious(): Boolean {
    if (playlist.isEmpty()) return false

    // With repeat ALL, there's always a "previous" (loops back to end)
    if (viewModel.shouldRepeatPlaylist()) return true

    return if (viewModel.shuffleEnabled.value) {
      shuffledPosition > 0
    } else {
      playlistIndex > 0
    }
  }

  /**
   * Generate shuffled indices for the playlist
   */
  private fun generateShuffledIndices() {
    if (playlist.isEmpty()) return

    // Create a list of all indices except the current one
    val indices = playlist.indices.filter { it != playlistIndex }.toMutableList()
    indices.shuffle()

    // Put current index at the beginning
    shuffledIndices = listOf(playlistIndex) + indices
    shuffledPosition = 0
  }

  /**
   * Called when shuffle is toggled on/off
   */
  fun onShuffleToggled(enabled: Boolean) {
    if (enabled && playlist.isNotEmpty()) {
      generateShuffledIndices()
    } else {
      shuffledIndices = emptyList()
      shuffledPosition = 0
    }
  }

  /**
   * Play the next video in the playlist
   */
  fun playNext() {
    if (playlist.isEmpty()) return

    // Use total count if we're doing windowed loading, otherwise use playlist size
    val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size

    if (viewModel.shuffleEnabled.value) {
      // Initialize shuffle if not done yet
      if (shuffledIndices.isEmpty()) {
        generateShuffledIndices()
      }

      // Move to next position
      if (shuffledPosition < shuffledIndices.size - 1) {
        shuffledPosition++
        playlistIndex = shuffledIndices[shuffledPosition]
        loadPlaylistItem(playlistIndex)
      } else if (viewModel.shouldRepeatPlaylist()) {
        // At end of shuffled playlist with repeat ALL: regenerate and restart
        generateShuffledIndices()
        shuffledPosition = 0
        playlistIndex = shuffledIndices[0]
        loadPlaylistItem(playlistIndex)
      }
    } else {
      // Normal sequential playback
      if (playlistIndex < effectiveSize - 1) {
        playlistIndex++
        loadPlaylistItem(playlistIndex)
      } else if (viewModel.shouldRepeatPlaylist()) {
        // At end of playlist with repeat ALL: restart from beginning
        playlistIndex = 0
        loadPlaylistItem(0)
      }
    }
  }

  /**
   * Play the previous video in the playlist
   */
  fun playPrevious() {
    if (playlist.isEmpty()) return

    // Use total count if we're doing windowed loading, otherwise use playlist size
    val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size

    if (viewModel.shuffleEnabled.value) {
      // Initialize shuffle if not done yet
      if (shuffledIndices.isEmpty()) {
        generateShuffledIndices()
      }

      // Move to previous position
      if (shuffledPosition > 0) {
        shuffledPosition--
        playlistIndex = shuffledIndices[shuffledPosition]
        loadPlaylistItem(playlistIndex)
      } else if (viewModel.shouldRepeatPlaylist()) {
        // At beginning of shuffled playlist with repeat ALL: go to end
        shuffledPosition = shuffledIndices.size - 1
        playlistIndex = shuffledIndices[shuffledPosition]
        loadPlaylistItem(playlistIndex)
      }
    } else {
      // Normal sequential playback
      if (playlistIndex > 0) {
        playlistIndex--
        loadPlaylistItem(playlistIndex)
      } else if (viewModel.shouldRepeatPlaylist()) {
        // At beginning of playlist with repeat ALL: go to last item
        playlistIndex = effectiveSize - 1
        loadPlaylistItem(playlistIndex)
      }
    }
  }

  /**
   * Load a playlist item by index
   */
  private fun loadPlaylistItem(index: Int) {
    // All items are loaded - just validate index and load directly
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }
    loadPlaylistItemInternal(index)
  }

  /**
   * Internal method to load a playlist item
   */
  private fun loadPlaylistItemInternal(index: Int) {
    if (index < 0 || index >= playlist.size) {
      Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
      return
    }

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

    // Set HTTP headers (including referer) for network streams
    setHttpHeadersForUri(uri)

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
    // Avoid blocking UI thread while mpv opens network streams (e.g., HLS).
    lifecycleScope.launch(Dispatchers.Default) {
      MPVLib.command("loadfile", playableUri)
    }

    // Update media title (this will trigger UI update)
    // Don't force media-title for m3u/m3u8 streams - let MPV provide it
    val isM3U = uri.toString().lowercase().contains(".m3u8") || uri.toString().lowercase().contains(".m3u")
    if (!isM3U) {
      MPVLib.setPropertyString("force-media-title", fileName)
      viewModel.setMediaTitle(fileName)
    }

    // Update media session metadata
    lifecycleScope.launch {
      kotlinx.coroutines.delay(100) // Wait for MPV to load the file
      val durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000))?.toLong() ?: 0L
      updateMediaSessionMetadata(
        title = fileName,
        durationMs = durationMs,
      )
      // Refresh playlist items to update the currently playing indicator
      viewModel.refreshPlaylistItems()
    }
  }

  /**
   * Get file name from URI (used for playlist items)
   */
  private fun getFileNameFromUri(uri: Uri): String {
    getDisplayNameFromUri(uri)?.let { return it }
    return extractFileNameFromUri(uri)
  }

  /**
   * Get the current video title for controls display.
   * Used as a fallback when MPV hasn't set the media-title property yet.
   * For m3u/m3u8 streams, returns the raw media-title from MPV instead of parsing.
   */
  fun getTitleForControls(): String {
    // For m3u/m3u8 streams, use MPV's raw media-title directly
    if (isCurrentStreamM3U()) {
      val rawTitle = MPVLib.getPropertyString("media-title")
      if (!rawTitle.isNullOrBlank()) {
        return rawTitle
      }
    }
    return fileName
  }

  /**
   * Check if the currently playing media is an m3u or m3u8 stream.
   * Checks both the intent URI and the current playlist item if playing from a playlist.
   */
  private fun isCurrentStreamM3U(): Boolean {
    // First check the intent URI
    val uri = extractUriFromIntent(intent)
    if (uri != null && isUriM3U(uri)) {
      return true
    }

    // Also check the current playlist item if playing from a playlist
    if (playlist.isNotEmpty() && playlistIndex >= 0 && playlistIndex < playlist.size) {
      return isUriM3U(playlist[playlistIndex])
    }

    return false
  }

  /**
   * Check if a specific URI is an m3u or m3u8 file/stream.
   */
  private fun isUriM3U(uri: Uri): Boolean {
    val lowerUrl = uri.toString().lowercase()
    return lowerUrl.contains(".m3u8") || lowerUrl.contains(".m3u") ||
      lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".m3u")
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

      // Get parsed video title from MPV
      val videoTitle = runCatching {
        MPVLib.getPropertyString("media-title")
      }.getOrNull()?.takeIf { it.isNotBlank() && it != name }

      // Get duration and file size from MPV
      val duration = runCatching {
        (MPVLib.getPropertyDouble("duration") ?: 0.0).times(1000).toLong()
      }.getOrDefault(0L)

      val fileSize = runCatching {
        // Try multiple properties to get file size
        MPVLib.getPropertyDouble("file-size")?.toLong()
          ?: MPVLib.getPropertyDouble("stream-end")?.toLong()
          ?: 0L
      }.getOrDefault(0L)

      // Get video resolution from MPV
      val width = runCatching {
        MPVLib.getPropertyInt("width") ?: MPVLib.getPropertyInt("video-params/w") ?: 0
      }.getOrDefault(0)

      val height = runCatching {
        MPVLib.getPropertyInt("height") ?: MPVLib.getPropertyInt("video-params/h") ?: 0
      }.getOrDefault(0)

      RecentlyPlayedOps.addRecentlyPlayed(
        filePath = filePath,
        fileName = name,
        videoTitle = videoTitle,
        duration = duration,
        fileSize = fileSize,
        width = width,
        height = height,
        launchSource = "playlist",
        playlistId = playlistId,
      )

      Log.d(TAG, "Saved recently played (playlist): $filePath")
      Log.d(TAG, "  - fileName: $name")
      Log.d(TAG, "  - videoTitle: $videoTitle")
      Log.d(TAG, "  - duration: ${duration}ms")
      Log.d(TAG, "  - size: ${fileSize}B")
      Log.d(TAG, "  - resolution: ${width}x${height}")
      Log.d(TAG, "  - playlistId: $playlistId")
    }.onFailure { e ->
      Log.e(TAG, "Error saving recently played for playlist item", e)
    }
  }

  /**
   * Generate a unique identifier for this media for playback state/history.
   *
   * For local/offline files, uses fileName (display name or path).
   * For network streams via proxy (SMB/WebDAV/FTP), uses the stable network file path from intent extras.
   * For other network URIs (http/https/rtmp/etc.), uses a hash of the URI string to distinguish different streams.
   */
  private fun getMediaIdentifier(intent: Intent, fileName: String): String {
    // Check if this is a network file played via proxy (SMB/WebDAV/FTP)
    // Use the stable network file path instead of the temporary proxy URL
    val networkFilePath = intent.getStringExtra("network_file_path")
    val networkConnectionId = intent.getLongExtra("network_connection_id", -1L)

    if (networkFilePath != null && networkConnectionId != -1L) {
      // For network files via proxy: use connection ID + file path for stable identifier
      val identifier = "network_${networkConnectionId}_${networkFilePath.hashCode()}"
      Log.d(
        TAG,
        "Using network file identifier: $identifier (connection: $networkConnectionId, path: $networkFilePath)",
      )
      return identifier
    }

    val uri = extractUriFromIntent(intent)
    return if (uri != null && (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms")) {
      // For remote protocols: hash the URI so position is per-episode or per-stream.
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

  private fun generatePlaylistFromFolder(currentPath: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val currentFile = File(currentPath)
        if (!currentFile.exists()) return@runCatching

        val parentFolder = currentFile.parentFile ?: return@runCatching

        val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "mpg", "mpeg", "ts")

        val files = parentFolder.listFiles { file ->
          file.isFile && file.extension.lowercase() in videoExtensions
        } ?: return@runCatching

        val launchSource = intent.getStringExtra("launch_source") ?: ""
        val siblingFiles = if (launchSource == "video_list") {
          val videoSortType = browserPreferences.videoSortType.get()
          val videoSortOrder = browserPreferences.videoSortOrder.get()
          val bucketId = parentFolder.absolutePath.replace("\\", "/")
          val videosInFolder = app.marlboroadvance.mpvex.repository.MediaFileRepository.getVideosForBuckets(context, setOf(bucketId))
          val sortedVideos = app.marlboroadvance.mpvex.utils.sort.SortUtils.sortVideos(videosInFolder, videoSortType, videoSortOrder)
          sortedVideos.mapNotNull { video -> files.find { it.absolutePath == video.path } }
        } else {
          files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        if (siblingFiles.size <= 1) return@runCatching

        val newPlaylist = siblingFiles.map { it.toUri() }

        val newIndex = siblingFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }

        if (newIndex != -1) {
          withContext(Dispatchers.Main) {
            playlist = newPlaylist
            playlistIndex = newIndex
            Log.d(TAG, "Auto-playlist generated: ${playlist.size} videos")
          }
        }
      }.onFailure { e ->
        Log.e(TAG, "Failed to auto-generate playlist", e)
      }
    }
  }

  /**
   * Check if the current playlist is an M3U playlist (sourced from database).
   */
  fun isCurrentPlaylistM3U(): Boolean = isM3uPlaylist


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
