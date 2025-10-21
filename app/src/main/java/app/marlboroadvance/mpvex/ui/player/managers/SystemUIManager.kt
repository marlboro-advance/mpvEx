package app.marlboroadvance.mpvex.ui.player.managers

import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.TAG

/**
 * Manages system UI visibility, window flags, and display settings.
 */
class SystemUIManager(
  private val window: Window,
  private val rootView: View,
  private val windowInsetsController: WindowInsetsControllerCompat,
  private val playerPreferences: PlayerPreferences,
) {
  private var systemUIRestored = false

  /**
   * Sets up window flags for player mode.
   */
  fun setupWindowFlags() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  }

  /**
   * Sets up system UI for immersive playback mode.
   */
  @Suppress("DEPRECATION")
  fun setupSystemUI() {
    rootView.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LOW_PROFILE

    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    setupDisplayCutout()
  }

  /**
   * Configures display cutout mode based on preferences.
   */
  fun setupDisplayCutout() {
    window.attributes.layoutInDisplayCutoutMode =
      if (playerPreferences.drawOverDisplayCutout.get()) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
      }
  }

  /**
   * Restores system UI to normal state when exiting player.
   */
  fun restoreSystemUI() {
    if (systemUIRestored) return

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

  /**
   * Enters Picture-in-Picture UI mode.
   */
  fun enterPipUIMode() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    WindowCompat.setDecorFitsSystemWindows(window, true)
    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
  }

  /**
   * Exits Picture-in-Picture UI mode.
   */
  fun exitPipUIMode() {
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

    setupDisplayCutout()
  }

  /**
   * Keeps screen on during playback.
   */
  fun keepScreenOn() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  /**
   * Allows screen to turn off.
   */
  fun allowScreenOff() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  /**
   * Gets current brightness setting.
   */
  fun getCurrentBrightness(contentResolver: android.content.ContentResolver): Float {
    return runCatching {
      Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        .normalize(0f, 255f, 0f, 1f)
    }.getOrElse { PlayerConstants.BRIGHTNESS_NOT_SET }
  }

  /**
   * Sets window brightness.
   */
  fun setBrightness(brightness: Float) {
    window.attributes = window.attributes.apply {
      screenBrightness = brightness.coerceIn(0f, 1f)
    }
  }

  /**
   * Checks if system UI has been restored.
   */
  fun isSystemUIRestored(): Boolean = systemUIRestored

  private fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
  }
}
