package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.MediaLayoutMode
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistDetailScreen
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject

@Serializable
object RecentlyPlayedScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val playlistRepository = koinInject<PlaylistRepository>()
    val viewModel: RecentlyPlayedViewModel =
      viewModel(factory = RecentlyPlayedViewModel.factory(context.applicationContext as android.app.Application))

    val recentItems by viewModel.recentItems.collectAsState()
    val recentVideos by viewModel.recentVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteFilesCheckbox = rememberSaveable { mutableStateOf(false) }
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current

    // FAB visibility for scroll-based hiding
    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Selection manager for all items (videos and playlists)
    val selectionManager =
      rememberSelectionManager(
        items = recentItems,
        getId = { item ->
          when (item) {
            is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}"
            is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}"
          }
        },
        onDeleteItems = { items, deleteFiles ->
          val videos = items.filterIsInstance<RecentlyPlayedItem.VideoItem>().map { it.video }
          val playlistIds = items.filterIsInstance<RecentlyPlayedItem.PlaylistItem>().map { it.playlist.id }

          var successCount = 0
          var failCount = 0

          // Delete videos from history
          if (videos.isNotEmpty()) {
            val (videoSuccess, videoFail) = viewModel.deleteVideosFromHistory(videos, deleteFiles)
            successCount += videoSuccess
            failCount += videoFail
          }

          // Delete playlist items from history
          if (playlistIds.isNotEmpty()) {
            val (playlistSuccess, playlistFail) = viewModel.deletePlaylistsFromHistory(playlistIds)
            successCount += playlistSuccess
            failCount += playlistFail
          }

          Pair(successCount, failCount)
        },
        onRenameItem = null, // Cannot rename from history screen
        onOperationComplete = { },
      )

    // Handle back button during selection mode or FAB menu expanded
    BackHandler(enabled = selectionManager.isInSelectionMode || isFabExpanded.value) {
      when {
        isFabExpanded.value -> isFabExpanded.value = false
        selectionManager.isInSelectionMode -> selectionManager.clear()
      }
    }
    
    // File picker for opening external files
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

    // Track scroll for FAB visibility
    val listState = LazyListState()
    val gridState = LazyGridState()
    val browserPreferences = koinInject<BrowserPreferences>()
    val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
    app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = if (mediaLayoutMode == MediaLayoutMode.GRID) gridState else null,
      isFabVisible = isFabVisible,
      expanded = isFabExpanded.value,
      onExpandedChange = { isFabExpanded.value = it },
    )

    Scaffold(
        topBar = {
          BrowserTopBar(
            title = "Recently Played",
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = recentItems.size,
            onBackClick = null, // No back button for recently played screen
            onCancelSelection = { selectionManager.clear() },
            onSortClick = null, // No sorting in recently played
            onSettingsClick = {
              backStack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
            },
            isSingleSelection = selectionManager.isSingleSelection,
            onInfoClick = null, // No info in recently played
            onShareClick = null,
            onPlayClick = null,
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
            onDeleteClick = { deleteDialogOpen.value = true },
          )
        },
      floatingActionButton = {
        FloatingActionButtonMenu(
          modifier = Modifier
            .padding( bottom = 88.dp),
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
                    visible = !selectionManager.isInSelectionMode && isFabVisible.value,
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
      },
    ) { padding ->
      when {
        !enableRecentlyPlayed -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.History,
              title = "Recently Played is disabled",
              message = "Enable it in Advanced Settings to track your playback history",
            )
          }
        }

        isLoading && recentItems.isEmpty() -> {
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

        recentItems.isEmpty() && !isLoading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            EmptyState(
              icon = Icons.Filled.History,
              title = "No recently played videos",
              message = "Videos you play will appear here",
            )
          }
        }

        else -> {
          RecentItemsContent(
            recentItems = recentItems,
            playlistRepository = playlistRepository,
            selectionManager = selectionManager,
            onVideoClick = { video ->
              // Always play individual videos without creating a playlist
              // regardless of playlist mode setting
              MediaUtils.playFile(video, context, "recently_played")
            },
            onPlaylistClick = { playlistItem ->
              // Navigate to playlist detail screen
              backStack.add(PlaylistDetailScreen(playlistItem.playlist.id))
            },
            modifier = Modifier.padding(padding),
            isInSelectionMode = selectionManager.isInSelectionMode,
          )
        }
      }

      // Delete confirmation dialog
      if (deleteDialogOpen.value && selectionManager.isInSelectionMode) {
        // Remove selected items from history
        val itemCount = selectionManager.selectedCount
        val itemText = if (itemCount == 1) "item" else "items"
        val deleteFiles = deleteFilesCheckbox.value

        val title = if (deleteFiles) {
          "Delete $itemCount $itemText?"
        } else {
          "Remove $itemCount $itemText from history?"
        }

        val subtitle = buildString {
          if (deleteFiles) {
            append("This will permanently delete the original video file(s) from your device storage.\n\n")
            append("This action cannot be undone.")
          } else {
            append("This will remove the selected $itemText from your recently played list. ")
            append("The original video files will not be deleted.")
          }
        }

        ConfirmDialog(
          title = title,
          subtitle = subtitle,
          customContent = {
            androidx.compose.foundation.layout.Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
              androidx.compose.material3.Checkbox(
                checked = deleteFilesCheckbox.value,
                onCheckedChange = {
                  deleteFilesCheckbox.value = it
                },
              )
              androidx.compose.material3.Text(
                text = "Also delete original file(s)",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          },
          onConfirm = {
            selectionManager.deleteSelected(deleteFilesCheckbox.value)
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
          onCancel = {
            deleteDialogOpen.value = false
            deleteFilesCheckbox.value = false
          },
        )
      }
      
      // Link dialog
      PlayLinkSheet(
        isOpen = showLinkDialog.value,
        onDismiss = { showLinkDialog.value = false },
        onPlayLink = { url -> MediaUtils.playFile(url, context, "play_link") },
      )
    }
  }
}

@Composable
private fun RecentItemsContent(
  recentItems: List<RecentlyPlayedItem>,
  playlistRepository: PlaylistRepository,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<RecentlyPlayedItem, String>,
  onVideoClick: (Video) -> Unit,
  onPlaylistClick: suspend (RecentlyPlayedItem.PlaylistItem) -> Unit,
  modifier: Modifier = Modifier,
  isInSelectionMode: Boolean = false,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val browserPreferences = koinInject<app.marlboroadvance.mpvex.preferences.BrowserPreferences>()
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumns by browserPreferences.folderGridColumns.collectAsState()
  val videoGridColumns by browserPreferences.videoGridColumns.collectAsState()

  val isGridMode = mediaLayoutMode == MediaLayoutMode.GRID

  val listState = LazyListState()
  val gridState = LazyGridState()
  val coroutineScope = rememberCoroutineScope()
  val isRefreshing = remember { mutableStateOf(false) }

  val thumbWidthDp = if (isGridMode) {
    (360.dp / videoGridColumns)
  } else {
    160.dp
  }
  val aspect = 16f / 9f
  val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = (thumbWidthPx / aspect).toInt()

  val recentVideos = remember(recentItems) {
    recentItems.filterIsInstance<RecentlyPlayedItem.VideoItem>().map { it.video }
  }

  LaunchedEffect(recentVideos.size, showVideoThumbnails, thumbWidthPx, thumbHeightPx) {
    if (showVideoThumbnails && recentVideos.isNotEmpty()) {
      thumbnailRepository.startFolderThumbnailGeneration(
        folderId = "recently_played",
        videos = recentVideos,
        widthPx = thumbWidthPx,
        heightPx = thumbHeightPx,
      )
    }
  }

  val isAtTop by remember {
    derivedStateOf {
      if (isGridMode) {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
      } else {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
      }
    }
  }

  val hasEnoughItems = recentItems.size > 20

  val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (!hasEnoughItems) 0f else 1f,
    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
    label = "scrollbarAlpha",
  )

  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { },
    listState = listState,
    modifier = modifier.fillMaxSize(),
  ) {
    if (isGridMode) {
      val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current
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
          LazyVerticalGrid(
            columns = GridCells.Fixed(videoGridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = 8.dp,
              end = 8.dp,
              bottom = if (isInSelectionMode) 88.dp else 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(
              count = recentItems.size,
              key = { index ->
                when (val item = recentItems[index]) {
                  is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}_${item.timestamp}"
                  is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.timestamp}"
                }
              },
            ) { index ->
              when (val item = recentItems[index]) {
                is RecentlyPlayedItem.VideoItem -> {
                  VideoCard(
                    video = item.video,
                    progressPercentage = null,
                    isSelected = selectionManager.isSelected(item),
                    onClick = {
                      if (selectionManager.isInSelectionMode) {
                        selectionManager.toggle(item)
                      } else {
                        onVideoClick(item.video)
                      }
                    },
                    onLongClick = { selectionManager.toggle(item) },
                    onThumbClick = if (tapThumbnailToSelect) {
                      { selectionManager.toggle(item) }
                    } else {
                      {
                        if (selectionManager.isInSelectionMode) {
                          selectionManager.toggle(item)
                        } else {
                          onVideoClick(item.video)
                        }
                      }
                    },
                    isGridMode = true,
                    gridColumns = videoGridColumns,
                    showSubtitleIndicator = showSubtitleIndicator,
                  )
                }

                is RecentlyPlayedItem.PlaylistItem -> {
                  val folderModel = VideoFolder(
                    bucketId = item.playlist.id.toString(),
                    name = item.playlist.name,
                    path = "",
                    videoCount = item.videoCount,
                    totalSize = 0,
                    totalDuration = 0,
                    lastModified = item.playlist.updatedAt / 1000,
                  )
                  FolderCard(
                    folder = folderModel,
                    isSelected = selectionManager.isSelected(item),
                    isRecentlyPlayed = false,
                    onClick = {
                      if (selectionManager.isInSelectionMode) {
                        selectionManager.toggle(item)
                      } else {
                        coroutineScope.launch {
                          onPlaylistClick(item)
                        }
                      }
                    },
                    onLongClick = { selectionManager.toggle(item) },
                    onThumbClick = {
                      if (tapThumbnailToSelect) {
                        selectionManager.toggle(item)
                      } else {
                        if (selectionManager.isInSelectionMode) {
                          selectionManager.toggle(item)
                        } else {
                          coroutineScope.launch {
                            onPlaylistClick(item)
                          }
                        }
                      }
                    },
                    customIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    showDateModified = true,
                    isGridMode = true,
                  )
                }
              }
            }
          }
        }
      }
    } else {
      val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current
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
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = 8.dp,
              end = 8.dp,
              bottom = if (isInSelectionMode) 88.dp else 16.dp
            ),
          ) {
            items(
              count = recentItems.size,
              key = { index ->
                when (val item = recentItems[index]) {
                  is RecentlyPlayedItem.VideoItem -> "video_${item.video.id}_${item.timestamp}"
                  is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.timestamp}"
                }
              },
            ) { index ->
              when (val item = recentItems[index]) {
                is RecentlyPlayedItem.VideoItem -> {
                  VideoCard(
                    video = item.video,
                    progressPercentage = null,
                    isSelected = selectionManager.isSelected(item),
                    onClick = {
                      if (selectionManager.isInSelectionMode) {
                        selectionManager.toggle(item)
                      } else {
                        onVideoClick(item.video)
                      }
                    },
                    onLongClick = { selectionManager.toggle(item) },
                    onThumbClick = if (tapThumbnailToSelect) {
                      { selectionManager.toggle(item) }
                    } else {
                      {
                        if (selectionManager.isInSelectionMode) {
                          selectionManager.toggle(item)
                        } else {
                          onVideoClick(item.video)
                        }
                      }
                    },
                    isGridMode = false,
                    showSubtitleIndicator = showSubtitleIndicator,
                  )
                }

                is RecentlyPlayedItem.PlaylistItem -> {
                  val folderModel = VideoFolder(
                    bucketId = item.playlist.id.toString(),
                    name = item.playlist.name,
                    path = "",
                    videoCount = item.videoCount,
                    totalSize = 0,
                    totalDuration = 0,
                    lastModified = item.playlist.updatedAt / 1000,
                  )
                  FolderCard(
                    folder = folderModel,
                    isSelected = selectionManager.isSelected(item),
                    isRecentlyPlayed = false,
                    onClick = {
                      if (selectionManager.isInSelectionMode) {
                        selectionManager.toggle(item)
                      } else {
                        coroutineScope.launch {
                          onPlaylistClick(item)
                        }
                      }
                    },
                    onLongClick = { selectionManager.toggle(item) },
                    onThumbClick = {
                      if (tapThumbnailToSelect) {
                        selectionManager.toggle(item)
                      } else {
                        if (selectionManager.isInSelectionMode) {
                          selectionManager.toggle(item)
                        } else {
                          coroutineScope.launch {
                            onPlaylistClick(item)
                          }
                        }
                      }
                    },
                    customIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    showDateModified = true,
                    isGridMode = false,
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

