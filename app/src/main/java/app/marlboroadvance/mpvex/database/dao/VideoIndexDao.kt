package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.marlboroadvance.mpvex.database.entities.VideoIndexEntity

@Dao
interface VideoIndexDao {
  @Query("SELECT * FROM video_index")
  suspend fun getAll(): List<VideoIndexEntity>

  @Query("SELECT * FROM video_index WHERE bucketId = :bucketId")
  suspend fun getByBucketId(bucketId: String): List<VideoIndexEntity>

  @Query("SELECT * FROM video_index WHERE path = :path")
  suspend fun getByPath(path: String): VideoIndexEntity?

  @Query("SELECT DISTINCT bucketId, bucketDisplayName FROM video_index")
  suspend fun getAllBuckets(): List<BucketInfo>

  @Query("SELECT COUNT(*) FROM video_index WHERE bucketId = :bucketId")
  suspend fun getVideoCountForBucket(bucketId: String): Int

  @Query("SELECT SUM(size) FROM video_index WHERE bucketId = :bucketId")
  suspend fun getTotalSizeForBucket(bucketId: String): Long?

  @Query("SELECT SUM(duration) FROM video_index WHERE bucketId = :bucketId")
  suspend fun getTotalDurationForBucket(bucketId: String): Long?

  @Query("SELECT MAX(dateModified) FROM video_index WHERE bucketId = :bucketId")
  suspend fun getLastModifiedForBucket(bucketId: String): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(video: VideoIndexEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(videos: List<VideoIndexEntity>)

  @Query("DELETE FROM video_index WHERE path = :path")
  suspend fun deleteByPath(path: String)

  @Query("DELETE FROM video_index WHERE path IN (:paths)")
  suspend fun deleteByPaths(paths: List<String>)

  @Query("DELETE FROM video_index")
  suspend fun deleteAll()

  @Transaction
  @Query("SELECT * FROM video_index WHERE path NOT IN (:existingPaths)")
  suspend fun getStaleEntries(existingPaths: List<String>): List<VideoIndexEntity>

  @Query("DELETE FROM video_index WHERE path NOT IN (:existingPaths)")
  suspend fun deleteStaleEntries(existingPaths: List<String>)

  data class BucketInfo(
    val bucketId: String,
    val bucketDisplayName: String,
  )
}
