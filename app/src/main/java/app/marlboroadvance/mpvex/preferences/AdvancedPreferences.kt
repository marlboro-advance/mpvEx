package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

class AdvancedPreferences(
    preferenceStore: PreferenceStore,
) {
    val mpvConfStorageUri = preferenceStore.getString("mpv_conf_storage_location_uri")
    val mpvConf = preferenceStore.getString("mpv.conf")

    val verboseLogging = preferenceStore.getBoolean("verbose_logging", BuildConfig.BUILD_TYPE != "release")

    val enabledStatisticsPage = preferenceStore.getInt("enabled_stats_page", 0)
}
