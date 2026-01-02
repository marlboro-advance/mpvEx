package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached video metadata to avoid repeated MediaInfo extractions
 * Stores duration, size, resolution, and framerate for videos that require MediaInfo processing
 */
@Entity(tableName = "video_metadata_cache")
data class VideoMetadataEntity(
  @PrimaryKey
  val path: String, // Unique identifier - absolute file path
  val size: Long, // File size in bytes
  val dateModified: Long, // Last modified timestamp (for cache invalidation)
  val duration: Long, // Duration in milliseconds
  val width: Int, // Video width in pixels
  val height: Int, // Video height in pixels
  val fps: Float, // Framerate in frames per second
  val hasEmbeddedSubtitles: Boolean,
  val subtitleCodec:  String = "",
  val lastScanned: Long, // When this metadata was extracted (timestamp)
)
