package app.marlboroadvance.mpvex.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.scale
import app.marlboroadvance.mpvex.domain.media.model.Video
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines. Dispatchers
import kotlinx.coroutines.async
import kotlinx. coroutines.withContext
import java. io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ThumbnailRepository(
  private val context: Context,
) {
  private val memoryCache: LruCache<String, Bitmap>
  private val diskDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }
  private val ongoingOperations = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Bitmap?>>()

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
      val key = buildKey(video, widthPx, heightPx)

      memoryCache.get(key)?.let { return@withContext it }

      ongoingOperations[key]?.let {
        return@withContext it.await()
      }

      val deferred =
        async {
          try {
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

            val generated = generateThumbnail(video, widthPx, heightPx)
            if (generated != null) {
              async(Dispatchers.IO) {
                runCatching {
                  FileOutputStream(diskFile).use { out ->
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

  fun getThumbnailFromMemory(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    val key = buildKey(video, widthPx, heightPx)
    return memoryCache.get(key)
  }

  suspend fun prefetchThumbnails(
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ) = withContext(Dispatchers.IO) {
    videos.take(10).map { video ->
      async {
        val key = buildKey(video, widthPx, heightPx)
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

  private suspend fun generateThumbnail(
    video: Video,
    width: Int,
    height: Int,
  ): Bitmap? {
    val isNetworkVideo = video.uri.scheme in listOf("http", "https", "smb", "ftp", "webdav", "rtmp", "rtsp")
    if (isNetworkVideo) {
      android.util.Log.d("ThumbnailRepository", "Network video: ${video.uri}")

      val cacheDir = File(context.cacheDir, "recently_played_thumbs")
      val thumbnailFile = File(cacheDir, "thumb_${video.uri.toString().hashCode()}.jpg")

      if (thumbnailFile.exists()) {
        android.util.Log.d("ThumbnailRepository", "Found cached thumbnail")
        return runCatching {
          val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
          }
          BitmapFactory.decodeFile(thumbnailFile.absolutePath, options)?.let { bmp ->
            if (bmp.width != width || bmp.height != height) {
              bmp.scale(width, height)
            } else {
              bmp
            }
          }
        }.getOrNull()
      }
      return null
    }

    // Try embedded thumbnail first
    val embeddedThumbnail = extractEmbeddedThumbnail(video, width, height)
    if (embeddedThumbnail != null) {
      android.util. Log.d("ThumbnailRepository", "Using embedded thumbnail for:  ${video.path}")
      return embeddedThumbnail
    }

    // 2. Try sidecar image (same name, different extension)
    val sidecarThumbnail = findSidecarThumbnail(video, width, height)
    if (sidecarThumbnail != null) {
      android.util.Log.d("ThumbnailRepository", "Using sidecar thumbnail for: ${video. path}")
      return sidecarThumbnail
    }

    // 3. Fallback to MediaStore
    android.util.Log. d("ThumbnailRepository", "No embedded/sidecar thumbnail, using MediaStore for: ${video.path}")
    return generateMediaStoreThumbnail(video, width, height)
  }

  private fun extractEmbeddedThumbnail(
    video: Video,
    width:  Int,
    height: Int,
  ): Bitmap? {
    return runCatching {
      val retriever = MediaMetadataRetriever()
      try {
        if (video.uri.scheme == "content") {
          retriever.setDataSource(context, video.uri)
        } else {
          retriever. setDataSource(video.path)
        }

        val embeddedPicture = retriever.embeddedPicture
        if (embeddedPicture != null) {
          val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
          }
          val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size, options)
          bitmap?. let { bmp ->
            if (bmp.width != width || bmp.height != height) {
              bmp.scale(width, height)
            } else {
              bmp
            }
          }
        } else {
          null
        }
      } finally {
        retriever.release()
      }
    }.getOrNull()
  }

  private suspend fun findSidecarThumbnail(
    video: Video,
    width:  Int,
    height: Int,
  ): Bitmap? {
    if (video.uri. scheme == "content") return null

    val videoFile = File(video. path)
    val parentDir = videoFile.parentFile ?:  return null
    val videoNameWithoutExtension = videoFile. nameWithoutExtension

    val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    for (ext in imageExtensions) {
      val sidecarFile = File(parentDir, "$videoNameWithoutExtension.$ext")
      if (sidecarFile.exists()) {
        android.util. Log.d("ThumbnailRepository", "Found sidecar thumbnail: ${sidecarFile. absolutePath}")
        return loadImageWithCoil(sidecarFile, width, height)
      }
    }
    return null
  }

  private suspend fun loadImageWithCoil(
    file: File,
    width: Int,
    height: Int,
  ): Bitmap? {
    return runCatching {
      val imageLoader = ImageLoader. Builder(context).build()

      val request = ImageRequest.Builder(context)
        .data(file)
        .size(width, height)
        .build()

      val result = imageLoader.execute(request)
      if (result is SuccessResult) {
        result.image.toBitmap()
      } else {
        null
      }
    }.getOrNull()
  }

  private fun generateMediaStoreThumbnail(
    video: Video,
    width:  Int,
    height: Int,
  ): Bitmap? {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ - use ContentResolver.loadThumbnail
        if (video.uri.scheme == "content") {
          // If we have a content URI, use it directly
          context.contentResolver.loadThumbnail(video.uri, Size(width, height), null)
        } else {
          // If we have a file path, query MediaStore for the content URI
          val contentUri = getMediaStoreUri(video.path)
          if (contentUri != null) {
            context.contentResolver.loadThumbnail(contentUri, Size(width, height), null)
          } else {
            // Fallback to ThumbnailUtils if not in MediaStore
            ThumbnailUtils.createVideoThumbnail(File(video.path), Size(width, height), null)
          }
        }
      } else {
        // Android 9 and below - use legacy ThumbnailUtils with MediaStore
        @Suppress("DEPRECATION")
        val raw = ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND)
        raw?.let { bmp ->
          if (bmp.width != width || bmp.height != height) {
            bmp.scale(width, height)
          } else {
            bmp
          }
        }
      }
    }.getOrNull()
  }

  private fun getMediaStoreUri(filePath: String): android.net.Uri? {
    return runCatching {
      val projection = arrayOf(MediaStore.Video.Media._ID)
      val selection = "${MediaStore.Video.Media.DATA} = ?"
      val selectionArgs = arrayOf(filePath)

      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
          val id = cursor.getLong(idColumn)
          android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        } else {
          null
        }
      }
    }.getOrNull()
  }
}
