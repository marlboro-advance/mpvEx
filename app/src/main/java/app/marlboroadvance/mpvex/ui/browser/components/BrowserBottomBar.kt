package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject

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
  val appearancePreferences = koinInject<AppearancePreferences>()
  val useFloatingNavigation by appearancePreferences.useFloatingNavigation.collectAsState()
  val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  AnimatedVisibility(
    visible = isSelectionMode,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
  ) {
    if (useFloatingNavigation) {
      androidx.compose.material3.Surface(
        modifier = modifier
          .fillMaxWidth()
          .padding(start = 14.dp, end = 14.dp, bottom = systemNavBarInset + 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 3.dp,
        tonalElevation = 2.dp
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BrowserBottomBarContent(
            onCopyClick, onMoveClick, onRenameClick, onDeleteClick, onAddToPlaylistClick,
            showCopy, showMove, showRename, showDelete, showAddToPlaylist
          )
        }
      }
    } else {
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
          BrowserBottomBarContent(
            onCopyClick, onMoveClick, onRenameClick, onDeleteClick, onAddToPlaylistClick,
            showCopy, showMove, showRename, showDelete, showAddToPlaylist
          )
        }
      }
    }
  }
}

@Composable
private fun BrowserBottomBarContent(
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    showCopy: Boolean,
    showMove: Boolean,
    showRename: Boolean,
    showDelete: Boolean,
    showAddToPlaylist: Boolean
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

        if (showAddToPlaylist) {
          IconButton(onClick = onAddToPlaylistClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
              contentDescription = "Add to Playlist",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        IconButton(
          onClick = onRenameClick,
          enabled = showRename,
        ) {
          Icon(
            imageVector = Icons.Default.DriveFileRenameOutline,
            contentDescription = "Rename",
            tint = if (showRename) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(
              alpha = 0.38f,
            ),
          )
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
