package app.sfsakhawat999.mpvrex.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(
  private val activity: PlayerActivity,
) : MPVLib.EventObserver {
  override fun eventProperty(property: String) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property) }
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun event(eventId: Int) {
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.event(eventId) }
  }
}
