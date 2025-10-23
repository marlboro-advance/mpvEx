package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.DarkMode

class AppearancePreferences(
  preferenceStore: PreferenceStore,
) {
  // Default to Dark theme and Material You disabled
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.Dark)
  val materialYou = preferenceStore.getBoolean("material_you", false)
  val unlimitedNameLines = preferenceStore.getBoolean("unlimited_name_lines", false)
}
