package app.sfsakhawat999.mpvrex.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val sid: Int,
  val subDelay: Int,
  val subSpeed: Double,
  val secondarySid: Int,
  val secondarySubDelay: Int,
  val aid: Int,
  val audioDelay: Int,
  val timeRemaining: Int = 0, // in seconds (duration - lastPosition)
)
