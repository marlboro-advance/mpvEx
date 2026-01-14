package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.FolderViewMode
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.MediaLayoutMode
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.GridColumnSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.ViewModeSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.filesystem.FileSystemBrowserRootScreen
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.LoadingState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen
import app.marlboroadvance.mpvex.ui.compose.LocalLazyGridState
import app.marlboroadvance.mpvex.ui.compose.LocalLazyListState
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject
import java.io.File

@Serializable
object FolderListScreen : Screen {
  @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val folderViewMode by browserPreferences.folderViewMode.collectAsState()

    when (folderViewMode) {
      FolderViewMode.FileManager -> FileSystemBrowserRootScreen.Content()
      FolderViewMode.AlbumView -> MediaStoreFolderListContent()
    }
  }

  @Composable
  private fun MediaStoreFolderListContent() {
    val context = LocalContext.current
    val viewModel: FolderListViewModel =
      viewModel(factory = FolderListViewModel.factory(context.applicationContext as android.app.Application))
    val videoFolders by viewModel.videoFolders.collectAsState()
    val foldersWithNewCount by viewModel.foldersWithNewCount.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()
    val foldersWereDeleted by viewModel.foldersWereDeleted.collectAsState()
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
    val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
    val foldersPreferences = koinInject<app.marlboroadvance.mpvex.preferences.FoldersPreferences>()
    val advancedPreferences = koinInject<app.marlboroadvance.mpvex.preferences.AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Using MediaFileRepository singleton directly
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Use the LazyListState from CompositionLocal instead of creating a new one
    val listState = LocalLazyListState.current
    // Use the LazyGridState from CompositionLocal instead of creating a new one
    val gridState = LocalLazyGridState.current
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var videosLoaded by remember { mutableStateOf(false) }

    // Sorting
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()

    // View mode
    val sortedFolders =
      remember(videoFolders, folderSortType, folderSortOrder) {
        SortUtils.sortFolders(videoFolders, folderSortType, folderSortOrder)
      }
    val filteredFolders = if (isSearching && searchQuery.isNotBlank()) {
      sortedFolders.filter { folder ->
        folder.name.contains(searchQuery, ignoreCase = true) ||
          folder.path.contains(searchQuery, ignoreCase = true)
      }
    } else {
      sortedFolders
    }

    // Selection manager (folders handle deletion through videos)
    val selectionManager =
      rememberSelectionManager(
        items = sortedFolders,
        getId = { it.bucketId },
        onDeleteItems = { folders, _ ->
          // Delete all videos in selected folders via ViewModel
          val ids = folders.map { it.bucketId }.toSet()
          val videos = MediaFileRepository.getVideosForBuckets(context, ids)
          viewModel.deleteVideos(videos)
          Pair(videos.size, 0) // Return (successCount, failureCount)
        },
        onOperationComplete = { viewModel.refresh() },
      )

    // Permissions
    val permissionState =
      PermissionUtils.handleStoragePermission(
        onPermissionGranted = { viewModel.refresh() },
      )
    
    // Update MainScreen about permission state to control FAB visibility
    LaunchedEffect(permissionState.status) {
      app.marlboroadvance.mpvex.ui.browser.MainScreen.updatePermissionState(
        isDenied = permissionState.status is PermissionStatus.Denied
      )
    }

    // Listen for lifecycle resume events to refresh new video counts when coming back
    DisposableEffect(lifecycleOwner) {
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            // Recalculate new video counts when returning to the screen
            viewModel.recalculateNewVideoCounts()
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    LaunchedEffect(isSearching) {
      if (isSearching && !videosLoaded) {
        // Load all videos across all folders using all bucketIds
        val bucketIds = videoFolders.map { it.bucketId }.toSet()
        allVideos = MediaFileRepository.getVideosForBuckets(context, bucketIds)
        videosLoaded = true
      }
      if (!isSearching) {
        videosLoaded = false
        allVideos = emptyList()
      }
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    val filteredVideos = if (isSearching && searchQuery.isNotBlank() && videosLoaded) {
      allVideos.filter { video ->
        video.title.contains(searchQuery, ignoreCase = true) ||
          video.displayName.contains(searchQuery, ignoreCase = true) ||
          video.path.contains(searchQuery, ignoreCase = true)
      }
    } else emptyList()

    // Predictive back: Only intercept when in selection mode OR search mode
    androidx.activity.compose.BackHandler(enabled = selectionManager.isInSelectionMode || isSearching) {
      when {
        selectionManager.isInSelectionMode -> selectionManager.clear()
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }
      }
    }

    Scaffold(
      topBar = {
        if (isSearching) {
          // Search mode - show search bar instead of top bar
          SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text("Search videos...") },
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
            title = stringResource(app.marlboroadvance.mpvex.R.string.app_name),
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = videoFolders.size,
            onBackClick = null, // No back button for folder list (root screen)
            onCancelSelection = { selectionManager.clear() },
            onSortClick = { sortDialogOpen.value = true },
            onSettingsClick = { backstack.add(PreferencesScreen) },
            onSearchClick = { isSearching = !isSearching },
            onDeleteClick = { deleteDialogOpen.value = true },
            onRenameClick = null,
            isSingleSelection = selectionManager.isSingleSelection,
            onInfoClick = null,
            onShareClick = {
              // Share all videos across selected folders with a single chooser
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = MediaFileRepository.getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  MediaUtils.shareVideos(context, allVideos)
                }
              }
            },
            onPlayClick = {
              // Play all videos from selected folders as a playlist
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = MediaFileRepository.getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  if (allVideos.size == 1) {
                    // Single video - play normally
                    MediaUtils.playFile(allVideos.first(), context)
                  } else {
                    // Multiple videos - play as playlist
                    val intent = Intent(Intent.ACTION_VIEW, allVideos.first().uri)
                    intent.setClass(context, app.marlboroadvance.mpvex.ui.player.PlayerActivity::class.java)
                    intent.putExtra("internal_launch", true)
                    intent.putParcelableArrayListExtra("playlist", ArrayList(allVideos.map { it.uri }))
                    intent.putExtra("playlist_index", 0)
                    intent.putExtra("launch_source", "playlist")
                    context.startActivity(intent)
                  }
                  // Clear selection after starting playback
                  selectionManager.clear()
                }
              }
            },
            onBlacklistClick = {
              // Add selected folders to blacklist
              coroutineScope.launch {
                val selectedFolders = selectionManager.getSelectedItems()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get().toMutableSet()
                selectedFolders.forEach { folder ->
                  blacklistedFolders.add(folder.path)
                }
                foldersPreferences.blacklistedFolders.set(blacklistedFolders)
                // Clear selection after blacklisting
                selectionManager.clear()
                // Refresh folder list to apply blacklist
                viewModel.refresh()
                // Show toast to confirm
                android.widget.Toast.makeText(
                  context,
                  context.getString(app.marlboroadvance.mpvex.R.string.pref_folders_blacklisted),
                  android.widget.Toast.LENGTH_SHORT,
                ).show()
              }
            },
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
          )
        }
      },
      floatingActionButton = {
        // FAB has been moved to MainScreen
      },
    ) { padding ->
      when (permissionState.status) {
        PermissionStatus.Granted -> {
          if (isSearching) {
            // Search results
            if (searchQuery.isNotBlank() && videosLoaded) {
              if (filteredVideos.isEmpty()) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = 80.dp), // Account for bottom navigation bar
                  contentAlignment = Alignment.Center,
                ) {
                  EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "No videos found",
                    message = "Try a different search term.",
                  )
                }
              } else {
                // Use the shared LazyListState from CompositionLocal for FAB to detect scrolling
                val searchListState = LocalLazyListState.current

                // Check if at top of list to hide scrollbar
                val isAtTop by remember {
                  derivedStateOf {
                    searchListState.firstVisibleItemIndex == 0 && searchListState.firstVisibleItemScrollOffset == 0
                  }
                }

                // Only show scrollbar if list has more than 20 items
                val hasEnoughItems = filteredVideos.size > 20

                // Animate scrollbar alpha
                val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
                  targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
                  animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                  label = "scrollbarAlpha",
                )

                LazyColumnScrollbar(
                  state = searchListState,
                  settings = ScrollbarSettings(
                    thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
                    thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
                  ),
                  modifier = Modifier
                    .padding(padding)
                    .padding(bottom = 80.dp),
                ) {
                  LazyColumn(
                    state = searchListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
                  ) {
                    items(filteredVideos) { video ->
                      VideoCard(
                        video = video,
                        progressPercentage = null,
                        isRecentlyPlayed = false,
                        isSelected = false,
                        onClick = { MediaUtils.playFile(video, context, "search") },
                        onLongClick = {},
                        onThumbClick = {},
                        showSubtitleIndicator = showSubtitleIndicator,
                      )
                    }
                  }
                }
              }
            }
          } else {
            // Normal mode - show folder list
            FolderListContent(
              folders = filteredFolders,
              foldersWithNewCount = foldersWithNewCount,
              listState = listState,
              gridState = gridState,
              isRefreshing = isRefreshing,
              isLoading = isLoading,
              hasCompletedInitialLoad = hasCompletedInitialLoad,
              foldersWereDeleted = foldersWereDeleted,
              recentlyPlayedFilePath = recentlyPlayedFilePath,
              onRefresh = { viewModel.refresh() },
              selectionManager = selectionManager,
              onFolderClick = { folder ->
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(folder)
                } else {
                  backstack.add(VideoListScreen(folder.bucketId, folder.name))
                }
              },
              onFolderLongClick = { folder -> selectionManager.toggle(folder) },
              modifier = Modifier.padding(padding),
              isGridMode = mediaLayoutMode == MediaLayoutMode.GRID,
            )
          }
        }

        is PermissionStatus.Denied -> {
          PermissionDeniedState(
            onRequestPermission = { permissionState.launchPermissionRequest() },
            modifier = Modifier.padding(padding),
          )
        }
      }

      FolderSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = folderSortType,
        sortOrder = folderSortOrder,
        onSortTypeChange = { browserPreferences.folderSortType.set(it) },
        onSortOrderChange = { browserPreferences.folderSortOrder.set(it) },
      )

      DeleteConfirmationDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemType = "folder",
        itemCount = selectionManager.selectedCount,
      )
    }
  }
}

@Composable
private fun FolderListContent(
  folders: List<VideoFolder>,
  foldersWithNewCount: List<FolderWithNewCount>,
  listState: LazyListState,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  isRefreshing: MutableState<Boolean>,
  isLoading: Boolean,
  hasCompletedInitialLoad: Boolean,
  foldersWereDeleted: Boolean,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
  modifier: Modifier = Modifier,
  isGridMode: Boolean = false
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()

  // Show loading or empty state based on loading status
  // Only show empty state after initial scan completes with no results
  val showEmpty = folders.isEmpty() && !isLoading && hasCompletedInitialLoad
  val showLoading = isLoading && folders.isEmpty()

  // Check if at top of list
  val isAtTop by remember(gridState, listState) {
    derivedStateOf {
      if (isGridMode) {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
      } else {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
      }
    }
  }

  // Only show scrollbar if list has more than 20 items
  val hasEnoughItems = folders.size > 20

  // Animate scrollbar alpha
  val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
    label = "scrollbarAlpha",
  )

  val columns = when (mediaLayoutMode) {
    MediaLayoutMode.LIST -> 1
    MediaLayoutMode.GRID -> folderGridColumns
  }

  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    listState = listState,
    modifier = modifier.fillMaxSize(),
  ) {
    // Show centered states when loading or empty
    if (showLoading || showEmpty) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 80.dp), // Account for bottom navigation bar
        contentAlignment = Alignment.Center,
      ) {
        if (showLoading) {
          LoadingState(
            icon = Icons.Filled.Folder,
            title = "Scanning for videos...",
            message = "Please wait while we search your device",
          )
        } else if (showEmpty) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No video folders found",
            message = "Add some video files to your device to see them here",
          )
        }
      }
    } else {
      // Show folder list
      if (isGridMode) {
        // Grid layout
        LazyVerticalGridScrollbar(
          state = gridState,
          settings = ScrollbarSettings(
            thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
            thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
          ),
          modifier = Modifier.padding(bottom = 80.dp),
        ) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(folderGridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
          items(folders.size) { index ->
            val folder = folders[index]
            val isRecentlyPlayed =
              recentlyPlayedFilePath?.let { filePath ->
                val file = File(filePath)
                file.parent == folder.path
              } ?: false

            val newCount = foldersWithNewCount.find { it.folder.bucketId == folder.bucketId }?.newVideoCount ?: 0

            FolderCard(
              folder = folder,
              isSelected = selectionManager.isSelected(folder),
              isRecentlyPlayed = isRecentlyPlayed,
              onClick = { onFolderClick(folder) },
              onLongClick = { onFolderLongClick(folder) },
              onThumbClick = if (tapThumbnailToSelect) {
                { onFolderLongClick(folder) }
              } else {
                { onFolderClick(folder) }
              },
              newVideoCount = newCount,
              isGridMode = true,
            )
          }
        }
        }
      } else {
        // List layout
        LazyColumnScrollbar(
          state = listState,
          settings = ScrollbarSettings(
            thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
            thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
          ),
          modifier = Modifier.padding(bottom = 80.dp),
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
          ) {
            items(folders) { folder ->
              val isRecentlyPlayed =
                recentlyPlayedFilePath?.let { filePath ->
                  val file = File(filePath)
                  file.parent == folder.path
                } ?: false

            // Get new video count for this folder
              val newCount = foldersWithNewCount.find { it.folder.bucketId == folder.bucketId }?.newVideoCount ?: 0

              FolderCard(
                folder = folder,
                isSelected = selectionManager.isSelected(folder),
                isRecentlyPlayed = isRecentlyPlayed,
                onClick = { onFolderClick(folder) },
                onLongClick = { onFolderLongClick(folder) },
                onThumbClick = if (tapThumbnailToSelect) {
                  { onFolderLongClick(folder) }
                } else {
                  { onFolderClick(folder) }
                },
                newVideoCount = newCount,
                isGridMode = false,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  // Folder thumbnails removed
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()

  val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
  val videoGridColumns by browserPreferences.videoGridColumns.collectAsState()

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Grid Columns",
      currentValue = folderGridColumns,
      onValueChange = { browserPreferences.folderGridColumns.set(it) },
      valueRange = 2f..4f,
      steps = 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Video Grid Columns",
      currentValue = videoGridColumns,
      onValueChange = { browserPreferences.videoGridColumns.set(it) },
    )
  } else null

  val isAlbumView = folderViewMode == FolderViewMode.AlbumView

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = if (isAlbumView) "Sort & View Options" else "View Options",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types =
      listOf(
        FolderSortType.Title.displayName,
        FolderSortType.Date.displayName,
        FolderSortType.Size.displayName,
      ),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
    showSortOptions = isAlbumView,
    viewModeSelector =
      ViewModeSelector(
        label = "View Mode",
        firstOptionLabel = "Folder",
        secondOptionLabel = "Tree",
        firstOptionIcon = Icons.Filled.ViewModule,
        secondOptionIcon = Icons.Filled.AccountTree,
        isFirstOptionSelected = folderViewMode == FolderViewMode.AlbumView,
        onViewModeChange = { isFirstOption ->
          browserPreferences.folderViewMode.set(
            if (isFirstOption) FolderViewMode.AlbumView else FolderViewMode.FileManager,
          )
        },
      ),
    layoutModeSelector = ViewModeSelector(
      label = "Layout",
      firstOptionLabel = "List",
      secondOptionLabel = "Grid",
      firstOptionIcon = Icons.AutoMirrored.Filled.ViewList,
      secondOptionIcon = Icons. Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences. mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode. GRID
        )
      },
    ),
    visibilityToggles =
      listOf(
        // Folder thumbnails toggle removed
        VisibilityToggle(
          label = "Full Name",
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        ),
        VisibilityToggle(
          label = "Path",
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        ),
        VisibilityToggle(
          label = "Total Videos",
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        ),
        VisibilityToggle(
          label = "Total Duration",
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        ),
        VisibilityToggle(
          label = "Folder Size",
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        ),
      ),
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}
