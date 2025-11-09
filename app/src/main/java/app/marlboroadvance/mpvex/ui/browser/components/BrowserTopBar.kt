package app.marlboroadvance.mpvex.ui.browser.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.theme.DarkMode
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
  onSortClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onRenameClick: (() -> Unit)? = null,
  isSingleSelection: Boolean = false,
  onInfoClick: (() -> Unit)? = null,
  onShareClick: (() -> Unit)? = null,
  onPlayClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  additionalActions: @Composable RowScope.() -> Unit = { },
  onTitleLongPress: (() -> Unit)? = null,
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
      onSelectAll = onSelectAll,
      onInvertSelection = onInvertSelection,
      onDeselectAll = onDeselectAll,
      modifier = modifier,
    )
  } else {
    NormalTopBar(
      title = title,
      onBackClick = onBackClick,
      onSortClick = onSortClick,
      onSettingsClick = onSettingsClick,
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
  onSortClick: (() -> Unit)?,
  onSettingsClick: (() -> Unit)?,
  additionalActions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  onTitleLongPress: (() -> Unit)?,
) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val context = LocalContext.current

  TopAppBar(
    title = {
      val titleModifier =
        if (onTitleLongPress != null) {
          Modifier.combinedClickable(
            onClick = {
              if (darkMode == DarkMode.System && darkTheme) {
                preferences.darkMode.set(DarkMode.Light)
              } else if (darkMode == DarkMode.System && !darkTheme) {
                preferences.darkMode.set(DarkMode.Dark)
              } else if (darkMode == DarkMode.Light && darkTheme) {
                preferences.darkMode.set(DarkMode.System)
              } else if (darkMode == DarkMode.Light && !darkTheme) {
                preferences.darkMode.set(DarkMode.Dark)
              } else if (darkMode == DarkMode.Dark && darkTheme) {
                preferences.darkMode.set(DarkMode.Light)
              } else {
                preferences.darkMode.set(DarkMode.System)
              }
            },
            onLongClick = onTitleLongPress,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          )
        } else {
          Modifier.combinedClickable(
            onClick = {
              if (darkMode == DarkMode.System && darkTheme) {
                preferences.darkMode.set(DarkMode.Light)
              } else if (darkMode == DarkMode.System && !darkTheme) {
                preferences.darkMode.set(DarkMode.Dark)
              } else if (darkMode == DarkMode.Light && darkTheme) {
                preferences.darkMode.set(DarkMode.System)
              } else if (darkMode == DarkMode.Light && !darkTheme) {
                preferences.darkMode.set(DarkMode.Dark)
              } else if (darkMode == DarkMode.Dark && darkTheme) {
                preferences.darkMode.set(DarkMode.Light)
              } else {
                preferences.darkMode.set(DarkMode.System)
              }
            },
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
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
      if (onBackClick != null) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
    actions = {
      additionalActions()
      if (onSortClick != null) {
        IconButton(
          onClick = onSortClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.sort),
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      if (onSettingsClick != null) {
        IconButton(
          onClick = onSettingsClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings),
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
  onSelectAll: (() -> Unit)?,
  onInvertSelection: (() -> Unit)?,
  onDeselectAll: (() -> Unit)?,
  modifier: Modifier = Modifier,
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
          modifier = Modifier.size(24.dp),
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
          modifier = Modifier.size(24.dp),
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
            modifier = Modifier.size(24.dp),
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
            modifier = Modifier.size(24.dp),
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
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      // Delete icon
      if (onDelete != null) {
        IconButton(
          onClick = onDelete,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    modifier = modifier,
  )
}
