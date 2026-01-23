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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DriveFolderUpload
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.DialogProperties
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilePickerDialog(
  modifier: Modifier = Modifier,
  isOpen: Boolean,
  currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
  onDismiss: () -> Unit,
  onFileSelected: (String) -> Unit,
  onSystemPickerRequest: () -> Unit,
  allowedExtensions: List<String> = listOf(
    // Common & modern
    "srt", "vtt", "ass", "ssa",

    // DVD / Blu-ray
    "sub", "idx", "sup",

    // Streaming / XML / Professional
    "xml", "ttml", "dfxp", "itt", "ebu", "imsc", "usf",

    // Online platforms
    "sbv", "srv1", "srv2", "srv3", "json",

    // Legacy & niche
    "sami", "smi", "mpl", "pjs", "stl", "rt", "psb", "cap",

    // Broadcast captions
    "scc", "vttx",

    // Karaoke / lyrics
    "lrc", "krc",

    // Fallback / raw text
    "txt"
  )

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

  // Determine what to show based on selectedPath
  val showStorageRoot = selectedPath == null
  
  val currentDir = remember(selectedPath) { 
    selectedPath?.let { File(it) }
  }
  
  // Get folders and allowed files
  val (folders, files) = remember(selectedPath) {
    if (showStorageRoot) {
      Pair(emptyList<File>(), emptyList<File>())
    } else {
      val allFiles = currentDir?.listFiles { file -> !file.name.startsWith(".") } ?: emptyArray()
      val dirs = allFiles.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
      val filteredFiles = allFiles.filter { file -> 
          !file.isDirectory && allowedExtensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) } 
      }.sortedBy { it.name.lowercase() }
      Pair(dirs, filteredFiles)
    }
  }

  androidx.compose.ui.window.Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
      Surface(
          modifier = modifier.fillMaxWidth(0.50f),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp,
      ) {
          Column(
              modifier = Modifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
              // Title Section
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "Select Subtitle",
                          style = MaterialTheme.typography.headlineMedium,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = selectedPath ?: "Select a storage location",
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Medium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Navigation Buttons in Title
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         if (selectedPath != null) {
                            FilledTonalIconButton(
                              onClick = {
                                val parent = currentDir?.parent
                                selectedPath = parent
                              },
                              modifier = Modifier.size(40.dp),
                              colors = IconButtonDefaults.filledTonalIconButtonColors(
                                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                              )
                            ) {
                              Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(24.dp))
                            }
                          }

                          FilledTonalIconButton(
                            onClick = {
                              selectedPath = Environment.getExternalStorageDirectory().absolutePath
                            },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                              )
                          ) {
                            Icon(Icons.Default.Home, "Home", modifier = Modifier.size(24.dp))
                          }

                          FilledTonalIconButton(
                            onClick = onSystemPickerRequest,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                          ) {
                            Icon(Icons.Default.DriveFolderUpload, "System Picker", modifier = Modifier.size(24.dp))
                          }
                    }
                }
              }

              // Content Section
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                // Folder/Volume/File list
                LazyColumn(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(400.dp),
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
                         Text("No storage devices found", modifier = Modifier.padding(16.dp))
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
                    // Show files
                    items(files) { file ->
                        FileItem(
                            file = file,
                            onClick = { onFileSelected(file.absolutePath) }
                        )
                    }
                    if (folders.isEmpty() && files.isEmpty()) {
                      item {
                         Text("No folders or supported files", modifier = Modifier.padding(16.dp))
                      }
                    }
                  }
                }
              }

              // Footer Section (Analyze padding here)
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
              ) {
                  TextButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.extraLarge,
                    // Reduced padding for the button itself if needed, or rely on Row padding
                  ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                  }
              }
          }
      }
  }
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

@Composable
private fun FileItem(
  file: File,
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
      imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(28.dp),
    )
    Text(
      text = file.name,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Normal,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
