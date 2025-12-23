package app.marlboroadvance.mpvex.domain.recentlyplayed.repository

import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

interface RecentlyPlayedRepository {
  suspend fun addRecentlyPlayed(
    filePath: String,
    fileName: String,
    videoTitle: String? = null,
    duration: Long = 0,
    fileSize: Long = 0,
    width: Int = 0,
    height: Int = 0,
    launchSource: String? = null,
    playlistId: Int? = null,
  )

  suspend fun getLastPlayed(): RecentlyPlayedEntity?

  fun observeLastPlayed(): Flow<RecentlyPlayedEntity?>

  suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity?

  fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?>

  suspend fun getRecentlyPlayed(limit: Int = 10): List<RecentlyPlayedEntity>

  fun observeRecentlyPlayed(limit: Int = 50): Flow<List<RecentlyPlayedEntity>>

  suspend fun getRecentlyPlayedBySource(
    launchSource: String,
    limit: Int = 10,
  ): List<RecentlyPlayedEntity>

  suspend fun clearAll()

  suspend fun deleteByFilePath(filePath: String)

  suspend fun deleteByPlaylistId(playlistId: Int)

  suspend fun updateFilePath(
    oldPath: String,
    newPath: String,
    newFileName: String,
  )

  suspend fun updateVideoTitle(
    filePath: String,
    videoTitle: String,
  )

  suspend fun updateVideoMetadata(
    filePath: String,
    videoTitle: String?,
    duration: Long,
    fileSize: Long,
    width: Int,
    height: Int,
  )
}
