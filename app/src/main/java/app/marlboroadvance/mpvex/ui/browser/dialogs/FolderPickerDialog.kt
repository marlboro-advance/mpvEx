package app.marlboroadvance.mpvex.ui.browser.dialogs

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderPickerDialog(
  modifier: Modifier = Modifier,
  isOpen: Boolean,
  currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
  onDismiss: () -> Unit,
  onFolderSelected: (String) -> Unit,
) {
  if (!isOpen) return

  var selectedPath by remember(isOpen) {
    mutableStateOf(Environment.getExternalStorageDirectory().absolutePath)
  }
  var showCreateFolderDialog by remember { mutableStateOf(false) }

  val currentDir = remember(selectedPath) { File(selectedPath) }
  val folders =
    remember(selectedPath) {
      currentDir
        .listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()
    }

  // Check if selected path is the same as current path
  val isSameAsSource =
    remember(selectedPath, currentPath) {
      selectedPath == currentPath
    }

  if (showCreateFolderDialog) {
    CreateFolderDialog(
      parentPath = selectedPath,
      onDismiss = { showCreateFolderDialog = false },
      onFolderCreated = { newFolderPath ->
        selectedPath = newFolderPath
        showCreateFolderDialog = false
      },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Select Folder",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = selectedPath,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp),
        )
        if (isSameAsSource) {
          Text(
            text = "Cannot select the same folder",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Navigation buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (currentDir.parent != null) {
            FilledTonalIconButton(
              onClick = { currentDir.parent?.let { selectedPath = it } },
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
              shape = MaterialTheme.shapes.extraLarge,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
              )
            }
          }

          FilledTonalIconButton(
            onClick = {
              selectedPath = Environment.getExternalStorageDirectory().absolutePath
            },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = "Go to home",
            )
          }

          FilledTonalIconButton(
            onClick = { showCreateFolderDialog = true },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            shape = MaterialTheme.shapes.extraLarge,
          ) {
            Icon(
              imageVector = Icons.Default.CreateNewFolder,
              contentDescription = "Create folder",
            )
          }
        }

        // Folder list
        LazyColumn(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(300.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onFolderSelected(selectedPath) },
        enabled = !isSameAsSource,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Select", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Cancel", fontWeight = FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier,
  )
}

@Composable
private fun FolderItem(
  folder: File,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.Folder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(28.dp),
    )
    Text(
      text = folder.name,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    title = {
      Text(
        "Create New Folder",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = folderName,
          onValueChange = {
            folderName = it
            error = null
          },
          label = { Text("Folder name", fontWeight = FontWeight.Medium) },
          singleLine = true,
          isError = error != null,
          modifier = Modifier.fillMaxWidth(),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              focusedLabelColor = MaterialTheme.colorScheme.primary,
            ),
          shape = MaterialTheme.shapes.extraLarge,
        )
        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Create", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Cancel", fontWeight = FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}
