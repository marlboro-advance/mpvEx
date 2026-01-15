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
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Migration from version 1 (v1.0.0) to version 2 (v1.1.0)
 *
 * This is a squashed migration that handles ALL schema changes between v1.0.0 and v1.1.0
 *
 * Version 1 (v1.0.0) had 3 tables:
 * - PlaybackStateEntity (with secondarySid and secondarySubDelay columns, no videoZoom)
 * - RecentlyPlayedEntity (with columns: id, filePath, fileName, timestamp, launchSource)
 * - ExternalSubtitleEntity
 *
 * Version 2 (v1.1.0) changes:
 * - PlaybackStateEntity: Removes secondarySid and secondarySubDelay, adds videoZoom
 * - Removes ExternalSubtitleEntity table
 * - Adds video_metadata_cache table (for MediaInfo caching)
 * - Adds network_connections table (for SMB/FTP/WebDAV)
 * - Adds PlaylistEntity table (for custom playlists)
 * - Adds PlaylistItemEntity table (playlist items with foreign key)
 * - Adds 6 new columns to RecentlyPlayedEntity (videoTitle, duration, fileSize, width, height, playlistId)
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_1_2", "Starting migration from v1.0.0 to v1.1.0")

      // ===== 1. Update PlaybackStateEntity: Remove secondarySid/secondarySubDelay, add videoZoom =====
      android.util.Log.d("Migration_1_2", "Updating PlaybackStateEntity schema")

      // Create new PlaybackStateEntity table with correct schema
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `PlaybackStateEntity_new` (
          `mediaTitle` TEXT NOT NULL,
          `lastPosition` INTEGER NOT NULL,
          `playbackSpeed` REAL NOT NULL,
          `videoZoom` REAL NOT NULL DEFAULT 0.0,
          `sid` INTEGER NOT NULL,
          `subDelay` INTEGER NOT NULL,
          `subSpeed` REAL NOT NULL,
          `aid` INTEGER NOT NULL,
          `audioDelay` INTEGER NOT NULL,
          `timeRemaining` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`mediaTitle`)
        )
        """.trimIndent(),
      )

      // Copy data from old table (excluding secondarySid and secondarySubDelay, adding videoZoom with default)
      db.execSQL(
        """
        INSERT INTO `PlaybackStateEntity_new` 
        (`mediaTitle`, `lastPosition`, `playbackSpeed`, `videoZoom`, `sid`, `subDelay`, 
         `subSpeed`, `aid`, `audioDelay`, `timeRemaining`)
        SELECT `mediaTitle`, `lastPosition`, `playbackSpeed`, 0.0, `sid`, `subDelay`, 
               `subSpeed`, `aid`, `audioDelay`, `timeRemaining`
        FROM `PlaybackStateEntity`
        """.trimIndent(),
      )

      // Drop old table and rename new one
      db.execSQL("DROP TABLE `PlaybackStateEntity`")
      db.execSQL("ALTER TABLE `PlaybackStateEntity_new` RENAME TO `PlaybackStateEntity`")

      // ===== 2. Drop ExternalSubtitleEntity table =====
      android.util.Log.d("Migration_1_2", "Removing ExternalSubtitleEntity table")
      db.execSQL("DROP TABLE IF EXISTS `ExternalSubtitleEntity`")

      // ===== 3. Create video_metadata_cache table =====
      android.util.Log.d("Migration_1_2", "Creating video_metadata_cache table")
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

      // ===== 4. Create network_connections table =====
      android.util.Log.d("Migration_1_2", "Creating network_connections table")
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

      // ===== 5. Create PlaylistEntity table =====
      android.util.Log.d("Migration_1_2", "Creating PlaylistEntity table")
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

      // ===== 6. Create PlaylistItemEntity table with foreign key =====
      android.util.Log.d("Migration_1_2", "Creating PlaylistItemEntity table")
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

      // Create index for PlaylistItemEntity foreign key
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_PlaylistItemEntity_playlistId` ON `PlaylistItemEntity` (`playlistId`)",
      )

      // ===== 7. Add new columns to RecentlyPlayedEntity =====
      android.util.Log.d("Migration_1_2", "Adding columns to RecentlyPlayedEntity")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `videoTitle` TEXT")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `playlistId` INTEGER")

      android.util.Log.d("Migration_1_2", "Migration completed successfully")

    } catch (e: Exception) {
      android.util.Log.e("Migration_1_2", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 2 to version 3
 *
 * Changes:
 * - Adds externalSubtitles column to PlaybackStateEntity to persist external subtitle URIs
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_2_3", "Starting migration from version 2 to version 3")

      // Add externalSubtitles column to PlaybackStateEntity
      db.execSQL("ALTER TABLE `PlaybackStateEntity` ADD COLUMN `externalSubtitles` TEXT NOT NULL DEFAULT ''")

      // Add subtitleCodec column to video_metadata_cache
      db.execSQL("ALTER TABLE video_metadata_cache ADD COLUMN subtitleCodec TEXT NOT NULL DEFAULT ''")


      android.util.Log.d("Migration_2_3", "Migration completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("Migration_2_3", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 3 to version 4
 *
 * Changes:
 * - Adds m3uSourceUrl and isM3uPlaylist columns to PlaylistEntity for M3U/M3U8 playlist support
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_3_4", "Starting migration from version 3 to version 4")

      // Add M3U-related columns to PlaylistEntity
      db.execSQL(
        "ALTER TABLE `PlaylistEntity` ADD COLUMN `m3uSourceUrl` TEXT DEFAULT NULL"
      )
      db.execSQL(
        "ALTER TABLE `PlaylistEntity` ADD COLUMN `isM3uPlaylist` INTEGER NOT NULL DEFAULT 0"
      )

      android.util.Log.d("Migration_3_4", "Migration completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("Migration_3_4", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 4 to version 5
 *
 * Changes:
 * - Adds secondarySid column to PlaybackStateEntity to persist secondary subtitle track
 *   This allows saving and restoring both primary and secondary subtitle selections
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_4_5", "Starting migration from version 4 to version 5")

      // Add secondarySid column to PlaybackStateEntity (-1 means disabled)
      db.execSQL(
        "ALTER TABLE `PlaybackStateEntity` ADD COLUMN `secondarySid` INTEGER NOT NULL DEFAULT -1"
      )

      android.util.Log.d("Migration_4_5", "Migration completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("Migration_4_5", "Migration failed", e)
      throw e
    }
  }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_5_6", "Starting migration from version 5 to 6")
      
      // Get existing columns to check what needs to be added
      val cursor = db.query("PRAGMA table_info(video_metadata_cache)")
      val existingColumns = mutableSetOf<String>()
      while (cursor.moveToNext()) {
        val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
        existingColumns.add(columnName)
      }
      cursor.close()
      
      // Add subtitleCodec if it doesn't exist yet (should have been added in MIGRATION_2_3 but might be missing)
      if (!existingColumns.contains("subtitleCodec")) {
        try {
          db.execSQL("ALTER TABLE `video_metadata_cache` ADD COLUMN `subtitleCodec` TEXT NOT NULL DEFAULT ''")
          android.util.Log.d("Migration_5_6", "Added subtitleCodec column")
        } catch (e: Exception) {
          android.util.Log.w("Migration_5_6", "Error adding subtitleCodec column, may already exist", e)
        }
      } else {
        android.util.Log.d("Migration_5_6", "subtitleCodec column already exists, skipping")
      }
      
      // Add hasEmbeddedSubtitles if it doesn't exist
      if (!existingColumns.contains("hasEmbeddedSubtitles")) {
        try {
          db.execSQL("ALTER TABLE `video_metadata_cache` ADD COLUMN `hasEmbeddedSubtitles` INTEGER NOT NULL DEFAULT 0")
          android.util.Log.d("Migration_5_6", "Added hasEmbeddedSubtitles column")
        } catch (e: Exception) {
          android.util.Log.w("Migration_5_6", "Error adding hasEmbeddedSubtitles column, may already exist", e)
        }
      } else {
        android.util.Log.d("Migration_5_6", "hasEmbeddedSubtitles column already exists, skipping")
      }
      
      android.util.Log.d("Migration_5_6", "Migration completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("Migration_5_6", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 6 to version 7
 *
 * Changes:
 * - Adds useHttps column to network_connections table for explicit HTTPS control on WebDAV
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_6_7", "Starting migration from version 6 to 7")
      
      // Add useHttps column to network_connections table
      db.execSQL("ALTER TABLE `network_connections` ADD COLUMN `useHttps` INTEGER NOT NULL DEFAULT 0")
      
      android.util.Log.d("Migration_6_7", "Migration completed successfully")
    } catch (e: Exception) {
      android.util.Log.e("Migration_6_7", "Migration failed", e)
      throw e
    }
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .fallbackToDestructiveMigration(true) // Fallback if migration fails (last resort)
        .build()
    }

    singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

    single<RecentlyPlayedRepository> {
      RecentlyPlayedRepositoryImpl(get<MpvExDatabase>().recentlyPlayedDao())
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
