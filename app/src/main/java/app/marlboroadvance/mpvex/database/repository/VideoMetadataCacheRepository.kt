package app.marlboroadvance.mpvex.database.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import app.marlboroadvance.mpvex.database.dao.VideoMetadataDao
import app.marlboroadvance.mpvex.database.entities.VideoMetadataEntity
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

/**
 * Repository for caching video metadata with parallel processing support
 * Provides progressive loading and batch processing capabilities
 */
class VideoMetadataCacheRepository(
  private val context: Context,
  private val dao: VideoMetadataDao,
) {
  companion object {
    private const val TAG = "VideoMetadataCache"
    private const val PARALLEL_PROCESSING_LIMIT = 6 // Process 6 videos simultaneously
    private const val CACHE_VALIDITY_DAYS = 30L
  }

  suspend fun getCachedMetadata(
    file: File,
    dateModified: Long,
    size: Long,
  ): MediaInfoOps.VideoMetadata? =
    withContext(Dispatchers.IO) {
      val path = file.absolutePath
      val cached = dao.getMetadata(path, dateModified, size)
      cached?.let {
        MediaInfoOps.VideoMetadata(
          sizeBytes = it.size,
          durationMs = it.duration,
          width = it.width,
          height = it.height,
          fps = it.fps,
        )
      }
    }

  suspend fun getOrExtractMetadata(
    file: File,
    uri: Uri,
    displayName: String,
  ): MediaInfoOps.VideoMetadata? =
    withContext(Dispatchers.IO) {
      val path = file.absolutePath
      val size = file.length()
      val dateModified = file.lastModified() / 1000

      val cached = dao.getMetadata(path, dateModified, size)
      if (cached != null) {
        return@withContext MediaInfoOps.VideoMetadata(
          sizeBytes = cached.size,
          durationMs = cached.duration,
          width = cached.width,
          height = cached.height,
          fps = cached.fps,
        )
      }

      val result = MediaInfoOps.extractBasicMetadata(context, uri, displayName)

      result.onSuccess { metadata ->
        // Save to cache
        dao.insertMetadata(
          VideoMetadataEntity(
            path = path,
            size = size,
            dateModified = dateModified,
            duration = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            fps = metadata.fps,
            lastScanned = System.currentTimeMillis(),
          ),
        )
      }.onFailure { error ->
        Log.w(TAG, "Failed to extract metadata for $displayName: ${error.message}")
      }

      result.getOrNull()
    }

  /**
   * Extract metadata for multiple videos in parallel with progressive updates
   * Emits results as they become available (progressive loading)
   *
   * @param videos List of (File, Uri, DisplayName) triples
   * @return Flow that emits (path, metadata) pairs as each video is processed
   */
  fun extractMetadataProgressively(
    videos: List<Triple<File, Uri, String>>,
  ): Flow<Pair<String, MediaInfoOps.VideoMetadata?>> =
    flow {
      // Process videos in batches to limit concurrent operations
      videos.chunked(PARALLEL_PROCESSING_LIMIT).forEach { batch ->
        coroutineScope {
          val results =
            batch.map { (file, uri, displayName) ->
              async {
                val metadata = getOrExtractMetadata(file, uri, displayName)
                file.absolutePath to metadata
              }
            }.awaitAll()

          // Emit each result as it completes
          results.forEach { result ->
            emit(result)
          }
        }
      }
    }

  /**
   * Batch extract metadata for videos without blocking UI
   * Processes in parallel and returns all results at once
   */
  suspend fun extractMetadataBatch(
    videos: List<Triple<File, Uri, String>>,
  ): Map<String, MediaInfoOps.VideoMetadata> =
    withContext(Dispatchers.IO) {
      val results = mutableMapOf<String, MediaInfoOps.VideoMetadata>()

      videos.chunked(PARALLEL_PROCESSING_LIMIT).forEach { batch ->
        coroutineScope {
          val batchResults =
            batch.map { (file, uri, displayName) ->
              async {
                val metadata = getOrExtractMetadata(file, uri, displayName)
                if (metadata != null) {
                  file.absolutePath to metadata
                } else {
                  null
                }
              }
            }.awaitAll().filterNotNull()

          results.putAll(batchResults)
        }
      }

      results
    }

  /**
   * Clear old cache entries (older than CACHE_VALIDITY_DAYS)
   */
  suspend fun clearOldCache() {
    val cutoffTimestamp = System.currentTimeMillis() - (CACHE_VALIDITY_DAYS * 24 * 60 * 60 * 1000)
    dao.clearOldCache(cutoffTimestamp)
    Log.d(TAG, "Cleared cache entries older than $CACHE_VALIDITY_DAYS days")
  }

  /**
   * Invalidate cache for deleted/moved/renamed videos
   * Checks if cached files still exist and removes stale entries
   */
  suspend fun invalidateStaleEntries() {
    withContext(Dispatchers.IO) {
      val cachedPaths = dao.getAllCachedPaths()
      if (cachedPaths.isEmpty()) return@withContext

      Log.d(TAG, "Validating ${cachedPaths.size} cached entries...")

      val existingPaths = cachedPaths.filter { path ->
        File(path).exists()
      }

      val staleCount = cachedPaths.size - existingPaths.size
      if (staleCount > 0) {
        // Delete entries for non-existent files
        if (existingPaths.isNotEmpty()) {
          // SQLite has a limit on IN clause size, so chunk if needed
          existingPaths.chunked(999).forEach { chunk ->
            dao.deleteStaleEntries(chunk)
          }
        } else {
          // All cached files are gone, clear everything
          dao.clearAll()
        }
        Log.d(TAG, "Removed $staleCount stale cache entries (deleted/moved/renamed videos)")
      } else {
        Log.d(TAG, "No stale entries found")
      }
    }
  }

  /**
   * Invalidate cache for specific video (when deleted/moved/renamed)
   */
  suspend fun invalidateVideo(path: String) {
    withContext(Dispatchers.IO) {
      dao.deleteByPath(path)
      Log.d(TAG, "Invalidated cache for: $path")
    }
  }

  /**
   * Invalidate cache for multiple videos (batch operation)
   */
  suspend fun invalidateVideos(paths: List<String>) {
    withContext(Dispatchers.IO) {
      if (paths.isEmpty()) return@withContext
      dao.deleteByPaths(paths)
      Log.d(TAG, "Invalidated cache for ${paths.size} videos")
    }
  }

  /**
   * Get cache statistics
   */
  suspend fun getCacheStats(): CacheStats =
    withContext(Dispatchers.IO) {
      CacheStats(
        totalEntries = dao.getCacheCount(),
        totalSizeBytes = dao.getTotalCacheSize() ?: 0L,
      )
    }

  /**
   * Clear all cached metadata
   */
  suspend fun clearAll() {
    dao.clearAll()
    Log.d(TAG, "Cleared all cached metadata")
  }

  /**
   * Perform comprehensive cache maintenance
   * - Remove stale entries (deleted/moved files)
   * - Clear old entries (30+ days)
   * Call this periodically (e.g., on app start)
   */
  suspend fun performMaintenance() {
    withContext(Dispatchers.IO) {
      Log.d(TAG, "Starting cache maintenance...")
      val startTime = System.currentTimeMillis()

      // Step 1: Remove stale entries
      invalidateStaleEntries()

      // Step 2: Clear old entries
      clearOldCache()

      val duration = System.currentTimeMillis() - startTime
      val stats = getCacheStats()
      Log.d(
        TAG,
        "Cache maintenance completed in ${duration}ms. " +
          "Entries: ${stats.totalEntries}, Size: ${formatSize(stats.totalSizeBytes)}",
      )
    }
  }

  private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
  }

  data class CacheStats(
    val totalEntries: Int,
    val totalSizeBytes: Long,
  )
}
