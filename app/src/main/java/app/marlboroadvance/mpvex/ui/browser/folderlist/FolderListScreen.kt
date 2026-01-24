package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.FolderViewMode
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.MediaLayoutMode
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.GridColumnSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.ViewModeSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.filesystem.FileSystemDirectoryScreen
import app.marlboroadvance.mpvex.ui.browser.filesystem.FileSystemBrowserRootScreen
import app.marlboroadvance.mpvex.ui.browser.filesystem.searchRecursively
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.LoadingState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.compose.LocalLazyGridState
import app.marlboroadvance.mpvex.ui.compose.LocalLazyListState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
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

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  private fun MediaStoreFolderListContent() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // ViewModels and preferences
    val viewModel: FolderListViewModel = viewModel(
      factory = FolderListViewModel.factory(context.applicationContext as android.app.Application)
    )
    val browserPreferences = koinInject<BrowserPreferences>()
    val gesturePreferences = koinInject<GesturePreferences>()
    val foldersPreferences = koinInject<FoldersPreferences>()
    val advancedPreferences = koinInject<app.marlboroadvance.mpvex.preferences.AdvancedPreferences>()

    // State collection
    val videoFolders by viewModel.videoFolders.collectAsState()
    val foldersWithNewCount by viewModel.foldersWithNewCount.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()
    val foldersWereDeleted by viewModel.foldersWereDeleted.collectAsState()

    // Preferences
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
    val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
    val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()

    // UI state
    val listState = LocalLazyListState.current
    val gridState = LocalLazyGridState.current
    val navigationBarHeight = LocalNavigationBarHeight.current
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
    var isSearchLoading by remember { mutableStateOf(false) }

    // FAB state
    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      uri?.let {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        MediaUtils.playFile(it.toString(), context, "open_file")
      }
    }

    // Sorting and filtering
    val sortedFolders = remember(videoFolders, folderSortType, folderSortOrder) {
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

    // Selection manager
    val selectionManager = rememberSelectionManager(
      items = sortedFolders,
      getId = { it.bucketId },
      onDeleteItems = { folders, _ ->
        val ids = folders.map { it.bucketId }.toSet()
        val videos = app.marlboroadvance.mpvex.repository.MediaFileRepository.getVideosForBuckets(context, ids)
        viewModel.deleteVideos(videos)
        Pair(videos.size, 0)
      },
      onOperationComplete = { viewModel.refresh() },
    )

    // Permissions
    val permissionState = PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() },
    )

    // Update MainScreen about permission state
    LaunchedEffect(permissionState.status) {
      app.marlboroadvance.mpvex.ui.browser.MainScreen.updatePermissionState(
        isDenied = permissionState.status is PermissionStatus.Denied
      )
    }

    // Lifecycle observer for refresh
    DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          viewModel.recalculateNewVideoCounts()
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Search functionality
    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    LaunchedEffect(searchQuery, isSearching) {
      if (isSearching && searchQuery.isNotBlank()) {
        isSearchLoading = true
        coroutineScope.launch {
          try {
            val allResults = mutableListOf<FileSystemItem>()
            videoFolders.forEach { folder ->
              try {
                val folderResults = searchRecursively(context, folder.path, searchQuery)
                allResults.addAll(folderResults)
              } catch (e: Exception) {
                android.util.Log.e("FolderListScreen", "Error searching folder ${folder.path}", e)
              }
            }
            searchResults = allResults
          } catch (e: Exception) {
            android.util.Log.e("FolderListScreen", "Error during search", e)
            searchResults = emptyList()
          } finally {
            isSearchLoading = false
          }
        }
      } else {
        searchResults = emptyList()
      }
    }

    // Back handler
    androidx.activity.compose.BackHandler(
      enabled = selectionManager.isInSelectionMode || isSearching || isFabExpanded.value
    ) {
      when {
        isFabExpanded.value -> isFabExpanded.value = false
        selectionManager.isInSelectionMode -> selectionManager.clear()
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }
      }
    }

    // FAB scroll tracking
    app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = isFabExpanded.value,
      onExpandedChange = { isFabExpanded.value = it },
    )

    Scaffold(
      topBar = {
        if (isSearching) {
          SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text("Search folders and videos...") },
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
              .padding(horizontal = 16.dp),
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
            onBackClick = null,
            onCancelSelection = { selectionManager.clear() },
            onSortClick = { sortDialogOpen.value = true },
            onSearchClick = { isSearching = !isSearching },
            onSettingsClick = {
              backstack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
            },
            onDeleteClick = { deleteDialogOpen.value = true },
            onRenameClick = null,
            isSingleSelection = selectionManager.isSingleSelection,
            onInfoClick = null,
            onShareClick = {
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = app.marlboroadvance.mpvex.repository.MediaFileRepository
                  .getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  MediaUtils.shareVideos(context, allVideos)
                }
              }
            },
            onPlayClick = {
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = app.marlboroadvance.mpvex.repository.MediaFileRepository
                  .getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  if (allVideos.size == 1) {
                    MediaUtils.playFile(allVideos.first(), context)
                  } else {
                    val intent = Intent(Intent.ACTION_VIEW, allVideos.first().uri)
                    intent.setClass(context, app.marlboroadvance.mpvex.ui.player.PlayerActivity::class.java)
                    intent.putExtra("internal_launch", true)
                    intent.putParcelableArrayListExtra("playlist", ArrayList(allVideos.map { it.uri }))
                    intent.putExtra("playlist_index", 0)
                    intent.putExtra("launch_source", "playlist")
                    context.startActivity(intent)
                  }
                  selectionManager.clear()
                }
              }
            },
            onBlacklistClick = {
              coroutineScope.launch {
                val selectedFolders = selectionManager.getSelectedItems()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get().toMutableSet()
                selectedFolders.forEach { folder ->
                  blacklistedFolders.add(folder.path)
                }
                foldersPreferences.blacklistedFolders.set(blacklistedFolders)
                selectionManager.clear()
                viewModel.refresh()
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
        FloatingActionButtonMenu(
          modifier = Modifier.padding(bottom = 88.dp),
          expanded = isFabExpanded.value,
          button = {
            TooltipBox(
              positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                if (isFabExpanded.value) {
                  TooltipAnchorPosition.Start
                } else {
                  TooltipAnchorPosition.Above
                }
              ),
              tooltip = { PlainTooltip { Text("Toggle menu") } },
              state = rememberTooltipState(),
            ) {
              ToggleFloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(
                  visible = !selectionManager.isInSelectionMode && isFabVisible.value && !app.marlboroadvance.mpvex.ui.browser.MainScreen.getPermissionDeniedState(),
                  alignment = Alignment.BottomEnd,
                ),
                checked = isFabExpanded.value,
                onCheckedChange = { isFabExpanded.value = !isFabExpanded.value },
              ) {
                val imageVector by remember {
                  derivedStateOf {
                    if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.PlayArrow
                  }
                }
                Icon(
                  painter = rememberVectorPainter(imageVector),
                  contentDescription = null,
                  modifier = Modifier.animateIcon({ checkedProgress }),
                )
              }
            }
          },
        ) {
          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              filePicker.launch(arrayOf("video/*"))
            },
            icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
            text = { Text(text = "Open File") },
          )

          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              coroutineScope.launch {
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                val lastPlayed = recentlyPlayedVideos.firstOrNull()
                if (lastPlayed != null) {
                  MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                }
              }
            },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            text = { Text(text = "Recently Played") },
          )

          FloatingActionButtonMenuItem(
            onClick = {
              isFabExpanded.value = false
              showLinkDialog.value = true
            },
            icon = { Icon(Icons.Filled.Link, contentDescription = null) },
            text = { Text(text = "Open Link") },
          )
        }
      },
    ) { padding ->
      Box(modifier = Modifier.padding(padding)) {
        when (permissionState.status) {
          PermissionStatus.Granted -> {
            if (isSearching) {
              SearchContent(
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearchLoading = isSearchLoading,
                navigationBarHeight = navigationBarHeight,
                showSubtitleIndicator = showSubtitleIndicator,
                onFolderClick = { folder ->
                  backstack.add(FileSystemDirectoryScreen(folder.path))
                },
                onVideoClick = { video ->
                  MediaUtils.playFile(video, context, "search")
                },
              )
            } else {
              FolderListContent(
                folders = filteredFolders,
                foldersWithNewCount = foldersWithNewCount,
                recentlyPlayedFilePath = recentlyPlayedFilePath,
                isLoading = isLoading,
                scanStatus = scanStatus,
                hasCompletedInitialLoad = hasCompletedInitialLoad,
                foldersWereDeleted = foldersWereDeleted,
                mediaLayoutMode = mediaLayoutMode,
                folderGridColumns = folderGridColumns,
                tapThumbnailToSelect = tapThumbnailToSelect,
                navigationBarHeight = navigationBarHeight,
                listState = listState,
                gridState = gridState,
                isRefreshing = isRefreshing,
                selectionManager = selectionManager,
                onRefresh = { viewModel.refresh() },
                onFolderClick = { folder ->
                  if (selectionManager.isInSelectionMode) {
                    selectionManager.toggle(folder)
                  } else {
                    backstack.add(app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen(folder.bucketId, folder.name))
                  }
                },
                onFolderLongClick = { folder ->
                  selectionManager.toggle(folder)
                },
              )
            }
          }

          is PermissionStatus.Denied -> {
            PermissionDeniedState(
              onRequestPermission = { permissionState.launchPermissionRequest() },
              modifier = Modifier,
            )
          }
        }
      }

      // Dialogs
      PlayLinkSheet(
        isOpen = showLinkDialog.value,
        onDismiss = { showLinkDialog.value = false },
        onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
      )

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
        itemNames = selectionManager.getSelectedItems().map { it.name },
      )
    }
  }
}

@Composable
private fun SearchContent(
  searchQuery: String,
  searchResults: List<FileSystemItem>,
  isSearchLoading: Boolean,
  navigationBarHeight: androidx.compose.ui.unit.Dp,
  showSubtitleIndicator: Boolean,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  onVideoClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
) {
  val searchListState = LazyListState()

  Box(modifier = Modifier.fillMaxSize()) {
    when {
      isSearchLoading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp), // Account for bottom navigation bar
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Searching all folders...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      searchQuery.isNotBlank() && searchResults.isEmpty() -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No results found",
            message = "No files or folders match \"$searchQuery\"",
          )
        }
      }

      searchResults.isNotEmpty() -> {
        Box(modifier = Modifier.fillMaxSize()) {
          // Content extends full height for transparency
          LazyColumn(
            state = searchListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = 8.dp,
              end = 8.dp,
              top = 12.dp,
              bottom = navigationBarHeight
            ),
          ) {
            // Separate folders and videos for proper ordering and deduplicate
            val folders = searchResults.filterIsInstance<FileSystemItem.Folder>().distinctBy { it.path }
            val videos = searchResults.filterIsInstance<FileSystemItem.VideoFile>().distinctBy { it.video.id }

            // Folders first
            items(
              items = folders,
              key = { "search_folder_${it.path}_${it.hashCode()}" },
            ) { folder ->
              val folderModel = VideoFolder(
                bucketId = folder.path,
                name = folder.name,
                path = folder.path,
                videoCount = folder.videoCount,
                totalSize = folder.totalSize,
                totalDuration = folder.totalDuration,
                lastModified = folder.lastModified / 1000,
              )

              FolderCard(
                folder = folderModel,
                isSelected = false,
                isRecentlyPlayed = false,
                onClick = { onFolderClick(folder) },
                onLongClick = { },
                onThumbClick = { onFolderClick(folder) },
                isGridMode = false,
              )
            }

            // Videos second
            items(
              items = videos,
              key = { "search_video_${it.video.id}_${it.video.path}_${it.hashCode()}" },
            ) { videoFile ->
              VideoCard(
                video = videoFile.video,
                progressPercentage = null,
                isRecentlyPlayed = false,
                isSelected = false,
                onClick = { onVideoClick(videoFile.video) },
                onLongClick = { },
                onThumbClick = { onVideoClick(videoFile.video) },
                isGridMode = false,
                showSubtitleIndicator = showSubtitleIndicator,
                overrideShowSizeChip = null,
                overrideShowResolutionChip = null,
                useFolderNameStyle = false,
              )
            }
          }

          // Scrollbar with bottom padding to avoid overlap with navigation
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = navigationBarHeight)
          ) {
            LazyColumnScrollbar(
              state = searchListState,
              settings = ScrollbarSettings(
                thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                thumbSelectedColor = MaterialTheme.colorScheme.primary,
              ),
            ) {
              // Empty content - scrollbar only
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FolderListContent(
  folders: List<VideoFolder>,
  foldersWithNewCount: List<app.marlboroadvance.mpvex.ui.browser.folderlist.FolderWithNewCount>,
  recentlyPlayedFilePath: String?,
  isLoading: Boolean,
  scanStatus: String?,
  hasCompletedInitialLoad: Boolean,
  foldersWereDeleted: Boolean,
  mediaLayoutMode: MediaLayoutMode,
  folderGridColumns: Int,
  tapThumbnailToSelect: Boolean,
  navigationBarHeight: androidx.compose.ui.unit.Dp,
  listState: LazyListState,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onRefresh: suspend () -> Unit,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
) {
  val isGridMode = mediaLayoutMode == MediaLayoutMode.GRID
  val showLoading = isLoading && !hasCompletedInitialLoad
  val showEmpty = folders.isEmpty() && hasCompletedInitialLoad && !foldersWereDeleted

  // Scrollbar alpha animation
  val isAtTop by remember {
    derivedStateOf {
      if (isGridMode) {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
      } else {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
      }
    }
  }

  val hasEnoughItems = folders.size > 20
  val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
    label = "scrollbarAlpha",
  )

  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    listState = listState,
    modifier = Modifier.fillMaxSize(),
  ) {
    if (showLoading || showEmpty) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        if (showLoading) {
          LoadingState(
            icon = Icons.Filled.Folder,
            title = "Scanning for videos...",
            message = scanStatus ?: "Please wait while we search your device",
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
      if (isGridMode) {
        GridContent(
          folders = folders,
          foldersWithNewCount = foldersWithNewCount,
          recentlyPlayedFilePath = recentlyPlayedFilePath,
          folderGridColumns = folderGridColumns,
          tapThumbnailToSelect = tapThumbnailToSelect,
          navigationBarHeight = navigationBarHeight,
          gridState = gridState,
          scrollbarAlpha = scrollbarAlpha,
          selectionManager = selectionManager,
          onFolderClick = onFolderClick,
          onFolderLongClick = onFolderLongClick,
        )
      } else {
        ListContent(
          folders = folders,
          foldersWithNewCount = foldersWithNewCount,
          recentlyPlayedFilePath = recentlyPlayedFilePath,
          tapThumbnailToSelect = tapThumbnailToSelect,
          navigationBarHeight = navigationBarHeight,
          listState = listState,
          scrollbarAlpha = scrollbarAlpha,
          selectionManager = selectionManager,
          onFolderClick = onFolderClick,
          onFolderLongClick = onFolderLongClick,
        )
      }

      // Show background enrichment progress
      if (scanStatus != null && !showLoading) {
        androidx.compose.material3.LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp),
          color = MaterialTheme.colorScheme.secondary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }
    }
  }
}

@Composable
private fun GridContent(
  folders: List<VideoFolder>,
  foldersWithNewCount: List<app.marlboroadvance.mpvex.ui.browser.folderlist.FolderWithNewCount>,
  recentlyPlayedFilePath: String?,
  folderGridColumns: Int,
  tapThumbnailToSelect: Boolean,
  navigationBarHeight: androidx.compose.ui.unit.Dp,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  scrollbarAlpha: Float,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(folderGridColumns),
      state = gridState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 8.dp,
        end = 8.dp,
        bottom = navigationBarHeight
      ),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      items(folders.size) { index ->
        val folder = folders[index]
        val isRecentlyPlayed = recentlyPlayedFilePath?.let { filePath ->
          val file = File(filePath)
          file.parent == folder.path
        } ?: false

        val newCount = foldersWithNewCount
          .find { it.folder.bucketId == folder.bucketId }
          ?.newVideoCount ?: 0

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

    // Scrollbar with bottom padding
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = navigationBarHeight)
    ) {
      LazyVerticalGridScrollbar(
        state = gridState,
        settings = ScrollbarSettings(
          thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
          thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
        ),
      ) {
        // Empty content - scrollbar only
      }
    }
  }
}

@Composable
private fun ListContent(
  folders: List<VideoFolder>,
  foldersWithNewCount: List<FolderWithNewCount>,
  recentlyPlayedFilePath: String?,
  tapThumbnailToSelect: Boolean,
  navigationBarHeight: androidx.compose.ui.unit.Dp,
  listState: LazyListState,
  scrollbarAlpha: Float,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 8.dp,
        end = 8.dp,
        bottom = navigationBarHeight
      ),
    ) {
      items(folders) { folder ->
        val isRecentlyPlayed = recentlyPlayedFilePath?.let { filePath ->
          val file = File(filePath)
          file.parent == folder.path
        } ?: false

        val newCount = foldersWithNewCount
          .find { it.folder.bucketId == folder.bucketId }
          ?.newVideoCount ?: 0

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

    // Scrollbar with bottom padding
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = navigationBarHeight)
    ) {
      LazyColumnScrollbar(
        state = listState,
        settings = ScrollbarSettings(
          thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
          thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
        ),
      ) {
        // Empty content - scrollbar only
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
      FolderSortType.entries
        .find { it.displayName == typeName }
        ?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
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
    viewModeSelector = ViewModeSelector(
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
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    visibilityToggles = listOf(
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
