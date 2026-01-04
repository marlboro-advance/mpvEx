package app.marlboroadvance.mpvex.ui.browser.fab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp

/**
 * FAB for playlist-related actions like creating a playlist or adding an M3U playlist
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistActionFab(
  listState: LazyListState,
  onCreatePlaylist: () -> Unit,
  onAddM3UPlaylist: () -> Unit,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  gridState: LazyGridState? = null,
) {
  val focusRequester = remember { FocusRequester() }
  val isFabVisible = remember { mutableStateOf(true) }

  // Use common scroll tracking for FAB visibility
  FabScrollHelper.trackScrollForFabVisibility(
    listState = listState,
    gridState = gridState,
    isFabVisible = isFabVisible,
    expanded = expanded,
    onExpandedChange = onExpandedChange
  )

  // Close menu on back press
  BackHandler(enabled = expanded) {
    onExpandedChange(false)
  }

  // Request focus when expanded to capture keyboard events
  LaunchedEffect(expanded) {
    if (expanded) {
      focusRequester.requestFocus()
    }
  }

  FloatingActionButtonMenu(
    modifier = modifier,
    expanded = expanded,
    button = {
      ToggleFloatingActionButton(
        checked = expanded,
        onCheckedChange = { onExpandedChange(!expanded) },
        containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
        modifier = Modifier
          .semantics {
            traversalIndex = -1f
            stateDescription = if (expanded) "Expanded" else "Collapsed"
            contentDescription = "Playlist actions menu"
          }
          .animateFloatingActionButton(
            visible = isFabVisible.value,
            alignment = Alignment.BottomEnd
          )
          .focusRequester(focusRequester)
          .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
              when {
                // Close on Escape
                keyEvent.key == Key.Escape -> {
                  onExpandedChange(false)
                  true
                }
                // Close on Shift + Tab (reverse tab)
                keyEvent.key == Key.Tab && keyEvent.isShiftPressed -> {
                  onExpandedChange(false)
                  true
                }
                else -> false
              }
            } else {
              false
            }
          },
      ) {
        val icon by remember {
          derivedStateOf {
            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
          }
        }
        Icon(
          painter = rememberVectorPainter(icon),
          contentDescription = null,
          modifier = Modifier.animateIcon(
            checkedProgress = { checkedProgress },
            size = ToggleFloatingActionButtonDefaults.iconSize(
              initialSize = 40.dp,
              finalSize = 24.dp,
            ),
          ),
        )
      }
    },
  ) {
    // Create playlist menu item
    FloatingActionButtonMenuItem(
      onClick = {
        onExpandedChange(false)
        onCreatePlaylist()
      },
      icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
      text = { Text("Create Empty Playlist") },
      modifier = Modifier.semantics { 
        traversalIndex = 0f
      },
    )
    
    // Add M3U playlist menu item
    FloatingActionButtonMenuItem(
      onClick = {
        onExpandedChange(false)
        onAddM3UPlaylist()
      },
      icon = { Icon(Icons.Filled.Link, contentDescription = null) },
      text = { Text("Add m3u playlist") },
      modifier = Modifier.semantics {
        traversalIndex = 1f
      },
    )
  }
}
