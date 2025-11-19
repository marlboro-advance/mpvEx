package app.marlboroadvance.mpvex.ui.browser.filesystem

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.repository.FileSystemRepository
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserBottomBar
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FileOperationProgressDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FolderPickerDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.MediaInfoDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.RenameDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.ViewModeSelector
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.fab.MediaActionFab
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
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

  // UI State
  val listState = rememberLazyListState()
  val isRefreshing = remember { mutableStateOf(false) }
  val showLinkDialog = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
  var hasRecentlyPlayed by remember { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val mediaInfoDialogOpen = rememberSaveable { mutableStateOf(false) }

  // Copy/Move state
  val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
  val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
  val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
  val operationProgress by CopyPasteOps.operationProgress.collectAsState()

  // Media info state
  val selectedVideo = remember { mutableStateOf<app.marlboroadvance.mpvex.domain.media.model.Video?>(null) }
  val mediaInfoData = remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }
  val mediaInfoLoading = remember { mutableStateOf(false) }
  val mediaInfoError = remember { mutableStateOf<String?>(null) }

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

  // Predictive back: Handle selection mode or navigate back
  BackHandler(enabled = isInSelectionMode) {
    folderSelectionManager.clear()
    videoSelectionManager.clear()
  }

  Scaffold(
    topBar = {
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
        onSearchClick = null,
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
        isSingleSelection = videoSelectionManager.isSingleSelection || folderSelectionManager.isSingleSelection,
        onInfoClick =
          if (videoSelectionManager.isSingleSelection && !isMixedSelection) {
            {
              val video = videoSelectionManager.getSelectedItems().firstOrNull()
              if (video != null) {
                selectedVideo.value = video
                mediaInfoDialogOpen.value = true
                mediaInfoLoading.value = true
                mediaInfoError.value = null
                mediaInfoData.value = null

                coroutineScope.launch {
                  MediaInfoOps
                    .getMediaInfo(context, video.uri, video.displayName)
                    .onSuccess { info ->
                      mediaInfoData.value = info
                      mediaInfoLoading.value = false
                    }.onFailure { error ->
                      mediaInfoError.value = error.message ?: "Unknown error"
                      mediaInfoLoading.value = false
                    }
                }
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
    },
    floatingActionButton = {
      if (!isInSelectionMode) {
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
          onNetworkStreaming = {
            fabMenuExpanded = false
            backstack.add(NetworkStreamingScreen)
          },
          expanded = fabMenuExpanded,
          onExpandedChange = { fabMenuExpanded = it },
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
        )
      }
    },
  ) { padding ->
    when (permissionState.status) {
      PermissionStatus.Granted -> {
        FileSystemBrowserContent(
          items = items,
          videoFilesWithPlayback = videoFilesWithPlayback,
          isLoading = isLoading && items.isEmpty(),
          isRefreshing = isRefreshing,
          error = error,
          isAtRoot = isAtRoot,
          breadcrumbs = breadcrumbs,
          playlistMode = playlistMode,
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

    // Media Info Dialog
    MediaInfoDialog(
      isOpen = mediaInfoDialogOpen.value,
      onDismiss = {
        mediaInfoDialogOpen.value = false
        selectedVideo.value = null
        mediaInfoData.value = null
        mediaInfoError.value = null
      },
      fileName = selectedVideo.value?.displayName ?: "",
      mediaInfo = mediaInfoData.value,
      isLoading = mediaInfoLoading.value,
      error = mediaInfoError.value,
      videoForShare = selectedVideo.value,
    )

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
          operationType.value = null
          videoSelectionManager.clear()
          viewModel.refresh()
        },
      )
    }
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
    // Scan the current directory
    val items = FileSystemRepository.scanDirectory(context, folderPath).getOrNull() ?: emptyList()

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
  items: List<FileSystemItem>,
  videoFilesWithPlayback: Map<Long, Float>,
  isLoading: Boolean,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  error: String?,
  isAtRoot: Boolean,
  breadcrumbs: List<app.marlboroadvance.mpvex.domain.browser.PathComponent>,
  playlistMode: Boolean,
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
  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) {
    when {
      isLoading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      error != null -> {
        EmptyState(
          icon = Icons.Filled.Folder,
          title = "Error loading directory",
          message = error,
          modifier = Modifier.fillMaxSize(),
        )
      }

      items.isEmpty() -> {
        EmptyState(
          icon = if (isAtRoot) Icons.Filled.Folder else Icons.Filled.FolderOpen,
          title = if (isAtRoot) "No storage volumes found" else "Empty folder",
          message = if (isAtRoot) "No accessible storage volumes" else "This folder contains no videos or subfolders",
          modifier = Modifier.fillMaxSize(),
        )
      }

      else -> {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
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
              onThumbClick = { onFolderLongClick(folder) },
            )
          }

          // Videos second
          items(
            items = items.filterIsInstance<FileSystemItem.VideoFile>(),
            key = { it.video.id },
          ) { videoFile ->
            VideoCard(
              video = videoFile.video,
              progressPercentage = videoFilesWithPlayback[videoFile.video.id],
              isRecentlyPlayed = false,
              isSelected = videoSelectionManager.isSelected(videoFile.video),
              onClick = { onVideoClick(videoFile.video) },
              onLongClick = { onVideoLongClick(videoFile.video) },
              onThumbClick = { onVideoLongClick(videoFile.video) },
            )
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
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()

  // This dialog is only shown when in Tree View mode
  // When user switches to Folder View via this dialog, they'll be redirected
  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "View Options",
    sortType = "Name",
    onSortTypeChange = { },
    sortOrderAsc = true,
    onSortOrderChange = { },
    types = listOf("Name"),
    icons = listOf(Icons.Filled.Folder),
    getLabelForType = { _, _ -> Pair("A-Z", "Z-A") },
    showSortOptions = false, // Tree View doesn't show sort options
    viewModeSelector =
      ViewModeSelector(
        label = "View Mode",
        firstOptionLabel = "Folder View",
        secondOptionLabel = "Tree View",
        firstOptionIcon = Icons.Filled.ViewModule,
        secondOptionIcon = Icons.Filled.AccountTree,
        isFirstOptionSelected = folderViewMode == app.marlboroadvance.mpvex.preferences.FolderViewMode.MediaStore,
        onViewModeChange = { isFirstOption ->
          browserPreferences.folderViewMode.set(
            if (isFirstOption) {
              app.marlboroadvance.mpvex.preferences.FolderViewMode.MediaStore
            } else {
              app.marlboroadvance.mpvex.preferences.FolderViewMode.FileManager
            },
          )
        },
      ),
    visibilityToggles =
      listOf(
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
          label = "Progress Bar",
          checked = showProgressBar,
          onCheckedChange = { browserPreferences.showProgressBar.set(it) },
        ),
      ),
  )
}
