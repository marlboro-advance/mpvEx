package app.marlboroadvance.mpvex.ui.player

import android.content.ContentResolver
import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Abstraction over host requirements so the player logic can run in an Activity or a Screen.
 */
interface PlayerHost {
  val context: Context
  val windowInsetsController: WindowInsetsControllerCompat
  val audioManager: AudioManager

  // Host OS primitives with non-conflicting names
  val hostWindow: Window
  val hostWindowManager: WindowManager
  val hostContentResolver: ContentResolver
  var hostRequestedOrientation: Int
}

/**
 * Callbacks that are invoked by the mpv event observer.
 */
interface PlayerObserverCallbacks {
  fun onObserverEvent()

  fun onObserverEvent(property: String)

  fun onObserverEvent(
    property: String,
    value: Boolean,
  )

  fun onObserverEvent(
    property: String,
    value: String,
  )

  fun event(eventId: Int)
}
