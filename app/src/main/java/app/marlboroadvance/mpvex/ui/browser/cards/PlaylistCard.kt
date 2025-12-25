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

  // Create a custom chip renderer for playlist type
  val customChipRenderer: @Composable () -> Unit = {
    androidx.compose.foundation.layout.Row {
      // Add the playlist type chip (Network or Local)
      val chipText = if (playlist.isM3uPlaylist) "Network" else "Local"
      
      // Use Material Design theme colors
      val materialTheme = androidx.compose.material3.MaterialTheme.colorScheme
      val (chipColor, chipBgColor) = if (playlist.isM3uPlaylist) {
        // Network: use tertiary color (usually blue/purple in Material themes)
        Pair(materialTheme.tertiary, materialTheme.tertiaryContainer)
      } else {
        // Local: use primary color (usually brand color in Material themes)
        Pair(materialTheme.primary, materialTheme.primaryContainer)
      }
        
      androidx.compose.material3.Text(
        text = chipText,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = androidx.compose.ui.Modifier
          .background(
            chipBgColor,
            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
          )
          .padding(horizontal = 8.dp, vertical = 4.dp),
        color = chipColor,
      )
    }
  }

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
    customChipContent = customChipRenderer
  )
}
