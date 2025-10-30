package app.marlboroadvance.mpvex.database

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

  val ALL: Array<Migration> =
    arrayOf(
      MIGRATION_1_2,
    )
}
