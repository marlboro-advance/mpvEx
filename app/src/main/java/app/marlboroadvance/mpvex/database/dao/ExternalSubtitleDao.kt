package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.marlboroadvance.mpvex.database.entities.ExternalSubtitleEntity

@Dao
interface ExternalSubtitleDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(subtitle: ExternalSubtitleEntity): Long

  @Query("SELECT * FROM ExternalSubtitleEntity WHERE mediaTitle = :mediaTitle ORDER BY addedTimestamp ASC")
  suspend fun getSubtitlesForMedia(mediaTitle: String): List<ExternalSubtitleEntity>

  @Query("SELECT * FROM ExternalSubtitleEntity WHERE cachedFilePath = :cachedFilePath LIMIT 1")
  suspend fun getSubtitleByCachedPath(cachedFilePath: String): ExternalSubtitleEntity?

  @Query("DELETE FROM ExternalSubtitleEntity WHERE id = :id")
  suspend fun deleteById(id: Int)

  @Query("DELETE FROM ExternalSubtitleEntity WHERE cachedFilePath = :cachedFilePath")
  suspend fun deleteByCachedPath(cachedFilePath: String)

  @Query("DELETE FROM ExternalSubtitleEntity WHERE mediaTitle = :mediaTitle")
  suspend fun deleteAllForMedia(mediaTitle: String)

  @Query("SELECT * FROM ExternalSubtitleEntity")
  suspend fun getAll(): List<ExternalSubtitleEntity>
}
