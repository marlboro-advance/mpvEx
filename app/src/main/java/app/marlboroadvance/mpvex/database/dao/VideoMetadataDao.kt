package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.marlboroadvance.mpvex.database.entities.VideoMetadataEntity

@Dao
interface VideoMetadataDao {
  /**
   * Get cached metadata for a video
   * Returns null if not cached or cache is stale (file was modified after cache entry)
   */
  @Query(
    """
    SELECT * FROM video_metadata_cache 
    WHERE path = :path 
    AND dateModified = :dateModified 
    AND size = :size
    LIMIT 1
    """,
  )
  suspend fun getMetadata(
    path: String,
    dateModified: Long,
    size: Long,
  ): VideoMetadataEntity?

  /**
   * Get cached metadata for multiple videos at once
   */
  @Query("SELECT * FROM video_metadata_cache WHERE path IN (:paths)")
  suspend fun getMetadataBatch(paths: List<String>): List<VideoMetadataEntity>

  /**
   * Insert or update cached metadata
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMetadata(metadata: VideoMetadataEntity)

  /**
   * Insert multiple metadata entries at once
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMetadataBatch(metadata: List<VideoMetadataEntity>)

  /**
   * Clear old cache entries (older than 30 days)
   */
  @Query("DELETE FROM video_metadata_cache WHERE lastScanned < :cutoffTimestamp")
  suspend fun clearOldCache(cutoffTimestamp: Long)

  /**
   * Clear all cached metadata
   */
  @Query("DELETE FROM video_metadata_cache")
  suspend fun clearAll()

  /**
   * Get total number of cached entries
   */
  @Query("SELECT COUNT(*) FROM video_metadata_cache")
  suspend fun getCacheCount(): Int

  /**
   * Delete specific cache entry by path
   */
  @Query("DELETE FROM video_metadata_cache WHERE path = :path")
  suspend fun deleteByPath(path: String)

  /**
   * Delete multiple cache entries by paths
   */
  @Query("DELETE FROM video_metadata_cache WHERE path IN (:paths)")
  suspend fun deleteByPaths(paths: List<String>)

  /**
   * Get all cached paths (for validating existence)
   */
  @Query("SELECT path FROM video_metadata_cache")
  suspend fun getAllCachedPaths(): List<String>

  /**
   * Get cache size in bytes (approximate)
   */
  @Query("SELECT SUM(size) FROM video_metadata_cache")
  suspend fun getTotalCacheSize(): Long?

  /**
   * Delete cache entries for non-existent files (stale entries)
   * This requires checking file existence outside the query
   */
  @Query("DELETE FROM video_metadata_cache WHERE path NOT IN (:existingPaths)")
  suspend fun deleteStaleEntries(existingPaths: List<String>)
}
