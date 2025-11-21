package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.domain.media.model.Video

sealed class RecentlyPlayedItem {
  abstract val timestamp: Long

  data class VideoItem(
    val video: Video,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()

  data class PlaylistItem(
    val playlist: PlaylistEntity,
    val videoCount: Int,
    val mostRecentVideoPath: String,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()
}
