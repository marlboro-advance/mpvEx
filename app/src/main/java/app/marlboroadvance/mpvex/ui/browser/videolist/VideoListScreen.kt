package app.marlboroadvance.mpvex.ui.browser.videolist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.VideoSortType
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.cards.VideoCard
import app.marlboroadvance.mpvex.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.device.TVUtils
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class VideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {

  @OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class,
  )
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val viewModel: VideoListViewModel = viewModel(
      key = "VideoListViewModel_$bucketId",
      factory = VideoListViewModel.factory(
        context.applicationContext as android.app.Application,
        bucketId,
        folderName,
      ),
    )
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()

    // UI State
    val isTV = TVUtils.isAndroidTV(context)
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }

    // Sorting
    val videoSortType by browserPreferences.videoSortType.collectAsState()
    val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()
    val sortedVideos = remember(videos, videoSortType, videoSortOrder) {
      SortUtils.sortVideos(videos, videoSortType, videoSortOrder)
    }

    // Pull to refresh
    val pullRefreshState = rememberPullRefreshState(
      refreshing = isRefreshing.value,
      onRefresh = {
        isRefreshing.value = true
        coroutineScope.launch {
          viewModel.refresh()
          delay(800)
          isRefreshing.value = false
        }
      },
      refreshingOffset = 80.dp,
      refreshThreshold = 72.dp,
    )

    val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName

    Scaffold(
      topBar = {
        VideoListTopBar(
          title = displayFolderName,
          isTV = isTV,
          onBackClick = backstack::removeLastOrNull,
          onSortClick = { sortDialogOpen.value = true },
        )
      },
    ) { padding ->
      VideoListContent(
        videos = sortedVideos,
        isLoading = isLoading && videos.isEmpty(),
        isTV = isTV,
        isRefreshing = isRefreshing,
        pullRefreshState = pullRefreshState,
        onVideoClick = { video -> MediaUtils.playFile(video.uri.toString(), context) },
        modifier = Modifier.padding(padding),
      )

      // Dialogs
      VideoSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        onSortTypeChange = { browserPreferences.videoSortType.set(it) },
        onSortOrderChange = { browserPreferences.videoSortOrder.set(it) },
      )
    }
  }
}

/**
 * Top app bar for the video list screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListTopBar(
  title: String,
  isTV: Boolean,
  onBackClick: () -> Unit,
  onSortClick: () -> Unit,
) {
  TopAppBar(
    title = {
      Text(
        title,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    navigationIcon = {
      if (!isTV) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.padding(horizontal = 4.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(28.dp),
          )
        }
      }
    },
    actions = {
      IconButton(
        onClick = onSortClick,
        modifier = Modifier.padding(horizontal = 4.dp),
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Sort,
          contentDescription = "Sort",
          modifier = Modifier.size(28.dp),
        )
      }
    },
  )
}

/**
 * Main content showing the list of videos
 */
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun VideoListContent(
  videos: List<Video>,
  isLoading: Boolean,
  isTV: Boolean,
  isRefreshing: MutableState<Boolean>,
  pullRefreshState: androidx.compose.material.pullrefresh.PullRefreshState,
  onVideoClick: (Video) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier
      .fillMaxSize()
      .then(if (!isTV) Modifier.pullRefresh(pullRefreshState) else Modifier),
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
            "No videos found in this folder.",
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
            VideoCard(
              video = videos[index],
              onClick = { onVideoClick(videos[index]) },
            )
          }
        }
      }
    }

    if (!isTV) {
      PullRefreshIndicator(
        isRefreshing.value,
        pullRefreshState,
        Modifier.align(Alignment.TopCenter),
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/**
 * Simplified sort dialog for videos
 */
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
    types = listOf(
      VideoSortType.Title.displayName,
      VideoSortType.Duration.displayName,
      VideoSortType.Date.displayName,
      VideoSortType.Size.displayName,
    ),
    icons = listOf(
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
