package app.marlboroadvance.mpvex.ui.browser.videolist

import android.content.Intent
import android.os.Environment
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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.VideoSortType
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.VideoCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserBottomBar
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FileOperationProgressDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.FolderPickerDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.LoadingDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.MediaInfoDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.RenameDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.CopyPasteOps
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt

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
    val playerPreferences = koinInject<PlayerPreferences>()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // ViewModel
    val viewModel: VideoListViewModel =
      viewModel(
        key = "VideoListViewModel_$bucketId",
        factory = VideoListViewModel.factory(context.applicationContext as android.app.Application, bucketId),
      )
    val videos by viewModel.videos.collectAsState()
    val videosWithPlaybackInfo by viewModel.videosWithPlaybackInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val playlistMode by playerPreferences.playlistMode.collectAsState()

    // Sorting
    val videoSortType by browserPreferences.videoSortType.collectAsState()
    val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()
    val sortedVideosWithInfo =
      remember(videosWithPlaybackInfo, videoSortType, videoSortOrder) {
        val sortedVideos = SortUtils.sortVideos(videosWithPlaybackInfo.map { it.video }, videoSortType, videoSortOrder)
        // Maintain the playback info mapping
        sortedVideos.map { video ->
          videosWithPlaybackInfo.find { it.video.id == video.id } ?: VideoWithPlaybackInfo(video)
        }
      }

    // Selection manager
    val selectionManager =
      rememberSelectionManager(
        items = sortedVideosWithInfo.map { it.video },
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

    // Copy/Move state
    val folderPickerOpen = rememberSaveable { mutableStateOf(false) }
    val operationType = remember { mutableStateOf<CopyPasteOps.OperationType?>(null) }
    val progressDialogOpen = rememberSaveable { mutableStateOf(false) }
    val operationProgress by CopyPasteOps.operationProgress.collectAsState()

    // Private space state
    val movingToPrivateSpace = rememberSaveable { mutableStateOf(false) }
    val showPrivateSpaceCompletionDialog = rememberSaveable { mutableStateOf(false) }
    val privateSpaceMovedCount = remember { mutableIntStateOf(0) }

    val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName

    // Predictive back: Only intercept when in selection mode
    BackHandler(enabled = selectionManager.isInSelectionMode) {
      selectionManager.clear()
    }

    // Listen for lifecycle resume events and refresh videos when coming into focus
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

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = displayFolderName,
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = sortedVideosWithInfo.size,
          onBackClick = {
            if (selectionManager.isInSelectionMode) {
              selectionManager.clear()
            } else {
              backstack.removeLastOrNull()
            }
          },
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
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
          onPlayClick = { selectionManager.playSelected() },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
        )
      },
      bottomBar = {
        BrowserBottomBar(
          isSelectionMode = selectionManager.isInSelectionMode,
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
      },
    ) { padding ->
      VideoListContent(
        videosWithInfo = sortedVideosWithInfo,
        isLoading = isLoading && videos.isEmpty(),
        isRefreshing = isRefreshing,
        recentlyPlayedFilePath = recentlyPlayedFilePath,
        onRefresh = { viewModel.refresh() },
        selectionManager = selectionManager,
        onVideoClick = { video ->
          if (selectionManager.isInSelectionMode) {
            selectionManager.toggle(video)
          } else {
            // If playlist mode is enabled, play all videos starting from the clicked one
            if (playlistMode) {
              val allVideos = sortedVideosWithInfo.map { it.video }
              val startIndex = allVideos.indexOfFirst { it.id == video.id }
              if (startIndex >= 0) {
                if (allVideos.size == 1) {
                  // Single video - play normally
                  MediaUtils.playFile(video, context, "video_list")
                } else {
                  // Multiple videos - play as playlist starting from clicked video
                  val intent = Intent(Intent.ACTION_VIEW, allVideos[startIndex].uri)
                  intent.setClass(context, PlayerActivity::class.java)
                  intent.putExtra("internal_launch", true)
                  intent.putParcelableArrayListExtra("playlist", ArrayList(allVideos.map { it.uri }))
                  intent.putExtra("playlist_index", startIndex)
                  intent.putExtra("launch_source", "playlist")
                  context.startActivity(intent)
                }
              } else {
                MediaUtils.playFile(video, context, "video_list")
              }
            } else {
              MediaUtils.playFile(video, context, "video_list")
            }
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

      // Folder Picker Dialog
      FolderPickerDialog(
        isOpen = folderPickerOpen.value,
        currentPath =
          videos.firstOrNull()?.let { File(it.path).parent }
            ?: Environment.getExternalStorageDirectory().absolutePath,
        onDismiss = { folderPickerOpen.value = false },
        onFolderSelected = { destinationPath ->
          folderPickerOpen.value = false
          val selectedVideos = selectionManager.getSelectedItems()
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
            selectionManager.clear()
            viewModel.refresh()
          },
        )
      }

      // Private Space Loading Dialog
      LoadingDialog(
        isOpen = movingToPrivateSpace.value,
        message = "Moving to private space...",
      )

      // Private Space Completion Dialog
      if (showPrivateSpaceCompletionDialog.value) {
        androidx.compose.material3.AlertDialog(
          onDismissRequest = { showPrivateSpaceCompletionDialog.value = false },
          title = {
            Text(
              text = "Moved to Private Space",
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          text = {
            Text(
              text =
                "Successfully moved ${privateSpaceMovedCount.intValue} video(s) to private space.\n\n" +
                  "To access private space, long press on the app name at the top of the main screen.",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          confirmButton = {
            androidx.compose.material3.Button(
              onClick = { showPrivateSpaceCompletionDialog.value = false },
            ) {
              Text("Close")
            }
          },
        )
      }
    }
  }
}

@Composable
private fun VideoListContent(
  videosWithInfo: List<VideoWithPlaybackInfo>,
  isLoading: Boolean,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
  selectionManager: SelectionManager<Video, Long>,
  onVideoClick: (Video) -> Unit,
  onVideoLongClick: (Video) -> Unit,
  modifier: Modifier = Modifier,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val thumbWidthDp = 128.dp
  val aspect = 16f / 9f
  val thumbWidthPx = with(density) { thumbWidthDp.roundToPx() }
  val thumbHeightPx = (thumbWidthPx / aspect).roundToInt()

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

      videosWithInfo.isEmpty() -> {
        EmptyState(
          icon = Icons.Filled.VideoLibrary,
          title = "No videos in this folder",
          message = "Videos you add to this folder will appear here",
          modifier = Modifier.fillMaxSize(),
        )
      }

      else -> {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(8.dp),
        ) {
          items(
            count = videosWithInfo.size,
            key = { index -> videosWithInfo[index].video.id },
          ) { index ->
            val videoWithInfo = videosWithInfo[index]
            val isRecentlyPlayed = recentlyPlayedFilePath?.let { videoWithInfo.video.path == it } ?: false

            // Prefetch upcoming thumbnails
            androidx.compose.runtime.LaunchedEffect(index) {
              if (index < videosWithInfo.size - 1) {
                val upcomingVideos =
                  videosWithInfo.subList(
                    (index + 1).coerceAtMost(videosWithInfo.size),
                    (index + 11).coerceAtMost(videosWithInfo.size),
                  )
                thumbnailRepository.prefetchThumbnails(upcomingVideos.map { it.video }, thumbWidthPx, thumbHeightPx)
              }
            }

            VideoCard(
              video = videoWithInfo.video,
              progressPercentage = videoWithInfo.progressPercentage,
              isRecentlyPlayed = isRecentlyPlayed,
              isSelected = selectionManager.isSelected(videoWithInfo.video),
              onClick = { onVideoClick(videoWithInfo.video) },
              onLongClick = { onVideoLongClick(videoWithInfo.video) },
              onThumbClick = { onVideoLongClick(videoWithInfo.video) },
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
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()

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
    visibilityToggles =
      listOf(
        VisibilityToggle(
          label = "Full Name",
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
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
