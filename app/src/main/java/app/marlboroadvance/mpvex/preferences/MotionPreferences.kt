package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.MotionQuality

/**
 * Motion/animation preferences for the app.
 * 
 * Controls animation quality tier across the entire application.
 */
class MotionPreferences(
    preferenceStore: PreferenceStore,
) {
    /**
     * The selected motion quality tier.
     *
     * Defaults to [MotionQuality.MINIMAL] for a balanced experience.
     * Users can switch to [MotionQuality.HIGH] for smoother fluid animations
     * or [MotionQuality.NONE] for accessibility/performance.
     */
    val motionQuality = preferenceStore.getEnum("motion_quality", MotionQuality.MINIMAL)
}
