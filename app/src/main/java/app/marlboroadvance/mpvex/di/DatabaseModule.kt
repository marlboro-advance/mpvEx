package app.marlboroadvance.mpvex.di

import androidx.room.Room
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
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
      Room
        .databaseBuilder(androidContext(), MpvExDatabase::class.java, "mpvex.db")
        .fallbackToDestructiveMigration()
        .fallbackToDestructiveMigrationOnDowngrade()
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

    // Ultra-fast thumbnail provider (platform-backed with caching)
    single { ThumbnailRepository(androidContext()) }

    // Start storage monitor early to auto-detect USB OTG mount/unmount
    single(createdAtStart = true) { StorageMonitor(androidContext(), get()) }
  }
