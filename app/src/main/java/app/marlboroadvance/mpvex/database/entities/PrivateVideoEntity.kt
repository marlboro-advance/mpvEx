package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "private_videos")
data class PrivateVideoEntity(
  @PrimaryKey
  val videoId: Long, // The ID from MediaStore
  val originalPath: String, // The original file path (for reference)
  val privateFilePath: String, // The path in private storage
  val addedAt: Long = System.currentTimeMillis(),
)
