package app.marlboroadvance.mpvex.ui.home.presentation.screens.folderlist

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.home.presentation.components.EmptyState
import app.marlboroadvance.mpvex.ui.home.presentation.components.FolderCard
import app.marlboroadvance.mpvex.ui.home.presentation.components.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.home.presentation.components.sort.SortDialog
import app.marlboroadvance.mpvex.ui.home.presentation.dialogs.PlayLinkDialog
import app.marlboroadvance.mpvex.ui.home.presentation.screens.videolist.VideoListScreen
import app.marlboroadvance.mpvex.ui.home.utils.MediaUtils
import app.marlboroadvance.mpvex.ui.home.utils.SortUtils
import app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.ui.utils.DeviceUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object FolderListScreen : Screen {

  @OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material.ExperimentalMaterialApi::class,
  )
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val isTV = DeviceUtils.isAndroidTV(context)
    val isRefreshing = remember { mutableStateOf(false) }
    val viewModel: FolderListViewModel =
      viewModel(factory = FolderListViewModel.factory(context.applicationContext as android.app.Application))
    val videoFolders by viewModel.videoFolders.collectAsState()
    val showLinkDialog = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }
    val sortType = remember { mutableStateOf("Date") }
    val sortOrderAsc = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      uri?.let { MediaUtils.playFile(it.toString(), context) }
    }

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

    val permissionState = rememberPermissionState(
      if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_VIDEO
      } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
      },
    )

    LaunchedEffect(Unit) {
      val prefs = context.getSharedPreferences("folder_list_prefs", android.content.Context.MODE_PRIVATE)
      sortType.value = prefs.getString("sort_type", "Date") ?: "Date"
      sortOrderAsc.value = prefs.getBoolean("sort_order_asc", false)
    }

    LaunchedEffect(sortType.value, sortOrderAsc.value) {
      val prefs = context.getSharedPreferences("folder_list_prefs", android.content.Context.MODE_PRIVATE)
      prefs.edit {
        putString("sort_type", sortType.value)
        putBoolean("sort_order_asc", sortOrderAsc.value)
      }
    }

    LaunchedEffect(permissionState.status) {
      if (permissionState.status == PermissionStatus.Granted) {
        viewModel.refresh()
      }
    }

    val sortedFolders = remember(videoFolders, sortType.value, sortOrderAsc.value) {
      SortUtils.sortFolders(videoFolders, sortType.value, sortOrderAsc.value)
    }

    val listState = rememberLazyListState()
    val fabVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val focusRequester = remember { FocusRequester() }

    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Check if there's a recently played file - use State for reactivity
    var hasRecentlyPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      hasRecentlyPlayed = MediaUtils.hasRecentlyPlayedFile(context)
    }

    // Update recently played status when fab menu is expanded
    LaunchedEffect(fabMenuExpanded) {
      if (fabMenuExpanded) {
        hasRecentlyPlayed = MediaUtils.hasRecentlyPlayedFile(context)
      }
    }

    // Collapse FAB menu when scrolling
    LaunchedEffect(listState.isScrollInProgress) {
      if (listState.isScrollInProgress && fabMenuExpanded) {
        fabMenuExpanded = false
      }
    }

    val items = remember(isTV, hasRecentlyPlayed) {
      buildList {
        // Don't show "Open File" on TV since there's no file picker
        if (!isTV) {
          add(Icons.Filled.FolderOpen to "Open File")
        }
        add(Icons.Filled.History to "Recently Played")
        add(Icons.Filled.AddLink to "Play Link")
      }
    }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              stringResource(app.marlboroadvance.mpvex.R.string.app_name),
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 8.dp),
            )
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
            IconButton(
              onClick = { backstack.add(PreferencesScreen) },
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
      },
      floatingActionButton = {
        FloatingActionButtonMenu(
          modifier = Modifier,
          expanded = fabMenuExpanded,
          button = {
            ToggleFloatingActionButton(
              modifier =
              Modifier
                .semantics {
                  traversalIndex = -1f
                  stateDescription = if (fabMenuExpanded) "Expanded" else "Collapsed"
                  contentDescription = "Toggle menu"
                }
                .animateFloatingActionButton(
                  visible = fabVisible || fabMenuExpanded,
                  alignment = Alignment.BottomEnd,
                )
                .focusRequester(focusRequester),
              checked = fabMenuExpanded,
              onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
              containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
            ) {
              val imageVector by remember {
                derivedStateOf {
                  if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.PlayArrow
                }
              }
              Icon(
                painter = rememberVectorPainter(imageVector),
                contentDescription = null,
                modifier = Modifier
                  .animateIcon(
                    checkedProgress = { checkedProgress },
                    size = ToggleFloatingActionButtonDefaults.iconSize(
                      initialSize = 40.dp,
                      finalSize = 24.dp,
                    ),
                  ),
              )
            }
          },
        ) {
          items.forEachIndexed { i, item ->
            FloatingActionButtonMenuItem(
              modifier =
              Modifier
                .semantics {
                  isTraversalGroup = true
                  // Add a custom a11y action to allow closing the menu when focusing
                  // the last menu item, since the close button comes before the first
                  // menu item in the traversal order.
                  if (i == items.size - 1) {
                    customActions =
                      listOf(
                        CustomAccessibilityAction(
                          label = "Close menu",
                          action = {
                            fabMenuExpanded = false
                            true
                          },
                        )
                      )
                  }
                }
                .then(
                  if (i == 0) {
                    Modifier.onKeyEvent {
                      // Navigating back from the first item should go back to the
                      // FAB menu button.
                      if (
                        it.type == KeyEventType.KeyDown &&
                        (
                          it.key == Key.DirectionUp ||
                            (it.isShiftPressed && it.key == Key.Tab)
                          )
                      ) {
                        focusRequester.requestFocus()
                        return@onKeyEvent true
                      }
                      return@onKeyEvent false
                    }
                  } else {
                    Modifier
                  },
                ),
              onClick = {
                fabMenuExpanded = false
                when (item.second) {
                  "Open File" -> filePicker.launch(arrayOf("*/*"))
                  "Recently Played" -> {
                    // Play recently played file
                    if (hasRecentlyPlayed) {
                      coroutineScope.launch {
                        MediaUtils.getRecentlyPlayedFile(context)?.let { filepath ->
                          MediaUtils.playFile(filepath, context)
                        }
                      }
                    }
                  }
                  "Play Link" -> showLinkDialog.value = true
                }
              },
              icon = {
                Icon(
                  item.first,
                  contentDescription = null,
                  modifier = if (item.second == "Recently Played" && !hasRecentlyPlayed) Modifier.alpha(0.5f) else Modifier,
                )
              },
              text = { Text(text = item.second) },
            )
          }
        }
      },
    ) { padding ->
      when (permissionState.status) {
        PermissionStatus.Granted -> {
          Box(
            Modifier
              .fillMaxWidth()
              .then(if (!isTV) Modifier.pullRefresh(pullRefreshState) else Modifier)
              .padding(padding),
          ) {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxWidth(),
              contentPadding = PaddingValues(8.dp),
            ) {
              items(sortedFolders.size) { index ->
                val folder = sortedFolders[index]
                FolderCard(
                  folder = folder,
                  onClick = {
                    backstack.add(VideoListScreen(folder.bucketId, folder.name))
                    fabMenuExpanded = false
                  },
                )
              }

              if (sortedFolders.isEmpty()) {
                item {
                  EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "No video folders found",
                    message = if (isTV)
                      "Android TV detected. Try:\n• Use 'Play Link' button to play from URL\n• Place videos in /Movies or /Download folders\n• Connect USB drive with videos"
                    else
                      "Add some video files to your device to see them here",
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
        }

        is PermissionStatus.Denied -> {
          PermissionDeniedState(
            onRequestPermission = { permissionState.launchPermissionRequest() },
            modifier = Modifier.padding(padding),
          )
        }
      }

      PlayLinkDialog(
        isOpen = showLinkDialog.value,
        onDismiss = { showLinkDialog.value = false },
        onPlayLink = { url -> MediaUtils.playFile(url, context) },
      )

      SortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        title = "Sort Folders",
        sortType = sortType.value,
        onSortTypeChange = { sortType.value = it },
        sortOrderAsc = sortOrderAsc.value,
        onSortOrderChange = { sortOrderAsc.value = it },
        types = listOf("Title", "Date", "Size"),
        icons = listOf(
          Icons.Filled.Title,
          Icons.Filled.CalendarToday,
          Icons.Filled.SwapVert,
        ),
        getLabelForType = { type, _ ->
          when (type) {
            "Title" -> Pair("A-Z", "Z-A")
            "Date" -> Pair("Oldest", "Newest")
            "Size" -> Pair("Smallest", "Largest")
            else -> Pair("Asc", "Desc")
          }
        },
      )
    }
  }
}
