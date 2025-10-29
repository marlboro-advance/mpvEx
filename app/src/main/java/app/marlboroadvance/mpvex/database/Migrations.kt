package app.marlboroadvance.mpvex.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
  val MIGRATION_1_2 =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `PlaybackStateEntity` (
            `mediaTitle` TEXT NOT NULL,
            `lastPosition` INTEGER NOT NULL,
            `playbackSpeed` REAL NOT NULL,
            `sid` INTEGER NOT NULL,
            `subDelay` INTEGER NOT NULL,
            `subSpeed` REAL NOT NULL,
            `secondarySid` INTEGER NOT NULL,
            `secondarySubDelay` INTEGER NOT NULL,
            `aid` INTEGER NOT NULL,
            `audioDelay` INTEGER NOT NULL,
            PRIMARY KEY(`mediaTitle`)
          )
          """.trimIndent(),
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `ExternalSubtitleEntity` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `originalUri` TEXT NOT NULL,
            `originalFileName` TEXT NOT NULL,
            `cachedFilePath` TEXT NOT NULL,
            `mediaTitle` TEXT NOT NULL,
            `addedTimestamp` INTEGER NOT NULL
          )
          """.trimIndent(),
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `private_videos` (
            `videoId` INTEGER NOT NULL,
            `originalPath` TEXT NOT NULL,
            `privateFilePath` TEXT NOT NULL,
            `addedAt` INTEGER NOT NULL,
            PRIMARY KEY(`videoId`)
          )
          """.trimIndent(),
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `video_index` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `path` TEXT NOT NULL,
            `displayName` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `size` INTEGER NOT NULL,
            `duration` INTEGER NOT NULL,
            `dateModified` INTEGER NOT NULL,
            `dateAdded` INTEGER NOT NULL,
            `lastModified` INTEGER NOT NULL,
            `mimeType` TEXT NOT NULL,
            `bucketId` TEXT NOT NULL,
            `bucketDisplayName` TEXT NOT NULL,
            `lastIndexed` INTEGER NOT NULL
          )
          """.trimIndent(),
        )

        db.execSQL(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS `index_video_index_path` ON `video_index` (`path`)
          """.trimIndent(),
        )
        db.execSQL(
          """
          CREATE INDEX IF NOT EXISTS `index_video_index_bucketId` ON `video_index` (`bucketId`)
          """.trimIndent(),
        )
        db.execSQL(
          """
          CREATE INDEX IF NOT EXISTS `index_video_index_lastModified` ON `video_index` (`lastModified`)
          """.trimIndent(),
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `RecentlyPlayedEntity_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `filePath` TEXT NOT NULL,
            `fileName` TEXT NOT NULL,
            `timestamp` INTEGER NOT NULL,
            `launchSource` TEXT
          )
          """.trimIndent(),
        )

        var hasLaunchSource = false
        db.query("PRAGMA table_info(`RecentlyPlayedEntity`)").use { cursor ->
          val nameIndex = cursor.getColumnIndex("name")
          while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == "launchSource") {
              hasLaunchSource = true
              break
            }
          }
        }

        if (hasLaunchSource) {
          db.execSQL(
            """
            INSERT INTO `RecentlyPlayedEntity_new` (`id`,`filePath`,`fileName`,`timestamp`,`launchSource`)
            SELECT `id`,`filePath`,`fileName`,`timestamp`,`launchSource` FROM `RecentlyPlayedEntity`
            """.trimIndent(),
          )
        } else {
          db.execSQL(
            """
            INSERT INTO `RecentlyPlayedEntity_new` (`id`,`filePath`,`fileName`,`timestamp`,`launchSource`)
            SELECT `id`,`filePath`,`fileName`,`timestamp`, NULL AS `launchSource` FROM `RecentlyPlayedEntity`
            """.trimIndent(),
          )
        }

        db.execSQL("DROP TABLE `RecentlyPlayedEntity`")
        db.execSQL("ALTER TABLE `RecentlyPlayedEntity_new` RENAME TO `RecentlyPlayedEntity`")
      }
    }

  val ALL: Array<Migration> =
    arrayOf(
      MIGRATION_1_2,
    )
}
