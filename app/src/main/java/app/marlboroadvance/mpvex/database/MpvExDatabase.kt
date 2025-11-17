package app.marlboroadvance.mpvex.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.marlboroadvance.mpvex.database.dao.ExternalSubtitleDao
import app.marlboroadvance.mpvex.database.dao.PlaybackStateDao
import app.marlboroadvance.mpvex.database.dao.RecentlyPlayedDao
import app.marlboroadvance.mpvex.database.dao.VideoMetadataDao
import app.marlboroadvance.mpvex.database.entities.ExternalSubtitleEntity
import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.database.entities.VideoMetadataEntity

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    ExternalSubtitleEntity::class,
    VideoMetadataEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class mpvexDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun externalSubtitleDao(): ExternalSubtitleDao

  abstract fun videoMetadataDao(): VideoMetadataDao
}
