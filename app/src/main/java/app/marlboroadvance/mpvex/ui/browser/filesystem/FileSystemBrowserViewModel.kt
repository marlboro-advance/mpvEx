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
import app.marlboroadvance.mpvex.repository.FileSystemRepository
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

data class VideoFileWithPlaybackInfo(
  val videoFile: FileSystemItem.VideoFile,
  val progressPercentage: Float? = null,
)

class FileSystemBrowserViewModel(
  application: Application,
  initialPath: String? = null,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val browserPreferences: BrowserPreferences by inject()
  private val videoRepository: app.marlboroadvance.mpvex.repository.VideoRepository by inject()

  // Special marker for "show storage volumes" mode
  private val STORAGE_ROOTS_MARKER = "__STORAGE_ROOTS__"

  private val _currentPath = MutableStateFlow(initialPath ?: STORAGE_ROOTS_MARKER)
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  private val _unsortedItems = MutableStateFlow<List<FileSystemItem>>(emptyList())
  private val _items = MutableStateFlow<List<FileSystemItem>>(emptyList())
  val items: StateFlow<List<FileSystemItem>> = _items.asStateFlow()

  private val _videoFilesWithPlayback = MutableStateFlow<Map<Long, Float>>(emptyMap())
  val videoFilesWithPlayback: StateFlow<Map<Long, Float>> = _videoFilesWithPlayback.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _breadcrumbs = MutableStateFlow<List<PathComponent>>(emptyList())
  val breadcrumbs: StateFlow<List<PathComponent>> = _breadcrumbs.asStateFlow()

  private val _isEnrichingMetadata = MutableStateFlow(false)
  val isEnrichingMetadata: StateFlow<Boolean> = _isEnrichingMetadata.asStateFlow()

  val isAtRoot: StateFlow<Boolean> =
    MutableStateFlow(initialPath == null).apply {
      viewModelScope.launch {
        _currentPath.collect { path ->
          value = path == STORAGE_ROOTS_MARKER
        }
      }
    }

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
    // Load initial directory (storage roots)
    loadCurrentDirectory()

    // Refresh on global media library changes
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadCurrentDirectory()
      }
    }

    // Apply sorting whenever items or sort preferences change
    viewModelScope.launch {
      combine(
        _unsortedItems,
        browserPreferences.folderSortType.changes(),
        browserPreferences.folderSortOrder.changes(),
      ) { items, sortType, sortOrder ->
        SortUtils.sortFileSystemItems(items, sortType, sortOrder)
      }.collectLatest { sortedItems ->
        _items.value = sortedItems
      }
    }
  }

  override fun refresh() {
    loadCurrentDirectory()
  }

  /**
   * Navigate to a specific path
   */
  fun navigateTo(path: String) {
    _currentPath.value = path
    loadCurrentDirectory()
  }

  /**
   * Navigate up one level in the directory hierarchy
   */
  fun navigateUp() {
    val current = _currentPath.value

    if (current == STORAGE_ROOTS_MARKER) {
      // Already at root, nowhere to go
      return
    }

    val parent = File(current).parent

    if (parent != null && parent != current) {
      _currentPath.value = parent
      loadCurrentDirectory()
    } else {
      // Go back to storage roots view
      _currentPath.value = STORAGE_ROOTS_MARKER
      loadCurrentDirectory()
    }
  }

  /**
   * Delete folders (and their contents)
   */
  fun deleteFolders(folders: List<FileSystemItem.Folder>): Pair<Int, Int> {
    var successCount = 0
    var failureCount = 0

    folders.forEach { folder ->
      try {
        val dir = File(folder.path)
        if (dir.exists() && dir.deleteRecursively()) {
          successCount++
        } else {
          failureCount++
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete folder: ${folder.path}", e)
        failureCount++
      }
    }

    return Pair(successCount, failureCount)
  }

  /**
   * Delete videos - delegates to base class implementation
   */
  override suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> = super.deleteVideos(videos)

  /**
   * Rename a video file - delegates to base class implementation
   */
  override suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> = super.renameVideo(video, newDisplayName)

  private fun loadCurrentDirectory() {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      _error.value = null

      try {
        val path = _currentPath.value

        // Special case: Show storage roots at the special marker
        if (path == STORAGE_ROOTS_MARKER) {
          _breadcrumbs.value = emptyList()
          val roots = FileSystemRepository.getStorageRoots(getApplication())
          _unsortedItems.value = roots
          Log.d(TAG, "Loaded ${roots.size} storage roots")
        } else {
          // Update breadcrumbs for real paths
          _breadcrumbs.value = FileSystemRepository.getPathComponents(path)

          // Scan directory
          FileSystemRepository
            .scanDirectory(getApplication(), path)
            .onSuccess { items ->
              _unsortedItems.value = items
              Log.d(TAG, "Loaded directory: $path with ${items.size} items")

              loadPlaybackInfo(items)
              enrichMetadataInBackground(items)
            }.onFailure { error ->
              _error.value = error.message
              _unsortedItems.value = emptyList()
              Log.e(TAG, "Error loading directory: $path", error)
            }
        }
      } catch (e: Exception) {
        _error.value = e.message
        _unsortedItems.value = emptyList()
        Log.e(TAG, "Error loading directory", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  private fun loadPlaybackInfo(items: List<FileSystemItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      val videoFiles = items.filterIsInstance<FileSystemItem.VideoFile>()
      val playbackMap = mutableMapOf<Long, Float>()

      videoFiles.forEach { videoFile ->
        val video = videoFile.video
        val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)

        if (playbackState != null && video.duration > 0) {
          val durationSeconds = video.duration / 1000
          val timeRemaining = playbackState.timeRemaining.toLong()
          val watched = durationSeconds - timeRemaining
          val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)

          if (progressValue in 0.01f..0.99f) {
            playbackMap[video.id] = progressValue
          }
        }
      }

      _videoFilesWithPlayback.value = playbackMap
    }
  }

  private fun enrichMetadataInBackground(items: List<FileSystemItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isEnrichingMetadata.value = true
        val videoFiles = items.filterIsInstance<FileSystemItem.VideoFile>()
        val videos = videoFiles.map { it.video }
        val enrichedVideos = videoRepository.enrichVideosMetadata(getApplication(), videos)

        val updatedItems = items.map { item ->
          if (item is FileSystemItem.VideoFile) {
            val enrichedVideo = enrichedVideos.find { it.id == item.video.id }
            if (enrichedVideo != null) {
              item.copy(video = enrichedVideo)
            } else {
              item
            }
          } else {
            item
          }
        }

        _unsortedItems.value = updatedItems
        loadPlaybackInfo(updatedItems)
      } catch (e: Exception) {
        Log.e(TAG, "Error enriching video metadata", e)
      } finally {
        _isEnrichingMetadata.value = false
      }
    }
  }
}
