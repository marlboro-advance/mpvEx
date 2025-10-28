package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "video_index",
  indices = [
    Index(value = ["path"], unique = true),
    Index(value = ["bucketId"]),
    Index(value = ["lastModified"]),
  ],
)
data class VideoIndexEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val path: String,
  val displayName: String,
  val title: String,
  val size: Long,
  val duration: Long,
  val dateModified: Long,
  val dateAdded: Long,
  val lastModified: Long, // File's last modified timestamp for cache validation
  val mimeType: String,
  val bucketId: String,
  val bucketDisplayName: String,
  val lastIndexed: Long = System.currentTimeMillis(), // When this entry was last indexed
)
