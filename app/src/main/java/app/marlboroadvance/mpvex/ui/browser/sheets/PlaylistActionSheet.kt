package app.marlboroadvance.mpvex.ui.browser.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  repository: app.marlboroadvance.mpvex.database.repository.PlaylistRepository,
  context: android.content.Context,
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var showM3UDialog by remember { mutableStateOf(false) }

  if (!isOpen) return

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Title
      Text(
        text = "Playlist Options",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(4.dp))

      // Action cards
      Card(
        onClick = {
          showCreateDialog = true
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Create Empty Playlist",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Create a new blank playlist",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Card(
        onClick = {
          showM3UDialog = true
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Add M3U Playlist from URL",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Import a playlist from a web URL",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  // Create Playlist Dialog
  if (showCreateDialog) {
    var playlistName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = { showCreateDialog = false }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = "Create Playlist",
            style = MaterialTheme.typography.headlineSmall,
          )
          OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            label = { Text("Playlist Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = { showCreateDialog = false },
              shape = MaterialTheme.shapes.extraLarge,
            ) {
              Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (playlistName.isNotBlank()) {
                  coroutineScope.launch {
                    try {
                      repository.createPlaylist(playlistName.trim())
                      android.widget.Toast.makeText(
                        context,
                        "Playlist created successfully",
                        android.widget.Toast.LENGTH_SHORT
                      ).show()
                      showCreateDialog = false
                    } catch (e: Exception) {
                      android.widget.Toast.makeText(
                        context,
                        "Failed to create playlist: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                      ).show()
                    }
                  }
                }
              },
              enabled = playlistName.isNotBlank(),
              shape = MaterialTheme.shapes.extraLarge,
            ) {
              Text("Create")
            }
          }
        }
      }
    }
  }

  // M3U Playlist Dialog
  if (showM3UDialog) {
    var playlistUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
      uri?.let {
        isLoading = true
        coroutineScope.launch {
          val result = repository.createM3UPlaylistFromFile(context, it)
          result.onSuccess {
            android.widget.Toast.makeText(
              context,
              "M3U Playlist added successfully",
              android.widget.Toast.LENGTH_SHORT
            ).show()
          }.onFailure { error ->
            android.widget.Toast.makeText(
              context,
              "Failed to add M3U playlist: ${error.message}",
              android.widget.Toast.LENGTH_LONG
            ).show()
          }
          isLoading = false
          showM3UDialog = false
        }
      }
    }

    Dialog(onDismissRequest = if (isLoading) { {} } else { onDismiss }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = "Add M3U Playlist",
            style = MaterialTheme.typography.headlineSmall,
          )

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = playlistUrl,
              onValueChange = { playlistUrl = it },
              label = { Text("Playlist URL") },
              singleLine = false,
              maxLines = 3,
              modifier = Modifier.fillMaxWidth(),
              enabled = !isLoading
            )

            // OR divider
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
              Text(
                text = "OR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // Local file picker button
            OutlinedButton(
              onClick = {
                filePickerLauncher.launch("*/*")
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Choose Local M3U File")
            }

            if (isLoading) {
              Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                androidx.compose.material3.CircularProgressIndicator(
                  modifier = Modifier.size(32.dp)
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = { showM3UDialog = false },
              enabled = !isLoading,
              shape = MaterialTheme.shapes.extraLarge,
            ) {
              Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (playlistUrl.isNotBlank()) {
                  isLoading = true
                  coroutineScope.launch {
                    val result = repository.createM3UPlaylist(playlistUrl.trim())
                    result.onSuccess {
                      android.widget.Toast.makeText(
                        context,
                        "M3U Playlist added successfully",
                        android.widget.Toast.LENGTH_SHORT
                      ).show()
                    }.onFailure { error ->
                      android.widget.Toast.makeText(
                        context,
                        "Failed to add M3U playlist: ${error.message}",
                        android.widget.Toast.LENGTH_LONG
                      ).show()
                    }
                    isLoading = false
                    showM3UDialog = false
                  }
                }
              },
              enabled = playlistUrl.isNotBlank() && !isLoading,
              shape = MaterialTheme.shapes.extraLarge,
            ) {
              Text("Add from URL")
            }
          }
        }
      }
    }
  }
}
