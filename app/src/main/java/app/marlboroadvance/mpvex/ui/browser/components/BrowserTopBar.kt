package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Unified top bar for browser screens that switches between normal and selection modes
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserTopBar(
  title: String,
  isInSelectionMode: Boolean,
  selectedCount: Int,
  totalCount: Int,
  onCancelSelection: () -> Unit,
  modifier: Modifier = Modifier,
  onBackClick: (() -> Unit)? = null,
  onNavigationClick: (() -> Unit)? = null,
  onSortClick: (() -> Unit)? = null,
  onSearchClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onRenameClick: (() -> Unit)? = null,
  isSingleSelection: Boolean = false,
  onInfoClick: (() -> Unit)? = null,
  onShareClick: (() -> Unit)? = null,
  onPlayClick: (() -> Unit)? = null,
  onBlacklistClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  additionalActions: @Composable RowScope.() -> Unit = { },
  onTitleLongPress: (() -> Unit)? = null,
  useRemoveIcon: Boolean = false,
) {
  if (isInSelectionMode) {
    SelectionTopBar(
      selectedCount = selectedCount,
      totalCount = totalCount,
      onCancel = onCancelSelection,
      onDelete = onDeleteClick,
      onRename = onRenameClick,
      isSingleSelection = isSingleSelection,
      onInfo = onInfoClick,
      onShare = onShareClick,
      onPlay = onPlayClick,
      onBlacklist = onBlacklistClick,
      onSelectAll = onSelectAll,
      onInvertSelection = onInvertSelection,
      onDeselectAll = onDeselectAll,
      modifier = modifier,
      useRemoveIcon = useRemoveIcon,
    )
  } else {
    NormalTopBar(
      title = title,
      onBackClick = onBackClick,
      onNavigationClick = onNavigationClick,
      onSortClick = onSortClick,
      onSearchClick = onSearchClick,
      additionalActions = additionalActions,
      modifier = modifier,
      onTitleLongPress = onTitleLongPress,
    )
  }
}

/**
 * Normal mode top bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalTopBar(
  title: String,
  onBackClick: (() -> Unit)?,
  onNavigationClick: (() -> Unit)?,
  onSortClick: (() -> Unit)?,
  onSearchClick: (() -> Unit)?,
  additionalActions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  onTitleLongPress: (() -> Unit)?,
) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val themeTransition = LocalThemeTransitionState.current
  val coroutineScope = rememberCoroutineScope()
  
  // Track title bounds for animation position
  val titleBounds = remember { mutableStateOf(Rect.Zero) }
  
  // Helper function to toggle dark mode
  fun toggleDarkMode() {
    when (darkMode) {
      DarkMode.System -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Light -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.System)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Dark -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.System)
      }
    }
  }

  TopAppBar(
    title = {
      val titleModifier = Modifier
        .onGloballyPositioned { coordinates ->
          titleBounds.value = coordinates.boundsInWindow()
        }
        .pointerInput(onTitleLongPress) {
          detectTapGestures(
            onTap = { localOffset ->
              // Don't allow theme change if animation is in progress
              if (themeTransition?.isAnimating == true) return@detectTapGestures
              
              // Calculate window position for circular reveal
              val windowOffset = Offset(
                titleBounds.value.left + localOffset.x,
                titleBounds.value.top + localOffset.y
              )
              themeTransition?.startTransition(windowOffset)
              // Delay theme change to allow overlay to display first
              coroutineScope.launch {
                toggleDarkMode()
              }
            },
            onLongPress = if (onTitleLongPress != null) {
              { onTitleLongPress() }
            } else null
          )
        }

      Text(
        title,
        style =
          if (onBackClick == null) {
            MaterialTheme.typography.headlineMediumEmphasized
          } else {
            MaterialTheme.typography.headlineSmall
          },
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          titleModifier.then(
            if (onBackClick == null) {
              Modifier.padding(start = 8.dp)
            } else {
              Modifier
            },
          ),
      )
    },
    navigationIcon = {
      when {
        onBackClick != null -> {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier.padding(horizontal = 2.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back),
              modifier = Modifier.size(28.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        onNavigationClick != null -> {
          IconButton(
            onClick = onNavigationClick,
            modifier = Modifier.padding(horizontal = 2.dp),
          ) {
            Icon(
              Icons.Filled.Menu,
              contentDescription = "Open navigation drawer",
              modifier = Modifier.size(28.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    },
    actions = {
      additionalActions()
      if (onSearchClick != null) {
        IconButton(
          onClick = onSearchClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Search,
            contentDescription = "Search",
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      if (onSortClick != null) {
        IconButton(
          onClick = onSortClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Default.ViewComfy,
            contentDescription = stringResource(R.string.sort),
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
    modifier = modifier,
  )
}

/**
 * Selection mode top bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionTopBar(
  selectedCount: Int,
  totalCount: Int,
  onCancel: () -> Unit,
  onDelete: (() -> Unit)?,
  onRename: (() -> Unit)?,
  isSingleSelection: Boolean,
  onInfo: (() -> Unit)?,
  onShare: (() -> Unit)?,
  onPlay: (() -> Unit)?,
  onBlacklist: (() -> Unit)?,
  onSelectAll: (() -> Unit)?,
  onInvertSelection: (() -> Unit)?,
  onDeselectAll: (() -> Unit)?,
  modifier: Modifier = Modifier,
  useRemoveIcon: Boolean = false,
) {
  var showDropdown by remember { mutableStateOf(false) }

  TopAppBar(
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { showDropdown = true },
      ) {
        Text(
          stringResource(R.string.selected_items, selectedCount, totalCount),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          Icons.Filled.ArrowDropDown,
          contentDescription = stringResource(R.string.selection_options),
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.primary,
        )

        DropdownMenu(
          expanded = showDropdown,
          onDismissRequest = { showDropdown = false },
        ) {
          if (onSelectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.select_all)) },
              onClick = {
                onSelectAll()
                showDropdown = false
              },
            )
          }
          if (onInvertSelection != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.invert_selection)) },
              onClick = {
                onInvertSelection()
                showDropdown = false
              },
            )
          }
          if (onDeselectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.deselect_all)) },
              onClick = {
                onDeselectAll()
                showDropdown = false
              },
            )
          }
        }
      }
    },
    navigationIcon = {
      IconButton(
        onClick = onCancel,
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(R.string.generic_cancel),
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    },
    actions = {
      // Play icon
      if (onPlay != null) {
        IconButton(
          onClick = onPlay,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // Rename icon
      if (onRename != null) {
        IconButton(
          onClick = onRename,
          enabled = isSingleSelection,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.DriveFileRenameOutline,
            contentDescription = stringResource(R.string.rename),
            modifier = Modifier.size(28.dp),
            tint =
              if (isSingleSelection) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
          )
        }
      }

      // Info icon
      if (onInfo != null) {
        IconButton(
          onClick = onInfo,
          enabled = isSingleSelection,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Info,
            contentDescription = stringResource(R.string.info),
            modifier = Modifier.size(28.dp),
            tint =
              if (isSingleSelection) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
          )
        }
      }

      // Share icon
      if (onShare != null) {
        IconButton(
          onClick = onShare,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Share,
            contentDescription = stringResource(R.string.generic_share),
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      // Blacklist icon
      if (onBlacklist != null) {
        IconButton(
          onClick = onBlacklist,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Block,
            contentDescription = stringResource(R.string.pref_folders_blacklist),
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      // Delete/Remove icon
      if (onDelete != null) {
        IconButton(
          onClick = onDelete,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            imageVector = if (useRemoveIcon) Icons.Filled.RemoveCircle else Icons.Filled.Delete,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    modifier = modifier,
  )
}
