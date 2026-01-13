package app.marlboroadvance.mpvex.ui.browser.networkstreaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalWifiConnectedNoInternet4
import androidx.compose.material.icons.rounded.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.cards.NetworkConnectionCard
import app.marlboroadvance.mpvex.ui.browser.dialogs.AddConnectionSheet
import app.marlboroadvance.mpvex.ui.browser.dialogs.EditConnectionSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.serialization.Serializable

@Serializable
object NetworkStreamingScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val viewModel: NetworkStreamingViewModel =
      viewModel(factory = NetworkStreamingViewModel.factory(context.applicationContext as android.app.Application))

    val connections by viewModel.connections.collectAsState()
    val connectionStatuses by viewModel.connectionStatuses.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<NetworkConnection?>(null) }

    // LazyList state for scroll tracking
    val listState = rememberLazyListState()

    // Track scroll direction to show/hide FAB
    var previousFirstVisibleItemIndex by remember { mutableIntStateOf(0) }
    var previousFirstVisibleItemScrollOffset by remember { mutableIntStateOf(0) }

    val isFabVisible by remember {
      derivedStateOf {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset

        // Show FAB when at the top
        if (currentIndex == 0 && currentOffset == 0) {
          true
        } else {
          // Show when scrolling up, hide when scrolling down
          val isScrollingUp = currentIndex < previousFirstVisibleItemIndex ||
            (currentIndex == previousFirstVisibleItemIndex && currentOffset < previousFirstVisibleItemScrollOffset)

          previousFirstVisibleItemIndex = currentIndex
          previousFirstVisibleItemScrollOffset = currentOffset

          isScrollingUp
        }
      }
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Network",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = null, // No back button for network screen (root tab)
          onCancelSelection = { },
          onSortClick = null,
          onSettingsClick = { backstack.add(PreferencesScreen) },
          // Search functionality disabled for production
          onSearchClick = null,
          onDeleteClick = null,
          onRenameClick = null,
          isSingleSelection = false,
          onInfoClick = null,
          onShareClick = null,
          onPlayClick = null,
          onSelectAll = null,
          onInvertSelection = null,
          onDeselectAll = null,
        )
      },
      floatingActionButton = {
        if (isFabVisible) {
          Box(
            modifier = Modifier.padding(bottom = 80.dp),
          ) {
            ExtendedFloatingActionButton(
              onClick = { showAddSheet = true },
              icon = { Icon(Icons.Filled.Add, contentDescription = null) },
              text = { Text("Add Connection") },
            )
          }
        }
      },
    ) { padding ->
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
      ) {
          // Section 1: Stream Link
          item {
            StreamLinkSection(
              onPlayLink = { url ->
                MediaUtils.playFile(url, context, "network_stream")
              },
            )
          }

          // Section 2: Local Network header
          item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = "Local Network",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }

          // Show empty state or connection list
          if (connections.isEmpty()) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Text(
                    text = "No network connections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, // a
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = "Add SMB, FTP, or WebDAV connections to browse network files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          } else {
            items(connections, key = { it.id }) { connection ->
              val status = connectionStatuses[connection.id]
              NetworkConnectionCard(
                connection = connection,
                onConnect = { conn ->
                  viewModel.connect(conn)
                },
                onDisconnect = { conn -> viewModel.disconnect(conn) },
                onEdit = { conn -> editingConnection = conn },
                onDelete = { conn -> viewModel.deleteConnection(conn) },
                onBrowse = { conn ->
                  // Navigate to browser screen if connected
                  if (status?.isConnected == true) {
                    backstack.add(
                      NetworkBrowserScreen(
                        connectionId = conn.id,
                        connectionName = conn.name,
                        currentPath = "/",  // Always start at root - conn.path is already included in connection
                      ),
                    )
                  }
                },
                onAutoConnectChange = { conn, autoConnect ->
                  viewModel.updateConnection(conn.copy(autoConnect = autoConnect))
                },
                isConnected = status?.isConnected ?: false,
                isConnecting = status?.isConnecting ?: false,
                error = status?.error,
                modifier = Modifier.padding(bottom = 16.dp),
              )
            }
          }
        }

      // Add Connection Sheet
      AddConnectionSheet(
        isOpen = showAddSheet,
        onDismiss = { showAddSheet = false },
        onSave = { connection ->
          viewModel.addConnection(connection)
          showAddSheet = false
        },
      )

      // Edit Connection Sheet
      editingConnection?.let { connection ->
        EditConnectionSheet(
          connection = connection,
          isOpen = true,
          onDismiss = { editingConnection = null },
          onSave = { updatedConnection ->
            viewModel.updateConnection(updatedConnection)
            editingConnection = null
          },
        )
      }
    }
  }
}

@Composable
private fun StreamLinkSection(
  onPlayLink: (String) -> Unit,
) {
  val context = LocalContext.current
  var linkUrl by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = "Stream Link",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(vertical = 8.dp),
    )
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
      ) {
        OutlinedTextField(
          value = linkUrl,
          onValueChange = { linkUrl = it },
          label = { Text("Video URL") },
          placeholder = {
            Text(
              text = "https://example.com/video.mp4",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Filled.Link,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
          FilledTonalButton(
            onClick = {
              val clipboardManager =
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
              val clipData = clipboardManager?.primaryClip
              if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                  linkUrl = text
                }
              }
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentPaste,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp),
            )
            Text(
              text = "Paste",
              fontWeight = FontWeight.Bold,
            )
          }

          FilledTonalButton(
            onClick = {
              if (linkUrl.isNotBlank()) {
                onPlayLink(linkUrl)
                linkUrl = ""
              }
            },
            enabled = linkUrl.isNotBlank(),
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp),
            )
            Text(
              text = "Play",
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
