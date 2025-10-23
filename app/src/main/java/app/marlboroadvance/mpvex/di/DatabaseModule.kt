package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // Add launchSource column to RecentlyPlayedEntity table
      db.execSQL("ALTER TABLE RecentlyPlayedEntity ADD COLUMN launchSource TEXT DEFAULT NULL")
    }
  }

val MIGRATION_2_3 =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // Create ExternalSubtitleEntity table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS ExternalSubtitleEntity (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          originalUri TEXT NOT NULL,
          originalFileName TEXT NOT NULL,
          cachedFilePath TEXT NOT NULL,
          mediaTitle TEXT NOT NULL,
          addedTimestamp INTEGER NOT NULL
        )
        """.trimIndent(),
      )
    }
  }

val DatabaseModule =
  module {
    single<MpvExDatabase> {
      Room
        .databaseBuilder(androidContext(), MpvExDatabase::class.java, "mpvex.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigrationOnDowngrade(true)
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
  }
