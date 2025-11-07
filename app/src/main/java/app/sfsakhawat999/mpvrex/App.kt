package app.sfsakhawat999.mpvrex

import android.app.Application
import app.sfsakhawat999.mpvrex.di.DatabaseModule
import app.sfsakhawat999.mpvrex.di.FileManagerModule
import app.sfsakhawat999.mpvrex.di.PreferencesModule
import app.sfsakhawat999.mpvrex.di.networkModule
import app.sfsakhawat999.mpvrex.presentation.crash.CrashActivity
import app.sfsakhawat999.mpvrex.presentation.crash.GlobalExceptionHandler
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
