package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistDetailScreen
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
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
    val playerPreferences = koinInject<PlayerPreferences>()
    val playlistMode by playerPreferences.playlistMode.collectAsState()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()

    // Selection manager for videos only
    val selectionManager =
      rememberSelectionManager(
        items = recentVideos,
        getId = { it.id },
        onDeleteItems = { videos -> viewModel.deleteVideosFromHistory(videos) },
        onRenameItem = null, // Cannot rename from history screen
        onOperationComplete = { },
      )

    // Handle back button during selection mode
    BackHandler(enabled = selectionManager.isInSelectionMode) {
      selectionManager.clear()
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Recently Played",
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = recentVideos.size,
          onBackClick = null, // No back button for recently played screen
          onCancelSelection = { selectionManager.clear() },
          onSortClick = null, // No sorting in recently played
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = null, // No info in recently played
          onShareClick = { selectionManager.shareSelected() },
          onPlayClick = { selectionManager.playSelected() },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
          onDeleteClick = { deleteDialogOpen.value = true },
        )
      },
    ) { padding ->
      when {
        !enableRecentlyPlayed -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding)
              .padding(bottom = 80.dp), // Account for bottom navigation bar
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
              .padding(padding)
              .padding(bottom = 80.dp), // Account for bottom navigation bar
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
            playlistMode = playlistMode,
            playlistRepository = playlistRepository,
            selectionManager = selectionManager,
            onVideoClick = { video ->
              if (selectionManager.isInSelectionMode) {
                selectionManager.toggle(video)
              } else {
                // If playlist mode is enabled, play all videos starting from the clicked one
                if (playlistMode) {
                  val startIndex = recentVideos.indexOfFirst { it.id == video.id }
                  if (startIndex >= 0) {
                    if (recentVideos.size == 1) {
                      // Single video - play normally
                      MediaUtils.playFile(video, context, "recently_played")
                    } else {
                      // Multiple videos - play as playlist starting from clicked video
                      val intent = Intent(Intent.ACTION_VIEW, recentVideos[startIndex].uri)
                      intent.setClass(context, PlayerActivity::class.java)
                      intent.putExtra("internal_launch", true)
                      intent.putParcelableArrayListExtra("playlist", ArrayList(recentVideos.map { it.uri }))
                      intent.putExtra("playlist_index", startIndex)
                      intent.putExtra("launch_source", "recently_played_list")
                      context.startActivity(intent)
                    }
                  } else {
                    MediaUtils.playFile(video, context, "recently_played")
                  }
                } else {
                  MediaUtils.playFile(video, context, "recently_played")
                }
              }
            },
            onVideoLongClick = { video ->
              selectionManager.toggle(video)
            },
            onPlaylistClick = { playlistItem ->
              // Navigate to playlist detail screen
              backStack.add(PlaylistDetailScreen(playlistItem.playlist.id))
            },
            modifier = Modifier.padding(padding),
          )
        }
      }

      // Delete confirmation dialog
      if (deleteDialogOpen.value && selectionManager.isInSelectionMode) {
        // Remove selected items from history
        val itemCount = selectionManager.selectedCount
        val itemText = if (itemCount == 1) "item" else "items"
        ConfirmDialog(
          title = "Remove $itemCount $itemText from history?",
          subtitle = "This will remove the selected $itemText from your recently played list. The video files will not be deleted.",
          onConfirm = {
            selectionManager.deleteSelected()
            deleteDialogOpen.value = false
          },
          onCancel = { deleteDialogOpen.value = false },
        )
      }
    }
  }
}

@Composable
private fun RecentItemsContent(
  recentItems: List<RecentlyPlayedItem>,
  playlistMode: Boolean,
  playlistRepository: PlaylistRepository,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<Video, Long>,
  onVideoClick: (Video) -> Unit,
  onVideoLongClick: (Video) -> Unit,
  onPlaylistClick: suspend (RecentlyPlayedItem.PlaylistItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  // Check if at top of list to hide scrollbar
  val isAtTop by remember {
    derivedStateOf {
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
  }

  val hasEnoughItems = recentItems.size > 20

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
              isSelected = selectionManager.isSelected(item.video),
              onClick = { onVideoClick(item.video) },
              onLongClick = { onVideoLongClick(item.video) },
              onThumbClick = if (tapThumbnailToSelect) {
                { onVideoLongClick(item.video) }
              } else {
                { onVideoClick(item.video) }
              },
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
              isSelected = false,
              isRecentlyPlayed = false,
              onClick = {
                coroutineScope.launch {
                  onPlaylistClick(item)
                }
              },
              onLongClick = { },
              onThumbClick = {
                coroutineScope.launch {
                  onPlaylistClick(item)
                }
              },
              customIcon = Icons.Filled.PlaylistPlay,
              showDateModified = true,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RecentVideosContent(
  recentVideos: List<Video>,
  playlistMode: Boolean,
  onVideoClick: (Video) -> Unit,
  modifier: Modifier = Modifier,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val listState = rememberLazyListState()

  // Check if at top of list to hide scrollbar
  val isAtTop by remember {
    derivedStateOf {
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
  }

  // Only show scrollbar if list has more than 20 items
  val hasEnoughItems = recentVideos.size > 20

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
      items(
        count = recentVideos.size,
        key = { index -> "${recentVideos[index].id}_${recentVideos[index].path}_$index" },
      ) { index ->
        val video = recentVideos[index]
        VideoCard(
          video = video,
          progressPercentage = null,
          isSelected = false,
          onClick = { onVideoClick(video) },
          onLongClick = { },
          onThumbClick = if (tapThumbnailToSelect) {
            { }
          } else {
            { onVideoClick(video) }
          },
        )
      }
    }
  }
}
