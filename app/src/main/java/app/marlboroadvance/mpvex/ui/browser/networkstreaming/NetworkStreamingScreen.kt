package app.marlboroadvance.mpvex.ui.browser.networkstreaming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var isInitialLoading by remember { mutableStateOf(true) }

    // Track initial loading state
    LaunchedEffect(connections) {
      isInitialLoading = false
    }

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
          title = "Local Network",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = { backstack.removeLastOrNull() },
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
          ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add Connection") },
          )
        }
      },
    ) { padding ->
      when {
        // Show loading indicator on initial load
        isInitialLoading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        // Show empty state when no connections
        connections.isEmpty() -> {
          EmptyState(
            icon = Icons.Filled.Add,
            title = "No network connections",
            message = "Add SMB, FTP, or WebDAV connections to browse network files",
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
          )
        }

        // Show connection list
        else -> {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxWidth()
              .padding(padding),
            contentPadding = PaddingValues(16.dp),
          ) {
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
                        currentPath = conn.path,
                      ),
                    )
                  }
                },
                isConnected = status?.isConnected ?: false,
                isConnecting = status?.isConnecting ?: false,
                error = status?.error,
                modifier = Modifier.padding(bottom = 16.dp),
              )
            }
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
