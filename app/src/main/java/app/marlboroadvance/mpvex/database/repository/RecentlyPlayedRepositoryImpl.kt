package app.marlboroadvance.mpvex.database.repository

import app.marlboroadvance.mpvex.database.dao.RecentlyPlayedDao
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.flow.Flow

class RecentlyPlayedRepositoryImpl(
  private val recentlyPlayedDao: RecentlyPlayedDao,
) : RecentlyPlayedRepository {
  override suspend fun addRecentlyPlayed(
    filePath: String,
    fileName: String,
    videoTitle: String?,
    duration: Long,
    fileSize: Long,
    width: Int,
    height: Int,
    launchSource: String?,
    playlistId: Int?,
  ) {
    recentlyPlayedDao.deleteExistingEntriesForFile(filePath)

    val entity =
      RecentlyPlayedEntity(
        filePath = filePath,
        fileName = fileName,
        videoTitle = videoTitle,
        duration = duration,
        fileSize = fileSize,
        width = width,
        height = height,
        timestamp = System.currentTimeMillis(),
        launchSource = launchSource,
        playlistId = playlistId,
      )
    recentlyPlayedDao.insert(entity)
  }

  override suspend fun getLastPlayed(): RecentlyPlayedEntity? = recentlyPlayedDao.getLastPlayed()

  override fun observeLastPlayed(): Flow<RecentlyPlayedEntity?> = recentlyPlayedDao.observeLastPlayed()

  override suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity? =
    recentlyPlayedDao.getLastPlayedForHighlight()

  override fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?> =
    recentlyPlayedDao.observeLastPlayedForHighlight()

  override suspend fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedEntity> =
    recentlyPlayedDao.getRecentlyPlayed(limit)

  override fun observeRecentlyPlayed(limit: Int): Flow<List<RecentlyPlayedEntity>> =
    recentlyPlayedDao.observeRecentlyPlayed(limit)

  override suspend fun getRecentlyPlayedBySource(
    launchSource: String,
    limit: Int,
  ): List<RecentlyPlayedEntity> = recentlyPlayedDao.getRecentlyPlayedBySource(launchSource, limit)

  override suspend fun clearAll() {
    recentlyPlayedDao.clearAll()
  }

  override suspend fun deleteByFilePath(filePath: String) {
    recentlyPlayedDao.deleteByFilePath(filePath)
  }

  override suspend fun deleteByPlaylistId(playlistId: Int) {
    recentlyPlayedDao.deleteByPlaylistId(playlistId)
  }

  override suspend fun updateFilePath(
    oldPath: String,
    newPath: String,
    newFileName: String,
  ) {
    recentlyPlayedDao.updateFilePath(oldPath, newPath, newFileName)
  }

  override suspend fun updateVideoTitle(
    filePath: String,
    videoTitle: String,
  ) {
    recentlyPlayedDao.updateVideoTitle(filePath, videoTitle)
  }

  override suspend fun updateVideoMetadata(
    filePath: String,
    videoTitle: String?,
    duration: Long,
    fileSize: Long,
    width: Int,
    height: Int,
  ) {
    recentlyPlayedDao.updateVideoMetadata(filePath, videoTitle, duration, fileSize, width, height)
  }
}
