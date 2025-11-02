package app.marlboroadvance.mpvex

import android.app.Application
import app.marlboroadvance.mpvex.di.DatabaseModule
import app.marlboroadvance.mpvex.di.FileManagerModule
import app.marlboroadvance.mpvex.di.PreferencesModule
import app.marlboroadvance.mpvex.di.networkModule
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity
import app.marlboroadvance.mpvex.presentation.crash.GlobalExceptionHandler
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
class App :
  Application(),
  KoinStartup {
  override fun onCreate() {
    super.onCreate()
    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
  }

  override fun onKoinStartup() =
    koinConfiguration {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        networkModule,
      )
    }
}
