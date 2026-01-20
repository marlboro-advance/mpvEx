package app.marlboroadvance.mpvex.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.fab.MediaActionFab
import app.marlboroadvance.mpvex.ui.browser.fab.PlaylistActionFab
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlayLinkSheet
import app.marlboroadvance.mpvex.ui.compose.LocalLazyGridState
import app.marlboroadvance.mpvex.ui.compose.LocalLazyListState
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import app.marlboroadvance.mpvex.preferences.preference.collectAsState

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0
  
  // Shared state that can be updated by FileSystemBrowserScreen
  @Volatile
  private var isInSelectionModeShared: Boolean = false  // Controls FAB visibility
  
  @Volatile
  private var shouldHideNavigationBar: Boolean = false  // Controls navigation bar visibility
  
  @Volatile
  private var sharedVideoSelectionManager: Any? = null
  
  // Check if the selection contains only videos and update navigation bar visibility accordingly
  @Volatile
  private var onlyVideosSelected: Boolean = false
  
  // Track when permission denied screen is showing to hide FAB
  @Volatile
  private var isPermissionDenied: Boolean = false
  
  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    this.isInSelectionModeShared = isInSelectionMode
    this.onlyVideosSelected = isOnlyVideosSelected
    this.sharedVideoSelectionManager = selectionManager
    
    // Only hide navigation bar when videos are selected AND in selection mode
    // This fixes the issue where bottom bar disappears when only videos are selected
    this.shouldHideNavigationBar = isInSelectionMode && isOnlyVideosSelected
  }
  
  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    this.isPermissionDenied = isDenied
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember {
      mutableIntStateOf(persistentSelectedTab)
    }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appearancePreferences = koinInject<app.marlboroadvance.mpvex.preferences.AppearancePreferences>()
    val useFloatingNavigation by appearancePreferences.useFloatingNavigation.collectAsState()
    
    // Check storage permission status directly in MainScreen for FAB visibility
    val hasStoragePermission = remember {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
      } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          app.marlboroadvance.mpvex.utils.permission.PermissionUtils.getStoragePermission()
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      }
    }
    
    // Track permission changes when app resumes
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
      val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
          permissionCheckTrigger++
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }
    
    // Recheck permission when trigger changes
    val currentHasPermission = remember(permissionCheckTrigger) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
      } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          app.marlboroadvance.mpvex.utils.permission.PermissionUtils.getStoragePermission()
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      }
    }
    
    // Create separate list and grid states for each tab to track scroll position
    val foldersListState = rememberLazyListState()
    val foldersGridState = rememberLazyGridState()
    val recentListState = rememberLazyListState()
    val recentGridState = rememberLazyGridState()
    val playlistListState = rememberLazyListState()
    val playlistGridState = rememberLazyGridState()
    val networkListState = rememberLazyListState()
    val networkGridState = rememberLazyGridState()
    
    // Current active list state based on selected tab
    val currentListState = when (selectedTab) {
        0 -> foldersListState
        1 -> recentListState
        2 -> playlistListState
        3 -> networkListState
        else -> foldersListState
    }
    
    // Current active grid state based on selected tab
    val currentGridState = when (selectedTab) {
        0 -> foldersGridState
        1 -> recentGridState
        2 -> playlistGridState
        3 -> networkGridState
        else -> foldersGridState
    }
    
    val playlistRepository = koinInject<PlaylistRepository>()
    
    // FAB state
    var fabMenuExpanded by remember { mutableStateOf(false) }
    val showLinkDialog = remember { mutableStateOf(false) }
    
    // Playlist FAB state
    val showCreatePlaylistDialog = remember { mutableStateOf(false) }
    val showM3UPlaylistDialog = remember { mutableStateOf(false) }
    
    // File picker
    val filePicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      uri?.let {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        }
        MediaUtils.playFile(it.toString(), context, "open_file")
      }
    }
    
    // Shared state (across the app)
    val isInSelectionMode = remember { mutableStateOf(isInSelectionModeShared) }
    val hideNavigationBar = remember { mutableStateOf(shouldHideNavigationBar) }
    val videoSelectionManager = remember { mutableStateOf<SelectionManager<*, *>?>(sharedVideoSelectionManager as? SelectionManager<*, *>) }
    
    // Check for state changes to ensure UI updates
    LaunchedEffect(Unit) {
      while (true) {
        // Update FAB visibility state
        if (isInSelectionMode.value != isInSelectionModeShared) {
          isInSelectionMode.value = isInSelectionModeShared
          android.util.Log.d("MainScreen", "Selection mode changed to: $isInSelectionModeShared")
        }
        
        // Update navigation bar visibility state - now considers if only videos are selected
        if (hideNavigationBar.value != shouldHideNavigationBar) {
          hideNavigationBar.value = shouldHideNavigationBar
          android.util.Log.d("MainScreen", "Navigation bar visibility changed to: ${!shouldHideNavigationBar}, onlyVideosSelected: $onlyVideosSelected")
        }
        
        // Update selection manager
        val currentManager = sharedVideoSelectionManager as? SelectionManager<*, *>
        if (videoSelectionManager.value != currentManager) {
          videoSelectionManager.value = currentManager
        }
        
        // Minimal delay for polling
        delay(16) // Roughly matches a frame at 60fps for responsive updates
      }
    }
    
    // Update persistent state whenever tab changes
    LaunchedEffect(selectedTab) {
      android.util.Log.d("MainScreen", "selectedTab changed to: $selectedTab (was ${persistentSelectedTab})")
      persistentSelectedTab = selectedTab
    }

    // Define items for the navigation bar
    val items = listOf("Folders", "Recent", "Playlist", "Network")
    val selectedIcons = listOf(
        Icons.Filled.Folder, 
        Icons.Filled.History,
        Icons.AutoMirrored.Filled.PlaylistPlay,
        Icons.Filled.Wifi
    )
    val unselectedIcons = listOf(
        Icons.Outlined.Folder, 
        Icons.Outlined.History,
        Icons.AutoMirrored.Outlined.PlaylistPlay,
        Icons.Outlined.Wifi
    )

    // Use Scaffold only for bottom bar, let nested screens handle their own top bars and padding
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
          // Navigation Bar Logic
          AnimatedVisibility(
            visible = !hideNavigationBar.value,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
          ) {
            if (useFloatingNavigation) {
              // Floating Navigation Bar - styled like Pixel Player
              // Get system navigation bar inset for proper spacing
              val systemNavBarInset = WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
              
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = systemNavBarInset) // Space above system nav bar
              ) {
                // Floating Surface with rounded corners and shadow
                androidx.compose.material3.Surface(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(horizontal = 14.dp), // Horizontal padding for floating effect
                  color = androidx.compose.material3.NavigationBarDefaults.containerColor,
                  shape = RoundedCornerShape(26.dp), // Rounded corners for pill shape
                  shadowElevation = 3.dp, // Shadow for floating effect
                  tonalElevation = 2.dp
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    items.forEachIndexed { index, item ->
                      val isSelected = selectedTab == index
                      val selectedIcon = selectedIcons[index]
                      val unselectedIcon = unselectedIcons[index]
                      
                      CustomNavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                          Icon(
                            imageVector = unselectedIcon,
                            contentDescription = item
                          )
                        },
                        selectedIcon = {
                          Icon(
                            imageVector = selectedIcon,
                            contentDescription = item
                          )
                        },
                        label = { Text(item) },
                        contentDescription = item
                      )
                    }
                  }
                }
              }
            } else {
              // Standard Navigation Bar
              NavigationBar(
                modifier = Modifier.fillMaxWidth(),
              ) {
                items.forEachIndexed { index, item ->
                  val isSelected = selectedTab == index
                  val selectedIcon = selectedIcons[index]
                  val unselectedIcon = unselectedIcons[index]
                  
                  NavigationBarItem(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    icon = {
                      Icon(
                        imageVector = if (isSelected) selectedIcon else unselectedIcon,
                        contentDescription = item
                      )
                    },
                    label = { Text(item) },
                    alwaysShowLabel = true
                  )
                }
              }
            }
          }
      },
      floatingActionButton = {
        // Only show FAB when not in selection mode, not on Network tab (index 3), and permission is granted
        // For Folders tab (0), also check storage permission directly
        val shouldShowFab = !isInSelectionMode.value && selectedTab != 3 && 
                           (selectedTab != 0 || currentHasPermission)
        if (shouldShowFab) {
          AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            // Show different FAB content based on selected tab
            when (selectedTab) {
              // Folders tab (0)
              0 -> {
                MediaActionFab(
                  listState = currentListState,
                  gridState = currentGridState, // Add grid state
                  hasRecentlyPlayed = true,
                  enableRecentlyPlayed = true,
                  onOpenFile = { 
                    // Launch file picker to select a file
                    filePicker.launch(arrayOf("*/*"))
                  },
                  onPlayRecentlyPlayed = {
                    coroutineScope.launch {
                      val lastPlayedEntity = app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps.getLastPlayedEntity()
                      if (lastPlayedEntity != null) {
                        // Just play the single video
                        MediaUtils.playFile(
                          lastPlayedEntity.filePath,
                          context,
                          "recently_played_button",
                        )
                      }
                    }
                  },
                  onPlayLink = { showLinkDialog.value = true },
                  expanded = fabMenuExpanded,
                  onExpandedChange = { fabMenuExpanded = it },
                  modifier = Modifier,
                )
              }
              
              // Recent tab (1)
              1 -> {
                MediaActionFab(
                  listState = currentListState,
                  gridState = currentGridState, // Add grid state
                  hasRecentlyPlayed = true,
                  enableRecentlyPlayed = true,
                  onOpenFile = { 
                    // Launch file picker to select a file
                    filePicker.launch(arrayOf("*/*"))
                  },
                  onPlayRecentlyPlayed = {
                    coroutineScope.launch {
                      val lastPlayedEntity = app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps.getLastPlayedEntity()
                      if (lastPlayedEntity != null) {
                        // Just play the single video
                        MediaUtils.playFile(
                          lastPlayedEntity.filePath,
                          context,
                          "recently_played_button",
                        )
                      }
                    }
                  },
                  onPlayLink = { showLinkDialog.value = true },
                  expanded = fabMenuExpanded,
                  onExpandedChange = { fabMenuExpanded = it },
                  modifier = Modifier,
                )
              }
              
              // Playlist tab (2)
              2 -> {
                PlaylistActionFab(
                  listState = currentListState,
                  gridState = currentGridState, // Add grid state
                  onCreatePlaylist = { showCreatePlaylistDialog.value = true },
                  onAddM3UPlaylist = { showM3UPlaylistDialog.value = true },
                  expanded = fabMenuExpanded,
                  onExpandedChange = { fabMenuExpanded = it },
                  modifier = Modifier,
                )
              }
              
              // Network tab (3) - No FAB for this tab
              else -> { /* No FAB for Network tab */ }
            }
          }
        }
      },
    ) { paddingValues ->
      // Each screen handles its own bottom padding for the navigation bar
      Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
          0 -> {
            CompositionLocalProvider(
              LocalLazyListState provides foldersListState,
              LocalLazyGridState provides foldersGridState
            ) {
              FolderListScreen.Content()
            }
          }
          1 -> {
            CompositionLocalProvider(
              LocalLazyListState provides recentListState,
              LocalLazyGridState provides recentGridState
            ) {
              RecentlyPlayedScreen.Content()
            }
          }
          2 -> {
            CompositionLocalProvider(
              LocalLazyListState provides playlistListState,
              LocalLazyGridState provides playlistGridState
            ) {
              PlaylistScreen.Content()
            }
          }
          3 -> {
            CompositionLocalProvider(
              LocalLazyListState provides networkListState,
              LocalLazyGridState provides networkGridState
            ) {
              NetworkStreamingScreen.Content()
            }
          }
        }
        
        // Dialog for play link functionality
        PlayLinkSheet(
          isOpen = showLinkDialog.value,
          onDismiss = { showLinkDialog.value = false },
          onPlayLink = { url -> 
            MediaUtils.playFile(url, context, "play_link") 
          },
        )
        
        // Dialog for playlist creation
        if (showCreatePlaylistDialog.value) {
          CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog.value = false },
            onConfirm = { name ->
              coroutineScope.launch {
                try {
                  playlistRepository.createPlaylist(name)
                  android.widget.Toast.makeText(
                    context,
                    "Playlist created successfully",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                } catch (e: Exception) {
                  android.widget.Toast.makeText(
                    context,
                    "Failed to create playlist: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                  ).show()
                }
                showCreatePlaylistDialog.value = false
              }
            }
          )
        }
        
        // Dialog for M3U playlist creation
        if (showM3UPlaylistDialog.value) {
          AddM3UPlaylistDialog(
            onDismiss = { showM3UPlaylistDialog.value = false },
            onConfirm = { url ->
              coroutineScope.launch {
                val result = playlistRepository.createM3UPlaylist(url)
                result.onSuccess {
                  android.widget.Toast.makeText(
                    context,
                    "M3U Playlist added successfully",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                }.onFailure { error ->
                  android.widget.Toast.makeText(
                    context,
                    "Failed to add M3U playlist: ${error.message}",
                    android.widget.Toast.LENGTH_LONG
                  ).show()
                }
                showM3UPlaylistDialog.value = false
              }
            },
            onPickLocalFile = { uri ->
              coroutineScope.launch {
                val result = playlistRepository.createM3UPlaylistFromFile(context, uri)
                result.onSuccess {
                  android.widget.Toast.makeText(
                    context,
                    "M3U Playlist added successfully",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                }.onFailure { error ->
                  android.widget.Toast.makeText(
                    context,
                    "Failed to add M3U playlist: ${error.message}",
                    android.widget.Toast.LENGTH_LONG
                  ).show()
                }
                showM3UPlaylistDialog.value = false
              }
            }
          )
        }
      }
    }
  }

  // Create Playlist Dialog
  @Composable
  private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
  ) {
    var playlistName by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Create Playlist") },
      text = {
        androidx.compose.material3.OutlinedTextField(
          value = playlistName,
          onValueChange = { playlistName = it },
          label = { Text("Playlist Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            if (playlistName.isNotBlank()) {
              onConfirm(playlistName)
            }
          },
          enabled = playlistName.isNotBlank(),
        ) {
          Text("Create")
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      },
    )
  }

  // M3U Playlist Dialog
  @Composable
  private fun AddM3UPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onPickLocalFile: (android.net.Uri) -> Unit,
  ) {
    var playlistUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
      uri?.let {
        isLoading = true
        onPickLocalFile(it)
      }
    }

    androidx.compose.material3.AlertDialog(
      onDismissRequest = if (isLoading) {
        {}
      } else {
        onDismiss
      },
      title = { Text("Add M3U Playlist") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "Enter the URL of an M3U playlist file, or choose a local file",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          OutlinedTextField(
            value = playlistUrl,
            onValueChange = { playlistUrl = it },
            label = { Text("Playlist URL") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
          )
          
          // Divider with "OR" text
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
              text = "OR",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
          }
          
          // Local file picker button
          OutlinedButton(
            onClick = {
              filePickerLauncher.launch("*/*")
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose Local M3U File")
          }
          
          if (isLoading) {
            Box(
              modifier = Modifier.fillMaxWidth(),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
          }
        }
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            if (playlistUrl.isNotBlank()) {
              isLoading = true
              onConfirm(playlistUrl.trim())
            }
          },
          enabled = playlistUrl.isNotBlank() && !isLoading,
        ) {
          Text("Add from URL")
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(
          onClick = onDismiss,
          enabled = !isLoading
        ) {
          Text("Cancel")
        }
      },
    )
  }
}

// Custom Navigation Bar Item from PixelPlayer
@Composable
private fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Animated colors
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = tween(durationMillis = 150),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = tween(durationMillis = 150),
        label = "textColor"
    )

    val showLabel = label != null && (alwaysShowLabel || selected)

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = { if (!selected) onClick() else null },
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics {
                 if (contentDescription != null) {
                     this.contentDescription = contentDescription
                 }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp, 32.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = tween(100)) + 
                        scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                exit = fadeOut(animationSpec = tween(100)) +
                        scaleOut(animationSpec = tween(100, easing = EaseInQuart))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .background(
                            color = indicatorColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp, 24.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            if (showLabel) {
                                // Semantics handled at top level
                            }
                        }
                    ) {
                        if (selected) selectedIcon() else icon()
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = tween(200, delayMillis = 50)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.padding(top = 4.dp)) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                ) {
                    label?.invoke()
                }
            }
        }
    }
}

private val EaseInQuart = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)
