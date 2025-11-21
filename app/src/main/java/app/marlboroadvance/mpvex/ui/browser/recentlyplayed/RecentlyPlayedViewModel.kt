package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
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
import app.marlboroadvance.mpvex.repository.VideoRepository
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
  private val videoRepository by lazy {
    val metadataCache by inject<VideoMetadataCacheRepository>(VideoMetadataCacheRepository::class.java)
    VideoRepository(metadataCache)
  }

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
        val file = File(filePath)
        if (file.exists()) {
          val video = createVideoFromFilePath(filePath, file)
          if (video != null) {
            items.add(RecentlyPlayedItem.VideoItem(video, timestamp))
          }
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

  private suspend fun createVideoFromFilePath(filePath: String, file: File): Video? {
    return try {
      val context = getApplication<Application>()

      // Try to find video in MediaStore first for better metadata
      val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
      )

      val cursor = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Video.Media.DATA} = ?",
        arrayOf(filePath),
        null,
      )

      cursor?.use {
        if (it.moveToFirst()) {
          val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
          val title = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)) ?: ""
          val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: file.name
          val duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
          val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
          val dateModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED))
          val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
          val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: ""
          val bucketId = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)) ?: ""
          val bucketDisplayName =
            it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)) ?: ""
          val width = it.getInt(it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
          val height = it.getInt(it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))

          val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

          return Video(
            id = id,
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
            fps = 0f,
            resolution = formatResolution(width, height),
          )
        }
      }

      // Fallback: Create basic Video from file if not in MediaStore
      createBasicVideoFromFile(file)
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error creating video from path: $filePath", e)
      null
    }
  }

  private fun createBasicVideoFromFile(file: File): Video {
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
