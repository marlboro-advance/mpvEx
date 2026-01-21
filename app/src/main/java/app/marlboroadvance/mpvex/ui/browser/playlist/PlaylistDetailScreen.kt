package app.marlboroadvance.mpvex.ui.browser.playlist

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.M3UVideoCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.animateFloatingActionButton
import app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper

/**
 * Playlist detail screen showing videos in a playlist.
 *
 * **M3U Playlist Behavior:**
 * M3U playlists (streaming URLs) now support full playlist navigation:
 * - Next/previous buttons available during playback
 * - Playlist continuation and shuffle modes
 * - Full playlist loaded into PlayerActivity
 * - Uses windowed loading for large playlists to prevent ANR
 *
 * **Regular Playlist Behavior:**
 * Local file playlists support full playlist navigation:
 * - Next/previous buttons available during playback
 * - Playlist continuation and shuffle modes
 * - Full playlist loaded into PlayerActivity
 */
@Serializable
data class PlaylistDetailScreen(val playlistId: Int) : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<PlaylistRepository>()
    val backStack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel
    val viewModel: PlaylistDetailViewModel =
      viewModel(
        key = "PlaylistDetailViewModel_$playlistId",
        factory = PlaylistDetailViewModel.factory(
          context.applicationContext as android.app.Application,
          playlistId,
        ),
      )

    val playlist by viewModel.playlist.collectAsState()
    val videoItems by viewModel.videoItems.collectAsState()
    val videos = videoItems.map { it.video }
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing = remember { mutableStateOf(false) }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Filter video items based on search query
    val filteredVideoItems = if (isSearching && searchQuery.isNotBlank()) {
      videoItems.filter { item ->
        item.video.displayName.contains(searchQuery, ignoreCase = true) ||
        item.video.path.contains(searchQuery, ignoreCase = true)
      }
    } else {
      videoItems
    }
    val filteredVideos = filteredVideoItems.map { it.video }

    // Request focus when search is activated
    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    // Selection manager - use playlist item ID as unique key, work with filtered items
    val selectionManager =
      rememberSelectionManager(
        items = filteredVideoItems,
        getId = { it.playlistItem.id },
        onDeleteItems = { itemsToDelete, _ ->
          // Remove all items in a single coroutine to avoid concurrent deletion race conditions
          coroutineScope.launch {
            val videosToRemove = itemsToDelete.map { it.video }
            viewModel.removeVideosFromPlaylist(videosToRemove)
          }
          Pair(itemsToDelete.size, 0)
        },
        onOperationComplete = { viewModel.refresh() },
      )

    // UI State
    val listState = rememberLazyListState()
    val isFabVisible = remember { mutableStateOf(true) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val mediaInfoDialogOpen = rememberSaveable { mutableStateOf(false) }
    val selectedVideo = remember { mutableStateOf<Video?>(null) }
    val mediaInfoData = remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }
    val mediaInfoLoading = remember { mutableStateOf(false) }
    val mediaInfoError = remember { mutableStateOf<String?>(null) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var urlDialogContent by remember { mutableStateOf("") }

    // Reorder mode state
    var isReorderMode by rememberSaveable { mutableStateOf(false) }

    // Predictive back: Intercept when in selection mode, reorder mode, or searching
    BackHandler(enabled = selectionManager.isInSelectionMode || isReorderMode || isSearching) {
      when {
        isReorderMode -> isReorderMode = false
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }
        selectionManager.isInSelectionMode -> selectionManager.clear()
      }
    }

    FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = null,
      isFabVisible = isFabVisible,
      expanded = false,
      onExpandedChange = {}
    )

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
            title = playlist?.name ?: "Playlist",
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = videos.size,
            onBackClick = {
              when {
                isReorderMode -> isReorderMode = false
                selectionManager.isInSelectionMode -> selectionManager.clear()
                else -> backStack.removeLastOrNull()
              }
            },
            onCancelSelection = { selectionManager.clear() },
          isSingleSelection = selectionManager.isSingleSelection,
          useRemoveIcon = true, // Show remove icon instead of delete for playlist
          onInfoClick =
            if (selectionManager.isSingleSelection) {
              {
                val item = selectionManager.getSelectedItems().firstOrNull()
                if (item != null) {
                  if (playlist?.isM3uPlaylist == true) {
                    // For M3U playlists, show URL dialog
                    urlDialogContent = item.video.path
                    showUrlDialog = true
                    selectionManager.clear()
                  } else {
                    // For regular playlists, show MediaInfo activity
                    val intent = Intent(context, app.marlboroadvance.mpvex.ui.mediainfo.MediaInfoActivity::class.java)
                    intent.action = Intent.ACTION_VIEW
                    intent.data = item.video.uri
                    context.startActivity(intent)
                    selectionManager.clear()
                  }
                }
              }
            } else {
              null
            },
          onShareClick = if (playlist?.isM3uPlaylist != true) {
            // Hide share button for M3U playlists
            {
              val videosToShare = selectionManager.getSelectedItems().map { it.video }
              MediaUtils.shareVideos(context, videosToShare)
            }
          } else {
            null
          },
          onPlayClick = null, // Don't show play icon in selection mode for playlist
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
          onDeleteClick = { deleteDialogOpen.value = true },
          additionalActions = {
            when {
              // Show done button when in reorder mode
              isReorderMode -> {
                IconButton(
                  onClick = { isReorderMode = false },
                ) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Done reordering",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              // Show reorder button and play button when not in selection mode
              !selectionManager.isInSelectionMode && videos.isNotEmpty() -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  // Search button
                  IconButton(
                    onClick = { isSearching = true },
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Search,
                      contentDescription = "Search videos",
                      tint = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Spacer(modifier = Modifier.width(4.dp))

                  // Reorder button (hide for M3U playlists)
                  if (playlist?.isM3uPlaylist != true) {
                    IconButton(
                      onClick = { isReorderMode = true },
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = "Reorder playlist",
                        tint = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                  }
                }
              }
            }
          },
        )
        }
      },
      floatingActionButton = {
        if (videoItems.isNotEmpty()) {
          TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text("Play recently played or first video") } },
            state = rememberTooltipState(),
          ) {
            FloatingActionButton(
              modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 16.dp, end = 16.dp)
                .animateFloatingActionButton(
                  visible = !selectionManager.isInSelectionMode && isFabVisible.value,
                  alignment = Alignment.BottomEnd,
                ),
              onClick = {
                coroutineScope.launch {
                  val folderPath = videoItems.firstOrNull()?.video?.path?.let { File(it).parent } ?: ""
                  val recentlyPlayedVideos = app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps.getRecentlyPlayed(limit = 100)
                  val lastPlayedInFolder = recentlyPlayedVideos.firstOrNull {
                    File(it.filePath).parent == folderPath
                  }

                  if (lastPlayedInFolder != null) {
                    MediaUtils.playFile(lastPlayedInFolder.filePath, context, "recently_played_button")
                  } else {
                    MediaUtils.playFile(videoItems.first().video, context, "first_video_button")
                  }
                }
              },
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = "Play recently played or first video")
            }
          }
        }
      },
    ) { padding ->
      // Show "no results" message when searching with no results
      if (isSearching && filteredVideoItems.isEmpty() && searchQuery.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = null,
              modifier = Modifier.size(64.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "No videos found",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Try a different search term",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        val pullToRefreshEnabled =
          !selectionManager.isInSelectionMode && !isReorderMode && !isSearching

        PullRefreshBox(
          isRefreshing = isRefreshing,
          enabled = pullToRefreshEnabled,
          listState = listState,
          modifier = Modifier.fillMaxSize().padding(padding),
          onRefresh = {
            val isM3uPlaylist = playlist?.isM3uPlaylist == true
            if (isM3uPlaylist) {
              val result = viewModel.refreshM3UPlaylist()
              result
                .onSuccess {
                  Toast.makeText(context, "Playlist refreshed successfully", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                  Toast.makeText(context, "Failed to refresh: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } else {
              viewModel.refreshNow()
            }
          },
        ) {
          PlaylistVideoListContent(
            videoItems = filteredVideoItems,
            isLoading = isLoading && videoItems.isEmpty(),
            selectionManager = selectionManager,
            isReorderMode = isReorderMode,
            isM3uPlaylist = playlist?.isM3uPlaylist == true,
            onVideoItemClick = { item ->
              if (selectionManager.isInSelectionMode) {
                selectionManager.toggle(item)
              } else if (!isReorderMode) {
                // Record play history
                coroutineScope.launch {
                  viewModel.updatePlayHistory(item.video.path)
                }

                // Play with full playlist navigation (both M3U and regular playlists)
                val startIndex = videoItems.indexOfFirst { it.playlistItem.id == item.playlistItem.id }
                if (startIndex >= 0) {
                  if (videos.size == 1) {
                    MediaUtils.playFile(item.video, context, "playlist_detail")
                  } else {
                    val intent = Intent(Intent.ACTION_VIEW, videos[startIndex].uri)
                    intent.setClass(context, PlayerActivity::class.java)
                    intent.putExtra("internal_launch", true)
                    intent.putExtra("playlist_index", startIndex)
                    intent.putExtra("launch_source", "playlist")
                    intent.putExtra("playlist_id", playlistId)
                    intent.putExtra("title", videos[startIndex].displayName)
                    context.startActivity(intent)
                  }
                } else {
                  MediaUtils.playFile(item.video, context, "playlist_detail")
                }
              }
            },
            onVideoItemLongClick = { item ->
              if (!isReorderMode) {
                selectionManager.toggle(item)
              }
            },
            onReorder = { fromIndex, toIndex ->
              coroutineScope.launch {
                viewModel.reorderPlaylistItems(fromIndex, toIndex)
              }
            },
            listState = listState,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // Dialogs
      RemoveFromPlaylistDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemCount = selectionManager.selectedCount,
      )

      // URL Dialog for M3U streams
      if (showUrlDialog) {
        StreamUrlDialog(
          url = urlDialogContent,
          onDismiss = { showUrlDialog = false },
          onCopy = {
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Stream URL", urlDialogContent)
            clipboardManager.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "URL copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
          }
        )
      }
    }
  }
}

@Composable
private fun PlaylistVideoListContent(
  videoItems: List<PlaylistVideoItem>,
  isLoading: Boolean,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<PlaylistVideoItem, Int>,
  isReorderMode: Boolean,
  onVideoItemClick: (PlaylistVideoItem) -> Unit,
  onVideoItemLongClick: (PlaylistVideoItem) -> Unit,
  onReorder: (Int, Int) -> Unit,
  listState: androidx.compose.foundation.lazy.LazyListState,
  modifier: Modifier = Modifier,
  isM3uPlaylist: Boolean = false,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val browserPreferences = koinInject<app.marlboroadvance.mpvex.preferences.BrowserPreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()

  // Find the most recently played video (highest lastPlayedAt timestamp)
  val mostRecentlyPlayedItem = remember(videoItems) {
    videoItems.filter { it.playlistItem.lastPlayedAt > 0 }
      .maxByOrNull { it.playlistItem.lastPlayedAt }
  }

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

    videoItems.isEmpty() -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        androidx.compose.foundation.layout.Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "No videos in playlist",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Add videos to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    else -> {
      // Only show scrollbar if list has more than 20 items
      val hasEnoughItems = videoItems.size > 20

      // Animate scrollbar alpha
      val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (!hasEnoughItems) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "scrollbarAlpha",
      )

      // Reorderable state
      val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        onReorder(from.index, to.index)
      }

      LazyColumnScrollbar(
        state = listState,
        settings = ScrollbarSettings(
          thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
          thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
        ),
        modifier = modifier.fillMaxSize(),
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
        ) {
          items(
            count = videoItems.size,
            key = { index -> videoItems[index].playlistItem.id },
          ) { index ->
            ReorderableItem(reorderableLazyListState, key = videoItems[index].playlistItem.id) {
              val item = videoItems[index]

              val progressPercentage = if (item.playlistItem.lastPosition > 0 && item.video.duration > 0) {
                item.playlistItem.lastPosition.toFloat() / item.video.duration.toFloat() * 100f
              } else null

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Use M3UVideoCard for streaming URLs, VideoCard for local files
                if (isM3uPlaylist) {
                  M3UVideoCard(
                    title = item.video.displayName,
                    url = item.video.path,
                    onClick = { onVideoItemClick(item) },
                    onLongClick = { onVideoItemLongClick(item) },
                    isSelected = selectionManager.isSelected(item),
                    isRecentlyPlayed = item.playlistItem.id == mostRecentlyPlayedItem?.playlistItem?.id,
                    modifier = Modifier.weight(1f),
                  )
                } else {
                  VideoCard(
                    video = item.video,
                    progressPercentage = progressPercentage,
                    isRecentlyPlayed = item.playlistItem.id == mostRecentlyPlayedItem?.playlistItem?.id,
                    isSelected = selectionManager.isSelected(item),
                    onClick = { onVideoItemClick(item) },
                    onLongClick = { onVideoItemLongClick(item) },
                    onThumbClick = if (tapThumbnailToSelect) {
                      { onVideoItemLongClick(item) }
                    } else {
                      { onVideoItemClick(item) }
                    },
                    showSubtitleIndicator = showSubtitleIndicator,
                    modifier = Modifier.weight(1f),
                  )
                }

                // Drag handle - only show when in reorder mode, positioned at the end
                if (isReorderMode) {
                  IconButton(
                    onClick = { },
                    modifier = Modifier
                      .size(48.dp)
                      .draggableHandle(),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.DragHandle,
                      contentDescription = "Drag to reorder",
                      tint = MaterialTheme.colorScheme.primary,
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

@Composable
private fun StreamUrlDialog(
  url: String,
  onDismiss: () -> Unit,
  onCopy: () -> Unit,
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Stream URL") },
    text = {
      Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
      )
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = {
          onCopy()
          onDismiss()
        }
      ) {
        Icon(
          imageVector = Icons.Filled.ContentCopy,
          contentDescription = null,
          modifier = Modifier.padding(end = 4.dp).size(18.dp)
        )
        Text("Copy")
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

@Composable
private fun RemoveFromPlaylistDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  itemCount: Int,
) {
  if (!isOpen) return

  val itemText = if (itemCount == 1) "video" else "videos"

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Remove $itemCount $itemText from playlist?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
      )
    },
    text = {
      androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
      ) {
        androidx.compose.material3.Card(
          colors =
            androidx.compose.material3.CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Text(
            text = "The selected $itemText will be removed from this playlist. The original ${if (itemCount == 1) "file" else "files"} will not be deleted.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors =
          androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(
          text = "Remove from Playlist",
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      androidx.compose.material3.TextButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Cancel", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
  return try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      cursor.moveToFirst()
      cursor.getString(nameIndex)
    } ?: uri.lastPathSegment
  } catch (e: Exception) {
    uri.lastPathSegment
  }
}
