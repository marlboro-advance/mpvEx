package app.marlboroadvance.mpvex.ui.browser.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import app.marlboroadvance.mpvex.ui.browser.fab.PlaylistActionFab
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.PlaylistCard
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject

@Serializable
object PlaylistScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<PlaylistRepository>()
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    // ViewModel
    val viewModel: PlaylistViewModel = viewModel(
      factory = PlaylistViewModel.factory(context.applicationContext as android.app.Application),
    )

    val playlistsWithCount by viewModel.playlistsWithCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Filter playlists based on search query
    val filteredPlaylists = if (isSearching && searchQuery.isNotBlank()) {
      playlistsWithCount.filter { playlistWithCount ->
        playlistWithCount.playlist.name.contains(searchQuery, ignoreCase = true)
      }
    } else {
      playlistsWithCount
    }

    // Request focus when search is activated
    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    // Selection manager - use filtered list
    val selectionManager = rememberSelectionManager(
      items = filteredPlaylists,
      getId = { it.playlist.id },
      onDeleteItems = { itemsToDelete ->
        itemsToDelete.forEach { item ->
          scope.launch {
            viewModel.deletePlaylist(item.playlist)
          }
        }
        Pair(itemsToDelete.size, 0)
      },
      onOperationComplete = { viewModel.refresh() },
    )

    // UI State
    val listState = rememberLazyListState()
    val isRefreshing = remember { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showM3UDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Predictive back: Intercept when in selection mode, FAB menu expanded, or searching
    BackHandler(enabled = selectionManager.isInSelectionMode || fabMenuExpanded || isSearching) {
      when {
        fabMenuExpanded -> fabMenuExpanded = false
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }
        selectionManager.isInSelectionMode -> selectionManager.clear()
      }
    }

    Scaffold(
      topBar = {
        if (isSearching) {
          // Search mode - show search bar
          SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text("Search playlists...") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                  )
                },
                trailingIcon = {
                  IconButton(
                    onClick = {
                      isSearching = false
                      searchQuery = ""
                    },
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Cancel",
                    )
                  }
                },
                modifier = Modifier.focusRequester(focusRequester),
              )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
          ) {
            // Empty content for SearchBar
          }
        } else {
          BrowserTopBar(
            title = "Playlists",
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = playlistsWithCount.size,
            onBackClick = null,
            onCancelSelection = { selectionManager.clear() },
            isSingleSelection = selectionManager.isSingleSelection,
            onSearchClick = { isSearching = true },
            onRenameClick = if (selectionManager.isSingleSelection) {
              { showRenameDialog = true }
            } else null,
            onDeleteClick = { showDeleteDialog = true },
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
          )
        }
      },
      floatingActionButton = {
        if (!selectionManager.isInSelectionMode) {
          Box(
            modifier = Modifier.padding(bottom = 75.dp)
          ) {
            PlaylistActionFab(
              listState = listState,
              onCreatePlaylist = { showCreateDialog = true },
              onAddM3UPlaylist = { showM3UDialog = true },
              expanded = fabMenuExpanded,
              onExpandedChange = { fabMenuExpanded = it },
            )
          }
        }
      },
    ) { paddingValues ->
      if (isSearching && filteredPlaylists.isEmpty() && searchQuery.isNotBlank()) {
        // Show "no results" for search
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(bottom = 80.dp),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Search,
            title = "No playlists found",
            message = "Try a different search term",
          )
        }
      } else if (playlistsWithCount.isEmpty() && hasCompletedInitialLoad) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(bottom = 80.dp), // Account for bottom navigation bar
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            EmptyState(
              icon = Icons.Outlined.PlaylistAdd,
              title = "No playlists yet",
              message = "Create a playlist or add one from an M3U/M3U8 URL",
            )
          }
        }
      } else {
        PlaylistListContent(
          playlistsWithCount = filteredPlaylists,
          listState = listState,
          isRefreshing = isRefreshing,
          onRefresh = { viewModel.refresh() },
          selectionManager = selectionManager,
          onPlaylistClick = { playlistWithCount ->
            if (selectionManager.isInSelectionMode) {
              selectionManager.toggle(playlistWithCount)
            } else {
              backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
            }
          },
          onPlaylistLongClick = { playlistWithCount ->
            selectionManager.toggle(playlistWithCount)
          },
          modifier = Modifier.padding(paddingValues),
        )
      }
    }

    // Dialogs
    if (showCreateDialog) {
      CreatePlaylistDialog(
        onDismiss = { showCreateDialog = false },
        onConfirm = { name ->
          scope.launch {
            viewModel.createPlaylist(name)
            showCreateDialog = false
          }
        },
      )
    }

    if (showM3UDialog) {
      AddM3UPlaylistDialog(
        onDismiss = { showM3UDialog = false },
        onConfirm = { url ->
          scope.launch {
            val result = viewModel.createM3UPlaylist(url)
            result.onSuccess {
              android.widget.Toast.makeText(
                context,
                "M3U playlist added successfully",
                android.widget.Toast.LENGTH_SHORT
              ).show()
            }.onFailure { error ->
              android.widget.Toast.makeText(
                context,
                "Failed to add M3U playlist: ${error.message}",
                android.widget.Toast.LENGTH_LONG
              ).show()
            }
            showM3UDialog = false
          }
        },
      )
    }

    if (showRenameDialog && selectionManager.isSingleSelection) {
      val selectedPlaylist = selectionManager.getSelectedItems().firstOrNull()
      if (selectedPlaylist != null) {
        var playlistName by remember { mutableStateOf(selectedPlaylist.playlist.name) }
        androidx.compose.material3.AlertDialog(
          onDismissRequest = { showRenameDialog = false },
          title = { Text("Rename Playlist") },
          text = {
            androidx.compose.material3.OutlinedTextField(
              value = playlistName,
              onValueChange = { playlistName = it },
              label = { Text("Playlist Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          },
          confirmButton = {
            androidx.compose.material3.TextButton(
              onClick = {
                if (playlistName.isNotBlank()) {
                  scope.launch {
                    repository.updatePlaylist(selectedPlaylist.playlist.copy(name = playlistName.trim()))
                    showRenameDialog = false
                    selectionManager.clear()
                  }
                }
              },
              enabled = playlistName.isNotBlank(),
            ) {
              Text("Rename")
            }
          },
          dismissButton = {
            androidx.compose.material3.TextButton(
              onClick = { showRenameDialog = false },
            ) {
              Text("Cancel")
            }
          },
        )
      }
    }

    if (showDeleteDialog) {
      DeleteConfirmationDialog(
        isOpen = true,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
          selectionManager.deleteSelected()
          showDeleteDialog = false
        },
        itemCount = selectionManager.selectedCount,
        itemType = "playlist",
      )
    }
  }
}

@Composable
private fun PlaylistListContent(
  playlistsWithCount: List<PlaylistWithCount>,
  listState: androidx.compose.foundation.lazy.LazyListState,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<PlaylistWithCount, Int>,
  onPlaylistClick: (PlaylistWithCount) -> Unit,
  onPlaylistLongClick: (PlaylistWithCount) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Check if at top of list to hide scrollbar during pull-to-refresh
  val isAtTop by remember {
    derivedStateOf {
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
  }

  // Only show scrollbar if list has more than 20 items
  val hasEnoughItems = playlistsWithCount.size > 20

  // Animate scrollbar alpha
  val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
    label = "scrollbarAlpha",
  )

  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    listState = listState,
    modifier = modifier.fillMaxSize(),
  ) {
    LazyColumnScrollbar(
      state = listState,
      settings = ScrollbarSettings(
        thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
        thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
      ),
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        items(playlistsWithCount, key = { it.playlist.id }) { playlistWithCount ->
          PlaylistCard(
            playlist = playlistWithCount.playlist,
            itemCount = playlistWithCount.itemCount,
            isSelected = selectionManager.isSelected(playlistWithCount),
            onClick = { onPlaylistClick(playlistWithCount) },
            onLongClick = { onPlaylistLongClick(playlistWithCount) },
            onThumbClick = { onPlaylistClick(playlistWithCount) },
          )
        }
      }
    }
  }
}

@Composable
private fun CreatePlaylistDialog(
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var playlistName by remember { mutableStateOf("") }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Playlist") },
    text = {
      androidx.compose.material3.OutlinedTextField(
        value = playlistName,
        onValueChange = { playlistName = it },
        label = { Text("Playlist Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = {
          if (playlistName.isNotBlank()) {
            onConfirm(playlistName)
          }
        },
        enabled = playlistName.isNotBlank(),
      ) {
        Text("Create")
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun AddM3UPlaylistDialog(
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var playlistUrl by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = if (isLoading) {
      {}
    } else {
      onDismiss
    },
    title = { Text("Add M3U/M3U8 Playlist") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Enter the URL of an M3U or M3U8 playlist file",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        androidx.compose.material3.OutlinedTextField(
          value = playlistUrl,
          onValueChange = { playlistUrl = it },
          label = { Text("Playlist URL") },
          placeholder = { Text("https://example.com/playlist.m3u8") },
          singleLine = false,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
          enabled = !isLoading,
          supportingText = {
            Text(
              text = "Supports .m3u and .m3u8 formats",
              style = MaterialTheme.typography.bodySmall
            )
          }
        )
        
        if (isLoading) {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
          }
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = {
          if (playlistUrl.isNotBlank()) {
            isLoading = true
            onConfirm(playlistUrl.trim())
          }
        },
        enabled = playlistUrl.isNotBlank() && !isLoading,
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(
        onClick = onDismiss,
        enabled = !isLoading
      ) {
        Text("Cancel")
      }
    },
  )
}
