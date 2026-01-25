package app.marlboroadvance.mpvex.ui.browser.networkstreaming

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.dao.NetworkConnectionDao
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.NetworkFolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.NetworkVideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Serializable
data class NetworkBrowserScreen(
  val connectionId: Long,
  val connectionName: String,
  val currentPath: String = "/",
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current

    val viewModel: NetworkBrowserViewModel =
      viewModel(
        key = "NetworkBrowser_${connectionId}_$currentPath",
        factory =
          NetworkBrowserViewModel.factory(
            context.applicationContext as android.app.Application,
            connectionId,
            currentPath,
          ),
      )

    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // UI State
    val isRefreshing = remember { mutableStateOf(false) }

    // Load files when connectionId or currentPath changes
    LaunchedEffect(connectionId, currentPath) {
      viewModel.loadFiles()
    }

    BackHandler {
      backstack.removeLastOrNull()
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = connectionName,
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onBackClick = { backstack.removeLastOrNull() },
          onCancelSelection = {},
          onSortClick = null,
          onSearchClick = null,
          onSettingsClick = {
            backstack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
          },
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
    ) { padding ->
      NetworkBrowserContent(
        files = files,
        connectionId = connectionId,
        connectionName = connectionName,
        isLoading = isLoading && files.isEmpty(),
        isRefreshing = isRefreshing,
        error = error,
        onRefresh = { viewModel.loadFiles() },
        onFolderClick = { folder ->
          backstack.add(
            NetworkBrowserScreen(
              connectionId = connectionId,
              connectionName = connectionName,
              currentPath = folder.path,
            ),
          )
        },
        onVideoClick = { video ->
          viewModel.playVideo(video)
        },
        modifier = Modifier.padding(padding),
      )
    }
  }
}

@Composable
private fun NetworkBrowserContent(
  files: List<NetworkFile>,
  connectionId: Long,
  connectionName: String,
  isLoading: Boolean,
  isRefreshing: MutableState<Boolean>,
  error: String?,
  onRefresh: suspend () -> Unit,
  onFolderClick: (NetworkFile) -> Unit,
  onVideoClick: (NetworkFile) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Load connection details
  val dao = org.koin.compose.koinInject<NetworkConnectionDao>()
  var connection by remember { mutableStateOf<NetworkConnection?>(null) }

  LaunchedEffect(connectionId) {
    connection = dao.getConnectionById(connectionId)
  }

  when {
    isLoading -> {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(bottom = 80.dp), // Account for bottom navigation bar
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(48.dp),
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    error != null -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.Folder,
          title = "Error loading files",
          message = error,
        )
      }
    }

    files.isEmpty() -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.Folder,
          title = "Empty folder",
          message = "This folder contains no files or directories",
        )
      }
    }

    else -> {
      val folders = files.filter { it.isDirectory }
      val videos = files.filter { !it.isDirectory && it.mimeType?.startsWith("video/") == true }
      val networkListState = LazyListState()

      // Check if at top of list to hide scrollbar during pull-to-refresh
      val isAtTop by remember {
        derivedStateOf {
          networkListState.firstVisibleItemIndex == 0 && networkListState.firstVisibleItemScrollOffset == 0
        }
      }

      // Only show scrollbar if list has more than 20 items (folders + videos)
      val hasEnoughItems = (folders.size + videos.size) > 20

      // Animate scrollbar alpha
      val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "scrollbarAlpha",
      )

      PullRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        listState = networkListState,
        modifier = modifier.fillMaxSize(),
      ) {
        val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = navigationBarHeight)
        ) {
          LazyColumnScrollbar(
            state = networkListState,
            settings = ScrollbarSettings(
              thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
              thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
            ),
          ) {
            LazyColumn(
              state = networkListState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = navigationBarHeight
              ),
            ) {
            // Folders section
            if (folders.isNotEmpty()) {
              item {
                Text(
                  text = "Folders",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
              }
              items(
                items = folders,
                key = { it.path },
              ) { folder ->
                NetworkFolderCard(
                  file = folder,
                  onClick = { onFolderClick(folder) },
                  modifier = Modifier,
                )
              }
            }

            // Videos section
            if (videos.isNotEmpty()) {
              item {
                Text(
                  text = "Videos",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
              }
              items(
                items = videos,
                key = { it.path },
              ) { video ->
                // Only show card if connection is loaded
                connection?.let { conn ->
                  NetworkVideoCard(
                    file = video,
                    connection = conn,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
}
