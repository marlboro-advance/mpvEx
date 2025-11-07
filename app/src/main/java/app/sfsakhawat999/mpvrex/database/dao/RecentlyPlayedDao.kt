package app.sfsakhawat999.mpvrex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.sfsakhawat999.mpvrex.database.entities.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(recentlyPlayed: RecentlyPlayedEntity)

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT 1")
  suspend fun getLastPlayed(): RecentlyPlayedEntity?

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT 1")
  fun observeLastPlayed(): Flow<RecentlyPlayedEntity?>

  @Query(
    """
    SELECT * FROM RecentlyPlayedEntity 
    WHERE launchSource IS NULL OR launchSource = '' OR launchSource = 'normal' OR launchSource = 'playlist'
    ORDER BY timestamp DESC 
    LIMIT 1
  """,
  )
  suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity?

  @Query(
    """
    SELECT * FROM RecentlyPlayedEntity 
    WHERE launchSource IS NULL OR launchSource = '' OR launchSource = 'normal' OR launchSource = 'playlist'
    ORDER BY timestamp DESC 
    LIMIT 1
  """,
  )
  fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?>

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentlyPlayed(limit: Int = 10): List<RecentlyPlayedEntity>

  @Query("SELECT * FROM RecentlyPlayedEntity WHERE launchSource = :launchSource ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentlyPlayedBySource(
    launchSource: String,
    limit: Int = 10,
  ): List<RecentlyPlayedEntity>

  @Query("DELETE FROM RecentlyPlayedEntity")
  suspend fun clearAll()

  @Query("DELETE FROM RecentlyPlayedEntity WHERE timestamp < :cutoffTime")
  suspend fun deleteOlderThan(cutoffTime: Long)

  @Query("DELETE FROM RecentlyPlayedEntity WHERE filePath = :filePath")
  suspend fun deleteByFilePath(filePath: String)

  @Query("UPDATE RecentlyPlayedEntity SET filePath = :newPath, fileName = :newFileName WHERE filePath = :oldPath")
  suspend fun updateFilePath(
    oldPath: String,
    newPath: String,
    newFileName: String,
  )
}
