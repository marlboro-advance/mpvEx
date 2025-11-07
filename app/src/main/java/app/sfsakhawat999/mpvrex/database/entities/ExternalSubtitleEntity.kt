package app.sfsakhawat999.mpvrex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ExternalSubtitleEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val originalUri: String, // Original URI from which subtitle was loaded
  val originalFileName: String, // Original filename for display
  val cachedFilePath: String, // Path to cached file in internal storage
  val mediaTitle: String, // Associated media file (for cleanup when media changes)
  val addedTimestamp: Long = System.currentTimeMillis(), // When it was added
)
