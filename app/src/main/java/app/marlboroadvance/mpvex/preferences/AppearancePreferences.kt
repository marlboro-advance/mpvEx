package app.marlboroadvance.mpvex.preferences

import android.os.Build
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.DarkMode

class AppearancePreferences(
  preferenceStore: PreferenceStore,
) {
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.System)
  val materialYou = preferenceStore.getBoolean("material_you", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}
