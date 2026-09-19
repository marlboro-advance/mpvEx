package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.MediaIdentifier
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.media.MetadataRetrieval
import app.marlboroadvance.mpvex.utils.storage.FileTypeUtils
import app.marlboroadvance.mpvex.repository.VideoStatCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.compose.runtime.Immutable

@Immutable
data class VideoWithPlaybackInfo(
  val video: Video,
  val timeRemaining: Long? = null, // in seconds
  val progressPercentage: Float? = null, // 0.0 to 1.0
  val isOldAndUnplayed: Boolean = false, // true if video is older than threshold and never played
  val isWatched: Boolean = false, // true if video has any playback history
)

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val appearancePreferences: app.marlboroadvance.mpvex.preferences.AppearancePreferences by inject()
  private val browserPreferences: app.marlboroadvance.mpvex.preferences.BrowserPreferences by inject()
  private val recentlyPlayedRepository: app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()
  // Using MediaFileRepository singleton directly

  // In-memory or disk cached videos available immediately on instantiation
  private val initialCached = loadCachedVideos()

  private val _videos = MutableStateFlow<List<Video>>(initialCached)
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(
    initialCached.map { VideoWithPlaybackInfo(it) }
  )
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val _isLoading = MutableStateFlow(initialCached.isEmpty())
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _hasCompletedInitialLoad = MutableStateFlow(initialCached.isNotEmpty())
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  // Track if items were deleted/moved leaving folder empty
  private val _videosWereDeletedOrMoved = MutableStateFlow(false)
  val videosWereDeletedOrMoved: StateFlow<Boolean> = _videosWereDeletedOrMoved.asStateFlow()

  val lastPlayedInFolderPath: StateFlow<String?> =
    recentlyPlayedRepository
      .observeRecentlyPlayed(limit = 100)
      .map { recentlyPlayedList ->
        val folderPath = _videos.value.firstOrNull()?.path?.let { File(it).parent }
        if (folderPath != null) {
          recentlyPlayedList.firstOrNull { entity ->
            try {
              File(entity.filePath).parent == folderPath
            } catch (_: Exception) {
              false
            }
          }?.filePath
        } else {
          null
        }
      }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Track previous video count to detect if folder became empty
  private var previousVideoCount = 0

  // Single tracked job to prevent concurrent load race conditions
  private var loadJob: Job? = null

  private val tag = "VideoListViewModel"

  init {
    previousVideoCount = initialCached.size

    if (initialCached.isNotEmpty()) {
      // Fast load watch progress for cached videos immediately
      viewModelScope.launch(Dispatchers.IO) {
        loadPlaybackInfo(initialCached)
      }

      // Silently refresh in the background to sync with MediaStore
      loadVideos(isBackgroundRefresh = true)
    } else {
      loadVideos(isBackgroundRefresh = false)
    }

    // Listen for global media library changes and refresh silently in background
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadVideos(isBackgroundRefresh = true)
      }
    }
  }

  private var cachedFolderLastModified: Long = 0L

  private fun loadCachedVideos(): List<Video> {
    val entry = VideoStatCache.load(getApplication(), bucketId)
    if (entry != null && entry.videos.isNotEmpty()) {
      cachedFolderLastModified = entry.folderLastModified
      return entry.videos
    }
    return emptyList()
  }

  override fun refresh() {
    Log.d(tag, "Refreshing video list for bucket: $bucketId")
    cachedFolderLastModified = 0L // Force full stat refresh
    _isLoading.value = true
    viewModelScope.launch(Dispatchers.IO) {
      triggerMediaScan()
      loadVideos(isBackgroundRefresh = false)
    }
  }

  /**
   * Fast refresh of playback state (watch progress, watched status)
   * without clearing cache, rescanning disk, or showing a loading indicator.
   */
  fun refreshPlaybackInfo() {
    viewModelScope.launch(Dispatchers.IO) {
      val currentVideos = _videos.value
      if (currentVideos.isNotEmpty()) {
        loadPlaybackInfo(currentVideos)
      } else {
        loadVideos(isBackgroundRefresh = false)
      }
    }
  }

  private fun loadVideos(isBackgroundRefresh: Boolean = false) {
    // Cancel any previous in-flight load job to prevent race conditions
    loadJob?.cancel()
    loadJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        if (!isBackgroundRefresh && _videos.value.isEmpty()) {
          _isLoading.value = true
        }

        val folderFile = File(bucketId)
        val currentFolderMod = if (folderFile.exists()) folderFile.lastModified() else 0L
        val needsMetadata = MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)
        val isMissingMetadata = needsMetadata && _videos.value.any { it.width > 0 && it.fps == 0f && !it.resolution.contains("@") }

        // Fast stat check: if folder's filesystem lastModified hasn't changed AND metadata is already present,
        // no files were added, deleted, or renamed. Skip heavy rescan completely!
        if (isBackgroundRefresh && currentFolderMod > 0L && (currentFolderMod == cachedFolderLastModified || VideoStatCache.isFolderStatUnchanged(bucketId, currentFolderMod)) && _videos.value.isNotEmpty() && !isMissingMetadata) {
          Log.d(tag, "Folder stat unchanged and metadata complete ($currentFolderMod), skipping rescan")
          return@launch
        }

        // Fast query of basic video info from MediaStore (or filesystem fallback)
        var videoList = MediaFileRepository.getVideosInFolder(getApplication(), bucketId)

        if (!isActive) return@launch

        // Check if folder became empty after having videos
        if (previousVideoCount > 0 && videoList.isEmpty()) {
          _videosWereDeletedOrMoved.value = true
          Log.d(tag, "Folder became empty (had $previousVideoCount videos before)")
        } else if (videoList.isNotEmpty()) {
          // Reset flag if folder now has videos
          _videosWereDeletedOrMoved.value = false
        }

        // Update previous count
        previousVideoCount = videoList.size

        if (videoList.isEmpty()) {
          // Only trigger rescan if we have no videos at all (not even cached)
          if (_videos.value.isEmpty()) {
            Log.d(tag, "No videos found for bucket $bucketId - attempting media rescan")
            triggerMediaScan()
            delay(1000)
            if (!isActive) return@launch
            var retryVideoList = MediaFileRepository.getVideosInFolder(getApplication(), bucketId)
            if (!isActive) return@launch

            if (previousVideoCount > 0 && retryVideoList.isEmpty()) {
              _videosWereDeletedOrMoved.value = true
            } else if (retryVideoList.isNotEmpty()) {
              _videosWereDeletedOrMoved.value = false
            }
            previousVideoCount = retryVideoList.size
            videoList = retryVideoList
          }
        }

        if (!isActive) return@launch

        // INCREMENTAL UPDATE BASED ON FILE STATS:
        // Match existing cached videos by path. If a file's size & dateModified match AND it already has metadata,
        // reuse the cached metadata directly without re-extracting!
        val existingMap = _videos.value.associateBy { it.path }
        val needsEnrichment = mutableListOf<Video>()
        val reconciledVideos = videoList.map { scanned ->
          val cached = existingMap[scanned.path]
          val isCachedEnriched = cached != null && (
            cached.fps > 0f ||
            (cached.width > 0 && cached.resolution.contains("@")) ||
            cached.hasEmbeddedSubtitles ||
            cached.subtitleCodec.isNotEmpty()
          )
          if (cached != null && cached.size == scanned.size && cached.dateModified == scanned.dateModified && (!needsMetadata || isCachedEnriched)) {
            // Unchanged file with complete metadata: reuse cached metadata directly
            cached
          } else {
            // New, modified, or missing metadata: needs enrichment
            needsEnrichment.add(scanned)
            scanned
          }
        }

        // Only enrich new, modified, or missing metadata files!
        val finalVideos = if (needsEnrichment.isNotEmpty() && needsMetadata) {
          val enrichedNew = MetadataRetrieval.enrichVideosIfNeeded(
            context = getApplication(),
            videos = needsEnrichment,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
          ).associateBy { it.path }
          reconciledVideos.map { enrichedNew[it.path] ?: it }
        } else {
          reconciledVideos
        }

        if (!isActive) return@launch

        // Save updated list and folder stat directly to the JSON stat file
        cachedFolderLastModified = currentFolderMod
        VideoStatCache.save(getApplication(), bucketId, currentFolderMod, finalVideos)

        // Check if video list actually changed (including metadata fields like fps, subtitles, resolution)
        val hasChanged = _videos.value != finalVideos

        if (hasChanged || _videosWithPlaybackInfo.value.isEmpty()) {
          _videos.value = finalVideos
          val existingInfoMap = _videosWithPlaybackInfo.value.associateBy { it.video.path }
          _videosWithPlaybackInfo.value = finalVideos.map { v ->
            val prev = existingInfoMap[v.path]
            if (prev != null) {
              prev.copy(video = v)
            } else {
              VideoWithPlaybackInfo(v)
            }
          }
          loadPlaybackInfo(finalVideos)
        }
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        if (_videos.value.isEmpty()) {
          _videos.value = emptyList()
          _videosWithPlaybackInfo.value = emptyList()
        }
      } finally {
        if (isActive) {
          _isLoading.value = false
          _hasCompletedInitialLoad.value = true
        }
      }
    }
  }

  /**
   * Set flag indicating videos were deleted or moved
   */
  fun setVideosWereDeletedOrMoved() {
    _videosWereDeletedOrMoved.value = true
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val videosWithInfo =
      videos.map { video ->
        val playbackState = playbackStateRepository.getVideoDataByTitle(MediaIdentifier.forLocalPath(video.path))
        val watchedThreshold = browserPreferences.watchedThreshold.get()

        // Calculate watch progress (0.0 to 1.0)
        val progress = if (playbackState != null && video.duration > 0) {
          // Duration is in milliseconds, convert to seconds
          val durationSeconds = video.duration / 1000
          val timeRemaining = playbackState.timeRemaining.toLong()
          val watched = durationSeconds - timeRemaining
          val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)

          // Only show progress for videos that are 1-99% complete
          if (progressValue in 0.01f..0.99f) progressValue else null
        } else {
          null
        }

        // Check if video is "new" (recently added) and unplayed.
        // A video qualifies if:
        //   1. It has no playback state (never been played), AND
        //   2. It was added/modified within the configured threshold days.
        val isUnplayed = playbackState == null
        val isOldAndUnplayed = if (isUnplayed) {
          val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
          val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
          val videoAge = System.currentTimeMillis() - (video.dateModified * 1000L)
          videoAge <= thresholdMillis
        } else {
          false
        }

        val isWatched = if (playbackState != null && video.duration > 0) {
           val durationSeconds = video.duration / 1000
           val timeRemaining = playbackState.timeRemaining.toLong()
           val watched = durationSeconds - timeRemaining
           val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
           val calculatedWatched = progressValue >= (watchedThreshold / 100f)
           playbackState.hasBeenWatched || calculatedWatched
        } else {
           false
        }

        VideoWithPlaybackInfo(
          video = video,
          timeRemaining = playbackState?.timeRemaining?.toLong(),
          progressPercentage = progress,
          isOldAndUnplayed = isOldAndUnplayed,
          isWatched = isWatched,
        )
      }
    _videosWithPlaybackInfo.value = videosWithInfo
  }

  private suspend fun triggerMediaScan() = withContext(Dispatchers.IO) {
    try {
      // Trigger a targeted media scan for the specific folder
      val folder = File(bucketId)
      
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
          ) { path, uri ->
            Log.d(tag, "Media scan completed for: $path -> $uri")
          }
          
          Log.d(tag, "Triggered media scan for ${filePaths.size} files in: $bucketId")
        } else {
          Log.d(tag, "No video files found in folder: $bucketId")
        }
      } else {
        // Fallback to scanning external storage root
        val externalStorage = android.os.Environment.getExternalStorageDirectory()
        android.media.MediaScannerConnection.scanFile(
          getApplication(),
          arrayOf(externalStorage.absolutePath),
          arrayOf("video/*"),
        ) { path, uri ->
          Log.d(tag, "Media scan completed for: $path -> $uri")
        }
        Log.d(tag, "Triggered media scan for: ${externalStorage.absolutePath}")
      }
    } catch (e: Exception) {
      Log.e(tag, "Failed to trigger media scan", e)
    }
  }

  companion object {
    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }
}
