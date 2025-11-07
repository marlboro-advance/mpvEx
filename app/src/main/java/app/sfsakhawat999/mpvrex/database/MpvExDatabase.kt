package app.sfsakhawat999.mpvrex.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.sfsakhawat999.mpvrex.database.dao.ExternalSubtitleDao
import app.sfsakhawat999.mpvrex.database.dao.PlaybackStateDao
import app.sfsakhawat999.mpvrex.database.dao.RecentlyPlayedDao
import app.sfsakhawat999.mpvrex.database.entities.ExternalSubtitleEntity
import app.sfsakhawat999.mpvrex.database.entities.PlaybackStateEntity
import app.sfsakhawat999.mpvrex.database.entities.RecentlyPlayedEntity

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    ExternalSubtitleEntity::class,
  ],
  version = 3,
)
abstract class MpvRexDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun externalSubtitleDao(): ExternalSubtitleDao
}
