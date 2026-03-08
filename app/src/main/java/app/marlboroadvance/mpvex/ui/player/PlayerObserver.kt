package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.lang.ref.WeakReference

class PlayerObserver(
  activity: PlayerActivity,
) : MPVLib.EventObserver {
  private val activityRef: WeakReference<PlayerActivity> = WeakReference(activity)

  override fun eventProperty(property: String) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property) }
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun event(eventId: Int, data: MPVNode) {
    val activity = activityRef.get() ?: return
    if (activity.player.isExiting) return
    activity.runOnUiThread { activity.event(eventId) }
  }
}
