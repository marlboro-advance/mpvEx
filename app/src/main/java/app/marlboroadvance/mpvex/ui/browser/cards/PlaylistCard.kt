package app.marlboroadvance.mpvex.ui.browser.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder

@Composable
fun PlaylistCard(
  playlist: PlaylistEntity,
  itemCount: Int,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onThumbClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
) {
  // Convert playlist to VideoFolder format for FolderCard
  val folderModel = VideoFolder(
    bucketId = playlist.id.toString(),
    name = playlist.name,
    path = "", // Not used for playlists
    videoCount = itemCount,
    totalSize = 0, // Not tracked for playlists
    totalDuration = 0, // Not tracked for playlists
    lastModified = playlist.updatedAt / 1000,
  )

  FolderCard(
    folder = folderModel,
    isSelected = isSelected,
    isRecentlyPlayed = false,
    onClick = onClick,
    onLongClick = onLongClick,
    onThumbClick = onThumbClick,
    showDateModified = true,
    customIcon = Icons.Filled.PlaylistPlay,
    modifier = modifier,
  )
}
