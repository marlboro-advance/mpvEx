package app.sfsakhawat999.mpvrex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrowserBottomBar(
  isSelectionMode: Boolean,
  onCopyClick: () -> Unit,
  onMoveClick: () -> Unit,
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
  showCopy: Boolean = true,
  showMove: Boolean = true,
  showRename: Boolean = true,
  showDelete: Boolean = true,
) {
  AnimatedVisibility(
    visible = isSelectionMode,
    enter = slideInVertically(initialOffsetY = { it }),
    exit = slideOutVertically(targetOffsetY = { it }),
  ) {
    BottomAppBar(
      modifier = modifier,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      tonalElevation = 3.dp,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showCopy) {
          IconButton(onClick = onCopyClick) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        if (showMove) {
          IconButton(onClick = onMoveClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
              contentDescription = "Move",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        if (showRename) {
          IconButton(onClick = onRenameClick) {
            Icon(
              imageVector = Icons.Default.DriveFileRenameOutline,
              contentDescription = "Rename",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        if (showDelete) {
          IconButton(onClick = onDeleteClick) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}
