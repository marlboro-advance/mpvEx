package app.sfsakhawat999.mpvrex.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
  val MIGRATION_1_2 =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Add launchSource column to RecentlyPlayedEntity table
        db.execSQL(
          "ALTER TABLE RecentlyPlayedEntity ADD COLUMN launchSource TEXT",
        )

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

  val MIGRATION_2_3 =
    object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Add timeRemaining column to PlaybackStateEntity table
        db.execSQL(
          "ALTER TABLE PlaybackStateEntity ADD COLUMN timeRemaining INTEGER NOT NULL DEFAULT 0",
        )
      }
    }

  val ALL: Array<Migration> =
    arrayOf(
      MIGRATION_1_2,
      MIGRATION_2_3,
    )
}
