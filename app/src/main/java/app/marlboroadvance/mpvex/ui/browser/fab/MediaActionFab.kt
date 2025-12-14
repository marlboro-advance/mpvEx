package app.marlboroadvance.mpvex.ui.browser.fab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp

data class MediaActionItem(
  val icon: ImageVector,
  val label: String,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaActionFab(
  listState: LazyListState,
  hasRecentlyPlayed: Boolean,
  enableRecentlyPlayed: Boolean,
  onOpenFile: () -> Unit,
  onPlayRecentlyPlayed: () -> Unit,
  onPlayLink: () -> Unit,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }

  // Track scroll position to determine direction
  var previousIndex by remember { mutableIntStateOf(0) }
  var previousScrollOffset by remember { mutableIntStateOf(0) }
  var isFabVisible by remember { mutableStateOf(true) }

  // Update FAB visibility based on scroll direction
  LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
    val currentIndex = listState.firstVisibleItemIndex
    val currentScrollOffset = listState.firstVisibleItemScrollOffset

    // Always show at top
    if (currentIndex == 0 && currentScrollOffset == 0) {
      isFabVisible = true
    } else {
      // Calculate if scrolling down or up
      val isScrollingDown = if (currentIndex != previousIndex) {
        currentIndex > previousIndex
      } else {
        currentScrollOffset > previousScrollOffset
      }

      // Hide when scrolling down, show when scrolling up
      isFabVisible = !isScrollingDown
    }

    previousIndex = currentIndex
    previousScrollOffset = currentScrollOffset
  }

  // Auto-collapse menu when scrolling
  LaunchedEffect(listState.isScrollInProgress) {
    if (expanded && listState.isScrollInProgress) {
      onExpandedChange(false)
    }
  }

  // Handle back button to close menu
  BackHandler(enabled = expanded) {
    onExpandedChange(false)
  }

  // Build menu items based on state
  val menuItems =
    remember(hasRecentlyPlayed, enableRecentlyPlayed) {
      buildList {
        add(MediaActionItem(Icons.Filled.FolderOpen, "Open File", onClick = onOpenFile))
        add(
          MediaActionItem(
            Icons.Filled.History,
            "Recently Played",
            hasRecentlyPlayed && enableRecentlyPlayed, onPlayRecentlyPlayed,
          ),
        )
        add(MediaActionItem(Icons.Filled.AddLink, "Play Link", onClick = onPlayLink))
      }
    }

  FloatingActionButtonMenu(
    modifier = modifier,
    expanded = expanded,
    button = {
      ToggleFabButton(
        expanded = expanded,
        onToggle = { onExpandedChange(!expanded) },
        focusRequester = focusRequester,
        visible = isFabVisible,
      )
    },
  ) {
    menuItems.forEachIndexed { index, item ->
      FloatingActionButtonMenuItem(
        modifier =
          Modifier
            .semantics {
              isTraversalGroup = true
              // Add close action for the last item
              if (index == menuItems.lastIndex) {
                customActions =
                  listOf(
                    CustomAccessibilityAction("Close menu") {
                      onExpandedChange(false)
                      true
                    },
                  )
              }
            }
            .then(
              if (index == 0) {
                // First item can navigate back to FAB button
                Modifier.onKeyEvent { event ->
                  if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || (event.isShiftPressed && event.key == Key.Tab))
                  ) {
                    focusRequester.requestFocus()
                    true
                  } else {
                    false
                  }
                }
              } else {
                Modifier
              },
            ),
        onClick = {
          if (item.enabled) {
            onExpandedChange(false)
            item.onClick()
          }
        },
        icon = {
          Icon(
            item.icon,
            contentDescription = null,
            modifier = if (item.enabled) Modifier else Modifier.alpha(0.38f),
          )
        },
        text = {
          Text(
            item.label,
            modifier = if (item.enabled) Modifier else Modifier.alpha(0.38f),
          )
        },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToggleFabButton(
  expanded: Boolean,
  onToggle: () -> Unit,
  focusRequester: FocusRequester,
  visible: Boolean,
) {
  ToggleFloatingActionButton(
    modifier =
      Modifier
        .semantics {
          traversalIndex = -1f
          stateDescription = if (expanded) "Expanded" else "Collapsed"
          contentDescription = "Toggle menu"
        }
        .animateFloatingActionButton(
          visible = visible,
          alignment = Alignment.BottomEnd,
        )
        .focusRequester(focusRequester),
    checked = expanded,
    onCheckedChange = { onToggle() },
    containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
  ) {
    val icon by remember {
      derivedStateOf {
        if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.PlayArrow
      }
    }
    Icon(
      painter = rememberVectorPainter(icon),
      contentDescription = null,
      modifier =
        Modifier.animateIcon(
          checkedProgress = { checkedProgress },
          size =
            ToggleFloatingActionButtonDefaults.iconSize(
              initialSize = 40.dp,
              finalSize = 24.dp,
            ),
        ),
    )
  }
}
