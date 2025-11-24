package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
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

/**
 * Migration from version 1 to version 2
 *
 * Version 1 had 3 tables:
 * - PlaybackStateEntity
 * - RecentlyPlayedEntity (with columns: id, filePath, fileName, timestamp, launchSource)
 * - ExternalSubtitleEntity
 *
 * Version 2 adds:
 * - video_metadata_cache table (for MediaInfo caching)
 * - network_connections table (for SMB/FTP/WebDAV)
 * - PlaylistEntity table (for custom playlists)
 * - PlaylistItemEntity table (playlist items with foreign key)
 * - 6 new columns to RecentlyPlayedEntity (videoTitle, duration, fileSize, width, height, playlistId)
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      // 1. Create video_metadata_cache table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `video_metadata_cache` (
          `path` TEXT NOT NULL,
          `size` INTEGER NOT NULL,
          `dateModified` INTEGER NOT NULL,
          `duration` INTEGER NOT NULL,
          `width` INTEGER NOT NULL,
          `height` INTEGER NOT NULL,
          `fps` REAL NOT NULL,
          `lastScanned` INTEGER NOT NULL,
          PRIMARY KEY(`path`)
        )
        """.trimIndent(),
      )

      // 2. Create network_connections table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `network_connections` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `name` TEXT NOT NULL,
          `protocol` TEXT NOT NULL,
          `host` TEXT NOT NULL,
          `port` INTEGER NOT NULL,
          `username` TEXT NOT NULL DEFAULT '',
          `password` TEXT NOT NULL DEFAULT '',
          `path` TEXT NOT NULL DEFAULT '/',
          `isAnonymous` INTEGER NOT NULL DEFAULT 0,
          `lastConnected` INTEGER NOT NULL DEFAULT 0,
          `autoConnect` INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
      )

      // 3. Create PlaylistEntity table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `PlaylistEntity` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `name` TEXT NOT NULL,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent(),
      )

      // 4. Create PlaylistItemEntity table with foreign key
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `PlaylistItemEntity` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `playlistId` INTEGER NOT NULL,
          `filePath` TEXT NOT NULL,
          `fileName` TEXT NOT NULL,
          `position` INTEGER NOT NULL,
          `addedAt` INTEGER NOT NULL,
          `lastPlayedAt` INTEGER NOT NULL DEFAULT 0,
          `playCount` INTEGER NOT NULL DEFAULT 0,
          `lastPosition` INTEGER NOT NULL DEFAULT 0,
          FOREIGN KEY(`playlistId`) REFERENCES `PlaylistEntity`(`id`) ON DELETE CASCADE
        )
        """.trimIndent(),
      )

      // 5. Create index for PlaylistItemEntity foreign key
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_PlaylistItemEntity_playlistId` ON `PlaylistItemEntity` (`playlistId`)",
      )

      // 6. Add new columns to RecentlyPlayedEntity
      // SQLite ALTER TABLE syntax: DEFAULT must come AFTER the type/constraint
      // For NOT NULL columns, we MUST provide a default value
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `videoTitle` TEXT")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `playlistId` INTEGER")

    } catch (e: Exception) {
      // If migration fails, log the error and rethrow
      android.util.Log.e("Migration_1_2", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 2 to version 3
 * No schema changes - just version bump for Room schema verification consistency
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // No-op migration - schema is identical between version 2 and 3
    // This exists only to handle version bump for Room schema verification
    android.util.Log.d("Migration_2_3", "No schema changes needed")
  }
}

/**
 * Migration from version 3 to version 4
 * No schema changes - just version bump after fixing migration syntax
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // No-op migration - schema is identical, version bump only
    android.util.Log.d("Migration_3_4", "No schema changes needed")
  }
}

val DatabaseModule =
  module {
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration() // Fallback if migration fails (last resort)
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

    single {
      app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository(
        context = androidContext(),
        dao = get<MpvExDatabase>().videoMetadataDao(),
      )
    }

    // MediaFileRepository is a singleton object - no DI needed

    single {
      get<MpvExDatabase>().networkConnectionDao()
    }

    single {
      app.marlboroadvance.mpvex.repository.NetworkRepository(
        dao = get(),
      )
    }

    single {
      PlaylistRepository(
        playlistDao = get<MpvExDatabase>().playlistDao(),
      )
    }
  }
