package app.marlboroadvance.mpvex.ui.browser.dialogs

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FolderPickerDialog(
  isOpen: Boolean,
  currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
  onDismiss: () -> Unit,
  onFolderSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  var selectedPath by remember(isOpen) { mutableStateOf(currentPath) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }

  val currentDir = remember(selectedPath) { File(selectedPath) }
  val folders =
    remember(selectedPath) {
      currentDir
        .listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = "Select Folder",
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Column {
        Text(
          text = "Select Destination Folder",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = selectedPath,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Navigation buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Back button
          if (currentDir.parent != null) {
            IconButton(
              onClick = {
                currentDir.parent?.let { selectedPath = it }
              },
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
              )
            }
          }

          // Home button
          IconButton(
            onClick = {
              selectedPath = Environment.getExternalStorageDirectory().absolutePath
            },
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = "Go to home",
            )
          }

          // Storage root button
          IconButton(
            onClick = {
              selectedPath = Environment.getRootDirectory().absolutePath
            },
          ) {
            Icon(
              imageVector = Icons.Default.SdCard,
              contentDescription = "Go to storage root",
            )
          }

          // Create folder button
          IconButton(
            onClick = { showCreateFolderDialog = true },
          ) {
            Icon(
              imageVector = Icons.Default.CreateNewFolder,
              contentDescription = "Create new folder",
            )
          }
        }

        // Folder list
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(1f, fill = false),
        ) {
          LazyColumn(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(folders) { folder ->
              FolderItem(
                folder = folder,
                onClick = { selectedPath = folder.absolutePath },
              )
            }

            if (folders.isEmpty()) {
              item {
                Text(
                  text = "No subfolders",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onFolderSelected(selectedPath) },
      ) {
        Text("Select")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
    modifier = modifier,
  )

  // Create folder dialog
  if (showCreateFolderDialog) {
    CreateFolderDialog(
      parentPath = selectedPath,
      onDismiss = { showCreateFolderDialog = false },
      onFolderCreated = { newFolderPath ->
        selectedPath = newFolderPath
        showCreateFolderDialog = false
      },
    )
  }
}

@Composable
private fun FolderItem(
  folder: File,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
      ),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = folder.name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CreateFolderDialog(
  parentPath: String,
  onDismiss: () -> Unit,
  onFolderCreated: (String) -> Unit,
) {
  var folderName by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Default.CreateNewFolder,
        contentDescription = "Create Folder",
      )
    },
    title = {
      Text("Create New Folder")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = folderName,
          onValueChange = {
            folderName = it
            error = null
          },
          label = { Text("Folder name") },
          singleLine = true,
          isError = error != null,
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (folderName.isBlank()) {
            error = "Folder name cannot be empty"
            return@Button
          }

          val newFolder = File(parentPath, folderName)
          if (newFolder.exists()) {
            error = "Folder already exists"
            return@Button
          }

          try {
            if (newFolder.mkdirs()) {
              onFolderCreated(newFolder.absolutePath)
            } else {
              error = "Failed to create folder"
            }
          } catch (e: Exception) {
            error = e.message ?: "Unknown error"
          }
        },
        enabled = folderName.isNotBlank(),
      ) {
        Text("Create")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
