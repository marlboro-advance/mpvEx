package app.marlboroadvance.mpvex.utils.media

import android.util.Log
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

/**
 * Single source of truth for cleaning up all data associated with a deleted
 * video, so removals stay consistent regardless of where the delete happened.
 *
 * Cleans up, for each deleted file:
 * - Recently played history ([RecentlyPlayedOps])
 * - Playback/resume state ([PlaybackStateOps])
 * - Cached extracted metadata ([VideoMetadataCacheRepository])
 * - Playlist items referencing the file ([PlaylistRepository])
 * - Cached thumbnails ([ThumbnailRepository]) when a [Video] is available
 *
 * Used by the in-app delete pipeline (which has full [Video] objects) and by the
 * external-delete reconciliation sweep (which only has paths).
 */
object VideoDeletionReconciler {
  private const val TAG = "VideoDeletionReconciler"

  private val playlistRepository: PlaylistRepository by inject(PlaylistRepository::class.java)
  private val metadataCache: VideoMetadataCacheRepository by inject(VideoMetadataCacheRepository::class.java)
  private val thumbnailRepository: ThumbnailRepository by inject(ThumbnailRepository::class.java)

  /**
   * Reconciles all app data after a set of videos was deleted. Prefer this
   * overload for in-app deletion since the [Video] objects allow thumbnail
   * cache cleanup too.
   */
  suspend fun onVideosDeleted(videos: List<Video>) {
    if (videos.isEmpty()) return
    withContext(Dispatchers.IO) {
      val paths = videos.map { it.path }.filter { it.isNotBlank() }

      cleanupByPaths(paths)

      // Thumbnails require the Video object (keys derive from file metadata).
      videos.forEach { video ->
        runCatching { thumbnailRepository.removeFromCache(video) }
          .onFailure { Log.w(TAG, "Thumbnail cleanup failed for ${video.path}", it) }
      }
    }
  }

  /**
   * Reconciles all app data for deleted files identified only by path (e.g. an
   * external delete detected during a sweep). Thumbnail cache is not touched
   * here because the thumbnail key cannot be reconstructed from a path alone;
   * those linger harmlessly and are cleared by [ThumbnailRepository.clearThumbnailCache].
   */
  suspend fun onVideoPathsDeleted(paths: List<String>) {
    val nonBlank = paths.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return
    withContext(Dispatchers.IO) {
      cleanupByPaths(nonBlank)
    }
  }

  /** Path-based cleanup shared by both entry points. */
  private suspend fun cleanupByPaths(paths: List<String>) {
    if (paths.isEmpty()) return

    paths.forEach { path ->
      runCatching { RecentlyPlayedOps.onVideoDeleted(path) }
        .onFailure { Log.w(TAG, "Recently-played cleanup failed for $path", it) }
      runCatching { PlaybackStateOps.onVideoDeleted(path) }
        .onFailure { Log.w(TAG, "Playback-state cleanup failed for $path", it) }
    }

    runCatching { metadataCache.invalidateVideos(paths) }
      .onFailure { Log.w(TAG, "Metadata cache cleanup failed", it) }

    runCatching {
      val removed = playlistRepository.removeItemsByFilePaths(paths)
      if (removed > 0) Log.d(TAG, "Removed $removed dangling playlist item(s)")
    }.onFailure { Log.w(TAG, "Playlist cleanup failed", it) }
  }
}
