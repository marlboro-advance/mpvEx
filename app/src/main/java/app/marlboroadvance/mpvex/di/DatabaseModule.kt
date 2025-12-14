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

/**
 * Migration from version 4 to version 5
 * Ensures RecentlyPlayedEntity and PlaylistEntity have all required columns
 * Handles corrupted database states where migrations were partially applied
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      // ===== Fix RecentlyPlayedEntity =====
      android.util.Log.d("Migration_4_5", "Ensuring RecentlyPlayedEntity schema integrity")

      val recentlyPlayedCursor = db.query("PRAGMA table_info(RecentlyPlayedEntity)")
      val recentlyPlayedColumns = mutableSetOf<String>()

      var nameColumnIndex = recentlyPlayedCursor.getColumnIndex("name")
      while (recentlyPlayedCursor.moveToNext()) {
        val columnName = recentlyPlayedCursor.getString(nameColumnIndex)
        recentlyPlayedColumns.add(columnName)
      }
      recentlyPlayedCursor.close()

      android.util.Log.d("Migration_4_5", "RecentlyPlayedEntity existing columns: $recentlyPlayedColumns")

      // Add missing columns if they don't exist
      if ("videoTitle" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: videoTitle")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `videoTitle` TEXT")
      }

      if ("duration" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: duration")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0")
      }

      if ("fileSize" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: fileSize")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
      }

      if ("width" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: width")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
      }

      if ("height" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: height")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
      }

      if ("playlistId" !in recentlyPlayedColumns) {
        android.util.Log.d("Migration_4_5", "Adding column: playlistId")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `playlistId` INTEGER")
      }

      android.util.Log.d("Migration_4_5", "RecentlyPlayedEntity schema updated successfully")

      // ===== Fix PlaylistEntity =====
      android.util.Log.d("Migration_4_5", "Checking PlaylistEntity schema integrity")

      val playlistCursor = db.query("PRAGMA table_info(PlaylistEntity)")
      val playlistColumns = mutableSetOf<String>()

      nameColumnIndex = playlistCursor.getColumnIndex("name")
      while (playlistCursor.moveToNext()) {
        val columnName = playlistCursor.getString(nameColumnIndex)
        playlistColumns.add(columnName)
      }
      playlistCursor.close()

      android.util.Log.d("Migration_4_5", "PlaylistEntity existing columns: $playlistColumns")

      // If PlaylistEntity is empty or missing columns, recreate it
      if (playlistColumns.isEmpty() ||
        "id" !in playlistColumns ||
        "name" !in playlistColumns ||
        "createdAt" !in playlistColumns ||
        "updatedAt" !in playlistColumns
      ) {

        android.util.Log.d("Migration_4_5", "Recreating PlaylistEntity table")

        // Drop corrupted table
        db.execSQL("DROP TABLE IF EXISTS `PlaylistEntity`")

        // Recreate PlaylistEntity table
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

        android.util.Log.d("Migration_4_5", "PlaylistEntity recreated successfully")
      }

      // ===== Fix PlaylistItemEntity =====
      android.util.Log.d("Migration_4_5", "Checking PlaylistItemEntity schema integrity")

      val playlistItemCursor = db.query("PRAGMA table_info(PlaylistItemEntity)")
      val playlistItemColumns = mutableSetOf<String>()

      nameColumnIndex = playlistItemCursor.getColumnIndex("name")
      while (playlistItemCursor.moveToNext()) {
        val columnName = playlistItemCursor.getString(nameColumnIndex)
        playlistItemColumns.add(columnName)
      }
      playlistItemCursor.close()

      android.util.Log.d("Migration_4_5", "PlaylistItemEntity existing columns: $playlistItemColumns")

      // If PlaylistItemEntity is corrupted, recreate it
      if (playlistItemColumns.isEmpty() || "id" !in playlistItemColumns) {
        android.util.Log.d("Migration_4_5", "Recreating PlaylistItemEntity table")

        // Drop corrupted table
        db.execSQL("DROP TABLE IF EXISTS `PlaylistItemEntity`")

        // Recreate PlaylistItemEntity table with foreign key
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

        // Create index for foreign key
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_PlaylistItemEntity_playlistId` ON `PlaylistItemEntity` (`playlistId`)",
        )

        android.util.Log.d("Migration_4_5", "PlaylistItemEntity recreated successfully")
      }

      android.util.Log.d("Migration_4_5", "All schema integrity checks completed successfully")

    } catch (e: Exception) {
      android.util.Log.e("Migration_4_5", "Migration failed", e)
      throw e
    }
  }
}

/**
 * Migration from version 5 to version 6
 * Adds videoZoom column to PlaybackStateEntity for per-video zoom persistence
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_5_6", "Adding videoZoom column to PlaybackStateEntity")

      // Check if videoZoom column already exists
      val cursor = db.query("PRAGMA table_info(PlaybackStateEntity)")
      val columns = mutableSetOf<String>()

      val nameColumnIndex = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        val columnName = cursor.getString(nameColumnIndex)
        columns.add(columnName)
      }
      cursor.close()

      // Add videoZoom column if it doesn't exist
      if ("videoZoom" !in columns) {
        android.util.Log.d("Migration_5_6", "Adding column: videoZoom")
        db.execSQL("ALTER TABLE `PlaybackStateEntity` ADD COLUMN `videoZoom` REAL NOT NULL DEFAULT 0.0")
      } else {
        android.util.Log.d("Migration_5_6", "videoZoom column already exists")
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
 * Removes ExternalSubtitleEntity table (subtitle download feature removed)
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    try {
      android.util.Log.d("Migration_6_7", "Removing ExternalSubtitleEntity table")

      // Drop the external subtitle table
      db.execSQL("DROP TABLE IF EXISTS `ExternalSubtitleEntity`")

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
        .fallbackToDestructiveMigration() // Fallback if migration fails (last resort)
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
