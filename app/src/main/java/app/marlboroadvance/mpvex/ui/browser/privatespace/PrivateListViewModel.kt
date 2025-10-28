package app.marlboroadvance.mpvex.ui.browser.privatespace

import android.app.Application
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.entities.PrivateVideoEntity
import app.marlboroadvance.mpvex.database.repository.PrivateVideoRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

class PrivateListViewModel(
  application: Application,
  private val privateVideoRepository: PrivateVideoRepository,
) : BaseBrowserViewModel(application) {
  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  companion object {
    private const val TAG = "PrivateListViewModel"

    fun factory(
      application: Application,
      privateVideoRepository: PrivateVideoRepository,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PrivateListViewModel(application, privateVideoRepository) as T
    }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        privateVideoRepository.syncFromPersistentStorage()
      }.onFailure { e ->
        Log.e(TAG, "Error syncing with persistent storage", e)
      }
    }
    loadPrivateVideos()
  }

  override fun refresh() = loadPrivateVideos()

  private fun loadPrivateVideos() {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      runCatching {
        val entities = privateVideoRepository.getAll()
        entities.mapNotNull { entity ->
          createVideoFromEntity(entity).also {
            if (it == null) {
              // Clean up missing files
              privateVideoRepository.deleteFromPrivateStorage(entity.videoId)
            }
          }
        }
      }.onSuccess { videos ->
        _videos.value = videos
      }.onFailure { e ->
        Log.e(TAG, "Error loading videos", e)
        _videos.value = emptyList()
      }
      _isLoading.value = false
    }
  }

  private fun createVideoFromEntity(entity: PrivateVideoEntity): Video? {
    val file = File(entity.privateFilePath)
    if (!file.exists()) {
      Log.w(TAG, "File not found: ${entity.privateFilePath}")
      return null
    }

    return runCatching {
      val displayName = file.name.substringAfter("_", file.name)
      Video(
        id = entity.videoId,
        title = displayName.substringBeforeLast('.'),
        displayName = displayName,
        path = entity.privateFilePath,
        uri = android.net.Uri.fromFile(file),
        duration = extractDuration(file.path),
        durationFormatted = formatDuration(extractDuration(file.path)),
        size = file.length(),
        sizeFormatted = formatFileSize(file.length()),
        dateModified = file.lastModified() / 1000,
        dateAdded = entity.addedAt / 1000,
        mimeType = getMimeType(displayName),
        bucketId = "private",
        bucketDisplayName = "Private Space",
      )
    }.getOrElse { e ->
      Log.e(TAG, "Error creating video", e)
      null
    }
  }

  private fun extractDuration(filePath: String): Long =
    runCatching {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(filePath)
        retriever
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull() ?: 0L
      } finally {
        retriever.release()
      }
    }.getOrDefault(0L)

  private fun getMimeType(filename: String): String =
    when (filename.substringAfterLast('.', "").lowercase()) {
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "m4v" -> "video/x-m4v"
      "mpg", "mpeg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "3g2" -> "video/3gpp2"
      else -> "video/*"
    }

  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0s"
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return buildString {
      if (hours > 0) append("${hours}h ")
      if (minutes > 0) append("${minutes}m ")
      if (hours == 0L) append("${secs}s")
    }.trim()
  }

  private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(
      "%.1f %s",
      bytes / 1024.0.pow(digitGroups.toDouble()),
      arrayOf("B", "KB", "MB", "GB", "TB")[digitGroups],
    )
  }
}
