package app.marlboroadvance.mpvex.ui.player.managers

import android.content.Context
import android.media.AudioManager
import android.view.View
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.ui.player.MPVPipHelper
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.PlayerObserver
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.CoroutineScope

/**
 * Factory for creating all player managers.
 * Centralizes dependency injection and manager creation.
 */
class PlayerManagerFactory(
  private val context: Context,
  private val player: MPVView,
  private val playerObserver: PlayerObserver,
  private val viewModel: PlayerViewModel,
  private val window: Window,
  private val rootView: View,
  private val windowInsetsController: WindowInsetsControllerCompat,
  private val audioManager: AudioManager,
  private val pipHelper: MPVPipHelper,
  private val scope: CoroutineScope,
  private val onPausePlayback: () -> Unit,
  private val onFinishActivity: () -> Unit,
  // Preferences
  private val playerPreferences: PlayerPreferences,
  private val audioPreferences: AudioPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
  private val advancedPreferences: AdvancedPreferences,
  // Repositories
  private val playbackStateRepository: PlaybackStateRepository,
  private val fileManager: FileManager,
) {

  // Lazy initialization of managers
  val audioFocusManager by lazy {
    AudioFocusManager(
      context = context,
      audioManager = audioManager,
      onPausePlayback = onPausePlayback,
    )
  }

  val systemUIManager by lazy {
    SystemUIManager(
      window = window,
      rootView = rootView,
      windowInsetsController = windowInsetsController,
      playerPreferences = playerPreferences,
    )
  }

  val intentHandler by lazy {
    IntentHandler(context = context)
  }

  val playbackStateManager by lazy {
    PlaybackStateManager(
      player = player,
      playbackStateRepository = playbackStateRepository,
      playerPreferences = playerPreferences,
      subtitlesPreferences = subtitlesPreferences,
    )
  }

  val orientationManager by lazy {
    OrientationManager(
      activity = context as android.app.Activity,
      player = player,
      playerPreferences = playerPreferences,
    )
  }

  val mpvConfigurationManager by lazy {
    MPVConfigurationManager(
      context = context,
      player = player,
      playerObserver = playerObserver,
      audioPreferences = audioPreferences,
      subtitlesPreferences = subtitlesPreferences,
      advancedPreferences = advancedPreferences,
      fileManager = fileManager,
      scope = scope,
    )
  }

  val mpvEventDispatcher by lazy {
    MPVEventDispatcher(
      player = player,
      viewModel = viewModel,
      playerPreferences = playerPreferences,
      intentHandler = intentHandler,
      playbackStateManager = playbackStateManager,
      orientationManager = orientationManager,
      systemUIManager = systemUIManager,
      audioFocusManager = audioFocusManager,
      scope = scope,
      onFinishActivity = onFinishActivity,
    )
  }

  val pipCoordinator by lazy {
    PipCoordinator(
      pipHelper = pipHelper,
      viewModel = viewModel,
      systemUIManager = systemUIManager,
      playerPreferences = playerPreferences,
    )
  }

  val keyEventHandler by lazy {
    KeyEventHandler(
      context = context,
      player = player,
      viewModel = viewModel,
    )
  }

  val lifecycleManager by lazy {
    PlayerLifecycleManager(
      audioFocusManager = audioFocusManager,
      mpvConfigurationManager = mpvConfigurationManager,
      systemUIManager = systemUIManager,
      playbackStateManager = playbackStateManager,
      mpvEventDispatcher = mpvEventDispatcher,
      pipCoordinator = pipCoordinator,
      playerPreferences = playerPreferences,
      scope = scope,
    )
  }
}
