package app.marlboroadvance.mpvex

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.di.DatabaseModule
import app.marlboroadvance.mpvex.di.FileManagerModule
import app.marlboroadvance.mpvex.di.PreferencesModule
import app.marlboroadvance.mpvex.preferences.AppLanguage
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity
import app.marlboroadvance.mpvex.presentation.crash.GlobalExceptionHandler
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Context
import java.util.Locale
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
class App : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()
  private val appearancePreferences: AppearancePreferences by inject()


  override fun onCreate() {
    super.onCreate()

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
      )
    }

    // Apply saved language preference
    val savedLanguage = appearancePreferences.appLanguage.get()
    android.util.Log.d("App", "Applying saved language: ${savedLanguage.name} (${savedLanguage.code})")
    val localeList = if (savedLanguage == AppLanguage.System) {
      LocaleListCompat.getEmptyLocaleList()
    } else {
      LocaleListCompat.forLanguageTags(savedLanguage.code)
    }
    AppCompatDelegate.setApplicationLocales(localeList)
    android.util.Log.d("App", "Current app locales: ${AppCompatDelegate.getApplicationLocales()}")

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    FastThumbnails.initialize(this)

    // Perform cache maintenance on app startup, but delay slightly to avoid contention during process bind
    applicationScope.launch {
      android.util.Log.d("App", "Delaying cache maintenance by 3000ms to avoid startup contention")
      kotlinx.coroutines.delay(3000)
      runCatching {
        metadataCache.performMaintenance()
      }.onFailure { android.util.Log.w("App", "Cache maintenance failed", it) }
    }
  }
}
