package app.marlboroadvance.mpvex.preferences

import android.content.Context
import android.os.Build
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.utils.TVUtils

class AppearancePreferences(
  preferenceStore: PreferenceStore,
  context: Context,
) {
  private val isTV = TVUtils.isAndroidTV(context)

  // On TV: default to Dark mode, on other devices: follow system
  private val defaultDarkMode = if (isTV) {
    DarkMode.Dark
  } else {
    DarkMode.System
  }

  // On TV: disable Material You by default, on other devices: enable if supported
  private val defaultMaterialYou = if (isTV) {
    TVUtils.Defaults.MATERIAL_YOU_ENABLED
  } else {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  }

  val darkMode = preferenceStore.getEnum("dark_mode", defaultDarkMode)
  val materialYou = preferenceStore.getBoolean("material_you", defaultMaterialYou)
}
