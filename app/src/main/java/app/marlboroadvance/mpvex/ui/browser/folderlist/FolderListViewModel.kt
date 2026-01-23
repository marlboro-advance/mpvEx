package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class FolderWithNewCount(
  val folder: VideoFolder,
  val newVideoCount: Int = 0,
)

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val foldersPreferences: FoldersPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val playbackStateRepository: PlaybackStateRepository by inject()

  private val _allVideoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val _foldersWithNewCount = MutableStateFlow<List<FolderWithNewCount>>(emptyList())
  val foldersWithNewCount: StateFlow<List<FolderWithNewCount>> = _foldersWithNewCount.asStateFlow()

  // Only show loading on fresh install (when there's no cached data)
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Track if initial load has completed to prevent empty state flicker
  private val _hasCompletedInitialLoad = MutableStateFlow(false)
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  // Track if folders were deleted leaving list empty
  private val _foldersWereDeleted = MutableStateFlow(false)
  val foldersWereDeleted: StateFlow<Boolean> = _foldersWereDeleted.asStateFlow()

  // Track previous folder count to detect if all folders were deleted
  private var previousFolderCount = 0

  /*
   * TRACKING LOADING STATE
   */
  private val _scanStatus = MutableStateFlow<String?>(null)
  val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

  private val _isEnriching = MutableStateFlow(false)
  val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

  // Track the current scan job to prevent concurrent scans
  private var currentScanJob: Job? = null

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Load cached folders instantly for immediate display
    val hasCachedData = loadCachedFolders()

    // If no cached data (first launch), scan immediately. Otherwise defer to not slow down app launch
    if (!hasCachedData) {
      loadVideoFolders()
    } else {
      viewModelScope.launch(Dispatchers.IO) {
        kotlinx.coroutines.delay(2000) // Wait 2 seconds before refreshing
        loadVideoFolders()
      }
    }

    // Refresh folders on global media library changes
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        // Clear cache when media library changes
        app.marlboroadvance.mpvex.repository.MediaFileRepository.clearCache()
        loadVideoFolders()
      }
    }

    // Filter folders based on blacklist
    viewModelScope.launch {
      combine(_allVideoFolders, foldersPreferences.blacklistedFolders.changes()) { folders, blacklist ->
        folders.filter { folder -> folder.path !in blacklist }
      }.collectLatest { filteredFolders ->
        // Check if folders became empty after having folders
        if (previousFolderCount > 0 && filteredFolders.isEmpty()) {
          _foldersWereDeleted.value = true
          Log.d(TAG, "Folders became empty (had $previousFolderCount folders before)")
        } else if (filteredFolders.isNotEmpty()) {
          // Reset flag if folders now exist
          _foldersWereDeleted.value = false
        }

        // Update previous count
        previousFolderCount = filteredFolders.size

        _videoFolders.value = filteredFolders
        // Calculate new video counts for each folder
        calculateNewVideoCounts(filteredFolders)

        // Save to cache for next app launch (save unfiltered list)
        saveFoldersToCache(_allVideoFolders.value)
      }
    }
  }

  private fun loadCachedFolders(): Boolean {
    var hasCachedData = false
    val prefs =
      getApplication<Application>().getSharedPreferences("folder_cache", android.content.Context.MODE_PRIVATE)
    val cachedJson = prefs.getString("folders", null)

    if (cachedJson != null) {
      try {
        // Parse JSON and restore folders
        val folders = parseFoldersFromJson(cachedJson)
        if (folders.isNotEmpty()) {
          Log.d(TAG, "Loaded ${folders.size} folders from cache instantly")
          hasCachedData = true
          viewModelScope.launch(Dispatchers.IO) {
            _allVideoFolders.value = folders
            _hasCompletedInitialLoad.value = true
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading cached folders", e)
      }
    }

    return hasCachedData
  }

  private fun saveFoldersToCache(folders: List<VideoFolder>) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val prefs =
          getApplication<Application>().getSharedPreferences("folder_cache", android.content.Context.MODE_PRIVATE)
        val json = serializeFoldersToJson(folders)
        prefs.edit().putString("folders", json).apply()
        Log.d(TAG, "Saved ${folders.size} folders to cache")
      } catch (e: Exception) {
        Log.e(TAG, "Error saving folders to cache", e)
      }
    }
  }

  private fun serializeFoldersToJson(folders: List<VideoFolder>): String {
    // Simple JSON serialization
    return folders.joinToString(separator = "|") { folder ->
      "${folder.bucketId}::${folder.name}::${folder.path}::${folder.videoCount}::${folder.totalSize}::${folder.totalDuration}::${folder.lastModified}"
    }
  }

  private fun parseFoldersFromJson(json: String): List<VideoFolder> {
    return try {
      json.split("|").mapNotNull { item ->
        val parts = item.split("::")
        if (parts.size == 7) {
          VideoFolder(
            bucketId = parts[0],
            name = parts[1],
            path = parts[2],
            videoCount = parts[3].toIntOrNull() ?: 0,
            totalSize = parts[4].toLongOrNull() ?: 0L,
            totalDuration = parts[5].toLongOrNull() ?: 0L,
            lastModified = parts[6].toLongOrNull() ?: 0L,
          )
        } else null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing cached folders", e)
      emptyList()
    }
  }

  private fun calculateNewVideoCounts(folders: List<VideoFolder>) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val showLabel = appearancePreferences.showUnplayedOldVideoLabel.get()
        if (!showLabel) {
          // If feature is disabled, just return folders with 0 count
          _foldersWithNewCount.value = folders.map { FolderWithNewCount(it, 0) }
          return@launch
        }

        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
        val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()
        val showHiddenFiles = appearancePreferences.showHiddenFiles.get()

        val foldersWithCounts = folders.map { folder ->
          try {
            // Get all videos in this folder
            val videos = app.marlboroadvance.mpvex.repository.MediaFileRepository
              .getVideosInFolder(getApplication(), folder.bucketId, showHiddenFiles)

            // Count new unplayed videos
            val newCount = videos.count { video ->
              // Check if video was added within threshold days
              val videoAge = currentTime - (video.dateAdded * 1000)
              val isRecent = videoAge <= thresholdMillis

              // Check if video has been played
              // A video is considered "played" if it has any playback state
              val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)
              val isUnplayed = playbackState == null

              isRecent && isUnplayed
            }

            FolderWithNewCount(folder, newCount)
          } catch (e: Exception) {
            Log.e(TAG, "Error counting new videos for folder ${folder.name}", e)
            FolderWithNewCount(folder, 0)
          }
        }

        _foldersWithNewCount.value = foldersWithCounts
      } catch (e: Exception) {
        Log.e(TAG, "Error calculating new video counts", e)
        _foldersWithNewCount.value = folders.map { FolderWithNewCount(it, 0) }
      }
    }
  }

  override fun refresh() {
    // Clear cache to force fresh data
    app.marlboroadvance.mpvex.repository.MediaFileRepository.clearCache()
    loadVideoFolders()
  }

  /**
   * Recalculate new video counts without refreshing the entire folder list
   * Useful when returning to the screen after playing videos
   */
  fun recalculateNewVideoCounts() {
    calculateNewVideoCounts(_videoFolders.value)
  }



  /**
   * Scans the filesystem recursively to find all folders containing videos.
   * Uses optimized parallel scanning with complete metadata (including duration)
   * to provide fast, non-flickering results.
   */
  private fun loadVideoFolders() {
    // Cancel any previous scan to prevent concurrent execution
    currentScanJob?.cancel()
    
    currentScanJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        // Show loading state if no folders yet
        val hasExistingData = _allVideoFolders.value.isNotEmpty()
        
        if (!hasExistingData) {
          _isLoading.value = true
          _scanStatus.value = "Scanning storage..."
        }

        // Capture current state for comparison
        val currentFoldersMap = _allVideoFolders.value.associateBy { it.bucketId }

        val showHiddenFiles = appearancePreferences.showHiddenFiles.get()

        // PHASE 1: Fast Parallel Scan
        val fastFolders = app.marlboroadvance.mpvex.repository.MediaFileRepository
          .getAllVideoFoldersFast(
            context = getApplication(),
            showHiddenFiles = showHiddenFiles,
            onProgress = { count ->
              // Only show progress if we don't have existing data (silent refresh)
              if (!hasExistingData) {
                _scanStatus.value = "Found $count folders..."
              }
            }
          )

        Log.d(TAG, "Fast scan completed: found ${fastFolders.size} folders")

        // EDGE CASE: Empty result when we had data (permissions revoked?)
        if (fastFolders.isEmpty() && hasExistingData) {
             Log.w(TAG, "Scan returned empty when we had data - possible permission issue")
             // Keep existing data, don't clear
             _isLoading.value = false
             _scanStatus.value = null
             return@launch
        }

        // MERGE STRATEGY:
        var needsEnrichment = false
        
        val mergedFolders = fastFolders.map { fastFolder ->
             val cached = currentFoldersMap[fastFolder.bucketId]
             // Check if cached data is valid, matches
             val cachedIsEnriched = cached != null && (
                 cached.videoCount == 0 || // Empty folders don't need duration
                 cached.totalDuration > 0   // Has been enriched
             )
             
             if (cached != null && 
                 cached.videoCount == fastFolder.videoCount && 
                 cached.lastModified == fastFolder.lastModified &&
                 cached.videoCount >= 0 && 
                 fastFolder.videoCount >= 0 &&
                 cachedIsEnriched) {
                 cached
             } else {
                 needsEnrichment = true
                 fastFolder
             }
        }

        // Immediate update with MERGED data
        if (mergedFolders.isNotEmpty()) {
            _allVideoFolders.value = mergedFolders
             _isLoading.value = false 
             _hasCompletedInitialLoad.value = true
        } else {
             // Legitimate empty result (no videos on device)
             _allVideoFolders.value = emptyList()
             _isLoading.value = false
             _hasCompletedInitialLoad.value = true
             _scanStatus.value = null
             return@launch
        }

        // OPTIMIZATION: Skip enrichment if data is up-to-date
        if (!needsEnrichment) {
             Log.d(TAG, "Data up to date, skipping enrichment")
             _scanStatus.value = null
             return@launch
        }

        // PHASE 2: Background Enrichment
        _isEnriching.value = true
        _scanStatus.value = "Processing metadata..."
        
        val enrichedFolders = app.marlboroadvance.mpvex.repository.MediaFileRepository
          .enrichVideoFolders(
            context = getApplication(),
            folders = mergedFolders,
            onProgress = { processed, total ->
               _scanStatus.value = "Processing metadata $processed/$total"
            }
          )

        Log.d(TAG, "Enrichment completed")
        _allVideoFolders.value = enrichedFolders

      } catch (e: kotlinx.coroutines.CancellationException) {
        // Job was cancelled (new scan started), this is expected
        Log.d(TAG, "Scan cancelled (new scan started)")
        throw e // Re-throw to properly cancel the coroutine
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        // EDGE CASE: Preserve existing data on error if we have it
        if (_allVideoFolders.value.isEmpty()) {
             _allVideoFolders.value = emptyList()
        }
        // If we have merged data from Phase 1, it's already in _allVideoFolders
        // So we don't overwrite it here
        _hasCompletedInitialLoad.value = true
      } finally {
        _isLoading.value = false
        _isEnriching.value = false
        _scanStatus.value = null
      }
    }
  }


}
