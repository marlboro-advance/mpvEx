package app.marlboroadvance.mpvex.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
  val MIGRATION_1_2 =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Drop tables that are no longer needed
        db.execSQL("DROP TABLE IF EXISTS `video_index`")
        db.execSQL("DROP TABLE IF EXISTS `private_videos`")
      }
    }

  val ALL: Array<Migration> =
    arrayOf(
      MIGRATION_1_2,
    )
}
