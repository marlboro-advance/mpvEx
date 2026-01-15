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
     * Defaults to [MotionQuality.HighQuality] for the best visual experience.
     * Users can switch to [MotionQuality.Minimal] for faster animations
     * or [MotionQuality.NoMotion] for accessibility/performance.
     */
    val motionQuality = preferenceStore.getEnum("motion_quality", MotionQuality.MINIMAL)
}