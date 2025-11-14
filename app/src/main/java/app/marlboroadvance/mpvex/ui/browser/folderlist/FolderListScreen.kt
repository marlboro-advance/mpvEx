package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.repository.VideoRepository
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.fab.MediaActionFab
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object FolderListScreen : Screen {
  @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val viewModel: FolderListViewModel =
      viewModel(factory = FolderListViewModel.factory(context.applicationContext as android.app.Application))
    val videoFolders by viewModel.videoFolders.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // UI State
    val listState = rememberLazyListState()
    val isRefreshing = remember { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var hasRecentlyPlayed by remember { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var videosLoaded by remember { mutableStateOf(false) }

    // Sorting
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
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
        onDeleteItems = { folders ->
          // Delete all videos in selected folders via ViewModel
          val ids = folders.map { it.bucketId }.toSet()
          val videos = VideoRepository.getVideosForBuckets(context, ids)
          viewModel.deleteVideos(videos)
        },
        onOperationComplete = { viewModel.refresh() },
      )

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

    LaunchedEffect(isSearching) {
      if (isSearching && !videosLoaded) {
        // Load all videos across all folders using all bucketIds
        val bucketIds = videoFolders.map { it.bucketId }.toSet()
        allVideos = VideoRepository.getVideosForBuckets(context, bucketIds)
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
                val allVideos = VideoRepository.getVideosForBuckets(context, selectedIds)
                if (allVideos.isNotEmpty()) {
                  MediaUtils.shareVideos(context, allVideos)
                }
              }
            },
            onPlayClick = {
              // Play all videos from selected folders as a playlist
              coroutineScope.launch {
                val selectedIds = selectionManager.getSelectedItems().map { it.bucketId }.toSet()
                val allVideos = VideoRepository.getVideosForBuckets(context, selectedIds)
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
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
          )
        }
      },
      floatingActionButton = {
        MediaActionFab(
          listState = listState,
          hasRecentlyPlayed = hasRecentlyPlayed,
          onOpenFile = { filePicker.launch(arrayOf("video/*")) },
          onPlayRecentlyPlayed = {
            coroutineScope.launch {
              app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
                .getLastPlayed()
                ?.let { MediaUtils.playFile(it, context, "recently_played_button") }
            }
          },
          onPlayLink = { showLinkDialog.value = true },
          expanded = fabMenuExpanded,
          onExpandedChange = { fabMenuExpanded = it },
        )
      },
    ) { padding ->
      when (permissionState.status) {
        PermissionStatus.Granted -> {
          if (isSearching) {
            // Search results
            if (searchQuery.isNotBlank() && videosLoaded) {
              if (filteredVideos.isEmpty()) {
                EmptyState(
                  icon = Icons.Filled.Folder,
                  title = "No videos found",
                  message = "Try a different search term.",
                  modifier = Modifier.padding(padding),
                )
              } else {
                LazyColumn(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                  contentPadding = PaddingValues(8.dp),
                ) {
                  items(filteredVideos) { video ->
                    VideoCard(
                      video = video,
                      timeRemainingFormatted = null,
                      isRecentlyPlayed = false,
                      isSelected = false,
                      onClick = { MediaUtils.playFile(video, context, "search") },
                      onLongClick = {},
                      onThumbClick = {},
                    )
                  }
                }
              }
            }
          } else {
            // Normal mode - show folder list
            FolderListContent(
              folders = filteredFolders,
              listState = listState,
              isRefreshing = isRefreshing,
              recentlyPlayedFilePath = recentlyPlayedFilePath,
              onRefresh = { viewModel.refresh() },
              selectionManager = selectionManager,
              onFolderClick = { folder ->
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(folder)
                } else {
                  fabMenuExpanded = false
                  backstack.add(VideoListScreen(folder.bucketId, folder.name))
                }
              },
              onFolderLongClick = { folder -> selectionManager.toggle(folder) },
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
  listState: LazyListState,
  isRefreshing: MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
  modifier: Modifier = Modifier,
) {
  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxWidth(),
  ) {
    // Avoid brief empty-state flicker by delaying its appearance slightly
    val showEmpty =
      remember(folders) {
        mutableStateOf(false)
      }
    LaunchedEffect(folders) {
      if (folders.isEmpty()) {
        kotlinx.coroutines.delay(250)
        showEmpty.value = folders.isEmpty()
      } else {
        showEmpty.value = false
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(8.dp),
    ) {
      // Regular folders
      items(folders) { folder ->
        val isRecentlyPlayed =
          recentlyPlayedFilePath?.let { filePath ->
            val file = File(filePath)
            file.parent == folder.path
          } ?: false

        FolderCard(
          folder = folder,
          isSelected = selectionManager.isSelected(folder),
          isRecentlyPlayed = isRecentlyPlayed,
          onClick = { onFolderClick(folder) },
          onLongClick = { onFolderLongClick(folder) },
          onThumbClick = { onFolderLongClick(folder) },
        )
      }

      if (showEmpty.value) {
        item {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No video folders found",
            message = "Add some video files to your device to see them here",
          )
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
  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort Folders",
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
  )
}
