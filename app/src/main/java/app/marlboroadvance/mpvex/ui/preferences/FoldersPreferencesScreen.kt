package app.marlboroadvance.mpvex.ui.preferences

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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionState
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.preferences.folderpicker.FolderPickerScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object FoldersPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val backstack = LocalBackStack.current

    val blacklistedFolders by preferences.blacklistedFolders.collectAsState()
    var selectionState by remember { mutableStateOf(SelectionState<String>()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val blacklistedFoldersList = remember(blacklistedFolders) { blacklistedFolders.toList().sorted() }

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
            preferences.removeFolders(selectionState.selectedIds)
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
            items(blacklistedFoldersList, key = { it }) { folderPath ->
              BlacklistedFolderItem(
                folderPath = folderPath,
                isSelected = selectionState.isSelected(folderPath),
                isInSelectionMode = selectionState.isInSelectionMode,
                onRemove = {
                  preferences.removeFolder(folderPath)
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

          // Add Folder Action launches the new navigable FolderPickerScreen
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                backstack.add(FolderPickerScreen)
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

    if (showClearAllDialog) {
      AlertDialog(
        onDismissRequest = { showClearAllDialog = false },
        title = { Text(stringResource(R.string.pref_folders_clear_all_confirm_title)) },
        text = { Text(stringResource(R.string.pref_folders_clear_all_confirm_message)) },
        confirmButton = {
          TextButton(
            onClick = {
              preferences.clearAll()
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
