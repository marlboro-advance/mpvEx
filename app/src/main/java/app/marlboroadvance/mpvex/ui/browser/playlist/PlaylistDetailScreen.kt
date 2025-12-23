package app.marlboroadvance.mpvex.ui.browser.playlist

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Serializable
data class PlaylistDetailScreen(val playlistId: Int) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
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

    // Selection manager - use playlist item ID as unique key
    val selectionManager =
      rememberSelectionManager(
        items = videoItems,
        getId = { it.playlistItem.id },
        onDeleteItems = { itemsToDelete ->
          itemsToDelete.forEach { item ->
            coroutineScope.launch {
              viewModel.removeVideoFromPlaylist(item.video)
            }
          }
          Pair(itemsToDelete.size, 0)
        },
        onOperationComplete = { viewModel.refresh() },
      )

    // UI State
    val listState = rememberLazyListState()
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val mediaInfoDialogOpen = rememberSaveable { mutableStateOf(false) }
    val selectedVideo = remember { mutableStateOf<Video?>(null) }
    val mediaInfoData = remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }
    val mediaInfoLoading = remember { mutableStateOf(false) }
    val mediaInfoError = remember { mutableStateOf<String?>(null) }

    // Reorder mode state
    var isReorderMode by rememberSaveable { mutableStateOf(false) }

    // Predictive back: Only intercept when in selection mode or reorder mode
    BackHandler(enabled = selectionManager.isInSelectionMode || isReorderMode) {
      if (isReorderMode) {
        isReorderMode = false
      } else {
        selectionManager.clear()
      }
    }

    Scaffold(
      topBar = {
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
                  val intent = Intent(context, app.marlboroadvance.mpvex.ui.mediainfo.MediaInfoActivity::class.java)
                  intent.action = Intent.ACTION_VIEW
                  intent.data = item.video.uri
                  context.startActivity(intent)
                  selectionManager.clear()
                }
              }
            } else {
              null
            },
          onShareClick = {
            val videosToShare = selectionManager.getSelectedItems().map { it.video }
            MediaUtils.shareVideos(context, videosToShare)
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
                  // Reorder button
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

                  // Play button
                  Button(
                    onClick = {
                      // Find the most recently played video
                      val mostRecentlyPlayedItem = videoItems
                        .filter { it.playlistItem.lastPlayedAt > 0 }
                        .maxByOrNull { it.playlistItem.lastPlayedAt }

                      // Start from most recently played video, or first video if none played yet
                      val startIndex = if (mostRecentlyPlayedItem != null) {
                        videoItems.indexOfFirst { it.playlistItem.id == mostRecentlyPlayedItem.playlistItem.id }
                      } else {
                        0
                      }

                      // Record play history for the video we're starting from
                      if (videos.isNotEmpty() && startIndex >= 0) {
                        coroutineScope.launch {
                          viewModel.updatePlayHistory(videos[startIndex].path)
                        }
                      }

                      val videoUris = videos.map { it.uri }
                      if (videoUris.isNotEmpty() && startIndex >= 0) {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                          action = Intent.ACTION_VIEW
                          data = videoUris[startIndex]
                          putParcelableArrayListExtra("playlist", ArrayList(videoUris))
                          putExtra("playlist_index", startIndex)
                          putExtra("launch_source", "playlist")
                          putExtra("playlist_id", playlistId) // Pass playlist ID for tracking
                        }
                        context.startActivity(intent)
                      }
                    },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primaryContainer,
                      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(end = 20.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          },
        )
      },
      floatingActionButton = { },
    ) { padding ->
      PlaylistVideoListContent(
        videoItems = videoItems,
        isLoading = isLoading && videoItems.isEmpty(),
        selectionManager = selectionManager,
        isReorderMode = isReorderMode,
        onVideoItemClick = { item ->
          if (selectionManager.isInSelectionMode) {
            selectionManager.toggle(item)
          } else if (!isReorderMode) {
            // Record play history
            coroutineScope.launch {
              viewModel.updatePlayHistory(item.video.path)
            }

            // Always play as playlist from playlist screen
            val startIndex = videoItems.indexOfFirst { it.playlistItem.id == item.playlistItem.id }
            if (startIndex >= 0) {
              if (videos.size == 1) {
                MediaUtils.playFile(item.video, context, "playlist_detail")
              } else {
                val intent = Intent(Intent.ACTION_VIEW, videos[startIndex].uri)
                intent.setClass(context, PlayerActivity::class.java)
                intent.putExtra("internal_launch", true)
                intent.putParcelableArrayListExtra("playlist", ArrayList(videos.map { it.uri }))
                intent.putExtra("playlist_index", startIndex)
                intent.putExtra("launch_source", "playlist")
                intent.putExtra("playlist_id", playlistId) // Pass playlist ID for tracking
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
        modifier = Modifier.padding(padding),
      )

      // Dialogs
      RemoveFromPlaylistDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemCount = selectionManager.selectedCount,
      )
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
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()

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
            imageVector = Icons.Outlined.PlaylistAdd,
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
          contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
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
                  modifier = Modifier.weight(1f),
                )

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
