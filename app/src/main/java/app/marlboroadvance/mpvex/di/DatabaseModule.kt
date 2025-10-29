package app.marlboroadvance.mpvex.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.room.Room
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
import app.marlboroadvance.mpvex.database.Migrations
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.PrivateVideoRepository
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.utils.storage.StorageMonitor
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val DatabaseModule =
  module {
    // Provide kotlinx.serialization Json as a singleton (used by PlayerViewModel)
    single<Json> {
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    }

    single<MpvExDatabase> {
      val context = androidContext()
      // Delete DB only on a true fresh install (not on app updates)
      val packageManager = context.packageManager
      val packageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          packageManager.getPackageInfo(context.packageName, 0)
        }
      val isFreshInstall = packageInfo.firstInstallTime == packageInfo.lastUpdateTime
      if (isFreshInstall) {
        context.deleteDatabase("mpvex.db")
      }
      Room
        .databaseBuilder(context, MpvExDatabase::class.java, "mpvex.db")
        .addMigrations(*Migrations.ALL)
        .build()
    }

    single<FileSystemVideoRepository> { FileSystemVideoRepository(get(), get<AdvancedPreferences>()) }

    singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

    single<RecentlyPlayedRepository> {
      RecentlyPlayedRepositoryImpl(get<MpvExDatabase>().recentlyPlayedDao())
    }

    single<ExternalSubtitleRepository> {
      ExternalSubtitleRepository(
        context = androidContext(),
        dao = get<MpvExDatabase>().externalSubtitleDao(),
      )
    }

    single {
      PrivateVideoRepository(
        dao = get<MpvExDatabase>().privateVideoDao(),
        context = androidContext(),
      )
    }

    single { ThumbnailRepository(androidContext()) }

    single(createdAtStart = true) { StorageMonitor(androidContext(), get()) }
  }
