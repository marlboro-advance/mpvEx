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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Ultra-fast thumbnail provider.
 *
 * Strategy:
 * - Prefer platform thumbnails (MediaStore.loadThumbnail) for content URIs.
 * - Fallback to ThumbnailUtils for file paths.
 * - Memory (LruCache) + disk cache (filesDir/thumbnails) keyed by uri/path + size + mtime.
 */
class ThumbnailRepository(
  private val context: Context,
) {
  private val memoryCache: LruCache<String, Bitmap>
  private val diskDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }

  init {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = maxMemoryKb / 8 // Use 1/8th of available memory for thumbnails
    memoryCache =
      object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(
          key: String,
          value: Bitmap,
        ): Int = value.byteCount / 1024
      }
  }

  suspend fun getThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = buildKey(video, widthPx, heightPx)
      memoryCache.get(key)?.let { return@withContext it }

      val diskFile = File(diskDir, keyToFileName(key))
      if (diskFile.exists()) {
        BitmapFactory.decodeFile(diskFile.absolutePath)?.let { bmp ->
          memoryCache.put(key, bmp)
          return@withContext bmp
        }
      }

      val generated = generateThumbnail(video, widthPx, heightPx)
      if (generated != null) {
        // Persist to disk cache
        runCatching {
          FileOutputStream(diskFile).use { out ->
            // JPEG for smaller size. If you need transparency, switch to PNG.
            generated.compress(Bitmap.CompressFormat.JPEG, 80, out)
          }
        }
        memoryCache.put(key, generated)
      }
      return@withContext generated
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
    // 1) Try platform thumbnail for indexed content URIs
    if (video.uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching {
        return context.contentResolver.loadThumbnail(video.uri, Size(width, height), null)
      }
    }

    // 2) Fallback to ThumbnailUtils for file paths
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching { ThumbnailUtils.createVideoThumbnail(File(video.path), Size(width, height), null) }.getOrNull()
    } else {
      // Deprecated API for pre-Q. May not match exact size; scale down if needed.
      val raw =
        runCatching {
          ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND)
        }.getOrNull()
      raw?.let { bmp ->
        if (bmp.width == width && bmp.height == height) return bmp
        bmp.scale(width, height)
      }
    }
  }
}
