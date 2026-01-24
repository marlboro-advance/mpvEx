package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material 3 Floating Button Bar for file/folder operations
 * Icon-only buttons in a floating pill-shaped surface
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserBottomBar(
  isSelectionMode: Boolean,
  onCopyClick: () -> Unit,
  onMoveClick: () -> Unit,
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onAddToPlaylistClick: () -> Unit,
  modifier: Modifier = Modifier,
  showCopy: Boolean = true,
  showMove: Boolean = true,
  showRename: Boolean = true,
  showDelete: Boolean = true,
  showAddToPlaylist: Boolean = true,
) {
  AnimatedVisibility(
    visible = isSelectionMode,
    modifier = modifier,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Surface(
      modifier = Modifier
        .windowInsetsPadding(WindowInsets.systemBars)
        .padding(horizontal = 20.dp, vertical = 8.dp),
      shape = RoundedCornerShape(32.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 3.dp,
      shadowElevation = 8.dp
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalIconButton(
          onClick = onCopyClick,
          enabled = showCopy,
          modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
          Icon(
            Icons.Filled.ContentCopy, 
            contentDescription = "Copy",
            modifier = Modifier.size(24.dp)
          )
        }
        
        FilledTonalIconButton(
          onClick = onMoveClick,
          enabled = showMove,
          modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
          Icon(
            Icons.AutoMirrored.Filled.DriveFileMove, 
            contentDescription = "Move",
            modifier = Modifier.size(24.dp)
          )
        }
        
        FilledTonalIconButton(
          onClick = onRenameClick,
          enabled = showRename,
          modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
          Icon(
            Icons.Filled.DriveFileRenameOutline, 
            contentDescription = "Rename",
            modifier = Modifier.size(24.dp)
          )
        }
        
        FilledTonalIconButton(
          onClick = onAddToPlaylistClick,
          enabled = showAddToPlaylist,
          modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
          Icon(
            Icons.AutoMirrored.Filled.PlaylistAdd, 
            contentDescription = "Add to Playlist",
            modifier = Modifier.size(24.dp)
          )
        }
        
        FilledTonalIconButton(
          onClick = onDeleteClick,
          enabled = showDelete,
          modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
          )
        ) {
          Icon(
            Icons.Filled.Delete, 
            contentDescription = "Delete",
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }
  }
}
