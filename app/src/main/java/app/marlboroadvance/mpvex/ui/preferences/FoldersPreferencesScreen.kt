package app.marlboroadvance.mpvex.ui.preferences

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionState
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object FoldersPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val blacklistedFolders by preferences.blacklistedFolders.collectAsState()
    var availableFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var selectionState by remember { mutableStateOf(SelectionState<String>()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val blacklistedFoldersList = remember(blacklistedFolders) { blacklistedFolders.toList() }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.pref_folders_title),
          isInSelectionMode = selectionState.isInSelectionMode,
          selectedCount = selectionState.selectedCount,
          totalCount = blacklistedFoldersList.size,
          onCancelSelection = { selectionState = selectionState.clear() },
          onBackClick = backstack::removeLastOrNull,
          onDeleteClick = {
            val updated = blacklistedFolders.toMutableSet().apply {
              removeAll(selectionState.selectedIds)
            }
            preferences.blacklistedFolders.set(updated)
            selectionState = selectionState.clear()
          },
          onSelectAll = {
            selectionState = selectionState.selectAll(blacklistedFoldersList)
          },
          onInvertSelection = {
            selectionState = selectionState.invertSelection(blacklistedFoldersList)
          },
          onDeselectAll = {
            selectionState = selectionState.clear()
          },
          additionalActions = {
            if (!selectionState.isInSelectionMode && blacklistedFolders.isNotEmpty()) {
              IconButton(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.padding(horizontal = 2.dp),
              ) {
                Icon(
                  Icons.Outlined.Restore,
                  contentDescription = stringResource(R.string.pref_folders_clear_all),
                  modifier = Modifier.size(28.dp),
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          },
          useRemoveIcon = true,
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp),
      ) {
        if (!selectionState.isInSelectionMode) {
          Text(
            text = stringResource(R.string.pref_folders_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))
        }

        if (blacklistedFolders.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          ) {
            EmptyState(
              icon = Icons.Filled.FolderOff,
              title = stringResource(R.string.pref_folders_empty_title),
              message = stringResource(R.string.pref_folders_empty_message),
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(blacklistedFoldersList) { folderPath ->
              BlacklistedFolderItem(
                folderPath = folderPath,
                isSelected = selectionState.isSelected(folderPath),
                isInSelectionMode = selectionState.isInSelectionMode,
                onRemove = {
                  val updated = blacklistedFolders.toMutableSet().apply { remove(folderPath) }
                  preferences.blacklistedFolders.set(updated)
                },
                onLongClick = {
                  selectionState = selectionState.toggle(folderPath)
                },
                onClick = {
                  if (selectionState.isInSelectionMode) {
                    selectionState = selectionState.toggle(folderPath)
                  }
                },
              )
            }
          }
        }

        if (!selectionState.isInSelectionMode) {
          Spacer(modifier = Modifier.height(16.dp))

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                showAddDialog = true
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                  try {
                    availableFolders = VideoFolderRepository.getVideoFolders(context.applicationContext as Application)
                  } finally {
                    isLoading = false
                  }
                }
              },
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(modifier = Modifier.padding(8.dp))
              Text(
                text = stringResource(R.string.pref_folders_add_folder),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }

    if (showAddDialog) {
      AddFolderDialog(
        folders = availableFolders,
        blacklistedFolders = blacklistedFolders,
        isLoading = isLoading,
        onDismiss = { showAddDialog = false },
        onAddFolders = { folderPaths ->
          val updated = blacklistedFolders.toMutableSet().apply { addAll(folderPaths) }
          preferences.blacklistedFolders.set(updated)
        },
      )
    }

    if (showClearAllDialog) {
      AlertDialog(
        onDismissRequest = { showClearAllDialog = false },
        title = { Text(stringResource(R.string.pref_folders_clear_all_confirm_title)) },
        text = { Text(stringResource(R.string.pref_folders_clear_all_confirm_message)) },
        confirmButton = {
          TextButton(
            onClick = {
              preferences.blacklistedFolders.set(emptySet())
              showClearAllDialog = false
            },
          ) {
            Text(stringResource(R.string.generic_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = { showClearAllDialog = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlacklistedFolderItem(
  folderPath: String,
  isSelected: Boolean,
  isInSelectionMode: Boolean,
  onRemove: () -> Unit,
  onLongClick: () -> Unit,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isInSelectionMode) {
          Checkbox(
            checked = isSelected,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 8.dp),
          )
        }
        Column {
          Text(
            text = folderPath.substringAfterLast('/'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = folderPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (!isInSelectionMode) {
        IconButton(onClick = onRemove) {
          Icon(
            imageVector = Icons.Default.RemoveCircle,
            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun AddFolderDialog(
  folders: List<VideoFolder>,
  blacklistedFolders: Set<String>,
  isLoading: Boolean,
  onDismiss: () -> Unit,
  onAddFolders: (Set<String>) -> Unit,
) {
  var selectionState by remember { mutableStateOf(SelectionState<String>()) }
  var showDropdown by remember { mutableStateOf(false) }

  // Filter folders that are not already blacklisted
  val availableFolders = remember(folders, blacklistedFolders) {
    folders.filter { it.path !in blacklistedFolders }
  }

  // Get all available folder paths
  val availableFolderPaths = remember(availableFolders) {
    availableFolders.map { it.path }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = !isLoading && availableFolders.isNotEmpty()) {
          showDropdown = true
        },
      ) {
        Text(
          text = if (selectionState.isInSelectionMode) {
            stringResource(R.string.selected_items, selectionState.selectedCount, availableFolders.size)
          } else {
            stringResource(R.string.pref_folders_select_folders)
          },
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!isLoading && availableFolders.isNotEmpty()) {
          Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = stringResource(R.string.selection_options),
            modifier = Modifier.size(24.dp),
          )
        }

        DropdownMenu(
          expanded = showDropdown,
          onDismissRequest = { showDropdown = false },
        ) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.select_all)) },
            onClick = {
              selectionState = selectionState.selectAll(availableFolderPaths)
              showDropdown = false
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.invert_selection)) },
            onClick = {
              selectionState = selectionState.invertSelection(availableFolderPaths)
              showDropdown = false
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.deselect_all)) },
            onClick = {
              selectionState = selectionState.clear()
              showDropdown = false
            },
          )
        }
      }
    },
    text = {
      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(stringResource(R.string.pref_folders_loading))
        }
      } else if (availableFolders.isEmpty()) {
        Text(stringResource(R.string.pref_folders_no_folders))
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        ) {
          items(availableFolders) { folder ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selectionState = selectionState.toggle(folder.path)
                }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = selectionState.isSelected(folder.path),
                onCheckedChange = {
                  selectionState = selectionState.toggle(folder.path)
                },
              )
              Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                  text = folder.name,
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = folder.path,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onAddFolders(selectionState.selectedIds)
          onDismiss()
        },
        enabled = selectionState.isInSelectionMode && !isLoading,
      ) {
        Text(stringResource(R.string.generic_ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
  )
}
