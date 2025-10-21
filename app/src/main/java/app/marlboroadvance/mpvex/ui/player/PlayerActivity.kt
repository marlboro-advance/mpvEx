package app.marlboroadvance.mpvex.ui.player

import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.marlboroadvance.mpvex.databinding.PlayerLayoutBinding
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.controls.PlayerControls
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants
import app.marlboroadvance.mpvex.ui.player.managers.PlayerManagerFactory
import app.marlboroadvance.mpvex.ui.player.managers.MPVConfigurationManager
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Main player activity that handles video playback using MPV library.
 * Delegates most responsibilities to specialized manager classes.
 */
class PlayerActivity : AppCompatActivity() {

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
  val windowInsetsController by lazy {
    WindowCompat.getInsetsController(window, window.decorView)
  }
  val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

  // Preferences
  private val playerPreferences: PlayerPreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val fileManager: FileManager by inject()

  // PiP Helper
  private lateinit var pipHelper: MPVPipHelper

  // Manager Factory and Managers
  private lateinit var managerFactory: PlayerManagerFactory

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    // CRITICAL: Initialize MPV library BEFORE creating ViewModel
    // The ViewModel accesses MPV properties in its constructor
    initializeMPVLibrary()

    setupPipHelper()
    setupManagers()
    setupBackPressHandler()
    setupPlayerControls()

    // Setup audio configuration
    managerFactory.mpvConfigurationManager.setupAudio()

    // Set event dispatcher for observer
    playerObserver.setEventDispatcher(managerFactory.mpvEventDispatcher)

    // Start playback (which will request audio focus first, stopping other apps)
    startPlaybackIfReady()

    managerFactory.orientationManager.setOrientation()
  }

  /**
   * Initializes the MPV library before ViewModel creation.
   * This is critical because PlayerViewModel accesses MPV properties in its constructor.
   * We do the bare minimum initialization here, and let the manager handle the rest.
   */
  private fun initializeMPVLibrary() {
    // Create a temporary configuration manager just for early initialization
    // This ensures all assets, scripts, and configs are copied before MPV starts
    val tempConfigManager = MPVConfigurationManager(
      context = this,
      player = player,
      playerObserver = playerObserver,
      audioPreferences = audioPreferences,
      subtitlesPreferences = subtitlesPreferences,
      advancedPreferences = advancedPreferences,
      fileManager = fileManager,
      scope = lifecycleScope,
    )

    // Copy all assets (scripts, fonts, configs)
    tempConfigManager.copyAssetsOnly()

    // Initialize the player and add observer
    tempConfigManager.initializePlayerOnly(filesDir.path, cacheDir.path)
  }

  private fun setupManagers() {
    managerFactory = PlayerManagerFactory(
      context = this,
      player = player,
      playerObserver = playerObserver,
      viewModel = viewModel,
      window = window,
      rootView = binding.root,
      windowInsetsController = windowInsetsController,
      audioManager = audioManager,
      pipHelper = pipHelper,
      scope = lifecycleScope,
      onPausePlayback = ::pausePlayback,
      onFinishActivity = ::finishAndRemoveTask,
      playerPreferences = playerPreferences,
      audioPreferences = audioPreferences,
      subtitlesPreferences = subtitlesPreferences,
      advancedPreferences = advancedPreferences,
      playbackStateRepository = playbackStateRepository,
      fileManager = fileManager,
    )
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          val handled = managerFactory.pipCoordinator.handleBackPress()
          if (!handled) {
            finish()
          }
        }
      },
    )
  }

  private fun setupPlayerControls() {
    binding.controls.setContent {
      MpvexTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = ::finish,
          modifier = Modifier,
        )
      }
    }
  }

  private fun setupPipHelper() {
    pipHelper = MPVPipHelper(
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

  private fun pausePlayback() {
    viewModel.pause()
    managerFactory.systemUIManager.allowScreenOff()
    // Audio focus will be abandoned automatically when pause state changes
  }

  override fun onDestroy() {
    Log.d(PlayerConstants.TAG, "Exiting PlayerActivity")
    managerFactory.audioFocusManager.cleanup()
    runCatching {
      player.isExiting = true
      managerFactory.lifecycleManager.onDestroy(isFinishing)
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error during onDestroy", e)
    }
    super.onDestroy()
  }

  override fun onPause() {
    runCatching {
      val isInPip = isInPictureInPictureMode
      if (!isInPip) {
        pausePlayback()
      }
      managerFactory.lifecycleManager.onPause(
        fileName = managerFactory.mpvEventDispatcher.getFileName(),
        currentPosition = viewModel.pos,
        duration = viewModel.duration,
        isInPictureInPictureMode = isInPip,
        isFinishing = isFinishing,
      )
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error during onPause", e)
    }
    super.onPause()
  }

  override fun finish() {
    runCatching {
      managerFactory.lifecycleManager.onFinish()
      setReturnIntent()
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error during finish", e)
    }

    super.finish()
  }

  override fun onStop() {
    // Unregister noisy receiver and abandon audio focus
    managerFactory.audioFocusManager.unregisterNoisyReceiver()
    managerFactory.audioFocusManager.abandonAudioFocus()
    runCatching {
      managerFactory.lifecycleManager.onStop(
        fileName = managerFactory.mpvEventDispatcher.getFileName(),
        currentPosition = viewModel.pos,
        duration = viewModel.duration,
      )
      viewModel.pause()
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error during onStop", e)
    }
    super.onStop()
  }

  override fun onStart() {
    super.onStart()
    // Register noisy receiver for headphone disconnect events
    managerFactory.audioFocusManager.registerNoisyReceiver()
    runCatching {
      managerFactory.lifecycleManager.onStart()
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error during onStart", e)
    }
  }

  override fun onUserLeaveHint() {
    managerFactory.pipCoordinator.onUserLeaveHint()
    super.onUserLeaveHint()
  }

  override fun onResume() {
    super.onResume()
    updateVolume()
  }

  private fun updateVolume() {
    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also { volume ->
        if (volume < viewModel.maxVolume) {
          viewModel.changeMPVVolumeTo(PlayerConstants.MAX_MPV_VOLUME)
        }
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    managerFactory.pipCoordinator.handleConfigurationChange(isInPictureInPictureMode)
  }

  // ==================== MPV Event Observers ====================
  // These are kept for backward compatibility with PlayerObserver
  // but now delegate to MPVEventDispatcher

  internal fun onObserverEvent() {
    if (player.isExiting) return
  }

  internal fun onObserverEvent(property: String, value: Boolean) {
    if (player.isExiting) return
    // Handle pause state changes for audio focus management
    when (property) {
      "pause" -> {
        if (value) {
          // Playback paused - abandon audio focus
          managerFactory.audioFocusManager.abandonAudioFocus()
        } else {
          // Playback resumed - request audio focus (stops other apps)
          managerFactory.audioFocusManager.requestAudioFocus()
        }
      }
    }

    // Let dispatcher handle other events
    managerFactory.mpvEventDispatcher.onObserverEvent(property, value)
  }

  internal fun onObserverEvent(property: String, value: String) {
    if (player.isExiting) return
    // Handled by MPVEventDispatcher
  }

  internal fun onObserverEvent(property: String) {
    if (player.isExiting) return

    when (property) {
      "video-params/aspect" -> {
        managerFactory.pipCoordinator.updatePictureInPictureParams()
      }
    }
  }

  internal fun event(eventId: Int) {
    if (player.isExiting) return

    when (eventId) {
      MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
        // Audio focus is handled in the dispatcher's handleFileLoaded
        managerFactory.mpvEventDispatcher.handleFileLoaded(intent)
      }
    }
  }

  // ==================== Intent and Result Management ====================

  private fun setReturnIntent() {
    Log.d(PlayerConstants.TAG, "Setting return intent")
    val resultIntent = managerFactory.intentHandler.createResultIntent(
      viewModel.pos,
      viewModel.duration,
    )
    setResult(RESULT_OK, resultIntent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Request focus before loading new file (stops other apps)
    managerFactory.audioFocusManager.requestAudioFocus()
    managerFactory.intentHandler.getPlayableUri(intent)?.let { uri ->
      val fileName = managerFactory.intentHandler.getFileName(intent)
      managerFactory.mpvEventDispatcher.setFileName(fileName)

      // Set the title BEFORE loading the file to prevent MPV's derived filename from showing
      MPVLib.setPropertyString("force-media-title", fileName)

      // Just load the file - position will be restored in handleFileLoaded
      player.playFile(uri)
    }
    setIntent(intent)
  }

  // ==================== Picture-in-Picture Management ====================

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    runCatching {
      managerFactory.pipCoordinator.onPictureInPictureModeChanged(
        isInPictureInPictureMode,
        newConfig,
      ) { alpha ->
        binding.controls.alpha = alpha
      }
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error handling PiP mode change", e)
    }
  }

  fun enterPipModeHidingOverlay() {
    runCatching {
      managerFactory.pipCoordinator.enterPipModeHidingOverlay { alpha ->
        binding.controls.alpha = alpha
      }
    }.onFailure { e ->
      Log.e(PlayerConstants.TAG, "Error entering PiP mode with hidden overlay", e)
    }
  }

  val isPipSupported: Boolean
    get() = managerFactory.pipCoordinator.isPipSupported

  // ==================== Key Event Handling ====================

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val handled = managerFactory.keyEventHandler.onKeyDown(keyCode, event)
    return if (handled) {
      if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
        finishAndRemoveTask()
      }
      true
    } else {
      super.onKeyDown(keyCode, event)
    }
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    return if (managerFactory.keyEventHandler.onKeyUp(keyCode, event)) {
      true
    } else {
      super.onKeyUp(keyCode, event)
    }
  }

  private fun startPlaybackIfReady() {
    // Request audio focus first - this will pause other apps
    managerFactory.audioFocusManager.requestAudioFocus()

    // Start playback
    managerFactory.intentHandler.getPlayableUri(intent)?.let { uri ->
      val fileName = managerFactory.intentHandler.getFileName(intent)
      managerFactory.mpvEventDispatcher.setFileName(fileName)

      // Set the title BEFORE loading the file to prevent MPV's derived filename from showing
      MPVLib.setPropertyString("force-media-title", fileName)

      // Just load the file - position will be restored in handleFileLoaded
      player.playFile(uri)
    }
  }

  companion object {
    const val TAG = "mpvex"
  }
}
