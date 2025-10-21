package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.ui.player.managers.MPVEventDispatcher
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(
  private val activity: PlayerActivity,
) : MPVLib.EventObserver {

  private var eventDispatcher: MPVEventDispatcher? = null

  fun setEventDispatcher(dispatcher: MPVEventDispatcher) {
    eventDispatcher = dispatcher
  }

  override fun eventProperty(property: String) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent() ?: activity.onObserverEvent()
    }
  }

  override fun eventProperty(property: String, value: Long) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent() ?: activity.onObserverEvent()
    }
  }

  override fun eventProperty(property: String, value: Boolean) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent(property, value) ?: activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(property: String, value: String) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent(property, value) ?: activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(property: String, value: Double) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent(property) ?: activity.onObserverEvent(property)
    }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String, value: MPVNode) {
    activity.runOnUiThread {
      eventDispatcher?.onObserverEvent(property) ?: activity.onObserverEvent(property)
    }
  }

  override fun event(eventId: Int) {
    activity.runOnUiThread {
      eventDispatcher?.event(eventId) ?: activity.event(eventId)
    }
  }
}
