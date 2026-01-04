package app.marlboroadvance.mpvex.ui.browser.dialogs

import android.content.Context
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
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
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

  val context = LocalContext.current
  
  // Get all available storage volumes
  val storageVolumes = remember(isOpen) {
    StorageScanUtils.getAllStorageVolumes(context)
  }
  
  // If there's only one storage volume, start there directly
  // Otherwise, start at storage root view to show all volumes
  var selectedPath by remember(isOpen, storageVolumes) {
    val initialPath = if (storageVolumes.size == 1) {
      StorageScanUtils.getVolumePath(storageVolumes.first())
    } else {
      null // Show storage root with all volumes
    }
    mutableStateOf(initialPath)
  }
  var showCreateFolderDialog by remember { mutableStateOf(false) }

  // Determine what to show based on selectedPath
  val showStorageRoot = selectedPath == null
  
  val currentDir = remember(selectedPath) { 
    selectedPath?.let { File(it) }
  }
  
  val folders =
    remember(selectedPath) {
      if (showStorageRoot) {
        // Show storage volumes as "folders"
        emptyList<File>()
      } else {
        currentDir
          ?.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
          ?.sortedBy { it.name.lowercase() }
          ?: emptyList()
      }
    }

  // Check if selected path is the same as current path
  val isSameAsSource =
    remember(selectedPath, currentPath) {
      selectedPath != null && selectedPath == currentPath
    }

  if (showCreateFolderDialog && selectedPath != null) {
    CreateFolderDialog(
      parentPath = selectedPath!!,
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
          text = selectedPath ?: "Select a storage location",
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
          // Back button - go to parent or storage root
          if (selectedPath != null) {
            FilledTonalIconButton(
              onClick = {
                val parent = currentDir?.parent
                selectedPath = parent // null will show storage root
              },
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

          // Home button - go to internal storage
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
              contentDescription = "Go to internal storage",
            )
          }

          // Create folder button - only enabled when not at storage root
          FilledTonalIconButton(
            onClick = { showCreateFolderDialog = true },
            enabled = selectedPath != null,
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

        // Folder/Volume list
        LazyColumn(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(300.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (showStorageRoot) {
            // Show storage volumes
            items(storageVolumes) { volume ->
              val volumePath = StorageScanUtils.getVolumePath(volume)
              if (volumePath != null) {
                StorageVolumeItem(
                  context = context,
                  volume = volume,
                  volumePath = volumePath,
                  onClick = { selectedPath = volumePath },
                )
              }
            }
            
            if (storageVolumes.isEmpty()) {
              item {
                Text(
                  text = "No storage devices found",
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          } else {
            // Show folders
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
      }
    },
    confirmButton = {
      Button(
        onClick = { selectedPath?.let { onFolderSelected(it) } },
        enabled = selectedPath != null && !isSameAsSource,
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
private fun StorageVolumeItem(
  context: Context,
  volume: android.os.storage.StorageVolume,
  volumePath: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val description = volume.getDescription(context)
  val isPrimary = volume.isPrimary
  val isRemovable = volume.isRemovable
  
  val icon = when {
    isPrimary -> Icons.Default.Home
    isRemovable && volumePath.contains("usb", ignoreCase = true) -> Icons.Default.Usb
    isRemovable -> Icons.Default.SdCard
    else -> Icons.Default.Folder
  }
  
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp),
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = volumePath,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
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
