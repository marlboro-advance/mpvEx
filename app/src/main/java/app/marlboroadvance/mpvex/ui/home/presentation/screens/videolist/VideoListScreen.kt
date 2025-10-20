package app.marlboroadvance.mpvex.ui.home.presentation.screens.videolist

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.home.presentation.components.VideoCard
import app.marlboroadvance.mpvex.ui.home.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.ui.home.utils.MediaUtils
import app.marlboroadvance.mpvex.ui.home.utils.SortUtils
import app.marlboroadvance.mpvex.ui.utils.DeviceUtils
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

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
    val backstack = LocalBackStack.current
    val isTV = DeviceUtils.isAndroidTV(context)
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
    val sortType = remember { mutableStateOf("Title") }
    val sortOrderAsc = remember { mutableStateOf(true) }
    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullRefreshState(
      isRefreshing.value,
      {
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

    LaunchedEffect(Unit) {
      val prefs = context.getSharedPreferences("video_list_prefs", Context.MODE_PRIVATE)
      sortType.value = prefs.getString("sort_type", "Title") ?: "Title"
      sortOrderAsc.value = prefs.getBoolean("sort_order_asc", true)
    }

    LaunchedEffect(sortType.value, sortOrderAsc.value) {
      val prefs = context.getSharedPreferences("video_list_prefs", Context.MODE_PRIVATE)
      prefs.edit {
        putString("sort_type", sortType.value)
        putBoolean("sort_order_asc", sortOrderAsc.value)
      }
    }

    val sortedVideos = remember(videos, sortType.value, sortOrderAsc.value) {
      SortUtils.sortVideos(videos, sortType.value, sortOrderAsc.value)
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            val displayFolderName = videos.firstOrNull()?.bucketDisplayName ?: folderName
            Text(
              displayFolderName,
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            if (!isTV) {
              IconButton(
                onClick = backstack::removeLastOrNull,
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
              onClick = { sortDialogOpen.value = true },
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
      },
    ) { padding ->
      Box(
        Modifier
          .fillMaxSize()
          .then(if (!isTV) Modifier.pullRefresh(pullRefreshState) else Modifier)
          .padding(padding),
      ) {
        if (isLoading && videos.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        } else if (sortedVideos.isEmpty()) {
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
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
          ) {
            items(sortedVideos.size) { index ->
              val video = sortedVideos[index]
              VideoCard(
                video = video,
                onClick = {
                  MediaUtils.playFile(video.uri.toString(), context)
                },
              )
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

      SortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        title = "Sort Videos",
        sortType = sortType.value,
        onSortTypeChange = { sortType.value = it },
        sortOrderAsc = sortOrderAsc.value,
        onSortOrderChange = { sortOrderAsc.value = it },
        types = listOf("Title", "Duration", "Date", "Size"),
        icons = listOf(
          Icons.Filled.Title,
          Icons.Filled.AccessTime,
          Icons.Filled.CalendarToday,
          Icons.Filled.SwapVert,
        ),
        getLabelForType = { type, _ ->
          when (type) {
            "Title" -> Pair("A-Z", "Z-A")
            "Duration" -> Pair("Shortest", "Longest")
            "Date" -> Pair("Oldest", "Newest")
            "Size" -> Pair("Smallest", "Biggest")
            else -> Pair("Asc", "Desc")
          }
        },
      )
    }
  }
}
