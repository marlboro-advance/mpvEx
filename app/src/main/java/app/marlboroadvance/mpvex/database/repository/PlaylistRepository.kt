package app.marlboroadvance.mpvex.database.repository

import android.net.Uri
import app.marlboroadvance.mpvex.database.dao.PlaylistDao
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {
  // Playlist operations
  suspend fun createPlaylist(name: String): Long {
    val now = System.currentTimeMillis()
    return playlistDao.insertPlaylist(
      PlaylistEntity(
        name = name,
        createdAt = now,
        updatedAt = now,
      ),
    )
  }

  suspend fun updatePlaylist(playlist: PlaylistEntity) {
    playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    playlistDao.deletePlaylist(playlist)
  }

  fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAllPlaylists()

  suspend fun getAllPlaylists(): List<PlaylistEntity> = playlistDao.getAllPlaylists()

  suspend fun getPlaylistById(playlistId: Int): PlaylistEntity? = playlistDao.getPlaylistById(playlistId)

  fun observePlaylistById(playlistId: Int): Flow<PlaylistEntity?> = playlistDao.observePlaylistById(playlistId)

  // Playlist item operations
  suspend fun addItemToPlaylist(playlistId: Int, filePath: String, fileName: String) {
    val maxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
    playlistDao.insertPlaylistItem(
      PlaylistItemEntity(
        playlistId = playlistId,
        filePath = filePath,
        fileName = fileName,
        position = maxPosition + 1,
        addedAt = System.currentTimeMillis(),
      ),
    )
    // Update playlist's updatedAt timestamp
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun addItemsToPlaylist(playlistId: Int, items: List<Pair<String, String>>) {
    val maxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
    val now = System.currentTimeMillis()
    val playlistItems = items.mapIndexed { index, (filePath, fileName) ->
      PlaylistItemEntity(
        playlistId = playlistId,
        filePath = filePath,
        fileName = fileName,
        position = maxPosition + 1 + index,
        addedAt = now,
      )
    }
    playlistDao.insertPlaylistItems(playlistItems)
    // Update playlist's updatedAt timestamp
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemFromPlaylist(item: PlaylistItemEntity) {
    playlistDao.deletePlaylistItem(item)
    // Update playlist's updatedAt timestamp
    getPlaylistById(item.playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemById(itemId: Int) {
    playlistDao.deletePlaylistItemById(itemId)
  }

  suspend fun clearPlaylist(playlistId: Int) {
    playlistDao.deleteAllItemsFromPlaylist(playlistId)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  fun observePlaylistItems(playlistId: Int): Flow<List<PlaylistItemEntity>> =
    playlistDao.observePlaylistItems(playlistId)

  suspend fun getPlaylistItems(playlistId: Int): List<PlaylistItemEntity> =
    playlistDao.getPlaylistItems(playlistId)

  fun observePlaylistItemCount(playlistId: Int): Flow<Int> =
    playlistDao.observePlaylistItemCount(playlistId)

  suspend fun getPlaylistItemCount(playlistId: Int): Int =
    playlistDao.getPlaylistItemCount(playlistId)

  suspend fun reorderPlaylistItems(playlistId: Int, newOrder: List<Int>) {
    playlistDao.reorderPlaylistItems(playlistId, newOrder)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  // Helper to get playlist items as URIs for playback
  suspend fun getPlaylistItemsAsUris(playlistId: Int): List<Uri> {
    return getPlaylistItems(playlistId).map { Uri.parse(it.filePath) }
  }

  // Play history operations
  suspend fun updatePlayHistory(playlistId: Int, filePath: String, position: Long = 0) {
    playlistDao.updatePlayHistory(playlistId, filePath, System.currentTimeMillis(), position)
  }

  suspend fun getRecentlyPlayedInPlaylist(playlistId: Int, limit: Int = 20): List<PlaylistItemEntity> {
    return playlistDao.getRecentlyPlayedInPlaylist(playlistId, limit)
  }

  fun observeRecentlyPlayedInPlaylist(playlistId: Int, limit: Int = 20): Flow<List<PlaylistItemEntity>> {
    return playlistDao.observeRecentlyPlayedInPlaylist(playlistId, limit)
  }

  suspend fun getPlaylistItemByPath(playlistId: Int, filePath: String): PlaylistItemEntity? {
    return playlistDao.getPlaylistItemByPath(playlistId, filePath)
  }
}
