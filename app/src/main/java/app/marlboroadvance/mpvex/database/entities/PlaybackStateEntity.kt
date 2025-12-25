package app.marlboroadvance.mpvex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val videoZoom: Float = 0f,
  val sid: Int,
  val subDelay: Int,
  val subSpeed: Double,
  val aid: Int,
  val audioDelay: Int,
  val timeRemaining: Int = 0, // in seconds (duration - lastPosition)
  val externalSubtitles: String = "", // Comma-separated list of external subtitle URIs
)
