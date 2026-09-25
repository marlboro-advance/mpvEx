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
import app.marlboroadvance.mpvex.utils.media.MediaIdentifier
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.media.MetadataRetrieval
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import app.marlboroadvance.mpvex.utils.storage.FileTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        // Clear cache when media library changes
        MediaFileRepository.clearCache()
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
    Log.d(TAG, "Hard refreshing current directory: ${_currentPath.value}")
    
    // Set loading state
    _isLoading.value = true
    
    // Clear all caches to force fresh data from filesystem
    MediaFileRepository.clearCache()
    
    // Trigger media scan to ensure MediaStore is up-to-date
    triggerMediaScan()
    
    // Wait for MediaStore to update, then reload
    viewModelScope.launch(Dispatchers.IO) {
      delay(1500) // Give MediaStore time to index
      loadCurrentDirectory()
    }
  }
  
  /**
   * Trigger a media scan for the current directory
   */
  private fun triggerMediaScan() {
    try {
      val path = _currentPath.value
      
      // Skip if we're at storage roots marker
      if (path == STORAGE_ROOTS_MARKER) {
        return
      }
      
      val folder = File(path)
      
      if (folder.exists() && folder.isDirectory) {
        // Scan all video files in the folder
        val videoFiles = folder.listFiles { file ->
          file.isFile && FileTypeUtils.isVideoFile(file)
        }
        
        if (!videoFiles.isNullOrEmpty()) {
          val filePaths = videoFiles.map { it.absolutePath }.toTypedArray()
          
          android.media.MediaScannerConnection.scanFile(
            getApplication(),
            filePaths,
            null, // Let MediaScanner detect MIME types
          ) { scanPath, uri ->
            Log.d(TAG, "Media scan completed for: $scanPath -> $uri")
          }
          
          Log.d(TAG, "Triggered media scan for ${filePaths.size} files in: $path")
        } else {
          Log.d(TAG, "No video files found in folder: $path")
        }
      } else {
        // Fallback to scanning external storage root
        val externalStorage = android.os.Environment.getExternalStorageDirectory()
        android.media.MediaScannerConnection.scanFile(
          getApplication(),
          arrayOf(externalStorage.absolutePath),
          null,
        ) { scanPath, uri ->
          Log.d(TAG, "Media scan completed for: $scanPath -> $uri")
        }
        Log.d(TAG, "Triggered media scan for: ${externalStorage.absolutePath}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to trigger media scan", e)
    }
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
      // Notify that media library has changed
      MediaLibraryEvents.notifyChanged()
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
          // Scan directory - equivalent to Fossify's getRegularItemsOf()
          // Always show only videos (showAllFileTypes = false)
          MediaFileRepository
            .scanDirectory(getApplication(), path, showAllFileTypes = false)
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

              // Enrich videos with metadata if chips are enabled
              val enrichedItems = if (MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)) {
                Log.d(TAG, "Metadata chips enabled, enriching $videoCount videos")
                val videoFiles = items.filterIsInstance<FileSystemItem.VideoFile>()
                val videos = videoFiles.map { it.video }
                val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
                  context = getApplication(),
                  videos = videos,
                  browserPreferences = browserPreferences,
                  metadataCache = metadataCache
                )
                
                // Replace videos in items with enriched versions
                val enrichedVideoMap = enrichedVideos.associateBy { it.id }
                items.map { item ->
                  when (item) {
                    is FileSystemItem.VideoFile -> {
                      val enrichedVideo = enrichedVideoMap[item.video.id]
                      if (enrichedVideo != null) {
                        item.copy(video = enrichedVideo)
                      } else {
                        item
                      }
                    }
                    else -> item
                  }
                }
              } else {
                items
              }

              _unsortedItems.value = enrichedItems

              // Load playback info for videos
              // Similar to Fossify's playback state tracking
              loadPlaybackInfo(enrichedItems)
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
        val playbackState = playbackStateRepository.getVideoDataByTitle(MediaIdentifier.forLocalPath(video.path))
          ?: playbackStateRepository.getVideoDataByTitle(video.displayName)

        if (playbackState != null) {
          val durationSeconds = if (video.duration > 0) {
            video.duration / 1000
          } else {
            (playbackState.lastPosition + playbackState.timeRemaining).toLong()
          }
          if (durationSeconds > 0) {
            val watched = playbackState.lastPosition.toLong()
            val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)

            // Only show progress for videos that are 1-99% complete
            // Similar to how media players show partial progress
            if (progressValue in 0.01f..0.99f) {
              playbackMap[video.id] = progressValue
            }
          }
        }
      }

      _videoFilesWithPlayback.value = playbackMap
      Log.d(TAG, "Loaded playback info for ${playbackMap.size} videos with progress")
    }
  }

  /**
   * Searches for files/folders matching query within current directory or storage roots
   */
  suspend fun search(context: android.content.Context, query: String): List<FileSystemItem> =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
      if (query.isBlank()) return@withContext emptyList()
      val current = _currentPath.value
      if (current == STORAGE_ROOTS_MARKER) {
        val roots = MediaFileRepository.getStorageRoots(context)
        val allResults = mutableListOf<FileSystemItem>()
        for (root in roots) {
          allResults.addAll(searchDirectoryRecursive(context, root.path, query))
        }
        allResults.distinctBy {
          when (it) {
            is FileSystemItem.VideoFile -> it.video.path
            is FileSystemItem.Folder -> it.path
          }
        }
      } else {
        searchDirectoryRecursive(context, current, query)
      }
    }

  private suspend fun searchDirectoryRecursive(
    context: android.content.Context,
    directoryPath: String,
    query: String,
    maxDepth: Int = 8,
    currentDepth: Int = 0,
  ): List<FileSystemItem> {
    currentCoroutineContext().ensureActive()
    if (currentDepth >= maxDepth) return emptyList()
    val results = mutableListOf<FileSystemItem>()
    try {
      val items = MediaFileRepository.scanDirectory(context, directoryPath).getOrNull() ?: emptyList()
      for (item in items) {
        currentCoroutineContext().ensureActive()
        when (item) {
          is FileSystemItem.VideoFile -> {
            if (item.video.displayName.contains(query, ignoreCase = true)) {
              results.add(item)
            }
          }
          is FileSystemItem.Folder -> {
            if (item.name.contains(query, ignoreCase = true)) {
              results.add(item)
            }
            results.addAll(searchDirectoryRecursive(context, item.path, query, maxDepth, currentDepth + 1))
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error searching directory $directoryPath", e)
    }
    return results
  }

  /**
   * Recursively collects all videos from a folder and its subfolders
   */
  suspend fun collectVideosRecursively(
    context: android.content.Context,
    folderPath: String,
    maxDepth: Int = 8,
    currentDepth: Int = 0,
  ): List<Video> {
    currentCoroutineContext().ensureActive()
    if (currentDepth >= maxDepth) return emptyList()
    val videos = mutableListOf<Video>()
    try {
      val items = MediaFileRepository.scanDirectory(context, folderPath).getOrNull() ?: emptyList()
      for (item in items) {
        currentCoroutineContext().ensureActive()
        when (item) {
          is FileSystemItem.VideoFile -> videos.add(item.video)
          is FileSystemItem.Folder -> videos.addAll(collectVideosRecursively(context, item.path, maxDepth, currentDepth + 1))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error collecting videos from $folderPath", e)
    }
    return videos
  }
}
