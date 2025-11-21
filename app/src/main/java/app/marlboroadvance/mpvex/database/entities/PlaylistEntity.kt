package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
)
