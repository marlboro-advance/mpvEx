package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.cards.FolderCard
import app.marlboroadvance.mpvex.presentation.components.dialogs.PlayLinkDialog
import app.marlboroadvance.mpvex.presentation.components.fab.MediaActionFab
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.presentation.components.states.EmptyState
import app.marlboroadvance.mpvex.presentation.components.states.PermissionDeniedState
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen
import app.marlboroadvance.mpvex.ui.player.PlayerScreen
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object FolderListScreen : Screen {

  @OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
  )
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val viewModel: FolderListViewModel =
      viewModel(factory = FolderListViewModel.factory(context.applicationContext as android.app.Application))
    val videoFolders by viewModel.videoFolders.collectAsState()
    val backstack = LocalBackStack.current
    val coroutineScope = rememberCoroutineScope()
    val browserPreferences = koinInject<BrowserPreferences>()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // UI State
    val listState = rememberLazyListState()
    val isRefreshing = remember { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var hasRecentlyPlayed by remember { mutableStateOf(false) }
    val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()

    // Sorting
    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
    val sortedFolders = remember(videoFolders, folderSortType, folderSortOrder) {
      SortUtils.sortFolders(videoFolders, folderSortType, folderSortOrder)
    }

    // Permissions
    val permissionState = PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() },
    )

    // File picker
    val filePicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      uri?.let {
        // Persist read permission so we can reopen later (e.g., recently played)
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        backstack.add(PlayerScreen(it.toString(), launchSource = "open_file"))
      }
    }

    // Effects
    LaunchedEffect(Unit) {
      hasRecentlyPlayed = MediaUtils.hasRecentlyPlayedFile()
    }

    // Refresh when returning to the screen (STARTED state)
    LaunchedEffect(lifecycleOwner) {
      lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.refresh()
      }
    }

    LaunchedEffect(fabMenuExpanded) {
      if (fabMenuExpanded) {
        hasRecentlyPlayed = MediaUtils.hasRecentlyPlayedFile()
      }
    }

    Scaffold(
      topBar = {
        FolderListTopBar(
          onSortClick = { sortDialogOpen.value = true },
          onSettingsClick = { backstack.add(PreferencesScreen) },
        )
      },
      floatingActionButton = {
        MediaActionFab(
          listState = listState,
          hasRecentlyPlayed = hasRecentlyPlayed,
          onOpenFile = { filePicker.launch(arrayOf("video/*")) },
          onPlayRecentlyPlayed = {
            coroutineScope.launch {
              MediaUtils.getRecentlyPlayedFile()
                ?.let { backstack.add(PlayerScreen(it, launchSource = "recently_played_button")) }
            }
          },
          onPlayLink = { showLinkDialog.value = true },
          expanded = fabMenuExpanded,
          onExpandedChange = { fabMenuExpanded = it },
        )
      },
    ) { padding ->
      when (permissionState.status) {
        PermissionStatus.Granted -> {
          FolderListContent(
            folders = sortedFolders,
            listState = listState,
            isRefreshing = isRefreshing,
            recentlyPlayedFilePath = recentlyPlayedFilePath,
            onRefresh = { viewModel.refresh() },
            onFolderClick = { folder ->
              fabMenuExpanded = false
              backstack.add(VideoListScreen(folder.bucketId, folder.name))
            },
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
      PlayLinkDialog(
        isOpen = showLinkDialog.value,
        onDismiss = { showLinkDialog.value = false },
        onPlayLink = { url -> backstack.add(PlayerScreen(url, launchSource = "play_link")) },
      )

      FolderSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = folderSortType,
        sortOrder = folderSortOrder,
        onSortTypeChange = { browserPreferences.folderSortType.set(it) },
        onSortOrderChange = { browserPreferences.folderSortOrder.set(it) },
      )
    }
  }
}

/**
 * Top app bar for the folder list screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderListTopBar(
  onSortClick: () -> Unit,
  onSettingsClick: () -> Unit,
) {
  TopAppBar(
    title = {
      Text(
        stringResource(app.marlboroadvance.mpvex.R.string.app_name),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp),
      )
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
      IconButton(
        onClick = onSettingsClick,
        modifier = Modifier.padding(horizontal = 4.dp),
      ) {
        Icon(
          Icons.Filled.Settings,
          contentDescription = "Preferences",
          modifier = Modifier.size(28.dp),
        )
      }
    },
  )
}

/**
 * Main content showing the list of folders
 */
@Composable
private fun FolderListContent(
  folders: List<VideoFolder>,
  listState: LazyListState,
  isRefreshing: MutableState<Boolean>,
  recentlyPlayedFilePath: String?,
  onRefresh: suspend () -> Unit,
  onFolderClick: (VideoFolder) -> Unit,
  modifier: Modifier = Modifier,
) {
  PullRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxWidth(),
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(8.dp),
    ) {
      items(folders) { folder ->
        // Check if this folder contains the recently played video
        val isRecentlyPlayed = recentlyPlayedFilePath?.let { filePath ->
          val file = File(filePath)
          file.parent == folder.path
        } ?: false

        FolderCard(
          folder = folder,
          isRecentlyPlayed = isRecentlyPlayed,
          onClick = { onFolderClick(folder) },
        )
      }

      if (folders.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No video folders found",
            message = "Add some video files to your device to see them here",
          )
        }
      }
    }
  }
}

/**
 * Simplified sort dialog for folders
 */
@Composable
private fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort Folders",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
  )
}
