package app.marlboroadvance.mpvex.ui.browser.filesystem

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * ViewModel for FileSystem Browser - based on Fossify's ItemsFragment logic
 * Handles directory navigation, file loading, sorting, and state management
 */
class FileSystemBrowserViewModel(
  application: Application,
  initialPath: String? = null,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val browserPreferences: BrowserPreferences by inject()
  private val appearancePreferences: app.marlboroadvance.mpvex.preferences.AppearancePreferences by inject()

  // Special marker for "show storage volumes" mode
  // Similar to Fossify's root/home folder detection
  private val STORAGE_ROOTS_MARKER = "__STORAGE_ROOTS__"

  // Home directory - the top-most directory we can navigate to (set once on init)
  // This prevents navigation errors when the app opens to a specific storage volume
  private var homeDirectory: String? = null

  // Current directory path - corresponds to Fossify's currentPath
  // If initialPath is null, we'll determine it after checking storage volumes
  private val _currentPath = MutableStateFlow(initialPath ?: STORAGE_ROOTS_MARKER)
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  // Unsorted items from filesystem scan - before sorting is applied
  // Similar to Fossify's items list before sorting
  private val _unsortedItems = MutableStateFlow<List<FileSystemItem>>(emptyList())

  // Sorted and filtered items ready for display
  // Similar to Fossify's final sorted items list
  private val _items = MutableStateFlow<List<FileSystemItem>>(emptyList())
  val items: StateFlow<List<FileSystemItem>> = _items.asStateFlow()

  // Video playback progress map - similar to Fossify's playback tracking
  private val _videoFilesWithPlayback = MutableStateFlow<Map<Long, Float>>(emptyMap())
  val videoFilesWithPlayback: StateFlow<Map<Long, Float>> = _videoFilesWithPlayback.asStateFlow()

  // Loading state - similar to Fossify's showProgressBar/hideProgressBar
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Error state for displaying error messages
  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  // Breadcrumb components for navigation - similar to Fossify's Breadcrumbs
  private val _breadcrumbs = MutableStateFlow<List<PathComponent>>(emptyList())
  val breadcrumbs: StateFlow<List<PathComponent>> = _breadcrumbs.asStateFlow()

  // Whether we're at the home directory (top-most allowed directory)
  // Similar to Fossify's check for home folder or root
  val isAtRoot: StateFlow<Boolean> =
    MutableStateFlow(initialPath == null).apply {
      viewModelScope.launch {
        _currentPath.collect { path ->
          value = path == STORAGE_ROOTS_MARKER || path == homeDirectory
        }
      }
    }

  // Track if items were deleted/moved leaving folder empty
  private val _itemsWereDeletedOrMoved = MutableStateFlow(false)
  val itemsWereDeletedOrMoved: StateFlow<Boolean> = _itemsWereDeletedOrMoved.asStateFlow()

  // Track previous item count per path to detect if folder became empty
  private val itemCountByPath = mutableMapOf<String, Int>()

  companion object {
    private const val TAG = "FileSystemBrowserVM"

    fun factory(
      application: Application,
      initialPath: String? = null,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FileSystemBrowserViewModel(application, initialPath) as T
    }
  }

  init {
    // If no initial path was specified, check storage volumes and navigate accordingly
    if (initialPath == null) {
      viewModelScope.launch(Dispatchers.IO) {
        val roots = MediaFileRepository.getStorageRoots(getApplication())
        if (roots.size == 1) {
          // Only one storage volume, navigate directly to it and set as home
          val singleRoot = roots.first()
          homeDirectory = singleRoot.path
          Log.d(TAG, "Single storage volume found, setting as home: ${singleRoot.path}")
          _currentPath.value = singleRoot.path
        } else {
          // Multiple roots - home is the storage roots view
          homeDirectory = null
        }
        // If multiple roots or none, stay at STORAGE_ROOTS_MARKER
        loadCurrentDirectory()
      }
    } else {
      // Specific path provided - set it as home directory
      homeDirectory = initialPath
      Log.d(TAG, "Initial path provided, setting as home: $initialPath")
      // Load initial directory - similar to Fossify's openPath() in onCreate
      loadCurrentDirectory()
    }

    // Refresh on global media library changes
    // Similar to Fossify's media scan completion listener
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        Log.d(TAG, "Media library changed, refreshing current directory")
        loadCurrentDirectory()
      }
    }

    // Apply sorting whenever items or sort preferences change
    // Based on Fossify's ChangeSortingDialog callback and sorting logic
    viewModelScope.launch {
      combine(
        _unsortedItems,
        browserPreferences.folderSortType.changes(),
        browserPreferences.folderSortOrder.changes(),
      ) { items, sortType, sortOrder ->
        // Sort using the same logic as Fossify's FileDirItem.sort()
        SortUtils.sortFileSystemItems(items, sortType, sortOrder)
      }.collectLatest { sortedItems ->
        _items.value = sortedItems
        Log.d(TAG, "Items sorted: ${sortedItems.size} items")
      }
    }
  }

  /**
   * Refresh current directory
   * Equivalent to Fossify's refreshFragment() callback
   */
  override fun refresh() {
    Log.d(TAG, "Refreshing current directory: ${_currentPath.value}")
    // Don't reset the flag on refresh, only on navigation
    loadCurrentDirectory()
  }

  /**
   * Set flag indicating items were deleted or moved
   */
  fun setItemsWereDeletedOrMoved() {
    _itemsWereDeletedOrMoved.value = true
  }

  /**
   * Delete folders (and their contents)
   * Based on Fossify's deleteFiles() logic with folder support
   */
  fun deleteFolders(folders: List<FileSystemItem.Folder>): Pair<Int, Int> {
    var successCount = 0
    var failureCount = 0

    Log.d(TAG, "Deleting ${folders.size} folders")

    folders.forEach { folder ->
      try {
        val dir = File(folder.path)
        if (dir.exists() && dir.deleteRecursively()) {
          successCount++
          Log.d(TAG, "Successfully deleted folder: ${folder.path}")
        } else {
          failureCount++
          Log.w(TAG, "Failed to delete folder: ${folder.path}")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Exception deleting folder: ${folder.path}", e)
        failureCount++
      }
    }

    // Set flag if any deletions were successful
    if (successCount > 0) {
      _itemsWereDeletedOrMoved.value = true
    }

    Log.d(TAG, "Folder deletion complete: $successCount success, $failureCount failed")
    return Pair(successCount, failureCount)
  }

  /**
   * Delete videos - delegates to base class implementation
   * Similar to Fossify's deleteFiles() for individual files
   */
  override suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    Log.d(TAG, "Deleting ${videos.size} videos")
    val result = super.deleteVideos(videos)

    // Set flag if any deletions were successful
    if (result.first > 0) {
      _itemsWereDeletedOrMoved.value = true
    }

    return result
  }

  /**
   * Rename a video file - delegates to base class implementation
   * Based on Fossify's RenameDialog and file renaming logic
   */
  override suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> {
    Log.d(TAG, "Renaming video ${video.displayName} to $newDisplayName")
    return super.renameVideo(video, newDisplayName)
  }

  /**
   * Load current directory contents
   * Main loading logic based on Fossify's getItems() and getRegularItemsOf()
   */
  private fun loadCurrentDirectory() {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      _error.value = null
      // Don't reset the flag here - let navigation handle it

      try {
        val path = _currentPath.value

        // Special case: Show storage roots at the special marker
        // Similar to Fossify's StoragePickerDialog logic
        if (path == STORAGE_ROOTS_MARKER) {
          Log.d(TAG, "Loading storage roots")
          _breadcrumbs.value = emptyList()
          val roots = MediaFileRepository.getStorageRoots(getApplication())
          _unsortedItems.value = roots
          Log.d(TAG, "Loaded ${roots.size} storage roots")
        } else {
          // Update breadcrumbs for real paths
          // Similar to Fossify's Breadcrumbs.setBreadcrumb()
          _breadcrumbs.value = MediaFileRepository.getPathComponents(path)
          Log.d(TAG, "Breadcrumbs updated: ${_breadcrumbs.value.size} components")

          // Get hidden files preference
          val showHiddenFiles = appearancePreferences.showHiddenFiles.get()

          // Scan directory - equivalent to Fossify's getRegularItemsOf()
          // Always show only videos (showAllFileTypes = false)
          MediaFileRepository
            .scanDirectory(getApplication(), path, showAllFileTypes = false, showHiddenFiles)
            .onSuccess { items ->
              // Get previous count for this path
              val previousCount = itemCountByPath[path] ?: 0

              // Check if folder became empty after having items
              if (previousCount > 0 && items.isEmpty()) {
                _itemsWereDeletedOrMoved.value = true
                Log.d(TAG, "Folder became empty (had $previousCount items before)")
              } else if (items.isNotEmpty()) {
                // Reset flag if folder now has items
                _itemsWereDeletedOrMoved.value = false
              }

              // Update count for this path
              itemCountByPath[path] = items.size

              _unsortedItems.value = items

              val folderCount = items.filterIsInstance<FileSystemItem.Folder>().size
              val videoCount = items.filterIsInstance<FileSystemItem.VideoFile>().size
              Log.d(TAG, "Loaded directory: $path with $folderCount folders, $videoCount videos")

              // Load playback info for videos
              // Similar to Fossify's playback state tracking
              loadPlaybackInfo(items)
            }.onFailure { error ->
              _error.value = error.message
              _unsortedItems.value = emptyList()
              Log.e(TAG, "Error loading directory: $path", error)
            }
        }
      } catch (e: Exception) {
        _error.value = e.message
        _unsortedItems.value = emptyList()
        Log.e(TAG, "Exception loading directory", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Load playback progress information for video files
   * Based on playback state tracking (not directly in Fossify, but similar pattern)
   */
  private fun loadPlaybackInfo(items: List<FileSystemItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      val videoFiles = items.filterIsInstance<FileSystemItem.VideoFile>()
      val playbackMap = mutableMapOf<Long, Float>()

      Log.d(TAG, "Loading playback info for ${videoFiles.size} videos")

      videoFiles.forEach { videoFile ->
        val video = videoFile.video
        val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)

        if (playbackState != null && video.duration > 0) {
          val durationSeconds = video.duration / 1000
          val timeRemaining = playbackState.timeRemaining.toLong()
          val watched = durationSeconds - timeRemaining
          val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)

          // Only show progress for videos that are 1-99% complete
          // Similar to how media players show partial progress
          if (progressValue in 0.01f..0.99f) {
            playbackMap[video.id] = progressValue
          }
        }
      }

      _videoFilesWithPlayback.value = playbackMap
      Log.d(TAG, "Loaded playback info for ${playbackMap.size} videos with progress")
    }
  }
}
