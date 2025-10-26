package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.DarkMode

class AppearancePreferences(
    preferenceStore: PreferenceStore,
) {
    // Default to Dark theme and Material You enabled
    val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.Dark)
    val materialYou = preferenceStore.getBoolean("material_you", true)
    val highContrastMode = preferenceStore.getBoolean("high_contrast_mode", false)
    val unlimitedNameLines = preferenceStore.getBoolean("unlimited_name_lines", false)
}
