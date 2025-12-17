package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(recentlyPlayed: RecentlyPlayedEntity)

  @Query("DELETE FROM RecentlyPlayedEntity WHERE filePath = :filePath")
  suspend fun deleteExistingEntriesForFile(filePath: String)

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT 1")
  suspend fun getLastPlayed(): RecentlyPlayedEntity?

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT 1")
  fun observeLastPlayed(): Flow<RecentlyPlayedEntity?>

  @Query(
    """
    SELECT * FROM RecentlyPlayedEntity 
    WHERE launchSource IS NULL OR launchSource = '' OR launchSource = 'normal' OR launchSource = 'playlist' OR launchSource = 'video_list'
    ORDER BY timestamp DESC 
    LIMIT 1
  """,
  )
  suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity?

  @Query(
    """
    SELECT * FROM RecentlyPlayedEntity 
    WHERE launchSource IS NULL OR launchSource = '' OR launchSource = 'normal' OR launchSource = 'playlist' OR launchSource = 'video_list'
    ORDER BY timestamp DESC 
    LIMIT 1
  """,
  )
  fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?>

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentlyPlayed(limit: Int = 10): List<RecentlyPlayedEntity>

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC LIMIT :limit")
  fun observeRecentlyPlayed(limit: Int = 50): Flow<List<RecentlyPlayedEntity>>

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

  @Query("DELETE FROM RecentlyPlayedEntity WHERE playlistId = :playlistId")
  suspend fun deleteByPlaylistId(playlistId: Int)

  @Query("UPDATE RecentlyPlayedEntity SET filePath = :newPath, fileName = :newFileName WHERE filePath = :oldPath")
  suspend fun updateFilePath(
    oldPath: String,
    newPath: String,
    newFileName: String,
  )

  @Query("UPDATE RecentlyPlayedEntity SET videoTitle = :videoTitle WHERE filePath = :filePath")
  suspend fun updateVideoTitle(
    filePath: String,
    videoTitle: String,
  )

  @Query("UPDATE RecentlyPlayedEntity SET videoTitle = :videoTitle, duration = :duration, fileSize = :fileSize, width = :width, height = :height WHERE filePath = :filePath")
  suspend fun updateVideoMetadata(
    filePath: String,
    videoTitle: String?,
    duration: Long,
    fileSize: Long,
    width: Int,
    height: Int,
  )

  @Query(
    """
    SELECT DISTINCT playlistId, MAX(timestamp) as timestamp
    FROM RecentlyPlayedEntity 
    WHERE playlistId IS NOT NULL
    GROUP BY playlistId
    ORDER BY timestamp DESC
    LIMIT :limit
  """,
  )
  suspend fun getRecentlyPlayedPlaylists(limit: Int = 10): List<RecentlyPlayedPlaylistInfo>

  @Query(
    """
    SELECT DISTINCT playlistId, MAX(timestamp) as timestamp
    FROM RecentlyPlayedEntity 
    WHERE playlistId IS NOT NULL
    GROUP BY playlistId
    ORDER BY timestamp DESC
    LIMIT :limit
  """,
  )
  fun observeRecentlyPlayedPlaylists(limit: Int = 50): Flow<List<RecentlyPlayedPlaylistInfo>>

  data class RecentlyPlayedPlaylistInfo(
    val playlistId: Int,
    val timestamp: Long,
  )

  @Query("SELECT * FROM RecentlyPlayedEntity ORDER BY timestamp DESC")
  suspend fun getAllRecentlyPlayed(): List<RecentlyPlayedEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(items: List<RecentlyPlayedEntity>)
}
