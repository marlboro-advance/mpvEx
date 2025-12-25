package app.marlboroadvance.mpvex.database.repository

import android.content.Context
import android.net.Uri
import app.marlboroadvance.mpvex.database.dao.PlaylistDao
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistItemEntity
import app.marlboroadvance.mpvex.utils.media.M3UParser
import app.marlboroadvance.mpvex.utils.media.M3UParseResult
import app.marlboroadvance.mpvex.utils.media.M3UPlaylistItem
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

  suspend fun removeItemsFromPlaylist(items: List<PlaylistItemEntity>) {
    if (items.isEmpty()) return
    // Use batch delete for better performance and to avoid race conditions
    playlistDao.deletePlaylistItems(items)
    // Update playlist's updatedAt timestamp
    getPlaylistById(items.first().playlistId)?.let { playlist ->
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

  /**
   * Get a windowed subset of playlist items as URIs to avoid loading huge playlists at once.
   * This prevents ANR issues and TransactionTooLargeException with large M3U playlists.
   * 
   * @param playlistId The playlist ID
   * @param centerIndex The index to center the window around (typically current playing position)
   * @param windowSize Total number of items to load (default 100)
   * @return List of URIs in the windowed range
   */
  suspend fun getPlaylistItemsWindowAsUris(
    playlistId: Int, 
    centerIndex: Int = 0, 
    windowSize: Int = 100
  ): List<Uri> {
    val totalCount = getPlaylistItemCount(playlistId)
    if (totalCount == 0) return emptyList()
    
    // If playlist is small enough, return all items
    if (totalCount <= windowSize) {
      return getPlaylistItemsAsUris(playlistId)
    }
    
    // Calculate window boundaries
    val halfWindow = windowSize / 2
    val startPosition = (centerIndex - halfWindow).coerceAtLeast(0)
    val endPosition = (startPosition + windowSize).coerceAtMost(totalCount)
    
    // Get items in range
    return playlistDao.getPlaylistItemsInRange(playlistId, startPosition, endPosition)
      .map { Uri.parse(it.filePath) }
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

  // M3U Playlist operations
  suspend fun createM3UPlaylist(url: String): Result<Long> {
    return try {
      val parseResult = M3UParser.parseFromUrl(url)
      
      when (parseResult) {
        is M3UParseResult.Success -> {
          val now = System.currentTimeMillis()
          val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
              name = parseResult.playlistName,
              createdAt = now,
              updatedAt = now,
              m3uSourceUrl = url,
              isM3uPlaylist = true
            )
          )
          
          // Add all items from the M3U playlist
          val items = parseResult.items.mapIndexed { index, m3uItem ->
            PlaylistItemEntity(
              playlistId = playlistId.toInt(),
              filePath = m3uItem.url,
              fileName = m3uItem.title ?: "Item ${index + 1}",
              position = index,
              addedAt = now
            )
          }
          
          playlistDao.insertPlaylistItems(items)
          
          Result.success(playlistId)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createM3UPlaylistFromFile(context: Context, uri: Uri): Result<Long> {
    return try {
      val parseResult = M3UParser.parseFromUri(context, uri)
      
      when (parseResult) {
        is M3UParseResult.Success -> {
          val now = System.currentTimeMillis()
          val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
              name = parseResult.playlistName,
              createdAt = now,
              updatedAt = now,
              m3uSourceUrl = null, // Local file, no URL to refresh from
              isM3uPlaylist = true
            )
          )
          
          // Add all items from the M3U playlist
          val items = parseResult.items.mapIndexed { index, m3uItem ->
            PlaylistItemEntity(
              playlistId = playlistId.toInt(),
              filePath = m3uItem.url,
              fileName = m3uItem.title ?: "Item ${index + 1}",
              position = index,
              addedAt = now
            )
          }
          
          playlistDao.insertPlaylistItems(items)
          
          Result.success(playlistId)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun refreshM3UPlaylist(playlistId: Int): Result<Unit> {
    return try {
      val playlist = getPlaylistById(playlistId)
        ?: return Result.failure(Exception("Playlist not found"))
      
      if (!playlist.isM3uPlaylist || playlist.m3uSourceUrl == null) {
        return Result.failure(Exception("Not an M3U playlist or no source URL available"))
      }
      
      val parseResult = M3UParser.parseFromUrl(playlist.m3uSourceUrl)
      
      when (parseResult) {
        is M3UParseResult.Success -> {
          // Clear existing items
          playlistDao.deleteAllItemsFromPlaylist(playlistId)
          
          // Add refreshed items
          val now = System.currentTimeMillis()
          val items = parseResult.items.mapIndexed { index, m3uItem ->
            PlaylistItemEntity(
              playlistId = playlistId,
              filePath = m3uItem.url,
              fileName = m3uItem.title ?: "Item ${index + 1}",
              position = index,
              addedAt = now
            )
          }
          
          playlistDao.insertPlaylistItems(items)
          updatePlaylist(playlist)
          
          Result.success(Unit)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
