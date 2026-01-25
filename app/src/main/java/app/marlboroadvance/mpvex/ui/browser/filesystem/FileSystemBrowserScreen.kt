package app.marlboroadvance.mpvex.ui.browser.filesystem

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.MediaLayoutMode
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserBottomBar
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.AddToPlaylistDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FileOperationProgressDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FolderPickerDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.GridColumnSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.RenameDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.ViewModeSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.CopyPasteOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject

/**
 * Root File System Browser screen - shows storage volumes
 */
@Serializable
object FileSystemBrowserRootScreen : app.marlboroadvance.mpvex.presentation.Screen {
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    FileSystemBrowserScreen(path = null)
  }
}

/**
 * File System Directory screen - shows contents of a specific directory
 */
@Serializable
data class FileSystemDirectoryScreen(
  val path: String,
) : app.marlboroadvance.mpvex.presentation.Screen {
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    FileSystemBrowserScreen(path = path)
  }
}

/**
 * File System Browser screen - browses directories and shows both folders and videos
 * @param path The directory path to browse, or null for storage roots
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileSystemBrowserScreen(path: String? = null) {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val browserPreferences = koinInject<BrowserPreferences>()
  val playerPreferences = koinInject<app.marlboroadvance.mpvex.preferences.PlayerPreferences>()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  // ViewModel - use path parameter if provided, otherwise show roots
  val viewModel: FileSystemBrowserViewModel = viewModel(
    key = "FileSystemBrowser_${path ?: "root"}",
    factory = FileSystemBrowserViewModel.factory(
      context.applicationContext as android.app.Application,
      path,
    ),
  )

  // State collection
  val currentPath by viewModel.currentPath.collectAsState()
  val items by viewModel.items.collectAsState()
  val videoFilesWithPlayback by viewModel.videoFilesWithPlayback.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val error by viewModel.error.collectAsState()
  val isAtRoot by viewModel.isAtRoot.collectAsState()
  val breadcrumbs by viewModel.breadcrumbs.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val itemsWereDeletedOrMoved by viewModel.itemsWereDeletedOrMoved.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
  val videoGridColumns by browserPreferences.videoGridColumns.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()

  // Check if there are folders mixed with videos AND we're in grid mode
  val hasMixedContentInGrid = items.any { it is FileSystemItem.Folder } &&
    items.any { it is FileSystemItem.VideoFile } &&
    mediaLayoutMode == MediaLayoutMode.GRID

  // Use standalone local states instead of CompositionLocal to avoid scroll issues with predictive back gesture
  val listState = remember { LazyListState() }
  val gridState = remember { androidx.compose.foundation.lazy.grid.LazyGridState() }
  
  // UI state
  val isRefreshing = remember { mutableStateOf(false) }
  val showLinkDialog = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }

  // FAB visibility for scroll-based hiding
  val isFabVisible = remember { mutableStateOf(true) }
  val isFabExpanded = remember { mutableStateOf(false) }
  
  // Search state
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var isSearching by rememberSaveable { mutableStateOf(false) }
  var searchResults by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
  var isSearchLoading by remember { mutableStateOf(false) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  
  // Get navigation bar height from MainScreen
  val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current

  // Copy/Move state
  val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
  val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
  val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
  val operationProgress by CopyPasteOps.operationProgress.collectAsState()

  // Bottom bar visibility state
  var showFloatingBottomBar by remember { mutableStateOf(false) }
  var showBottomNavigation by remember { mutableStateOf(true) }

  // Animation duration for responsive slide animations
  val animationDuration = 200

  // Selection managers - separate for folders and videos
  val folders = items.filterIsInstance<FileSystemItem.Folder>()
  val videos = items.filterIsInstance<FileSystemItem.VideoFile>().map { it.video }

  val folderSelectionManager = rememberSelectionManager(
    items = folders,
    getId = { it.path },
    onDeleteItems = { foldersToDelete, _ ->
      viewModel.deleteFolders(foldersToDelete)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  val videoSelectionManager = rememberSelectionManager(
    items = videos,
    getId = { it.id },
    onDeleteItems = { videosToDelete, _ ->
      viewModel.deleteVideos(videosToDelete)
    },
    onRenameItem = { video, newName ->
      viewModel.renameVideo(video, newName)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  // Determine which selection manager is active
  val isInSelectionMode = folderSelectionManager.isInSelectionMode || videoSelectionManager.isInSelectionMode
  val selectedCount = folderSelectionManager.selectedCount + videoSelectionManager.selectedCount
  val totalCount = folders.size + videos.size
  val isMixedSelection = folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode

  // Update bottom bar visibility with optimized animation sequencing
  LaunchedEffect(isInSelectionMode, videoSelectionManager.isInSelectionMode, isMixedSelection) {
    val shouldShowFloatingBar = isInSelectionMode && videoSelectionManager.isInSelectionMode && !isMixedSelection
    
    if (shouldShowFloatingBar) {
      // Entering selection mode: Hide bottom navigation immediately, then show floating bar
      showBottomNavigation = false
      showFloatingBottomBar = true
    } else {
      // Exiting selection mode: Hide floating bar and show bottom navigation immediately for better responsiveness
      showFloatingBottomBar = false
      showBottomNavigation = true
    }
  }

  // Permissions
  val permissionState = PermissionUtils.handleStoragePermission(
    onPermissionGranted = { viewModel.refresh() },
  )

  // Combined MainScreen updates for better performance and responsiveness
  LaunchedEffect(
    showBottomNavigation, 
    isInSelectionMode, 
    isMixedSelection, 
    videoSelectionManager.isInSelectionMode,
    permissionState.status
  ) {
    if (isAtRoot) {
      try {
        val mainScreenObj = app.marlboroadvance.mpvex.ui.browser.MainScreen
        val onlyVideosSelected = videoSelectionManager.isInSelectionMode && !folderSelectionManager.isInSelectionMode

        // Update all MainScreen states in one call to reduce overhead
        mainScreenObj.updateBottomBarVisibility(showBottomNavigation)
        mainScreenObj.updateSelectionState(
          isInSelectionMode = isInSelectionMode,
          isOnlyVideosSelected = onlyVideosSelected,
          selectionManager = if (onlyVideosSelected) videoSelectionManager else null
        )
        mainScreenObj.updatePermissionState(
          isDenied = permissionState.status is PermissionStatus.Denied
        )
      } catch (e: Exception) {
        Log.e("FileSystemBrowserScreen", "Failed to update MainScreen state", e)
      }
    }
  }

  // Cleanup: Restore bottom navigation bar when leaving the screen
  DisposableEffect(Unit) {
    onDispose {
      if (isAtRoot) {
        try {
          val mainScreenObj = app.marlboroadvance.mpvex.ui.browser.MainScreen
          // Restore bottom navigation when leaving the screen
          mainScreenObj.updateBottomBarVisibility(true)
        } catch (e: Exception) {
          Log.e("FileSystemBrowserScreen", "Failed to restore MainScreen bottom bar visibility", e)
        }
      }
    }
  }

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

  // Listen for lifecycle resume events
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Search functionality - recursive search through all subfolders
  LaunchedEffect(isSearching) {
    if (isSearching) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  LaunchedEffect(searchQuery, isSearching, isAtRoot, items) {
    if (isSearching && searchQuery.isNotBlank()) {
      isSearchLoading = true
      coroutineScope.launch {
        try {
          val results = if (isAtRoot) {
            // At storage roots - search across all storage volumes AND their parent directories
            val allResults = mutableListOf<FileSystemItem>()
            
            // Get unique parent directories from storage volumes
            val parentDirectories = items.filterIsInstance<FileSystemItem.Folder>()
              .map { it.path }
              .mapNotNull { path ->
                // Extract parent directory (e.g., /storage/emulated/0 from /storage/emulated/0/DCIM)
                val parentPath = java.io.File(path).parent
                parentPath
              }
              .distinct()
            
            // Search in parent directories (like /storage/emulated/0) directly
            parentDirectories.forEach { parentPath ->
              try {
                Log.d("FileSystemBrowserScreen", "Searching in parent directory: $parentPath")
                val parentResults = app.marlboroadvance.mpvex.ui.browser.filesystem.searchRecursively(context, parentPath, searchQuery)
                Log.d("FileSystemBrowserScreen", "Found ${parentResults.size} results in parent $parentPath")
                allResults.addAll(parentResults)
              } catch (e: Exception) {
                Log.e("FileSystemBrowserScreen", "Error searching parent directory $parentPath", e)
              }
            }
            
            // Also search in the storage volume folders themselves (existing behavior)
            items.filterIsInstance<FileSystemItem.Folder>().forEach { storageVolume ->
              try {
                Log.d("FileSystemBrowserScreen", "Searching in storage volume: ${storageVolume.path}")
                val rootResults = app.marlboroadvance.mpvex.ui.browser.filesystem.searchRecursively(context, storageVolume.path, searchQuery)
                Log.d("FileSystemBrowserScreen", "Found ${rootResults.size} results in ${storageVolume.path}")
                allResults.addAll(rootResults)
              } catch (e: Exception) {
                Log.e("FileSystemBrowserScreen", "Error searching volume ${storageVolume.path}", e)
              }
            }
            
            // Remove duplicates based on file path
            val uniqueResults = allResults.distinctBy { item ->
              when (item) {
                is FileSystemItem.VideoFile -> item.video.path
                is FileSystemItem.Folder -> item.path
              }
            }
            
            Log.d("FileSystemBrowserScreen", "Total search results after deduplication: ${uniqueResults.size}")
            uniqueResults
          } else if (currentPath != null) {
            // In a specific directory - search from there
            Log.d("FileSystemBrowserScreen", "Searching in directory: $currentPath")
            val results = app.marlboroadvance.mpvex.ui.browser.filesystem.searchRecursively(context, currentPath, searchQuery)
            Log.d("FileSystemBrowserScreen", "Found ${results.size} results in $currentPath")
            results
          } else {
            emptyList()
          }
          searchResults = results
        } catch (e: Exception) {
          Log.e("FileSystemBrowserScreen", "Error during search", e)
          searchResults = emptyList()
        } finally {
          isSearchLoading = false
        }
      }
    } else {
      searchResults = emptyList()
    }
  }

  // Optimized predictive back handler for immediate response
  val shouldHandleBack = isInSelectionMode || isSearching || isFabExpanded.value
  BackHandler(enabled = shouldHandleBack) {
    when {
      isFabExpanded.value -> isFabExpanded.value = false
      isInSelectionMode -> {
        folderSelectionManager.clear()
        videoSelectionManager.clear()
      }
      isSearching -> {
        isSearching = false
        searchQuery = ""
      }
    }
  }

  // Track scroll for FAB visibility
  app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
    listState = listState,
    gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
    isFabVisible = isFabVisible,
    expanded = isFabExpanded.value,
    onExpandedChange = { isFabExpanded.value = it },
  )

  // Main content
  Box(modifier = Modifier.fillMaxSize()) {
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
                placeholder = {
                  Text(
                    if (isAtRoot) {
                      "Search in all storage volumes..."
                    } else {
                      "Search in ${breadcrumbs.lastOrNull()?.name ?: "folder"}..."
                    }
                  )
                },
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
            title = if (isAtRoot) {
              stringResource(app.marlboroadvance.mpvex.R.string.app_name)
            } else {
              breadcrumbs.lastOrNull()?.name ?: "Tree View"
            },
            isInSelectionMode = isInSelectionMode,
            selectedCount = selectedCount,
            totalCount = totalCount,
            onBackClick = if (isAtRoot) {
              null
            } else {
              { backstack.removeLastOrNull() }
            },
            onCancelSelection = {
              folderSelectionManager.clear()
              videoSelectionManager.clear()
            },
            onSortClick = { sortDialogOpen.value = true },
            onSearchClick = {
              isSearching = !isSearching
            },
            onSettingsClick = {
              backstack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
            },
            onDeleteClick = if (videoSelectionManager.isInSelectionMode && !isMixedSelection) {
              null
            } else {
              { deleteDialogOpen.value = true }
            },
            onRenameClick = if (videoSelectionManager.isSingleSelection && !isMixedSelection) {
              null
            } else {
              null
            },
            isSingleSelection = videoSelectionManager.isSingleSelection && !isMixedSelection,
            onInfoClick = if (videoSelectionManager.isInSelectionMode && !folderSelectionManager.isInSelectionMode) {
              {
                val video = videoSelectionManager.getSelectedItems().firstOrNull()
                if (video != null) {
                  val intent = Intent(context, app.marlboroadvance.mpvex.ui.mediainfo.MediaInfoActivity::class.java)
                  intent.action = Intent.ACTION_VIEW
                  intent.data = video.uri
                  context.startActivity(intent)
                  videoSelectionManager.clear()
                }
              }
            } else {
              null
            },
            onShareClick = {
              when {
                // Mixed selection: share videos from both selected videos and selected folders
                isMixedSelection -> {
                  coroutineScope.launch {
                    val selectedVideos = videoSelectionManager.getSelectedItems()
                    val selectedFolders = folderSelectionManager.getSelectedItems()

                    // Get all videos recursively from selected folders
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }

                    // Combine and share all videos
                    val allVideos = (selectedVideos + videosFromFolders).distinctBy { it.id }
                    if (allVideos.isNotEmpty()) {
                      MediaUtils.shareVideos(context, allVideos)
                    }
                  }
                }
                // Folders only: share all videos from selected folders
                folderSelectionManager.isInSelectionMode -> {
                  coroutineScope.launch {
                    val selectedFolders = folderSelectionManager.getSelectedItems()
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }
                    if (videosFromFolders.isNotEmpty()) {
                      MediaUtils.shareVideos(context, videosFromFolders)
                    }
                  }
                }
                // Videos only: use existing functionality
                videoSelectionManager.isInSelectionMode -> {
                  videoSelectionManager.shareSelected()
                }
              }
            },
            onPlayClick = {
              when {
                // Mixed selection: play videos from both selected videos and selected folders
                isMixedSelection -> {
                  coroutineScope.launch {
                    val selectedVideos = videoSelectionManager.getSelectedItems()
                    val selectedFolders = folderSelectionManager.getSelectedItems()

                    // Get all videos recursively from selected folders
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }

                    // Combine and play all videos as playlist
                    val allVideos = (selectedVideos + videosFromFolders).distinctBy { it.id }
                    if (allVideos.isNotEmpty()) {
                      playVideosAsPlaylist(context, allVideos)
                    }

                    // Clear selections
                    folderSelectionManager.clear()
                    videoSelectionManager.clear()
                  }
                }
                // Folders only: play all videos from selected folders as playlist
                folderSelectionManager.isInSelectionMode -> {
                  coroutineScope.launch {
                    val selectedFolders = folderSelectionManager.getSelectedItems()
                    val videosFromFolders = selectedFolders.flatMap { folder ->
                      collectVideosRecursively(context, folder.path)
                    }
                    if (videosFromFolders.isNotEmpty()) {
                      playVideosAsPlaylist(context, videosFromFolders)
                    }

                    // Clear selection
                    folderSelectionManager.clear()
                  }
                }
                // Videos only: use existing functionality
                videoSelectionManager.isInSelectionMode -> {
                  videoSelectionManager.playSelected()
                }
              }
            },
            onSelectAll = {
              folderSelectionManager.selectAll()
              videoSelectionManager.selectAll()
            },
            onInvertSelection = {
              folderSelectionManager.invertSelection()
              videoSelectionManager.invertSelection()
            },
            onDeselectAll = {
              folderSelectionManager.clear()
              videoSelectionManager.clear()
            },
          )
        }
      },
      floatingActionButton = {
        if (isAtRoot) {
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
                  modifier = Modifier
                    .animateFloatingActionButton(
                      visible = !isInSelectionMode && isFabVisible.value && !app.marlboroadvance.mpvex.ui.browser.MainScreen.getPermissionDeniedState(),
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
                  val recentlyPlayedVideos = app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
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
        }
      },
    ) { padding ->
      Box(modifier = Modifier.padding(padding)) {
        when (permissionState.status) {
          PermissionStatus.Granted -> {
            if (isSearching) {
              // Show search results
              FileSystemSearchContent(
                listState = listState, // Use the main listState for FAB tracking
                searchQuery = searchQuery,
                searchResults = searchResults,
                isLoading = isSearchLoading,
                videoFilesWithPlayback = videoFilesWithPlayback,
                showSubtitleIndicator = showSubtitleIndicator,
                isAtRoot = isAtRoot,
                navigationBarHeight = navigationBarHeight,
                isFabVisible = isFabVisible, // Pass FAB visibility state
                onVideoClick = { video ->
                  MediaUtils.playFile(video, context, "search")
                },
                onFolderClick = { folder ->
                  backstack.add(FileSystemDirectoryScreen(folder.path))
                  isSearching = false
                  searchQuery = ""
                },
                modifier = Modifier,
              )
            } else {
              FileSystemBrowserContent(
                listState = listState,
                gridState = gridState,
                items = items,
                videoFilesWithPlayback = videoFilesWithPlayback,
                isLoading = isLoading && items.isEmpty(),
                isRefreshing = isRefreshing,
                error = error,
                isAtRoot = isAtRoot,
                breadcrumbs = breadcrumbs,
                playlistMode = playlistMode,
                itemsWereDeletedOrMoved = itemsWereDeletedOrMoved,
                mediaLayoutMode = mediaLayoutMode,
                folderGridColumns = folderGridColumns,
                videoGridColumns = videoGridColumns,
                showSubtitleIndicator = if (hasMixedContentInGrid) false else showSubtitleIndicator,
                navigationBarHeight = navigationBarHeight,
                onRefresh = { viewModel.refresh() },
                onFolderClick = { folder ->
                  if (isInSelectionMode) {
                    folderSelectionManager.toggle(folder)
                  } else {
                    backstack.add(FileSystemDirectoryScreen(folder.path))
                  }
                },
                onFolderLongClick = { folder ->
                  folderSelectionManager.toggle(folder)
                },
                onVideoClick = { video ->
                  if (isInSelectionMode) {
                    videoSelectionManager.toggle(video)
                  } else {
                    // If playlist mode is enabled, play all videos in current folder starting from clicked one
                    if (playlistMode) {
                      val allVideos = videos
                      val startIndex = allVideos.indexOfFirst { it.id == video.id }
                      if (startIndex >= 0) {
                        if (allVideos.size == 1) {
                          // Single video - play normally
                          MediaUtils.playFile(video, context)
                        } else {
                          // Multiple videos - play as playlist starting from clicked video
                          val intent = Intent(Intent.ACTION_VIEW, allVideos[startIndex].uri)
                          intent.setClass(context, app.marlboroadvance.mpvex.ui.player.PlayerActivity::class.java)
                          intent.putExtra("internal_launch", true)
                          intent.putParcelableArrayListExtra("playlist", ArrayList(allVideos.map { it.uri }))
                          intent.putExtra("playlist_index", startIndex)
                          intent.putExtra("launch_source", "playlist")
                          context.startActivity(intent)
                        }
                      } else {
                        MediaUtils.playFile(video, context)
                      }
                    } else {
                      MediaUtils.playFile(video, context)
                    }
                  }
                },
                onVideoLongClick = { video ->
                  videoSelectionManager.toggle(video)
                },
                onBreadcrumbClick = { component ->
                  // Navigate to the breadcrumb by popping until we reach it
                  // or pushing if it's a new path
                  backstack.add(FileSystemDirectoryScreen(component.fullPath))
                },
                folderSelectionManager = folderSelectionManager,
                videoSelectionManager = videoSelectionManager,
                modifier = Modifier,
                isInSelectionMode = isInSelectionMode,
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
    }

    // Independent Floating Bottom Bar - positioned at absolute bottom
    AnimatedVisibility(
      visible = showFloatingBottomBar,
      enter = slideInVertically(
        animationSpec = tween(durationMillis = animationDuration),
        initialOffsetY = { fullHeight -> fullHeight }
      ),
      exit = slideOutVertically(
        animationSpec = tween(durationMillis = animationDuration),
        targetOffsetY = { fullHeight -> fullHeight }
      ),
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      BrowserBottomBar(
        isSelectionMode = true,
        onCopyClick = {
          operationType.value = CopyPasteOps.OperationType.Copy
          folderPickerOpen.value = true
        },
        onMoveClick = {
          operationType.value = CopyPasteOps.OperationType.Move
          folderPickerOpen.value = true
        },
        onRenameClick = { renameDialogOpen.value = true },
        onDeleteClick = { deleteDialogOpen.value = true },
        onAddToPlaylistClick = { addToPlaylistDialogOpen.value = true },
        showRename = videoSelectionManager.isSingleSelection,
        modifier = Modifier.padding(bottom = 0.dp) // Zero bottom padding - absolute bottom
      )
    }

    // Dialogs
    PlayLinkSheet(
      isOpen = showLinkDialog.value,
      onDismiss = { showLinkDialog.value = false },
      onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
    )

    FileSystemSortDialog(
      isOpen = sortDialogOpen.value,
      onDismiss = { sortDialogOpen.value = false },
      isAtRoot = isAtRoot,
    )

    DeleteConfirmationDialog(
      isOpen = deleteDialogOpen.value,
      onDismiss = { deleteDialogOpen.value = false },
      onConfirm = {
        if (folderSelectionManager.isInSelectionMode) {
          folderSelectionManager.deleteSelected()
        }
        if (videoSelectionManager.isInSelectionMode) {
          videoSelectionManager.deleteSelected()
        }
      },
      itemType = when {
        folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode -> "item"
        folderSelectionManager.isInSelectionMode -> "folder"
        else -> "video"
      },
      itemCount = selectedCount,
      itemNames = (folderSelectionManager.getSelectedItems().map { it.name } +
        videoSelectionManager.getSelectedItems().map { it.displayName }),
    )

    // Rename Dialog (only for videos)
    if (renameDialogOpen.value && videoSelectionManager.isSingleSelection) {
      val video = videoSelectionManager.getSelectedItems().firstOrNull()
      if (video != null) {
        val baseName = video.displayName.substringBeforeLast('.')
        val extension = "." + video.displayName.substringAfterLast('.', "")
        RenameDialog(
          isOpen = true,
          onDismiss = { renameDialogOpen.value = false },
          onConfirm = { newName -> videoSelectionManager.renameSelected(newName) },
          currentName = baseName,
          itemType = "file",
          extension = if (extension != ".") extension else null,
        )
      }
    }

    // Folder Picker Dialog
    FolderPickerDialog(
      isOpen = folderPickerOpen.value,
      currentPath = currentPath,
      onDismiss = { folderPickerOpen.value = false },
      onFolderSelected = { destinationPath ->
        folderPickerOpen.value = false
        val selectedVideos = videoSelectionManager.getSelectedItems()
        if (selectedVideos.isNotEmpty() && operationType.value != null) {
          progressDialogOpen.value = true
          coroutineScope.launch {
            when (operationType.value) {
              is CopyPasteOps.OperationType.Copy -> {
                CopyPasteOps.copyFiles(context, selectedVideos, destinationPath)
              }

              is CopyPasteOps.OperationType.Move -> {
                CopyPasteOps.moveFiles(context, selectedVideos, destinationPath)
              }

              else -> {}
            }
          }
        }
      },
    )

    // File Operation Progress Dialog
    if (operationType.value != null) {
      FileOperationProgressDialog(
        isOpen = progressDialogOpen.value,
        operationType = operationType.value!!,
        progress = operationProgress,
        onCancel = {
          CopyPasteOps.cancelOperation()
        },
        onDismiss = {
          progressDialogOpen.value = false
          // Set flag if move operation was successful
          if (operationType.value is CopyPasteOps.OperationType.Move &&
            operationProgress.isComplete &&
            operationProgress.error == null) {
            viewModel.setItemsWereDeletedOrMoved()
          }
          operationType.value = null
          videoSelectionManager.clear()
          viewModel.refresh()
        },
      )
    }

    // Add to Playlist Dialog
    AddToPlaylistDialog(
      isOpen = addToPlaylistDialogOpen.value,
      videos = videoSelectionManager.getSelectedItems(),
      onDismiss = { addToPlaylistDialogOpen.value = false },
      onSuccess = {
        videoSelectionManager.clear()
        viewModel.refresh()
      },
    )
  }
}

/**
 * Recursively searches for files matching the query in a directory and its subdirectories
 */
suspend fun searchRecursively(
  context: Context,
  directoryPath: String,
  query: String,
): List<FileSystemItem> {
  val results = mutableListOf<FileSystemItem>()
  
  try {
    Log.d("FileSystemBrowserScreen", "Scanning directory: $directoryPath for query: $query")
    // Scan the current directory
    val items = app.marlboroadvance.mpvex.repository.MediaFileRepository
      .scanDirectory(context, directoryPath, showAllFileTypes = false, showHiddenFiles = false)
      .getOrNull() ?: emptyList()

    Log.d("FileSystemBrowserScreen", "Found ${items.size} items in $directoryPath")

    // Filter items that match the search query (case-insensitive)
    items.forEach { item ->
      when (item) {
        is FileSystemItem.VideoFile -> {
          if (item.video.displayName.contains(query, ignoreCase = true)) {
            Log.d("FileSystemBrowserScreen", "Found matching video: ${item.video.displayName}")
            results.add(item)
          }
        }
        is FileSystemItem.Folder -> {
          if (item.name.contains(query, ignoreCase = true)) {
            Log.d("FileSystemBrowserScreen", "Found matching folder: ${item.name}")
            results.add(item)
          }
          // Recursively search in subdirectories
          try {
            val subResults = searchRecursively(context, item.path, query)
            results.addAll(subResults)
          } catch (e: Exception) {
            Log.e("FileSystemBrowserScreen", "Error searching subdirectory ${item.path}", e)
          }
        }
      }
    }
    
    Log.d("FileSystemBrowserScreen", "Returning ${results.size} results from $directoryPath")
  } catch (e: Exception) {
    Log.e("FileSystemBrowserScreen", "Error searching directory $directoryPath", e)
  }

  return results
}

/**
 * Recursively collects all videos from a folder and its subfolders
 */
private suspend fun collectVideosRecursively(
  context: Context,
  folderPath: String,
): List<app.marlboroadvance.mpvex.domain.media.model.Video> {
  val videos = mutableListOf<app.marlboroadvance.mpvex.domain.media.model.Video>()

  try {
    // Scan the current directory using MediaFileRepository
    val items = app.marlboroadvance.mpvex.repository.MediaFileRepository
      .scanDirectory(context, folderPath, showAllFileTypes = false, showHiddenFiles = false)
      .getOrNull() ?: emptyList()

    // Add videos from current folder
    items.filterIsInstance<FileSystemItem.VideoFile>().forEach { videoFile ->
      videos.add(videoFile.video)
    }

    // Recursively scan subfolders
    items.filterIsInstance<FileSystemItem.Folder>().forEach { folder ->
      val subVideos = collectVideosRecursively(context, folder.path)
      videos.addAll(subVideos)
    }
  } catch (e: Exception) {
    Log.e("FileSystemBrowserScreen", "Error collecting videos from $folderPath", e)
  }

  return videos
}

/**
 * Plays a list of videos as a playlist
 */
private fun playVideosAsPlaylist(
  context: Context,
  videos: List<app.marlboroadvance.mpvex.domain.media.model.Video>,
) {
  if (videos.isEmpty()) return

  if (videos.size == 1) {
    // Single video - play normally
    MediaUtils.playFile(videos.first(), context)
  } else {
    // Multiple videos - play as playlist
    val intent = Intent(Intent.ACTION_VIEW, videos.first().uri)
    intent.setClass(context, app.marlboroadvance.mpvex.ui.player.PlayerActivity::class.java)
    intent.putExtra("internal_launch", true)
    intent.putParcelableArrayListExtra("playlist", ArrayList(videos.map { it.uri }))
    intent.putExtra("playlist_index", 0)
    intent.putExtra("launch_source", "playlist")
    context.startActivity(intent)
  }
}

@Composable
private fun FileSystemBrowserContent(
  listState: LazyListState,
  gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
  items: List<FileSystemItem>,
  videoFilesWithPlayback: Map<Long, Float>,
  isLoading: Boolean,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  error: String?,
  isAtRoot: Boolean,
  breadcrumbs: List<app.marlboroadvance.mpvex.domain.browser.PathComponent>,
  playlistMode: Boolean,
  itemsWereDeletedOrMoved: Boolean,
  mediaLayoutMode: MediaLayoutMode,
  folderGridColumns: Int,
  videoGridColumns: Int,
  showSubtitleIndicator: Boolean,
  navigationBarHeight: Dp,
  onRefresh: suspend () -> Unit,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  onFolderLongClick: (FileSystemItem.Folder) -> Unit,
  onVideoClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onVideoLongClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onBreadcrumbClick: (app.marlboroadvance.mpvex.domain.browser.PathComponent) -> Unit,
  folderSelectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<FileSystemItem.Folder, String>,
  videoSelectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<app.marlboroadvance.mpvex.domain.media.model.Video, Long>,
  modifier: Modifier = Modifier,
  isInSelectionMode: Boolean = false,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val thumbnailRepository = koinInject<app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val isGridMode = mediaLayoutMode == MediaLayoutMode.GRID

  // Check if there are folders mixed with videos AND we're in grid mode - if so, hide video chips and use folder style
  val folders = items.filterIsInstance<FileSystemItem.Folder>()
  val videos = items.filterIsInstance<FileSystemItem.VideoFile>()
  val hasMixedContentInGrid = folders.isNotEmpty() && videos.isNotEmpty() && isGridMode

  // Calculate thumbnail dimensions
  val density = androidx.compose.ui.platform.LocalDensity.current
  val thumbWidthDp = if (isGridMode) {
    if (folders.isNotEmpty()) {
      (320 / folderGridColumns).dp
    } else {
      (360 / videoGridColumns).dp
    }
  } else {
    160.dp
  }
  val aspect = 16f / 9f
  val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = ((thumbWidthPx.toFloat() / aspect).toInt())

  // Create a unique folderId based on the current directories
  val folderId = remember(folders, isAtRoot, breadcrumbs) {
    if (isAtRoot && breadcrumbs.isEmpty()) {
      "filesystem_root"
    } else {
      breadcrumbs.lastOrNull()?.fullPath ?: "filesystem_${breadcrumbs.size}"
    }
  }

  // Generate thumbnails sequentially
  LaunchedEffect(folderId, showVideoThumbnails, videos.size, thumbWidthPx, thumbHeightPx) {
    if (showVideoThumbnails && videos.isNotEmpty()) {
      thumbnailRepository.startFolderThumbnailGeneration(
        folderId = folderId,
        videos = videos.map { it.video },
        widthPx = thumbWidthPx,
        heightPx = thumbHeightPx,
      )
    }
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
          title = "Error loading directory",
          message = error,
        )
      }
    }

    items.isEmpty() && itemsWereDeletedOrMoved && !isAtRoot -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.FolderOpen,
          title = "Empty folder",
          message = "This folder contains no videos or subfolders",
        )
      }
    }

    else -> {
      // Check if at top of list/grid to hide scrollbar during pull-to-refresh
      val isAtTop by remember {
        derivedStateOf {
          if (isGridMode) {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
          } else {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
          }
        }
      }

      // Only show scrollbar if list has more than 20 items
      val hasEnoughItems = items.size > 20

      // Animate scrollbar alpha
      val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha",
      )

      PullRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        listState = listState,
        modifier = modifier.fillMaxSize(),
      ) {
        if (isGridMode) {
          Box(
            modifier = Modifier.fillMaxSize()
          ) {
            LazyVerticalGrid(
              columns = GridCells.Fixed(if (folders.isNotEmpty()) folderGridColumns else videoGridColumns),
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
              // Breadcrumb navigation (if not at root)
              if (!isAtRoot && breadcrumbs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  app.marlboroadvance.mpvex.ui.browser.filesystem.BreadcrumbNavigation(
                    breadcrumbs = breadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick,
                  )
                }
              }

              // Folders first
              items(
                count = folders.size,
                key = { folders[it].path },
              ) { index ->
                val folder = folders[index]
                val folderModel = app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
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
                  isSelected = folderSelectionManager.isSelected(folder),
                  isRecentlyPlayed = false,
                  onClick = { onFolderClick(folder) },
                  onLongClick = { onFolderLongClick(folder) },
                  onThumbClick = if (tapThumbnailToSelect) {
                    { onFolderLongClick(folder) }
                  } else {
                    { onFolderClick(folder) }
                  },
                  isGridMode = true,
                )
              }

              // Videos second
              items(
                count = videos.size,
                key = { "${videos[it].video.id}_${videos[it].video.path}" },
              ) { index ->
                val videoFile = videos[index]
                VideoCard(
                  video = videoFile.video,
                  progressPercentage = videoFilesWithPlayback[videoFile.video.id],
                  isRecentlyPlayed = false,
                  isSelected = videoSelectionManager.isSelected(videoFile.video),
                  onClick = { onVideoClick(videoFile.video) },
                  onLongClick = { onVideoLongClick(videoFile.video) },
                  onThumbClick = if (tapThumbnailToSelect) {
                    { onVideoLongClick(videoFile.video) }
                  } else {
                    { onVideoClick(videoFile.video) }
                  },
                  isGridMode = true,
                  gridColumns = videoGridColumns,
                  showSubtitleIndicator = showSubtitleIndicator,
                  overrideShowSizeChip = if (hasMixedContentInGrid) false else null,
                  overrideShowResolutionChip = if (hasMixedContentInGrid) false else null,
                  useFolderNameStyle = hasMixedContentInGrid,
                )
              }
            }
            
            // Scrollbar with bottom padding to avoid overlap with navigation
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
        } else {
          Box(
            modifier = Modifier.fillMaxSize()
          ) {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                bottom = navigationBarHeight
              ),
            ) {
              // Breadcrumb navigation (if not at root)
              if (!isAtRoot && breadcrumbs.isNotEmpty()) {
                item {
                  app.marlboroadvance.mpvex.ui.browser.filesystem.BreadcrumbNavigation(
                    breadcrumbs = breadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick,
                  )
                }
              }

              // Folders first
              items(
                items = items.filterIsInstance<FileSystemItem.Folder>(),
                key = { it.path },
              ) { folder ->
                val folderModel = app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
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
                  isSelected = folderSelectionManager.isSelected(folder),
                  isRecentlyPlayed = false,
                  onClick = { onFolderClick(folder) },
                  onLongClick = { onFolderLongClick(folder) },
                  onThumbClick = if (tapThumbnailToSelect) {
                    { onFolderLongClick(folder) }
                  } else {
                    { onFolderClick(folder) }
                  },
                  isGridMode = false,
                )
              }

              // Videos second
              items(
                items = items.filterIsInstance<FileSystemItem.VideoFile>(),
                key = { "${it.video.id}_${it.video.path}" },
              ) { videoFile ->
                VideoCard(
                  video = videoFile.video,
                  progressPercentage = videoFilesWithPlayback[videoFile.video.id],
                  isRecentlyPlayed = false,
                  isSelected = videoSelectionManager.isSelected(videoFile.video),
                  onClick = { onVideoClick(videoFile.video) },
                  onLongClick = { onVideoLongClick(videoFile.video) },
                  onThumbClick = if (tapThumbnailToSelect) {
                    { onVideoLongClick(videoFile.video) }
                  } else {
                    { onVideoClick(videoFile.video) }
                  },
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
      }
    }
  }
}

@Composable
private fun FileSystemSearchContent(
  listState: LazyListState,
  searchQuery: String,
  searchResults: List<FileSystemItem>,
  isLoading: Boolean,
  videoFilesWithPlayback: Map<Long, Float>,
  showSubtitleIndicator: Boolean,
  isAtRoot: Boolean,
  navigationBarHeight: Dp,
  isFabVisible: androidx.compose.runtime.MutableState<Boolean>, // Add FAB visibility state
  onVideoClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  modifier: Modifier = Modifier,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()

  // Track scroll for FAB visibility in search mode with proper scroll direction detection
  val previousIndex = remember { mutableIntStateOf(0) }
  val previousOffset = remember { mutableIntStateOf(0) }
  
  LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
    val currentIndex = listState.firstVisibleItemIndex
    val currentOffset = listState.firstVisibleItemScrollOffset
    
    // Show FAB when at the top
    if (currentIndex == 0 && currentOffset == 0) {
      isFabVisible.value = true
    } else {
      // Calculate if scrolling down or up
      val isScrollingDown = if (currentIndex != previousIndex.value) {
        currentIndex > previousIndex.value
      } else {
        currentOffset > previousOffset.value
      }
      
      // Hide when scrolling down, show when scrolling up
      isFabVisible.value = !isScrollingDown
    }
    
    previousIndex.value = currentIndex
    previousOffset.value = currentOffset
  }

  Box(modifier = modifier.fillMaxSize()) {
    when {
      isLoading -> {
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
              text = if (isAtRoot) "Searching all storage volumes..." else "Searching...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      searchResults.isEmpty() && searchQuery.isNotBlank() -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Search,
            title = "No results found",
            message = "No files or folders match \"$searchQuery\"",
          )
        }
      }

      else -> {
        Box(
          modifier = Modifier.fillMaxSize()
        ) {
          // Content extends full height for transparency
          LazyColumn(
            state = listState,
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
              val folderModel = app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
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
                progressPercentage = videoFilesWithPlayback[videoFile.video.id],
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
              state = listState,
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
fun FileSystemSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  isAtRoot: Boolean = true,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<app.marlboroadvance.mpvex.preferences.AppearancePreferences>()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val folderSortType by browserPreferences.folderSortType.collectAsState()
  val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
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

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort & View Options",
    sortType = folderSortType.displayName,
    onSortTypeChange = { typeName ->
      app.marlboroadvance.mpvex.preferences.FolderSortType.entries.find { it.displayName == typeName }?.let {
        browserPreferences.folderSortType.set(it)
      }
    },
    sortOrderAsc = folderSortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      browserPreferences.folderSortOrder.set(
        if (isAsc) app.marlboroadvance.mpvex.preferences.SortOrder.Ascending
        else app.marlboroadvance.mpvex.preferences.SortOrder.Descending,
      )
    },
    types = listOf(
      app.marlboroadvance.mpvex.preferences.FolderSortType.Title.displayName,
      app.marlboroadvance.mpvex.preferences.FolderSortType.Date.displayName,
      app.marlboroadvance.mpvex.preferences.FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        app.marlboroadvance.mpvex.preferences.FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        app.marlboroadvance.mpvex.preferences.FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        app.marlboroadvance.mpvex.preferences.FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
    showSortOptions = true,
    viewModeSelector = ViewModeSelector(
      label = "View Mode",
      firstOptionLabel = "Folder",
      secondOptionLabel = "Tree",
      firstOptionIcon = Icons.Filled.ViewModule,
      secondOptionIcon = Icons.Filled.AccountTree,
      isFirstOptionSelected = folderViewMode == app.marlboroadvance.mpvex.preferences.FolderViewMode.AlbumView,
      onViewModeChange = { isFirstOption ->
        browserPreferences.folderViewMode.set(
          if (isFirstOption) {
            app.marlboroadvance.mpvex.preferences.FolderViewMode.AlbumView
          } else {
            app.marlboroadvance.mpvex.preferences.FolderViewMode.FileManager
          },
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
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
    enableViewModeOptions = isAtRoot,
    enableLayoutModeOptions = true,
    visibilityToggles = listOf(
      VisibilityToggle(
        label = "Video Thumbnails",
        checked = showVideoThumbnails,
        onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
      ),
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
        label = "Folder Size",
        checked = showTotalSizeChip,
        onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = "Size",
        checked = showSizeChip,
        onCheckedChange = { browserPreferences.showSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = "Resolution",
        checked = showResolutionChip,
        onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
      ),
      VisibilityToggle(
        label = "Framerate",
        checked = showFramerateInResolution,
        onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
      ),
      VisibilityToggle(
        label = "Subtitle",
        checked = showSubtitleIndicator,
        onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
      ),
      VisibilityToggle(
        label = "Progress Bar",
        checked = showProgressBar,
        onCheckedChange = { browserPreferences.showProgressBar.set(it) },
      ),
    )
  )
}
