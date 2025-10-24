package app.marlboroadvance.mpvex.ui.browser.videolist

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.marlboroadvance.mpvex.presentation.components.dialogs.MediaInfoDialog
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaInfoData
import app.marlboroadvance.mpvex.utils.media.MediaInfoHelper
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class VideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: VideoListViewModel =
      viewModel(
        key = "VideoListViewModel_$bucketId",
        factory =
          VideoListViewModel.factory(
            context.applicationContext as android.app.Application,
            bucketId,
          ),
      )
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()
    val backstack = LocalBackStack.current
    val browserPreferences = koinInject<BrowserPreferences>()

    // UI State
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }

    // MediaInfo State
    val mediaInfoDialogOpen = remember { mutableStateOf(false) }
    val selectedVideo = remember { mutableStateOf<Video?>(null) }
    val mediaInfoData = remember { mutableStateOf<MediaInfoData?>(null) }
    val mediaInfoLoading = remember { mutableStateOf(false) }
    val mediaInfoError = remember { mutableStateOf<String?>(null) }

    // Sorting
    val videoSortType by browserPreferences.videoSortType.collectAsState()
    val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()
    val sortedVideos =
      remember(videos, videoSortType, videoSortOrder) {
        SortUtils.sortVideos(videos, videoSortType, videoSortOrder)
      }

    val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName

    Scaffold(
      topBar = {
        VideoListTopBar(
          title = displayFolderName,
          onBackClick = backstack::removeLastOrNull,
          onSortClick = { sortDialogOpen.value = true },
        )
      },
    ) { padding ->
      VideoListContent(
        videos = sortedVideos,
        isLoading = isLoading && videos.isEmpty(),
        isRefreshing = isRefreshing,
        recentlyPlayedFilePath = recentlyPlayedFilePath,
        onRefresh = { viewModel.refresh() },
        onVideoClick = { video -> MediaUtils.playFile(video, context) },
        onVideoLongClick = { video ->
          selectedVideo.value = video
          mediaInfoDialogOpen.value = true
          mediaInfoLoading.value = true
          mediaInfoError.value = null
          mediaInfoData.value = null

          coroutineScope.launch {
            MediaInfoHelper.getMediaInfo(context, video.uri, video.displayName)
              .onSuccess { info ->
                mediaInfoData.value = info
                mediaInfoLoading.value = false
              }
              .onFailure { error ->
                mediaInfoError.value = error.message ?: "Unknown error"
                mediaInfoLoading.value = false
              }
          }
        },
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
        onDownload = {
          val video = selectedVideo.value
          if (video != null) {
            coroutineScope.launch {
              try {
                // Generate text output with header/footer for saving
                val result = MediaInfoHelper.generateTextOutput(context, video.uri, video.displayName)

                result.onSuccess { textContent ->
                  val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                  val fileName = "mediainfo_${video.displayName.substringBeforeLast('.')}_$timestamp.txt"

                  // Create temp file in cache directory
                  val cacheDir = context.cacheDir
                  val file = File(cacheDir, fileName)
                  file.writeText(textContent)

                  // Share the file
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                      Intent.EXTRA_STREAM,
                      androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file,
                      ),
                    )
                    putExtra(Intent.EXTRA_SUBJECT, "Media Info - ${video.displayName}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }

                  context.startActivity(Intent.createChooser(shareIntent, "Save Media Info"))
                }.onFailure { e ->
                  e.printStackTrace()
                  Toast.makeText(context, "Failed to generate: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
              }
            }
          }
        },
      )
    }
  }
}

/**
 * Top app bar for the video list screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoListTopBar(
  title: String,
  onBackClick: () -> Unit,
  onSortClick: () -> Unit,
) {
  TopAppBar(
    title = {
      Text(
        title,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    navigationIcon = {
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
@Composable
private fun VideoListContent(
  videos: List<Video>,
  isLoading: Boolean,
  isRefreshing: MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
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
            val video = videos[index]
            val isRecentlyPlayed =
              recentlyPlayedFilePath?.let { filePath ->
                video.path == filePath
              } ?: false

            VideoCard(
              video = video,
              isRecentlyPlayed = isRecentlyPlayed,
              onClick = { onVideoClick(video) },
              onLongClick = { onVideoLongClick(video) },
            )
          }
        }
      }
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
