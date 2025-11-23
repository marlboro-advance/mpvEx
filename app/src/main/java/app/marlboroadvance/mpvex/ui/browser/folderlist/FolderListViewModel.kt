package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
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
import java.util.Locale

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val foldersPreferences: FoldersPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val metadataCache: VideoMetadataCacheRepository by inject()

  private val _allVideoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // Load folders asynchronously on initialization
    loadVideoFolders()

    // Refresh folders on global media library changes
    viewModelScope.launch(Dispatchers.IO) {
      MediaLibraryEvents.changes.collectLatest {
        loadVideoFolders()
      }
    }

    // Filter folders based on blacklist
    viewModelScope.launch {
      combine(_allVideoFolders, foldersPreferences.blacklistedFolders.changes()) { folders, blacklist ->
        folders.filter { folder -> folder.path !in blacklist }
      }.collectLatest { filteredFolders ->
        _videoFolders.value = filteredFolders
      }
    }
  }

  override fun refresh() {
    loadVideoFolders()
  }

  /**
   * Scans the filesystem recursively to find all folders containing videos.
   * Returns a flat list of folders with video metadata.
   */
  private fun loadVideoFolders() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val showHiddenFiles = appearancePreferences.showHiddenFiles.get()
        val folders = mutableMapOf<String, FolderData>()

        // Get storage roots
        val primaryStorage = Environment.getExternalStorageDirectory()
        val storageRoots = mutableListOf(primaryStorage)

        // Add external volumes (SD cards, USB OTG)
        val externalVolumes = StorageScanUtils.getExternalStorageVolumes(getApplication())
        for (volume in externalVolumes) {
          val volumePath = StorageScanUtils.getVolumePath(volume)
          if (volumePath != null) {
            val volumeDir = File(volumePath)
            if (volumeDir.exists() && volumeDir.canRead()) {
              storageRoots.add(volumeDir)
            }
          }
        }

        Log.d(TAG, "Scanning ${storageRoots.size} storage volumes for video folders")

        // Scan each storage root recursively
        for (root in storageRoots) {
          scanDirectoryRecursively(root, folders, showHiddenFiles)
        }

        // Convert to VideoFolder list
        val videoFolders = folders.values.map { folderData ->
          VideoFolder(
            bucketId = folderData.path,
            name = folderData.name,
            path = folderData.path,
            videoCount = folderData.videoCount,
            totalSize = folderData.totalSize,
            totalDuration = folderData.totalDuration,
            lastModified = folderData.lastModified,
          )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }

        Log.d(TAG, "Found ${videoFolders.size} folders containing videos")
        _allVideoFolders.value = videoFolders
      } catch (e: Exception) {
        Log.e(TAG, "Error loading video folders", e)
        _allVideoFolders.value = emptyList()
      }
    }
  }

  /**
   * Recursively scans a directory for folders containing videos.
   * Only adds folders that directly contain video files.
   */
  private suspend fun scanDirectoryRecursively(
    directory: File,
    folders: MutableMap<String, FolderData>,
    showHiddenFiles: Boolean,
    maxDepth: Int = 20,
    currentDepth: Int = 0,
  ) {
    if (currentDepth >= maxDepth) return
    if (!directory.exists() || !directory.canRead() || !directory.isDirectory) return

    try {
      val files = directory.listFiles() ?: return

      // Separate files into videos and subdirectories
      val videoFiles = mutableListOf<File>()
      val subdirectories = mutableListOf<File>()

      for (file in files) {
        when {
          // Skip hidden files/folders if needed
          !showHiddenFiles && file.name.startsWith(".") -> continue

          // Skip system folders
          file.isDirectory && shouldSkipFolder(file, showHiddenFiles) -> continue

          // Collect video files
          file.isFile && StorageScanUtils.isVideoFile(file) -> {
            videoFiles.add(file)
          }

          // Collect subdirectories
          file.isDirectory -> {
            subdirectories.add(file)
          }
        }
      }

      // If this directory contains videos, add it to the list
      if (videoFiles.isNotEmpty()) {
        val folderPath = directory.absolutePath
        val folderName = directory.name
        val totalSize = videoFiles.sumOf { it.length() }
        val lastModified = videoFiles.maxOfOrNull { it.lastModified() }?.div(1000) ?: 0L

        // Calculate total duration by loading video metadata
        var totalDuration = 0L
        for (videoFile in videoFiles) {
          try {
            val uri = Uri.fromFile(videoFile)
            val metadata = metadataCache.getOrExtractMetadata(videoFile, uri, videoFile.name)
            if (metadata != null && metadata.durationMs > 0) {
              totalDuration += metadata.durationMs
            }
          } catch (e: Exception) {
            Log.w(TAG, "Failed to extract duration for ${videoFile.name}", e)
          }
        }

        folders[folderPath] = FolderData(
          path = folderPath,
          name = folderName,
          videoCount = videoFiles.size,
          totalSize = totalSize,
          totalDuration = totalDuration,
          lastModified = lastModified,
        )

        Log.d(TAG, "Found folder with videos: $folderPath (${videoFiles.size} videos)")
      }

      // Recursively scan subdirectories
      for (subdir in subdirectories) {
        scanDirectoryRecursively(subdir, folders, showHiddenFiles, maxDepth, currentDepth + 1)
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "Permission denied scanning: ${directory.absolutePath}", e)
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
    }
  }

  /**
   * Checks if a folder should be skipped during scanning
   */
  private fun shouldSkipFolder(folder: File, showHiddenFiles: Boolean): Boolean {
    if (showHiddenFiles) {
      return false
    }

    val skipFolders = setOf(
      "android",
      "data",
      ".thumbnails",
      ".cache",
      "cache",
      "lost.dir",
      "system",
      ".android_secure",
      ".trash",
      ".trashbin",
    )

    val name = folder.name.lowercase()
    val isHidden = name.startsWith(".")
    return isHidden || skipFolders.contains(name)
  }

  /**
   * Internal data class for collecting folder information
   */
  private data class FolderData(
    val path: String,
    val name: String,
    val videoCount: Int,
    val totalSize: Long,
    val totalDuration: Long,
    val lastModified: Long,
  )
}
