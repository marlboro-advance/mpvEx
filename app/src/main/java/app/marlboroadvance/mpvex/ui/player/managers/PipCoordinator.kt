package app.marlboroadvance.mpvex.ui.player.managers

import android.content.res.Configuration
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.MPVPipHelper
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import kotlinx.coroutines.flow.update

/**
 * Coordinates Picture-in-Picture mode with UI state management.
 */
class PipCoordinator(
  private val pipHelper: MPVPipHelper,
  private val viewModel: PlayerViewModel,
  private val systemUIManager: SystemUIManager,
  private val playerPreferences: PlayerPreferences,
) {

  /**
   * Checks if entering PiP mode is appropriate and does so if needed.
   */
  fun handleBackPress(): Boolean {
    val shouldEnterPip = pipHelper.isPipSupported &&
      viewModel.paused != true &&
      playerPreferences.automaticallyEnterPip.get()

    return if (shouldEnterPip &&
      viewModel.sheetShown.value == Sheets.None &&
      viewModel.panelShown.value == Panels.None
    ) {
      pipHelper.enterPipMode()
      true
    } else {
      false
    }
  }

  /**
   * Handles configuration changes related to PiP.
   */
  fun handleConfigurationChange(isInPictureInPictureMode: Boolean) {
    if (!isInPictureInPictureMode) {
      viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
    } else {
      viewModel.hideControls()
    }
  }

  /**
   * Handles Picture-in-Picture mode changes.
   */
  fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
    controlsAlpha: (Float) -> Unit,
  ) {
    pipHelper.onPictureInPictureModeChanged(isInPictureInPictureMode)

    controlsAlpha(if (isInPictureInPictureMode) 0f else 1f)

    if (isInPictureInPictureMode) {
      enterPipUIMode()
    } else {
      exitPipUIMode()
    }
  }

  /**
   * Handles user leaving the app.
   */
  fun onUserLeaveHint() {
    pipHelper.onUserLeaveHint()
  }

  /**
   * Updates PiP parameters when video aspect changes.
   */
  fun updatePictureInPictureParams() {
    if (pipHelper.isPipSupported) {
      pipHelper.updatePictureInPictureParams()
    }
  }

  /**
   * Enters PiP mode while hiding overlay.
   */
  fun enterPipModeHidingOverlay(controlsAlpha: (Float) -> Unit) {
    systemUIManager.enterPipUIMode()
    controlsAlpha(0f)
    pipHelper.enterPipMode()
  }

  /**
   * Called when activity stops.
   */
  fun onStop() {
    pipHelper.onStop()
  }

  /**
   * Checks if PiP is supported.
   */
  val isPipSupported: Boolean
    get() = pipHelper.isPipSupported

  private fun enterPipUIMode() {
    systemUIManager.enterPipUIMode()
    hideAllUIElements()
  }

  private fun exitPipUIMode() {
    systemUIManager.exitPipUIMode()
  }

  private fun hideAllUIElements() {
    viewModel.hideControls()
    viewModel.hideSeekBar()
    viewModel.isBrightnessSliderShown.update { false }
    viewModel.isVolumeSliderShown.update { false }
    viewModel.sheetShown.update { Sheets.None }
    viewModel.panelShown.update { Panels.None }
  }
}
