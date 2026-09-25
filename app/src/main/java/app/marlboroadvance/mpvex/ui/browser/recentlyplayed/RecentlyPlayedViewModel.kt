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
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.util.Locale
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
      
      // Get a set of all network playlist IDs to filter them out
      val networkPlaylistIds = mutableSetOf<Int>()
      for (playlistId in allRecentEntities.mapNotNull { it.playlistId }.distinct()) {
        val playlist = playlistRepository.getPlaylistById(playlistId)
        if (playlist?.isM3uPlaylist == true) {
          networkPlaylistIds.add(playlistId)
        }
      }

      for (entity in allRecentEntities) {
        // Skip videos from network playlists
        if (entity.playlistId != null) {
          if (entity.playlistId in networkPlaylistIds) {
            // Skip videos from network playlists
            continue
          }
          playlistMap.getOrPut(entity.playlistId) { mutableListOf() }
            .add(Pair(entity.filePath, entity.timestamp))
        } else {
          standaloneVideos.add(Pair(entity.filePath, entity.timestamp))
        }
      }

      // Create playlist items (excluding network/M3U playlists)
      for (playlistInfo in recentPlaylists) {
        val playlist = playlistRepository.getPlaylistById(playlistInfo.playlistId)
        
        // Skip M3U/network playlists - only include local playlists
        if (playlist != null && !playlist.isM3uPlaylist) {
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

        // Check if this is a network URL
        val isNetworkUri = filePath.startsWith("http://", ignoreCase = true) ||
          filePath.startsWith("https://", ignoreCase = true) ||
          filePath.startsWith("rtmp://", ignoreCase = true) ||
          filePath.startsWith("rtsp://", ignoreCase = true)

        // Skip any kind of streaming playlist entries
        if (isStreamingPlaylist(filePath)) {
          // Skip streaming playlist entries
          continue
        }

        val isContentUri = filePath.startsWith("content://", ignoreCase = true)

        val video = when {
          isNetworkUri -> createNetworkVideoFromUrl(filePath, entity?.videoTitle, entity)
          isContentUri -> createVideoFromContentUri(filePath, Uri.parse(filePath), entity?.videoTitle, entity)
          else -> {
            val file = File(filePath)
            if (file.exists()) {
              createVideoFromFilePath(filePath, file, entity?.videoTitle, entity)
            } else {
              null
            }
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
    entity: RecentlyPlayedEntity? = null,
  ): Video? {
    return try {
      val context = getApplication<Application>()

      // 1. Try MediaStore first to get matching MediaStore ID, duration, size, and content URI
      val mediaStoreVideo = queryVideoFromMediaStore(context, file)
      if (mediaStoreVideo != null) {
        val effectiveDuration = if (mediaStoreVideo.duration > 0L) {
          mediaStoreVideo.duration
        } else {
          entity?.duration?.takeIf { it > 0L } ?: 0L
        }
        val effectiveTitle = parsedVideoTitle ?: mediaStoreVideo.title
        return mediaStoreVideo.copy(
          title = effectiveTitle,
          duration = effectiveDuration,
          durationFormatted = formatDuration(effectiveDuration),
        )
      }

      // 2. Fallback for local files not indexed in MediaStore
      val uri = Uri.fromFile(file)
      val displayName = file.name
      val title = parsedVideoTitle ?: file.nameWithoutExtension

      val entityDuration = entity?.duration?.takeIf { it > 0L } ?: 0L
      val entitySize = entity?.fileSize?.takeIf { it > 0L } ?: file.length()
      val entityWidth = entity?.width ?: 0
      val entityHeight = entity?.height ?: 0

      var duration = entityDuration
      var width = entityWidth
      var height = entityHeight
      var size = entitySize

      // Get metadata from cache or extract it if duration is missing
      if (duration <= 0L) {
        val metadataCache by inject<VideoMetadataCacheRepository>(VideoMetadataCacheRepository::class.java)
        val metadata = metadataCache.getOrExtractMetadata(file, uri, displayName)
        if (metadata != null) {
          if (metadata.durationMs > 0L) duration = metadata.durationMs
          if (metadata.width > 0 && width == 0) width = metadata.width
          if (metadata.height > 0 && height == 0) height = metadata.height
          if (metadata.sizeBytes > 0L && size <= 0L) size = metadata.sizeBytes
        }
      }

      // If still 0, try MediaMetadataRetriever directly on the file
      if (duration <= 0L) {
        runCatching {
          val mmr = android.media.MediaMetadataRetriever()
          mmr.setDataSource(file.absolutePath)
          duration = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
          if (width == 0) {
            width = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
          }
          if (height == 0) {
            height = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
          }
          mmr.release()
        }
      }

      val dateModified = file.lastModified() / 1000
      val dateAdded = dateModified
      val parent = file.parent ?: ""
      val bucketId = parent.hashCode().toString()
      val bucketDisplayName = File(parent).name

      // Determine mime type from extension
      val mimeType = when (file.extension.lowercase(Locale.US)) {
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
        fps = 0f,
        resolution = formatResolution(width, height),
      )
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error creating video from path: $filePath", e)
      null
    }
  }

  private fun queryVideoFromMediaStore(context: Context, file: File): Video? {
    val projection = arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DISPLAY_NAME,
      MediaStore.Video.Media.TITLE,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.DATE_ADDED,
      MediaStore.Video.Media.MIME_TYPE,
      MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.HEIGHT,
    )
    val selection = "${MediaStore.Video.Media.DATA} = ?"
    val selectionArgs = arrayOf(file.absolutePath)

    return runCatching {
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
          val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: file.name
          val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)) ?: file.nameWithoutExtension
          val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
          val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
          val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED))
          val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
          val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/*"
          val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
          val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))

          val uri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id,
          )
          val parent = file.parent ?: ""

          Video(
            id = id,
            title = title,
            displayName = displayName,
            path = file.absolutePath,
            uri = uri,
            duration = duration,
            durationFormatted = formatDuration(duration),
            size = size,
            sizeFormatted = formatFileSize(size),
            dateModified = dateModified,
            dateAdded = dateAdded,
            mimeType = mimeType,
            bucketId = parent.hashCode().toString(),
            bucketDisplayName = File(parent).name,
            width = width,
            height = height,
            fps = 0f,
            resolution = formatResolution(width, height),
          )
        } else null
      }
    }.getOrNull()
  }

  private fun createVideoFromContentUri(
    uriString: String,
    uri: Uri,
    parsedVideoTitle: String?,
    entity: RecentlyPlayedEntity?,
  ): Video? {
    return runCatching {
      val context = getApplication<Application>()
      val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
      )

      var id = uriString.hashCode().toLong()
      var displayName = parsedVideoTitle ?: uri.lastPathSegment ?: "Video"
      var duration = entity?.duration ?: 0L
      var size = entity?.fileSize ?: 0L
      var width = entity?.width ?: 0
      var height = entity?.height ?: 0
      var dateModified = System.currentTimeMillis() / 1000

      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
          if (idCol >= 0) id = cursor.getLong(idCol)

          val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
          if (nameCol >= 0) displayName = cursor.getString(nameCol) ?: displayName

          val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
          if (durCol >= 0 && duration <= 0L) duration = cursor.getLong(durCol)

          val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
          if (sizeCol >= 0 && size <= 0L) size = cursor.getLong(sizeCol)

          val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
          if (dateCol >= 0) dateModified = cursor.getLong(dateCol)

          val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
          if (wCol >= 0 && width == 0) width = cursor.getInt(wCol)

          val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
          if (hCol >= 0 && height == 0) height = cursor.getInt(hCol)
        }
      }

      if (duration <= 0L) {
        runCatching {
          val mmr = android.media.MediaMetadataRetriever()
          mmr.setDataSource(context, uri)
          duration = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
          if (width == 0) {
            width = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
          }
          if (height == 0) {
            height = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
          }
          mmr.release()
        }
      }

      Video(
        id = id,
        title = parsedVideoTitle ?: displayName,
        displayName = displayName,
        path = uriString,
        uri = uri,
        duration = duration,
        durationFormatted = formatDuration(duration),
        size = size,
        sizeFormatted = formatFileSize(size),
        dateModified = dateModified,
        dateAdded = dateModified,
        mimeType = "video/*",
        bucketId = "content",
        bucketDisplayName = "External",
        width = width,
        height = height,
        fps = 0f,
        resolution = formatResolution(width, height),
      )
    }.getOrNull()
  }

  /**
   * Creates a Video object from a network URL
   */
  private fun createNetworkVideoFromUrl(
    url: String,
    parsedVideoTitle: String?,
    entity: RecentlyPlayedEntity?,
  ): Video {
    // Extract URI components
    val uri = Uri.parse(url)
    
    // Use parsed title from database if available, otherwise fallback to URI path
    val videoTitle = parsedVideoTitle ?: uri.lastPathSegment ?: "Stream"
    val displayName = videoTitle
    
    // Use metadata from entity if available
    val duration = entity?.duration ?: 0L
    val size = entity?.fileSize ?: 0L
    val width = entity?.width ?: 0
    val height = entity?.height ?: 0
    
    // Current timestamp for dates (network streams don't have file dates)
    val dateModified = System.currentTimeMillis() / 1000
    val dateAdded = dateModified
    
    // Use host as bucket ID (grouping by domain)
    val bucketId = (uri.host ?: "network").hashCode().toString()
    val bucketDisplayName = uri.host ?: "Network Streams"
    
    // Determine mime type based on URL extension, default to generic video
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
    val mimeType = when (extension) {
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "webm" -> "video/webm"
      "m3u8" -> "application/x-mpegURL"
      "m3u" -> "application/x-mpegURL"
      "mpd" -> "application/dash+xml"
      else -> "video/*"
    }
    
    return Video(
      id = url.hashCode().toLong(),
      title = videoTitle,
      displayName = displayName,
      path = url,
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
      fps = 0f, // Network videos typically don't have fps metadata stored
      resolution = formatResolution(width, height),
    )
  }

  // Basic video creation function removed as it's no longer used

  suspend fun clearAllRecentlyPlayed() {
    try {
      recentlyPlayedRepository.clearAll()
      // The observe flow will automatically update the UI
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error clearing recent videos", e)
    }
  }

  suspend fun deleteVideosFromHistory(videos: List<Video>, deleteFiles: Boolean = false): Pair<Int, Int> {
    return try {
      var successCount = 0
      var failCount = 0

      videos.forEach { video ->
        try {
          // Delete from history database
          recentlyPlayedRepository.deleteByFilePath(video.path)

          // If deleteFiles is true and it's a local file, delete the actual file
          if (deleteFiles) {
            // Check if it's a local file (not a network URL)
            val isNetworkUri = video.path.startsWith("http://", ignoreCase = true) ||
              video.path.startsWith("https://", ignoreCase = true) ||
              video.path.startsWith("rtmp://", ignoreCase = true) ||
              video.path.startsWith("rtsp://", ignoreCase = true)

            if (!isNetworkUri) {
              val (deleted, failed) =
                PermissionUtils.StorageOps.deleteVideos(
                  getApplication(),
                  listOf(video),
                )
              if (deleted <= 0 || failed > 0) {
                Log.w("RecentlyPlayedViewModel", "Failed to delete file: ${video.path}")
                failCount++
              } else {
                Log.d("RecentlyPlayedViewModel", "Deleted file: ${video.path}")
              }
            }
          }

          successCount++
        } catch (e: Exception) {
          Log.e("RecentlyPlayedViewModel", "Error deleting video from history: ${video.path}", e)
          failCount++
        }
      }

      Pair(successCount, failCount)
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error deleting videos from history", e)
      Pair(0, videos.size)
    }
  }

  suspend fun deletePlaylistsFromHistory(playlistIds: List<Int>): Pair<Int, Int> {
    return try {
      var successCount = 0
      var failCount = 0
      
      playlistIds.forEach { playlistId ->
        try {
          recentlyPlayedRepository.deleteByPlaylistId(playlistId)
          successCount++
        } catch (e: Exception) {
          Log.e("RecentlyPlayedViewModel", "Error deleting playlist from history: $playlistId", e)
          failCount++
        }
      }
      
      Pair(successCount, failCount)
    } catch (e: Exception) {
      Log.e("RecentlyPlayedViewModel", "Error deleting playlists from history", e)
      Pair(0, playlistIds.size)
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
  
  /**
   * Checks if a URL is likely a streaming playlist (M3U, HLS, DASH, etc.)
   * 
   * @param url The URL to check
   * @return True if the URL appears to be a streaming playlist
   */
  private fun isStreamingPlaylist(url: String): Boolean {
    val lowerCaseUrl = url.lowercase()
    
    // Direct extensions
    if (lowerCaseUrl.endsWith(".m3u") || 
        lowerCaseUrl.endsWith(".m3u8") ||
        lowerCaseUrl.endsWith(".mpd")) {
      return true
    }
    
    // Common playlist keywords
    if (lowerCaseUrl.contains("playlist") || 
        lowerCaseUrl.contains("manifest")) {
      return true
    }
    
    // Index files with streaming format indicators
    if (lowerCaseUrl.contains("index") && (
        lowerCaseUrl.contains(".m3u") ||
        lowerCaseUrl.contains("hls") ||
        lowerCaseUrl.contains("dash") ||
        lowerCaseUrl.contains("mpd"))) {
      return true
    }
    
    // IPTV and streaming service patterns
    if (lowerCaseUrl.contains("iptv") ||
        lowerCaseUrl.contains("channel") && lowerCaseUrl.contains("stream")) {
      return true
    }
    
    return false
  }


  
  companion object {
    fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        RecentlyPlayedViewModel(application)
      }
    }
  }
}
