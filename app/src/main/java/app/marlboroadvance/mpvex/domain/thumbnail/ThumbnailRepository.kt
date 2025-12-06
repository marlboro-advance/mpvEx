package app.marlboroadvance.mpvex.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.scale
import app.marlboroadvance.mpvex.domain.media.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Ultra-fast thumbnail provider with optimized caching and generation.
 *
 * Optimizations:
 * - Prefer platform thumbnails (MediaStore.loadThumbnail) for content URIs.
 * - Fallback to ThumbnailUtils for file paths.
 * - Multi-level caching: Memory (LruCache) + Disk cache with concurrent access.
 * - Parallel thumbnail generation with coroutines.
 * - JPEG compression with optimized quality for smaller file sizes.
 * - Prefetching support for upcoming thumbnails.
 */
class ThumbnailRepository(
  private val context: Context,
) {
  private val memoryCache: LruCache<String, Bitmap>
  private val diskDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }

  // Track ongoing operations to prevent duplicate work
  private val ongoingOperations = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Bitmap?>>()

  init {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = maxMemoryKb / 6 // Increased from 1/8 to 1/6 for better hit rate
    memoryCache =
      object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(
          key: String,
          value: Bitmap,
        ): Int = value.byteCount / 1024
      }
  }

  /**
   * Get thumbnail with optimized caching and generation.
   */
  suspend fun getThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = buildKey(video, widthPx, heightPx)

      // 1) Check memory cache
      memoryCache.get(key)?.let { return@withContext it }

      // 2) Check if operation is already in progress
      ongoingOperations[key]?.let {
        return@withContext it.await()
      }

      // 3) Start new operation
      val deferred =
        async {
          try {
            // Check disk cache
            val diskFile = File(diskDir, keyToFileName(key))
            if (diskFile.exists()) {
              val options =
                BitmapFactory.Options().apply {
                  inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                }
              BitmapFactory.decodeFile(diskFile.absolutePath, options)?.let { bmp ->
                memoryCache.put(key, bmp)
                return@async bmp
              }
            }

            // Generate new thumbnail
            val generated = generateThumbnail(video, widthPx, heightPx)
            if (generated != null) {
              // Persist to disk cache asynchronously
              async(Dispatchers.IO) {
                runCatching {
                  FileOutputStream(diskFile).use { out ->
                    // Optimized JPEG compression (85 quality for better size/quality balance)
                    generated.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                  }
                }
              }
              memoryCache.put(key, generated)
            }
            generated
          } finally {
            ongoingOperations.remove(key)
          }
        }

      ongoingOperations[key] = deferred
      return@withContext deferred.await()
    }

  /**
   * Synchronously check if thumbnail exists in memory cache.
   * Useful for immediate UI updates without suspending.
   */
  fun getThumbnailFromMemory(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    val key = buildKey(video, widthPx, heightPx)
    return memoryCache.get(key)
  }

  /**
   * Prefetch thumbnails for a list of videos (non-blocking).
   * Useful for preloading upcoming items in a list.
   */
  suspend fun prefetchThumbnails(
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ) = withContext(Dispatchers.IO) {
    videos.take(10).map { video ->
      // Prefetch next 10 items
      async {
        val key = buildKey(video, widthPx, heightPx)
        // Only prefetch if not in memory and not already being processed
        if (memoryCache.get(key) == null && !ongoingOperations.containsKey(key)) {
          getThumbnail(video, widthPx, heightPx)
        }
      }
    }
  }

  private fun buildKey(
    video: Video,
    width: Int,
    height: Int,
  ): String {
    val base = if (video.uri.scheme == "content") video.uri.toString() else video.path
    return "$base|$width|$height|${video.size}|${video.dateModified}"
  }

  private fun keyToFileName(key: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(key.toByteArray())
    val hex = digest.joinToString("") { b -> "%02x".format(b) }
    return "$hex.jpg"
  }

  private fun generateThumbnail(
    video: Video,
    width: Int,
    height: Int,
  ): Bitmap? {
    // 1) Try platform thumbnail for indexed content URIs (fastest method)
    if (video.uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching {
        return context.contentResolver.loadThumbnail(video.uri, Size(width, height), null)
      }
    }

    // 2) Fallback to ThumbnailUtils for file paths
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching {
        ThumbnailUtils.createVideoThumbnail(File(video.path), Size(width, height), null)
      }.getOrNull()
    } else {
      // Deprecated API for pre-Q. May not match exact size; scale down if needed.
      val raw =
        runCatching {
          @Suppress("DEPRECATION")
          ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND)
        }.getOrNull()
      raw?.let { bmp ->
        if (bmp.width == width && bmp.height == height) return bmp
        bmp.scale(width, height)
      }
    }
  }
}
