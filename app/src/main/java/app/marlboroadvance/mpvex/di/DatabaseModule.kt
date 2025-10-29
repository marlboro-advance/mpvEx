package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import app.marlboroadvance.mpvex.database.Migrations
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
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
      Room
        .databaseBuilder(context, MpvExDatabase::class.java, "mpvex.db")
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(*Migrations.ALL)
        .build()
    }

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

    single { ThumbnailRepository(androidContext()) }
  }
