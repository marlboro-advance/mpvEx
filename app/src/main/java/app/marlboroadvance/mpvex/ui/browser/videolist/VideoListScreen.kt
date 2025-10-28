package app.marlboroadvance.mpvex.ui.browser.videolist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.VideoSortType
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.MediaInfoDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.RenameDialog
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class VideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backstack = LocalBackStack.current
    val browserPreferences = koinInject<BrowserPreferences>()

    // ViewModel
    val viewModel: VideoListViewModel =
      viewModel(
        key = "VideoListViewModel_$bucketId",
        factory = VideoListViewModel.factory(context.applicationContext as android.app.Application, bucketId),
      )
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()

    // Sorting
    val videoSortType by browserPreferences.videoSortType.collectAsState()
    val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()
    val sortedVideos =
      remember(videos, videoSortType, videoSortOrder) {
        SortUtils.sortVideos(videos, videoSortType, videoSortOrder)
      }

    // Selection manager
    val selectionManager =
      rememberSelectionManager(
        items = sortedVideos,
        getId = { it.id },
        onDeleteItems = { viewModel.deleteVideos(it) },
        onRenameItem = { video, newName -> viewModel.renameVideo(video, newName) },
        onOperationComplete = { viewModel.refresh() },
      )

    // UI State
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val mediaInfoDialogOpen = rememberSaveable { mutableStateOf(false) }
    val selectedVideo = remember { mutableStateOf<Video?>(null) }
    val mediaInfoData = remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }
    val mediaInfoLoading = remember { mutableStateOf(false) }
    val mediaInfoError = remember { mutableStateOf<String?>(null) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val renameDialogOpen = rememberSaveable { mutableStateOf(false) }

    val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName

    // Predictive back: Only intercept when in selection mode
    BackHandler(enabled = selectionManager.isInSelectionMode) {
      selectionManager.clear()
    }

    androidx.compose.runtime.LaunchedEffect(
      deleteDialogOpen.value,
      renameDialogOpen.value,
    ) {
      if (!deleteDialogOpen.value && !renameDialogOpen.value) {
        kotlinx.coroutines.delay(100)
        viewModel.refresh()
      }
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = displayFolderName,
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = sortedVideos.size,
          onBackClick = {
            if (selectionManager.isInSelectionMode) {
              selectionManager.clear()
            } else {
              backstack.removeLastOrNull()
            }
          },
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onDeleteClick = { deleteDialogOpen.value = true },
          onRenameClick = { renameDialogOpen.value = true },
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = {
            if (selectionManager.isSingleSelection) {
              val video = selectionManager.getSelectedItems().firstOrNull()
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
          },
          onShareClick = { selectionManager.shareSelected() },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
        )
      },
    ) { padding ->
      VideoListContent(
        videos = sortedVideos,
        isLoading = isLoading && videos.isEmpty(),
        isRefreshing = isRefreshing,
        recentlyPlayedFilePath = recentlyPlayedFilePath,
        onRefresh = { viewModel.refresh() },
        selectionManager = selectionManager,
        onVideoClick = { video ->
          if (selectionManager.isInSelectionMode) {
            selectionManager.toggle(video)
          } else {
            MediaUtils.playFile(video, context)
          }
        },
        onVideoLongClick = { video -> selectionManager.toggle(video) },
        modifier = Modifier.padding(padding),
      )

      // Sort Dialog
      VideoSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        onSortTypeChange = { browserPreferences.videoSortType.set(it) },
        onSortOrderChange = { browserPreferences.videoSortOrder.set(it) },
      )

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

      // Delete Dialog
      DeleteConfirmationDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemType = "video",
        itemCount = selectionManager.selectedCount,
      )

      // Rename Dialog
      if (renameDialogOpen.value && selectionManager.isSingleSelection) {
        val video = selectionManager.getSelectedItems().firstOrNull()
        if (video != null) {
          val baseName = video.displayName.substringBeforeLast('.')
          val extension = "." + video.displayName.substringAfterLast('.', "")
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen.value = false },
            onConfirm = { newName -> selectionManager.renameSelected(newName) },
            currentName = baseName,
            itemType = "file",
            extension = if (extension != ".") extension else null,
          )
        }
      }
    }
  }
}

@Composable
private fun VideoListContent(
  videos: List<Video>,
  isLoading: Boolean,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
  selectionManager: SelectionManager<Video, Long>,
  onVideoClick: (Video) -> Unit,
  onVideoLongClick: (Video) -> Unit,
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

      videos.isEmpty() -> {
        Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            stringResource(R.string.no_videos_found),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
        }
      }

      else -> {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(8.dp),
        ) {
          items(videos.size) { index ->
            val video = videos[index]
            val isRecentlyPlayed = recentlyPlayedFilePath?.let { video.path == it } ?: false

            VideoCard(
              video = video,
              isRecentlyPlayed = isRecentlyPlayed,
              isSelected = selectionManager.isSelected(video),
              onClick = { onVideoClick(video) },
              onLongClick = { onVideoLongClick(video) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VideoSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: VideoSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (VideoSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort Videos",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      VideoSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types =
      listOf(
        VideoSortType.Title.displayName,
        VideoSortType.Duration.displayName,
        VideoSortType.Date.displayName,
        VideoSortType.Size.displayName,
      ),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.AccessTime,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    getLabelForType = { type, _ ->
      when (type) {
        VideoSortType.Title.displayName -> Pair("A-Z", "Z-A")
        VideoSortType.Duration.displayName -> Pair("Shortest", "Longest")
        VideoSortType.Date.displayName -> Pair("Oldest", "Newest")
        VideoSortType.Size.displayName -> Pair("Smallest", "Biggest")
        else -> Pair("Asc", "Desc")
      }
    },
  )
}
