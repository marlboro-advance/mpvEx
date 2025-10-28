package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.marlboroadvance.mpvex.database.entities.PrivateVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateVideoDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(video: PrivateVideoEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(videos: List<PrivateVideoEntity>)

  @Query("DELETE FROM private_videos WHERE videoId = :videoId")
  suspend fun delete(videoId: Long)

  @Query("DELETE FROM private_videos WHERE videoId IN (:videoIds)")
  suspend fun deleteAll(videoIds: List<Long>)

  @Query("SELECT * FROM private_videos ORDER BY addedAt DESC")
  fun getAllFlow(): Flow<List<PrivateVideoEntity>>

  @Query("SELECT * FROM private_videos ORDER BY addedAt DESC")
  suspend fun getAll(): List<PrivateVideoEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM private_videos WHERE videoId = :videoId)")
  suspend fun isPrivate(videoId: Long): Boolean

  @Query("SELECT COUNT(*) FROM private_videos")
  suspend fun getCount(): Int
}
