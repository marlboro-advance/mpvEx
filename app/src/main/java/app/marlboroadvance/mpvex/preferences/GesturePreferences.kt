package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.player.SingleActionGesture

class GesturePreferences(
  preferenceStore: PreferenceStore,
) {
  val doubleTapToSeekDuration = preferenceStore.getInt("double_tap_to_seek_duration", 10)
  val doubleTapSeekAreaWidth = preferenceStore.getInt("double_tap_seek_area_width", 35)
  val leftSingleActionGesture = preferenceStore.getEnum("left_double_tap_gesture", SingleActionGesture.Seek)
  val centerSingleActionGesture = preferenceStore.getEnum("center_drag_gesture", SingleActionGesture.PlayPause)
  val rightSingleActionGesture = preferenceStore.getEnum("right_drag_gesture", SingleActionGesture.Seek)
  val useSingleTapForCenter = preferenceStore.getBoolean("use_single_tap_for_center", false)
  val mediaPreviousGesture = preferenceStore.getEnum("meda_previous_gesture", SingleActionGesture.Seek)
  val mediaPlayGesture = preferenceStore.getEnum("media_play_gesture", SingleActionGesture.PlayPause)
  val mediaNextGesture = preferenceStore.getEnum("media_next_gesture", SingleActionGesture.Seek)
  val tapThumbnailToSelect = preferenceStore.getBoolean("tap_thumbnail_to_select", false)
}
