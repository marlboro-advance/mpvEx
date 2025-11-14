package app.marlboroadvance.mpvex.ui.preferences

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.RemoveCircle
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.repository.VideoFolderRepository
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

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_folders_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp),
      ) {
        Text(
          text = stringResource(R.string.pref_folders_summary),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

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
            items(blacklistedFolders.toList()) { folderPath ->
              BlacklistedFolderItem(
                folderPath = folderPath,
                onRemove = {
                  val updated = blacklistedFolders.toMutableSet().apply { remove(folderPath) }
                  preferences.blacklistedFolders.set(updated)
                },
              )
            }
          }
        }

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
  }
}

@Composable
private fun BlacklistedFolderItem(
  folderPath: String,
  onRemove: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
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

@Composable
private fun AddFolderDialog(
  folders: List<VideoFolder>,
  blacklistedFolders: Set<String>,
  isLoading: Boolean,
  onDismiss: () -> Unit,
  onAddFolders: (Set<String>) -> Unit,
) {
  val selectedFolders = remember { mutableStateOf<Set<String>>(emptySet()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.pref_folders_select_folders)) },
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
      } else if (folders.isEmpty()) {
        Text(stringResource(R.string.pref_folders_no_folders))
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        ) {
          items(folders.filter { it.path !in blacklistedFolders }) { folder ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selectedFolders.value = if (folder.path in selectedFolders.value) {
                    selectedFolders.value - folder.path
                  } else {
                    selectedFolders.value + folder.path
                  }
                }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = folder.path in selectedFolders.value,
                onCheckedChange = {
                  selectedFolders.value = if (it) {
                    selectedFolders.value + folder.path
                  } else {
                    selectedFolders.value - folder.path
                  }
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
          onAddFolders(selectedFolders.value)
          onDismiss()
        },
        enabled = selectedFolders.value.isNotEmpty() && !isLoading,
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
