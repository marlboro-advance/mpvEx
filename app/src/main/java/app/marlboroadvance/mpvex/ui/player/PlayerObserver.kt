package app.marlboroadvance.mpvex.ui.player

import android.os.Handler
import android.os.Looper
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(
  private val callbacks: PlayerObserverCallbacks,
) : MPVLib.EventObserver {
  private val handler = Handler(Looper.getMainLooper())

  override fun eventProperty(property: String) {
    handler.post { callbacks.onObserverEvent() }
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
    handler.post { callbacks.onObserverEvent() }
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    handler.post { callbacks.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    handler.post { callbacks.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {
    handler.post { callbacks.onObserverEvent(property) }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
    handler.post { callbacks.onObserverEvent(property) }
  }

  override fun event(eventId: Int) {
    handler.post { callbacks.event(eventId) }
  }
}
