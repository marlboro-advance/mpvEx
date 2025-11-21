package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["playlistId"])],
)
data class PlaylistItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val playlistId: Int,
  val filePath: String,
  val fileName: String,
  val position: Int, // Order in playlist
  val addedAt: Long,
  val lastPlayedAt: Long = 0, // When this video was last played from this playlist
  val playCount: Int = 0, // How many times played from this playlist
  val lastPosition: Long = 0, // Last playback position in milliseconds
)
