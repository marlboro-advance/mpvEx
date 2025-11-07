package app.sfsakhawat999.mpvrex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import app.sfsakhawat999.mpvrex.database.Migrations
import app.sfsakhawat999.mpvrex.database.MpvRexDatabase
import app.sfsakhawat999.mpvrex.database.repository.PlaybackStateRepositoryImpl
import app.sfsakhawat999.mpvrex.database.repository.RecentlyPlayedRepositoryImpl
import app.sfsakhawat999.mpvrex.domain.playbackstate.repository.PlaybackStateRepository
import app.sfsakhawat999.mpvrex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.sfsakhawat999.mpvrex.domain.subtitle.repository.ExternalSubtitleRepository
import app.sfsakhawat999.mpvrex.domain.thumbnail.ThumbnailRepository
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

    single<MpvRexDatabase> {
      val context = androidContext()
      Room
        .databaseBuilder(context, MpvRexDatabase::class.java, "mpvrex.db")
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(*Migrations.ALL)
        .build()
    }

    singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

    single<RecentlyPlayedRepository> {
      RecentlyPlayedRepositoryImpl(get<MpvRexDatabase>().recentlyPlayedDao())
    }

    single<ExternalSubtitleRepository> {
      ExternalSubtitleRepository(
        context = androidContext(),
        dao = get<MpvRexDatabase>().externalSubtitleDao(),
      )
    }

    single { ThumbnailRepository(androidContext()) }
  }
