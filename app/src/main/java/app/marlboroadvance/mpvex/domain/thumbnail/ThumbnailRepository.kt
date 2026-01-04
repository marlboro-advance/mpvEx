package app.marlboroadvance.mpvex.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class ThumbnailRepository(
  private val context: Context,
) {
  private val diskCacheDimension = 1024
  private val diskJpegQuality = 50
  private val memoryCache: LruCache<String, Bitmap>
  private val diskDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }
  private val ongoingOperations = ConcurrentHashMap<String, Deferred<Bitmap?>>()

  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private data class FolderState(
    val signature: String,
    @Volatile var nextIndex: Int = 0,
  )

  private val folderStates = ConcurrentHashMap<String, FolderState>()
  private val folderJobs = ConcurrentHashMap<String, Job>()

  private val _thumbnailReadyKeys =
    MutableSharedFlow<String>(
      extraBufferCapacity = 256,
    )
  val thumbnailReadyKeys: SharedFlow<String> = _thumbnailReadyKeys.asSharedFlow()

  init {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = maxMemoryKb / 6
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
      val key = thumbnailKey(video, widthPx, heightPx)

      synchronized(memoryCache) {
        memoryCache.get(key)
      }?.let { return@withContext it }

      ongoingOperations[key]?.let {
        return@withContext it.await()
      }

      val deferred =
        async {
          try {
            // 1) Check disk cache
            loadFromDisk(video)?.let { thumbnail ->
              synchronized(memoryCache) { memoryCache.put(key, thumbnail) }
              _thumbnailReadyKeys.tryEmit(key)
              return@async thumbnail
            }

            // 2) Generate with disk cache dimension
            // FastThumbnails API returns bitmaps at correct aspect ratio
            val thumbnail =
              generateWithFastThumbnails(video, diskCacheDimension)
                ?: return@async null

            // 3) Cache in memory and disk
            synchronized(memoryCache) { memoryCache.put(key, thumbnail) }
            _thumbnailReadyKeys.tryEmit(key)
            writeToDisk(video, thumbnail)

            thumbnail
          } finally {
            ongoingOperations.remove(key)
          }
        }

      ongoingOperations[key] = deferred
      return@withContext deferred.await()
    }

  /**
   * Cache-only thumbnail lookup: checks memory + disk, but never generates.
   * Useful when changing view modes (list/grid) so we don't restart generation.
   */
  suspend fun getCachedThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = thumbnailKey(video, widthPx, heightPx)
      synchronized(memoryCache) { memoryCache.get(key) }?.let { return@withContext it }
      loadFromDisk(video)?.let { thumbnail ->
        synchronized(memoryCache) { memoryCache.put(key, thumbnail) }
        return@withContext thumbnail
      }
      null
    }

  fun getThumbnailFromMemory(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    val key = thumbnailKey(video, widthPx, heightPx)
    return synchronized(memoryCache) { memoryCache.get(key) }
  }

  fun clearThumbnailCache() {
    // Stop any active folder work.
    folderJobs.values.forEach { it.cancel() }
    folderJobs.clear()
    folderStates.clear()
    ongoingOperations.clear()

    synchronized(memoryCache) {
      memoryCache.evictAll()
    }

    // Clear disk cache.
    runCatching {
      if (diskDir.exists()) {
        diskDir.listFiles()?.forEach { it.delete() }
      }
    }
  }

  fun startFolderThumbnailGeneration(
    folderId: String,
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ) {
    val signature = folderSignature(videos, widthPx, heightPx)
    val state =
      folderStates.compute(folderId) { _, existing ->
        if (existing == null || existing.signature != signature) {
          FolderState(signature = signature, nextIndex = 0)
        } else {
          existing
        }
      }!!

    folderJobs.remove(folderId)?.cancel()
    folderJobs[folderId] =
      repositoryScope.launch {
        var i = state.nextIndex
        while (i < videos.size) {
          val video = videos[i]
          getThumbnail(video, widthPx, heightPx)
          i++
          state.nextIndex = i
        }
      }
  }

  fun pauseFolderThumbnailGeneration(folderId: String) {
    folderJobs.remove(folderId)?.cancel()
  }

  fun thumbnailKey(
    video: Video,
    width: Int,
    height: Int,
  ): String {
    val base = videoBaseKey(video)
    return "$base|$width|$height"
  }

  private fun videoBaseKey(video: Video): String {
    val base = video.path.ifBlank { video.uri.toString() }
    // Include mutable file attributes so cache invalidates when file changes.
    return "$base|${video.size}|${video.dateModified}"
  }

  private fun keyToFileName(key: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(key.toByteArray())
    val hex = digest.joinToString("") { b -> "%02x".format(b) }
    return "$hex.jpg"
  }

  private fun diskKey(video: Video): String = "${videoBaseKey(video)}|disk|d$diskCacheDimension"

  private fun loadFromDisk(video: Video): Bitmap? {
    val diskFile = File(diskDir, keyToFileName(diskKey(video)))
    if (!diskFile.exists()) return null
    return runCatching {
      val options =
        BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      BitmapFactory.decodeFile(diskFile.absolutePath, options)
    }.getOrNull()
  }

  private fun writeToDisk(video: Video, bitmap: Bitmap) {
    val diskFile = File(diskDir, keyToFileName(diskKey(video)))
    runCatching {
      FileOutputStream(diskFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, diskJpegQuality, out)
        out.flush()
      }
    }
  }

  private suspend fun rotateIfNeeded(
    video: Video,
    bitmap: Bitmap
  ): Bitmap {
    val rotation = MediaInfoOps.getRotation(context, video.uri, video.displayName)
    if (rotation == 0) return bitmap
    val matrix = android.graphics.Matrix()
    matrix.postRotate(rotation.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  private suspend fun generateWithFastThumbnails(
    video: Video,
    dimension: Int,
  ): Bitmap? {
    return runCatching {
      val positionSec = preferredPositionSeconds(video)
      // New API: generateAsync(path, position, dimension, useHwDec)
     val bmp = FastThumbnails.generateAsync(
          video.path.ifBlank { video.uri.toString() },
          positionSec,
          dimension,
          false
      ) ?: return@runCatching null
      rotateIfNeeded(video, bmp)
    }.getOrNull()
  }

  private fun preferredPositionSeconds(video: Video): Double {
    val durationSec = video.duration / 1000.0
    if (durationSec <= 0.0) return 0.0
    val candidate = min(2.5, durationSec * 0.1)
    return candidate.coerceIn(0.0, max(0.0, durationSec - 0.1))
  }

  private fun folderSignature(
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ): String {
    val md = MessageDigest.getInstance("MD5")
    md.update("$widthPx|$heightPx|".toByteArray())
    for (v in videos) {
      md.update(v.path.toByteArray())
      md.update("|".toByteArray())
      md.update(v.size.toString().toByteArray())
      md.update("|".toByteArray())
      md.update(v.dateModified.toString().toByteArray())
      md.update(";".toByteArray())
    }
    return md.digest().joinToString("") { b -> "%02x".format(b) }
  }
}
