package app.marlboroadvance.mpvex.ui.browser.filesystem

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import my.nanihadesuka.compose.LazyColumnScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
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
import app.marlboroadvance.mpvex.ui.browser.fab.MediaActionFab
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.CopyPasteOps
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject
import java.io.File

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
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FileSystemBrowserScreen(path: String? = null) {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val coroutineScope = rememberCoroutineScope()
  val browserPreferences = koinInject<BrowserPreferences>()
  val playerPreferences = koinInject<app.marlboroadvance.mpvex.preferences.PlayerPreferences>()
  val advancedPreferences = koinInject<app.marlboroadvance.mpvex.preferences.AdvancedPreferences>()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  // ViewModel - use path parameter if provided, otherwise show roots
  val viewModel: FileSystemBrowserViewModel =
    viewModel(
      key = "FileSystemBrowser_${path ?: "root"}",
      factory =
        FileSystemBrowserViewModel.factory(
          context.applicationContext as android.app.Application,
          path, // Pass the path parameter
        ),
    )

  val currentPath by viewModel.currentPath.collectAsState()
  val items by viewModel.items.collectAsState()
  val videoFilesWithPlayback by viewModel.videoFilesWithPlayback.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val error by viewModel.error.collectAsState()
  val isAtRoot by viewModel.isAtRoot.collectAsState()
  val breadcrumbs by viewModel.breadcrumbs.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
  val itemsWereDeletedOrMoved by viewModel.itemsWereDeletedOrMoved.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
  val videoGridColumns by browserPreferences.videoGridColumns.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()

  // UI State
  val listState = rememberLazyListState()
  val isRefreshing = remember { mutableStateOf(false) }
  val showLinkDialog = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
  var hasRecentlyPlayed by remember { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var isSearching by rememberSaveable { mutableStateOf(false) }
  var searchResults by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
  var isSearchLoading by remember { mutableStateOf(false) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }

  // Copy/Move state
  val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
  val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
  val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
  val operationProgress by CopyPasteOps.operationProgress.collectAsState()

  // Selection managers - separate for folders and videos
  val folders = items.filterIsInstance<FileSystemItem.Folder>()
  val videos = items.filterIsInstance<FileSystemItem.VideoFile>().map { it.video }

  val folderSelectionManager =
    rememberSelectionManager(
      items = folders,
      getId = { it.path },
      onDeleteItems = { foldersToDelete ->
        viewModel.deleteFolders(foldersToDelete)
      },
      onOperationComplete = { viewModel.refresh() },
    )

  val videoSelectionManager =
    rememberSelectionManager(
      items = videos,
      getId = { it.id },
      onDeleteItems = { videosToDelete ->
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

  // Permissions
  val permissionState =
    PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() },
    )

  // File picker
  val filePicker =
    rememberLauncherForActivityResult(
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

  // Effects
  LaunchedEffect(Unit) {
    hasRecentlyPlayed =
      app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
        .hasRecentlyPlayed()
  }

  LaunchedEffect(fabMenuExpanded) {
    if (fabMenuExpanded) {
      hasRecentlyPlayed =
        app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
          .hasRecentlyPlayed()
    }
  }

  // Listen for lifecycle resume events
  DisposableEffect(lifecycleOwner) {
    val observer =
      LifecycleEventObserver { _, event ->
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

  LaunchedEffect(searchQuery, isSearching) {
    if (isSearching && searchQuery.isNotBlank() && currentPath != null) {
      isSearchLoading = true
      coroutineScope.launch {
        try {
          val results = searchRecursively(context, currentPath, searchQuery)
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

  // Predictive back: Handle selection mode or search mode
  BackHandler(enabled = isInSelectionMode || isSearching) {
    when {
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
              placeholder = { Text("Search in ${breadcrumbs.lastOrNull()?.name ?: "folder"}...") },
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
          title =
            if (isAtRoot) {
              stringResource(app.marlboroadvance.mpvex.R.string.app_name)
            } else {
              breadcrumbs.lastOrNull()?.name ?: "Tree View"
            },
          isInSelectionMode = isInSelectionMode,
          selectedCount = selectedCount,
          totalCount = totalCount,
          onBackClick =
            if (isAtRoot) {
              null
            } else {
              { backstack.removeLastOrNull() }
            },
          onCancelSelection = {
            folderSelectionManager.clear()
            videoSelectionManager.clear()
          },
          onSortClick = { sortDialogOpen.value = true },
          onSettingsClick = { backstack.add(PreferencesScreen) },
          onSearchClick = if (!isAtRoot && currentPath != null) {
            { isSearching = !isSearching }
          } else {
            null
          },
        // Hide delete from top bar when bottom bar is shown (videos only, no mixed selection)
        onDeleteClick =
          if (videoSelectionManager.isInSelectionMode && !isMixedSelection) {
            null
          } else {
            { deleteDialogOpen.value = true }
          },
        // Hide rename from top bar when bottom bar is shown (videos only, no mixed selection)
        onRenameClick =
          if (videoSelectionManager.isSingleSelection && !isMixedSelection) {
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
    bottomBar = {
      // Only show bottom bar for videos (not for mixed selection of folders + videos)
      if (videoSelectionManager.isInSelectionMode && !isMixedSelection) {
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
        )
      }
    },
    floatingActionButton = {
      if (isAtRoot && items.isNotEmpty()) {
        MediaActionFab(
          listState = listState,
          hasRecentlyPlayed = true,
          enableRecentlyPlayed = enableRecentlyPlayed,
          onOpenFile = { filePicker.launch(arrayOf("*/*")) },
          onPlayRecentlyPlayed = {
            val ctx = context
            coroutineScope.launch {
              val lastPlayedEntity = app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps.getLastPlayedEntity()
              if (lastPlayedEntity != null) {
                if (lastPlayedEntity.playlistId != null) {
                  // Play full playlist from most recent video
                  val playlistRepository =
                    org.koin.java.KoinJavaComponent.get<app.marlboroadvance.mpvex.database.repository.PlaylistRepository>(
                      app.marlboroadvance.mpvex.database.repository.PlaylistRepository::class.java,
                    )
                  // Using MediaFileRepository singleton directly
                  val playlistItems = playlistRepository.getPlaylistItems(lastPlayedEntity.playlistId)
                  if (playlistItems.isNotEmpty()) {
                    val pathToBucketMap = mutableMapOf<String, String>()
                    val bucketIds = mutableSetOf<String>()
                    playlistItems.forEach { item ->
                      val file = java.io.File(item.filePath)
                      val parentPath = file.parent
                      if (parentPath != null) {
                        val normalizedPath = parentPath.replace("\\", "/")
                        pathToBucketMap[item.filePath] = normalizedPath
                        bucketIds.add(normalizedPath)
                      }
                    }
                    val allVideos = app.marlboroadvance.mpvex.repository.MediaFileRepository.getVideosForBuckets(ctx, bucketIds)
                    val videos =
                      playlistItems.mapNotNull { item -> allVideos.find { video -> video.path == item.filePath } }
                    if (videos.isNotEmpty()) {
                      val mostRecentItem = playlistItems.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }
                      val startIndex = mostRecentItem?.let { videos.indexOfFirst { v -> v.path == it.filePath } } ?: 0
                      val validStartIndex = if (startIndex >= 0) startIndex else 0
                      val uris = videos.map { it.uri }
                      val intent =
                        android.content.Intent(ctx, app.marlboroadvance.mpvex.ui.player.PlayerActivity::class.java)
                          .apply {
                            action = android.content.Intent.ACTION_VIEW
                            data = uris[validStartIndex]
                            putParcelableArrayListExtra("playlist", java.util.ArrayList(uris))
                            putExtra("playlist_index", validStartIndex)
                            putExtra("launch_source", "playlist")
                            putExtra("playlist_id", lastPlayedEntity.playlistId)
                          }
                      ctx.startActivity(intent)
                    }
                  }
                } else {
                  // Just play the single video
                  app.marlboroadvance.mpvex.utils.media.MediaUtils.playFile(
                    lastPlayedEntity.filePath,
                    ctx,
                    "recently_played_button",
                  )
                }
              }
            }
          },
          onPlayLink = { showLinkDialog.value = true },
          expanded = fabMenuExpanded,
          onExpandedChange = { fabMenuExpanded = it },
          modifier = Modifier.padding(bottom = 75.dp),
        )
      }
    },
  ) { padding ->
    when (permissionState.status) {
      PermissionStatus.Granted -> {
        if (isSearching) {
          // Show search results
          FileSystemSearchContent(
            listState = listState,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isLoading = isSearchLoading,
            videoFilesWithPlayback = videoFilesWithPlayback,
            showSubtitleIndicator = showSubtitleIndicator,
            onVideoClick = { video ->
              MediaUtils.playFile(video, context, "search")
            },
            onFolderClick = { folder ->
              fabMenuExpanded = false
              backstack.add(FileSystemDirectoryScreen(folder.path))
              isSearching = false
              searchQuery = ""
            },
            modifier = Modifier.padding(padding),
          )
        } else {
          FileSystemBrowserContent(
            listState = listState,
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
            showSubtitleIndicator = showSubtitleIndicator,
            onRefresh = { viewModel.refresh() },
          onFolderClick = { folder ->
            if (isInSelectionMode) {
              folderSelectionManager.toggle(folder)
            } else {
              fabMenuExpanded = false
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
          modifier = Modifier.padding(padding),
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

    // Dialogs
    PlayLinkSheet(
      isOpen = showLinkDialog.value,
      onDismiss = { showLinkDialog.value = false },
      onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
    )

    FileSystemSortDialog(
      isOpen = sortDialogOpen.value,
      onDismiss = { sortDialogOpen.value = false },
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
      itemType =
        when {
          folderSelectionManager.isInSelectionMode && videoSelectionManager.isInSelectionMode -> "item"
          folderSelectionManager.isInSelectionMode -> "folder"
          else -> "video"
        },
      itemCount = selectedCount,
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
  onRefresh: suspend () -> Unit,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  onFolderLongClick: (FileSystemItem.Folder) -> Unit,
  onVideoClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onVideoLongClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onBreadcrumbClick: (app.marlboroadvance.mpvex.domain.browser.PathComponent) -> Unit,
  folderSelectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<FileSystemItem.Folder, String>,
  videoSelectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<app.marlboroadvance.mpvex.domain.media.model.Video, Long>,
  modifier: Modifier = Modifier,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val isGridMode = mediaLayoutMode == MediaLayoutMode.GRID

  when {
    isLoading -> {
      Box(
        modifier = modifier.fillMaxSize(),
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
      // Check if at top of list to hide scrollbar during pull-to-refresh
      val isAtTop by remember {
        derivedStateOf {
          listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
      }

      // Only show scrollbar if list has more than 20 items
      val hasEnoughItems = items.size > 20

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
        if (isGridMode) {
          val gridState = rememberLazyGridState()
          val folders = items.filterIsInstance<FileSystemItem.Folder>()
          val videos = items.filterIsInstance<FileSystemItem.VideoFile>()
          
          LazyVerticalGrid(
            columns = GridCells.Fixed(if (folders.isNotEmpty()) folderGridColumns else videoGridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Breadcrumb navigation (if not at root)
            if (!isAtRoot && breadcrumbs.isNotEmpty()) {
              item(span = { GridItemSpan(maxLineSpan) }) {
                BreadcrumbNavigation(
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
              val folderModel =
                app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
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
              )
            }
          }
        } else {
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
              contentPadding = PaddingValues(8.dp),
            ) {
              // Breadcrumb navigation (if not at root)
              if (!isAtRoot && breadcrumbs.isNotEmpty()) {
                item {
                  BreadcrumbNavigation(
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
                val folderModel =
                  app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
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
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FileSystemSortDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
  ) {
    val browserPreferences = koinInject<BrowserPreferences>()
    val appearancePreferences = koinInject<app.marlboroadvance.mpvex.preferences.AppearancePreferences>()
    val folderViewMode by browserPreferences.folderViewMode.collectAsState()
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
    val showThumbnails by browserPreferences.showThumbnails.collectAsState()
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
      showSortOptions = true, // Enable sorting for Tree View
      viewModeSelector =
        ViewModeSelector(
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
      visibilityToggles =
        listOf(
          VisibilityToggle(
            label = "Thumbnails",
            checked = showThumbnails,
            onCheckedChange = { browserPreferences.showThumbnails.set(it) },
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
        ),
    )
  }

/**
 * Recursively searches for files and folders matching the query
 */
private suspend fun searchRecursively(
  context: Context,
  folderPath: String,
  query: String,
): List<FileSystemItem> {
  val results = mutableListOf<FileSystemItem>()
  
  try {
    // Scan the current directory
    val items = app.marlboroadvance.mpvex.repository.MediaFileRepository
      .scanDirectory(context, folderPath, showAllFileTypes = false, showHiddenFiles = false)
      .getOrNull() ?: emptyList()
    
    // Add matching items from current folder
    items.forEach { item ->
      when (item) {
        is FileSystemItem.Folder -> {
          // Add folder if it matches the query
          if (item.name.contains(query, ignoreCase = true) || 
              item.path.contains(query, ignoreCase = true)) {
            results.add(item)
          }
          // Recursively search in subfolders
          val subResults = searchRecursively(context, item.path, query)
          results.addAll(subResults)
        }
        is FileSystemItem.VideoFile -> {
          // Add video if it matches the query
          if (item.video.title.contains(query, ignoreCase = true) ||
              item.video.displayName.contains(query, ignoreCase = true) ||
              item.video.path.contains(query, ignoreCase = true)) {
            results.add(item)
          }
        }
      }
    }
  } catch (e: Exception) {
    Log.e("FileSystemBrowserScreen", "Error searching in $folderPath", e)
  }
  
  return results
}

/**
 * Displays search results
 */
@Composable
private fun FileSystemSearchContent(
  listState: LazyListState,
  searchQuery: String,
  searchResults: List<FileSystemItem>,
  isLoading: Boolean,
  videoFilesWithPlayback: Map<Long, Float>,
  showSubtitleIndicator: Boolean,
  onVideoClick: (app.marlboroadvance.mpvex.domain.media.model.Video) -> Unit,
  onFolderClick: (FileSystemItem.Folder) -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    isLoading -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(48.dp),
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    
    searchQuery.isBlank() -> {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.Search,
          title = "Search files and folders",
          message = "Enter a search term to find videos and folders recursively",
        )
      }
    }
    
    searchResults.isEmpty() -> {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.Search,
          title = "No results found",
          message = "Try a different search term",
        )
      }
    }
    
    else -> {
      // Check if at top of list to hide scrollbar
      val isAtTop by remember {
        derivedStateOf {
          listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
      }
      
      // Only show scrollbar if list has more than 20 items
      val hasEnoughItems = searchResults.size > 20
      
      // Animate scrollbar alpha
      val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "scrollbarAlpha",
      )
      
      LazyColumnScrollbar(
        state = listState,
        settings = ScrollbarSettings(
          thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
          thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
        ),
      ) {
        LazyColumn(
          state = listState,
          modifier = modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
        ) {
          items(searchResults) { item ->
            when (item) {
              is FileSystemItem.Folder -> {
                val folderModel = app.marlboroadvance.mpvex.domain.media.model.VideoFolder(
                  bucketId = item.path,
                  name = item.name,
                  path = item.path,
                  videoCount = item.videoCount,
                  totalSize = item.totalSize,
                  totalDuration = item.totalDuration,
                  lastModified = item.lastModified / 1000,
                )
                
                FolderCard(
                  folder = folderModel,
                  isSelected = false,
                  isRecentlyPlayed = false,
                  onClick = { onFolderClick(item) },
                  onLongClick = {},
                  onThumbClick = { onFolderClick(item) },
                )
              }
              
              is FileSystemItem.VideoFile -> {
                VideoCard(
                  video = item.video,
                  progressPercentage = videoFilesWithPlayback[item.video.id],
                  isRecentlyPlayed = false,
                  isSelected = false,
                  onClick = { onVideoClick(item.video) },
                  onLongClick = {},
                  onThumbClick = {},
                  showSubtitleIndicator = showSubtitleIndicator,
                )
              }
            }
          }
        }
      }
    }
  }
}

