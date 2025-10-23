package app.marlboroadvance.mpvex.database.repository

import app.marlboroadvance.mpvex.database.dao.RecentlyPlayedDao
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import kotlinx.coroutines.flow.Flow

class RecentlyPlayedRepositoryImpl(
  private val recentlyPlayedDao: RecentlyPlayedDao,
) : RecentlyPlayedRepository {

  override suspend fun addRecentlyPlayed(filePath: String, fileName: String, launchSource: String?) {
    val entity = RecentlyPlayedEntity(
      filePath = filePath,
      fileName = fileName,
      timestamp = System.currentTimeMillis(),
      launchSource = launchSource,
    )
    recentlyPlayedDao.insert(entity)
  }

  override suspend fun getLastPlayed(): RecentlyPlayedEntity? {
    return recentlyPlayedDao.getLastPlayed()
  }

  override fun observeLastPlayed(): Flow<RecentlyPlayedEntity?> {
    return recentlyPlayedDao.observeLastPlayed()
  }

  override suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity? {
    return recentlyPlayedDao.getLastPlayedForHighlight()
  }

  override fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?> {
    return recentlyPlayedDao.observeLastPlayedForHighlight()
  }

  override suspend fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedEntity> {
    return recentlyPlayedDao.getRecentlyPlayed(limit)
  }

  override suspend fun clearAll() {
    recentlyPlayedDao.clearAll()
  }
}
