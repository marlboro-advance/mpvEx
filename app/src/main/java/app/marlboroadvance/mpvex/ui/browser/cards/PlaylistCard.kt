package app.marlboroadvance.mpvex.ui.browser.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder

/**
 * Card for displaying a playlist item
 * 
 * @param playlist The playlist entity to display
 * @param itemCount Number of items in the playlist
 * @param onClick Action to perform when the card is clicked
 * @param onLongClick Action to perform when the card is long-pressed
 * @param onThumbClick Action to perform when the thumbnail is clicked
 * @param modifier Optional modifier for the card
 * @param isSelected Whether the card is in a selected state
 * @param isGridMode Whether the card should display in grid mode
 */
@Composable
fun PlaylistCard(
  playlist: PlaylistEntity,
  itemCount: Int,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onThumbClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSelected: Boolean = false,
  isGridMode: Boolean = false,
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

  // Create a custom chip renderer for playlist type
  val customChipRenderer: @Composable () -> Unit = {
    // Add the playlist type chip (Network or Local)
    val chipText = if (playlist.isM3uPlaylist) "Network" else "Local"

    // Use Material Design theme colors
    val materialTheme = androidx.compose.material3.MaterialTheme.colorScheme
    val (chipColor, chipBgColor) = if (playlist.isM3uPlaylist) {
      Pair(materialTheme.tertiary, materialTheme.tertiaryContainer)
    } else {
      Pair(materialTheme.primary, materialTheme.primaryContainer)
    }

    androidx.compose.material3.Text(
      text = chipText,
      style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
      modifier = Modifier
        .background(
          chipBgColor,
          androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )
        .padding(horizontal = 8.dp, vertical = 4.dp),
      color = chipColor,
    )
  }

  // Use the FolderCard component with playlist-specific customizations
  FolderCard(
    folder = folderModel,
    isSelected = isSelected,
    isRecentlyPlayed = false,
    onClick = onClick,
    onLongClick = onLongClick,
    onThumbClick = onThumbClick,
    showDateModified = true,
    customIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
    modifier = modifier,
    customChipContent = customChipRenderer,
    isGridMode = isGridMode
  )
}
