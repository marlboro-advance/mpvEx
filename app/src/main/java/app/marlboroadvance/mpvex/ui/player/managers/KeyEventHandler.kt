package app.marlboroadvance.mpvex.ui.player.managers

import android.content.Context
import android.view.KeyEvent
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.utils.device.TVUtils
import kotlinx.coroutines.flow.update

/**
 * Handles keyboard and remote control key events.
 */
class KeyEventHandler(
  private val context: Context,
  private val player: MPVView,
  private val viewModel: PlayerViewModel,
) {

  /**
   * Handles key down events.
   * @return true if the event was handled, false otherwise
   */
  @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
  fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val isTrackSheetOpen = viewModel.sheetShown.value == Sheets.TracksTV
    val isNoSheetOpen = viewModel.sheetShown.value == Sheets.None
    val isNoOverlayOpen = isNoSheetOpen && viewModel.panelShown.value == Panels.None
    val isTV = TVUtils.isAndroidTV(context)

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        if (isTV && isNoOverlayOpen) {
          viewModel.sheetShown.update { Sheets.TracksTV }
          return true
        }
        return false
      }

      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_DPAD_LEFT,
        -> {
        if (isTrackSheetOpen) {
          return false
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
        return false
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
        if (isTrackSheetOpen) {
          return false
        }
        if (isTV && isNoOverlayOpen) {
          viewModel.pauseUnpause()
          return true
        }
        return false
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
        return true // Caller should finish activity
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
        return false
      }
    }
  }

  /**
   * Handles key up events.
   * @return true if the event was handled, false otherwise
   */
  fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    event?.let {
      if (player.onKey(it)) return true
    }
    return false
  }
}
