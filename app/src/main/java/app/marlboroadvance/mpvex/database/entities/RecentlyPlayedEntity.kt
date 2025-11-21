package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RecentlyPlayedEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val filePath: String,
  val fileName: String,
  val timestamp: Long,
  val launchSource: String? = null, // null or empty means normal playback from list
  val playlistId: Int? = null, // ID of playlist if played from a playlist
)
