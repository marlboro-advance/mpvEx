package app.marlboroadvance.mpvex.ui.player.managers

/**
 * Constants used throughout the player module.
 */
object PlayerConstants {
  // Result intent
  const val RESULT_INTENT = "app.marlboroadvance.mpvex.ui.player.PlayerActivity.result"

  // Timing constants (in milliseconds)
  const val PAUSE_DELAY_MS = 100L
  const val QUIT_DELAY_MS = 150L
  const val OBSERVER_REMOVAL_DELAY_MS = 50L

  // Value constants
  const val BRIGHTNESS_NOT_SET = -1f
  const val POSITION_NOT_SET = 0
  const val MAX_MPV_VOLUME = 100
  const val MILLISECONDS_TO_SECONDS = 1000
  const val DELAY_DIVISOR = 1000.0
  const val DEFAULT_PLAYBACK_SPEED = 1.0
  const val DEFAULT_SUB_SPEED = 1.0

  // Logging
  const val TAG = "mpvex"
}
