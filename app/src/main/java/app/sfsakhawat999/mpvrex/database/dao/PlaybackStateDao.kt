package app.sfsakhawat999.mpvrex.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.sfsakhawat999.mpvrex.database.entities.PlaybackStateEntity

@Dao
interface PlaybackStateDao {
  @Upsert
  suspend fun upsert(playbackStateEntity: PlaybackStateEntity)

  @Query("SELECT * FROM PlaybackStateEntity WHERE mediaTitle = :mediaTitle LIMIT 1")
  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  @Query("DELETE FROM PlaybackStateEntity")
  suspend fun clearAllPlaybackStates()

  @Query("DELETE FROM PlaybackStateEntity WHERE mediaTitle = :mediaTitle")
  suspend fun deleteByTitle(mediaTitle: String)

  @Query(
    """
    UPDATE PlaybackStateEntity 
    SET mediaTitle = :newTitle 
    WHERE mediaTitle = :oldTitle
  """,
  )
  suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  )
}
