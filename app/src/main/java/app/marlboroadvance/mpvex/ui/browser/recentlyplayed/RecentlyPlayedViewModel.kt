package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import kotlin.math.pow

class RecentlyPlayedViewModel(application: Application) : AndroidViewModel(application) {
  private val recentlyPlayedRepository by inject<RecentlyPlayedRepository>(RecentlyPlayedRepository::class.java)
  private val playlistRepository by inject<PlaylistRepository>(PlaylistRepository::class.java)
  private val metadataCache by inject<VideoMetadataCacheRepository>(VideoMetadataCacheRepository::class.java)

  private val _recentItems = MutableStateFlow<List<RecentlyPlayedItem>>(emptyList())
  val recentItems: StateFlow<List<RecentlyPlayedItem>> = _recentItems.asStateFlow()

  // Keep for backward compatibility
  private val _recentVideos = MutableStateFlow<List<Video>>(emptyList())
  val recentVideos: StateFlow<List<Video>> = _recentVideos.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  init {
    // Observe recently played changes and update automatically
    viewModelScope.launch {
      val db = org.koin.java.KoinJavaComponent.get<MpvExDatabase>(MpvExDatabase::class.java)

      // Combine both flows - entities and playlists
      kotlinx.coroutines.flow.combine(
        recentlyPlayedRepository.observeRecentlyPlayed(limit = 50),
        db.recentlyPlayedDao().observeRecentlyPlayedPlaylists(limit = 50),
      ) { entities, playlists ->
        Pair(entities, playlists)
      }.collect { (entities, playlists) ->
        loadRecentVideosFromEntities(entities, playlists)
      }
    }
  }

  private suspend fun loadRecentVideosFromEntities(
    allRecentEntities: List<RecentlyPlayedEntity>,
    recentPlaylists: List<app.marlboroadvance.mpvex.database.dao.RecentlyPlayedDao.RecentlyPlayedPlaylistInfo>,
  ) {
    try {
      val items = mutableListOf<RecentlyPlayedItem>()

      // Group videos by playlist and standalone videos
      val playlistMap = mutableMapOf<Int, MutableList<Pair<String, Long>>>()
      val standaloneVideos = mutableListOf<Pair<String, Long>>()

      for (entity in allRecentEntities) {
        if (entity.playlistId != null) {
          playlistMap.getOrPut(entity.playlistId) { mutableListOf() }
            .add(Pair(entity.filePath, entity.timestamp))
        } else {
          standaloneVideos.add(Pair(entity.filePath, entity.timestamp))
        }
      }

      // Create playlist items
      for (playlistInfo in recentPlaylists) {
        val playlist = playlistRepository.getPlaylistById(playlistInfo.playlistId)
        if (playlist != null) {
          val playlistVideos = playlistMap[playlistInfo.playlistId] ?: emptyList()
          val mostRecent = playlistVideos.maxByOrNull { it.second }
          if (mostRecent != null) {
            val itemCount = playlistRepository.getPlaylistItemCount(playlist.id)
            items.add(
              RecentlyPlayedItem.PlaylistItem(
                playlist = playlist,
                videoCount = itemCount,
                mostRecentVideoPath = mostRecent.first,
                timestamp = playlistInfo.timestamp,
              ),
            )
          }
        }
      }

      // Create standalone video items
      for ((filePath, timestamp) in standaloneVideos) {
        val entity = allRecentEntities.find { it.filePath == filePath }

        val isNetworkUri = filePath.startsWith("http://", ignoreCase = true) ||
          filePath.startsWith("https://", ignoreCase = true) ||
          filePath.startsWith("rtmp://", ignoreCase = true) ||
          filePath.startsWith("rtsp://", ignoreCase = true)

        val video = if (isNetworkUri) {
          createNetworkVideoFromUri(filePath, entity)
        } else {
          val file = File(filePath)
          if (file.exists()) {
            createVideoFromFilePath(filePath, file, entity?.videoTitle)
          } else {
            null
          }
        }

        if (video != null) {
          items.add(RecentlyPlayedItem.VideoItem(video, timestamp))
        }
      }

      // Sort by timestamp
      val sortedItems = items.sortedByDescending { it.timestamp }
      _recentItems.value = sortedItems

      // Keep backward compatibility
      val videos = sortedItems.filterIsInstance<RecentlyPlayedItem.VideoItem>().map { it.video }
      _recentVideos.value = videos
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error loading recent videos", e)
      _recentItems.value = emptyList()
      _recentVideos.value = emptyList()
    } finally {
      _isLoading.value = false
    }
  }

  private suspend fun createVideoFromFilePath(
    filePath: String,
    file: File,
    parsedVideoTitle: String? = null,
  ): Video? {
    return try {
      val context = getApplication<Application>()

      // Extract metadata directly from file using metadata cache
      val uri = Uri.fromFile(file)
      val displayName = file.name
      val title = file.nameWithoutExtension

      // Get metadata from cache or extract it
      val metadataCache by inject<VideoMetadataCacheRepository>(VideoMetadataCacheRepository::class.java)
      val metadata = metadataCache.getOrExtractMetadata(file, uri, displayName)

      val duration = metadata?.durationMs ?: 0L
      val width = metadata?.width ?: 0
      val height = metadata?.height ?: 0
      val fps = metadata?.fps ?: 0f
      val size = if (metadata?.sizeBytes != null && metadata.sizeBytes > 0) {
        metadata.sizeBytes
      } else {
        file.length()
      }

      val dateModified = file.lastModified() / 1000
      val dateAdded = dateModified
      val parent = file.parent ?: ""
      val bucketId = parent.hashCode().toString()
      val bucketDisplayName = File(parent).name

      // Determine mime type from extension
      val mimeType = when (file.extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "m4v" -> "video/x-m4v"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        else -> "video/*"
      }

      Video(
        id = file.absolutePath.hashCode().toLong(),
        title = title,
        displayName = displayName,
        path = filePath,
        uri = uri,
        duration = duration,
        durationFormatted = formatDuration(duration),
        size = size,
        sizeFormatted = formatFileSize(size),
        dateModified = dateModified,
        dateAdded = dateAdded,
        mimeType = mimeType,
        bucketId = bucketId,
        bucketDisplayName = bucketDisplayName,
        width = width,
        height = height,
        fps = fps,
        resolution = formatResolution(width, height),
      )
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error creating video from path: $filePath", e)
      null
    }
  }

  private fun createNetworkVideoFromUri(
    uri: String,
    entity: RecentlyPlayedEntity?,
  ): Video {
    val parsedUri = Uri.parse(uri)

    // For HTTP/HTTPS network streams, prefer parsed video title (e.g., from HTTP headers)
    // Always use videoTitle first (parsed from MPV), then fileName, then URI fallback
    val displayName =
      entity?.videoTitle?.takeIf { it.isNotBlank() } ?: entity?.fileName ?: parsedUri.lastPathSegment ?: uri
    val title = entity?.videoTitle?.takeIf { it.isNotBlank() }?.substringBeforeLast('.') ?: displayName.substringBeforeLast('.')

    // Use cached duration, file size, resolution, and thumbnail from entity
    val duration = entity?.duration ?: 0L
    val fileSize = entity?.fileSize ?: 0L
    val width = entity?.width ?: 0
    val height = entity?.height ?: 0

    val videoPath = uri

    return Video(
      id = uri.hashCode().toLong(),
      title = title,
      displayName = displayName,
      path = videoPath,  // Use thumbnail path if available for thumbnail loading
      uri = parsedUri,   // Keep original URI for playback
      duration = duration,
      durationFormatted = formatDuration(duration),
      size = fileSize,
      sizeFormatted = formatFileSize(fileSize),
      dateModified = entity?.timestamp?.div(1000) ?: 0L,
      dateAdded = entity?.timestamp?.div(1000) ?: 0L,
      mimeType = "video/*",
      bucketId = parsedUri.host?.hashCode()?.toString() ?: "0",
      bucketDisplayName = parsedUri.host ?: "Network",
      width = width,
      height = height,
      fps = 0f,
      resolution = formatResolution(width, height),
    )
  }

  private fun createBasicVideoFromFile(
    file: File,
    parsedVideoTitle: String? = null,
  ): Video {
    // For local videos, always use filename (ignore parsed title)
    val displayName = file.name
    val title = file.nameWithoutExtension
    val size = file.length()
    val dateModified = file.lastModified() / 1000
    val uri = Uri.fromFile(file)
    val parent = file.parent ?: ""

    return Video(
      id = file.absolutePath.hashCode().toLong(),
      title = title,
      displayName = displayName,
      path = file.absolutePath,
      uri = uri,
      duration = 0L,
      durationFormatted = "--",
      size = size,
      sizeFormatted = formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateModified,
      mimeType = "video/*",
      bucketId = parent.hashCode().toString(),
      bucketDisplayName = File(parent).name,
      width = 0,
      height = 0,
      fps = 0f,
      resolution = "--",
    )
  }

  suspend fun clearAllRecentlyPlayed() {
    try {
      recentlyPlayedRepository.clearAll()
      // The observe flow will automatically update the UI
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error clearing recent videos", e)
    }
  }

  suspend fun deleteRecentlyPlayedItem(item: RecentlyPlayedItem) {
    try {
      when (item) {
        is RecentlyPlayedItem.VideoItem -> {
          // Delete by file path for video items
          recentlyPlayedRepository.deleteByFilePath(item.video.path)
        }
        is RecentlyPlayedItem.PlaylistItem -> {
          // For playlist items, we need to delete all entries with that playlistId
          val db = org.koin.java.KoinJavaComponent.get<MpvExDatabase>(MpvExDatabase::class.java)
          val allEntries = db.recentlyPlayedDao().getAllRecentlyPlayed()
          allEntries.filter { it.playlistId == item.playlist.id }.forEach { entity ->
            recentlyPlayedRepository.deleteById(entity.id)
          }
        }
      }
      // The observe flow will automatically update the UI
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error deleting recently played item", e)
    }
  }

  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--"
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
      hours > 0 -> "${hours}h ${minutes}m ${secs}s"
      minutes > 0 -> "${minutes}m ${secs}s"
      else -> "${secs}s"
    }
  }

  private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt()
    return String.format(
      java.util.Locale.getDefault(),
      "%.1f %s",
      bytes / 1024.0.pow(digitGroups.toDouble()),
      units[digitGroups],
    )
  }

  private fun formatResolution(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "--"

    return when {
      width >= 7680 || height >= 4320 -> "4320p"
      width >= 3840 || height >= 2160 -> "2160p"
      width >= 2560 || height >= 1440 -> "1440p"
      width >= 1920 || height >= 1080 -> "1080p"
      width >= 1280 || height >= 720 -> "720p"
      width >= 854 || height >= 480 -> "480p"
      width >= 640 || height >= 360 -> "360p"
      width >= 426 || height >= 240 -> "240p"
      width >= 256 || height >= 144 -> "144p"
      else -> "${height}p"
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        RecentlyPlayedViewModel(application)
      }
    }
  }
}
