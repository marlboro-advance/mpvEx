package app.marlboroadvance.mpvex.ui.browser.components

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
import androidx.compose.material.icons.filled.VisibilityOff
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
  onHideClick: () -> Unit,
  modifier: Modifier = Modifier,
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
        IconButton(onClick = onCopyClick) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        IconButton(onClick = onMoveClick) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
            contentDescription = "Move",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        IconButton(onClick = onRenameClick) {
          Icon(
            imageVector = Icons.Default.DriveFileRenameOutline,
            contentDescription = "Rename",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        IconButton(onClick = onDeleteClick) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error,
          )
        }

        IconButton(onClick = onHideClick) {
          Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = "Hide",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}
