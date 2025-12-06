package app.marlboroadvance.mpvex.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
  // Playlist operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Update
  suspend fun updatePlaylist(playlist: PlaylistEntity)

  @Delete
  suspend fun deletePlaylist(playlist: PlaylistEntity)

  @Query("SELECT * FROM PlaylistEntity ORDER BY updatedAt DESC")
  fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM PlaylistEntity ORDER BY updatedAt DESC")
  suspend fun getAllPlaylists(): List<PlaylistEntity>

  @Query("SELECT * FROM PlaylistEntity WHERE id = :playlistId")
  suspend fun getPlaylistById(playlistId: Int): PlaylistEntity?

  @Query("SELECT * FROM PlaylistEntity WHERE id = :playlistId")
  fun observePlaylistById(playlistId: Int): Flow<PlaylistEntity?>

  // Playlist item operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItem(item: PlaylistItemEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

  @Update
  suspend fun updatePlaylistItem(item: PlaylistItemEntity)

  @Delete
  suspend fun deletePlaylistItem(item: PlaylistItemEntity)

  @Query("DELETE FROM PlaylistItemEntity WHERE id = :itemId")
  suspend fun deletePlaylistItemById(itemId: Int)

  @Query("SELECT * FROM PlaylistItemEntity WHERE playlistId = :playlistId ORDER BY position ASC")
  fun observePlaylistItems(playlistId: Int): Flow<List<PlaylistItemEntity>>

  @Query("SELECT * FROM PlaylistItemEntity WHERE playlistId = :playlistId ORDER BY position ASC")
  suspend fun getPlaylistItems(playlistId: Int): List<PlaylistItemEntity>

  @Query("SELECT COUNT(*) FROM PlaylistItemEntity WHERE playlistId = :playlistId")
  suspend fun getPlaylistItemCount(playlistId: Int): Int

  @Query("SELECT COUNT(*) FROM PlaylistItemEntity WHERE playlistId = :playlistId")
  fun observePlaylistItemCount(playlistId: Int): Flow<Int>

  @Query("DELETE FROM PlaylistItemEntity WHERE playlistId = :playlistId")
  suspend fun deleteAllItemsFromPlaylist(playlistId: Int)

  @Query("UPDATE PlaylistItemEntity SET position = :newPosition WHERE id = :itemId")
  suspend fun updateItemPosition(itemId: Int, newPosition: Int)

  @Transaction
  suspend fun reorderPlaylistItems(playlistId: Int, newOrder: List<Int>) {
    newOrder.forEachIndexed { index, itemId ->
      updateItemPosition(itemId, index)
    }
  }

  @Query("SELECT MAX(position) FROM PlaylistItemEntity WHERE playlistId = :playlistId")
  suspend fun getMaxPosition(playlistId: Int): Int?

  // Play history operations
  @Query(
    """
    UPDATE PlaylistItemEntity 
    SET lastPlayedAt = :timestamp, playCount = playCount + 1, lastPosition = :position
    WHERE playlistId = :playlistId AND filePath = :filePath
    """,
  )
  suspend fun updatePlayHistory(playlistId: Int, filePath: String, timestamp: Long, position: Long)

  @Query(
    """
    SELECT * FROM PlaylistItemEntity 
    WHERE playlistId = :playlistId 
    ORDER BY lastPlayedAt DESC
    LIMIT :limit
    """,
  )
  suspend fun getRecentlyPlayedInPlaylist(playlistId: Int, limit: Int): List<PlaylistItemEntity>

  @Query(
    """
    SELECT * FROM PlaylistItemEntity 
    WHERE playlistId = :playlistId 
    ORDER BY lastPlayedAt DESC
    LIMIT :limit
    """,
  )
  fun observeRecentlyPlayedInPlaylist(playlistId: Int, limit: Int): Flow<List<PlaylistItemEntity>>

  @Query(
    """
    SELECT * FROM PlaylistItemEntity 
    WHERE playlistId = :playlistId AND filePath = :filePath
    """,
  )
  suspend fun getPlaylistItemByPath(playlistId: Int, filePath: String): PlaylistItemEntity?
}
