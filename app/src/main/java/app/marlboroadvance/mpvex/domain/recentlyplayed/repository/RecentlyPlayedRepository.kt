package app.marlboroadvance.mpvex.domain.recentlyplayed.repository

import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

interface RecentlyPlayedRepository {

  suspend fun addRecentlyPlayed(filePath: String, fileName: String, launchSource: String? = null)

  suspend fun getLastPlayed(): RecentlyPlayedEntity?

  fun observeLastPlayed(): Flow<RecentlyPlayedEntity?>

  suspend fun getLastPlayedForHighlight(): RecentlyPlayedEntity?

  fun observeLastPlayedForHighlight(): Flow<RecentlyPlayedEntity?>

  suspend fun getRecentlyPlayed(limit: Int = 10): List<RecentlyPlayedEntity>

  suspend fun clearAll()
}
