package app.marlboroadvance.mpvex.ui.player.managers

import android.app.Activity
import android.content.pm.ActivityInfo
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.MPVView
import app.marlboroadvance.mpvex.ui.player.PlayerOrientation

/**
 * Manages screen orientation based on preferences and video properties.
 */
class OrientationManager(
  private val activity: Activity,
  private val player: MPVView,
  private val playerPreferences: PlayerPreferences,
) {

  /**
   * Sets the screen orientation based on preferences.
   */
  fun setOrientation() {
    activity.requestedOrientation = when (playerPreferences.orientation.get()) {
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
}
