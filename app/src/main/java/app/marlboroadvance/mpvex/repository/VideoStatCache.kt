package app.marlboroadvance.mpvex.repository

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance JSON streaming cache for folder videos with filesystem stat tracking.
 *
 * Performance features:
 * - Direct streaming with [JsonReader] and [JsonWriter] (zero intermediate JSONObject/JSONArray tree allocations).
 * - 16KB buffered streaming I/O for minimum disk syscalls.
 * - In-memory stat table storing only 64-bit timestamps (folderLastModified) for sub-microsecond stat checks.
 * - Atomic writes via .tmp files to prevent corruption.
 */
object VideoStatCache {
  private const val TAG = "VideoStatCache"
  private const val CACHE_DIR_NAME = "video_stat_cache"
  private const val BUFFER_SIZE = 16384

  data class VideoStatEntry(
    val folderLastModified: Long,
    val videos: List<Video>,
  )

  // Ultra-compact in-memory map storing folder stat timestamps
  private val folderStatTimestamps = ConcurrentHashMap<String, Long>()

  // Instant in-memory cache for parsed entries (0ms access)
  private val memCache = ConcurrentHashMap<String, VideoStatEntry>()

  private fun getCacheDir(context: Context): File {
    val dir = File(context.filesDir, CACHE_DIR_NAME)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  private fun getCacheFile(context: Context, bucketId: String): File {
    val hash = bucketId.hashCode().toUInt().toString(16)
    return File(getCacheDir(context), "$hash.json")
  }

  /**
   * Preload folder cache in background so it's ready in memory before user taps.
   */
  fun preload(context: Context, bucketId: String) {
    if (!memCache.containsKey(bucketId)) {
      load(context, bucketId)
    }
  }

  fun invalidate(bucketId: String) {
    folderStatTimestamps.remove(bucketId)
    memCache.remove(bucketId)
  }

  fun clear() {
    folderStatTimestamps.clear()
    memCache.clear()
  }

  /**
   * Fast check if the folder's filesystem lastModified matches the cached stat timestamp.
   * Returns true if timestamps match and are non-zero.
   */
  fun isFolderStatUnchanged(bucketId: String, currentFolderMod: Long): Boolean {
    if (currentFolderMod <= 0L) return false
    val cachedMod = folderStatTimestamps[bucketId] ?: return false
    return cachedMod == currentFolderMod
  }

  /**
   * Load cached videos and folder stat timestamp.
   * Returns immediately from in-memory cache if available (0ms), otherwise streams from disk JSON.
   */
  fun load(context: Context, bucketId: String): VideoStatEntry? {
    memCache[bucketId]?.let { return it }

    val file = getCacheFile(context, bucketId)
    if (!file.exists() || file.length() == 0L) return null

    return try {
      FileInputStream(file).use { fis ->
        BufferedReader(InputStreamReader(fis, Charsets.UTF_8), BUFFER_SIZE).use { br ->
          JsonReader(br).use { reader ->
            var folderLastModified = 0L
            var videos: List<Video> = emptyList()

            reader.beginObject()
            while (reader.hasNext()) {
              when (reader.nextName()) {
                "folderLastModified" -> folderLastModified = reader.nextLong()
                "videos" -> {
                  reader.beginArray()
                  val list = ArrayList<Video>()
                  while (reader.hasNext()) {
                    list.add(readVideo(reader))
                  }
                  reader.endArray()
                  videos = list
                }
                else -> reader.skipValue()
              }
            }
            reader.endObject()

            if (folderLastModified > 0L) {
              folderStatTimestamps[bucketId] = folderLastModified
            }

            val entry = VideoStatEntry(folderLastModified, videos)
            memCache[bucketId] = entry
            entry
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stream video stat cache for $bucketId", e)
      null
    }
  }

  /**
   * Atomically save folder videos and directory stat timestamp using high-speed streaming [JsonWriter].
   */
  fun save(context: Context, bucketId: String, folderLastModified: Long, videos: List<Video>) {
    folderStatTimestamps[bucketId] = folderLastModified
    memCache[bucketId] = VideoStatEntry(folderLastModified, videos)
    val cacheFile = getCacheFile(context, bucketId)
    val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")

    try {
      FileOutputStream(tempFile).use { fos ->
        BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), BUFFER_SIZE).use { bw ->
          JsonWriter(bw).use { writer ->
            writer.beginObject()
            writer.name("bucketId").value(bucketId)
            writer.name("folderLastModified").value(folderLastModified)
            writer.name("videos").beginArray()
            for (i in videos.indices) {
              writeVideo(writer, videos[i])
            }
            writer.endArray()
            writer.endObject()
          }
        }
      }

      if (tempFile.exists()) {
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
        tempFile.renameTo(cacheFile)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stream-save video stat cache for $bucketId", e)
    }
  }

  private fun readVideo(reader: JsonReader): Video {
    reader.beginObject()
    var id = 0L
    var title = ""
    var displayName = ""
    var path = ""
    var uriStr = ""
    var duration = 0L
    var durationFormatted = ""
    var size = 0L
    var sizeFormatted = ""
    var dateModified = 0L
    var dateAdded = 0L
    var mimeType = "video/*"
    var bucketId = ""
    var bucketDisplayName = ""
    var width = 0
    var height = 0
    var fps = 0f
    var resolution = "--"
    var hasEmbeddedSubtitles = false
    var subtitleCodec = ""

    while (reader.hasNext()) {
      when (reader.nextName()) {
        "id" -> id = if (reader.peek() != android.util.JsonToken.NULL) reader.nextLong() else { reader.nextNull(); 0L }
        "title" -> title = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "displayName" -> displayName = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "path" -> path = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "uri" -> uriStr = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "duration" -> duration = if (reader.peek() != android.util.JsonToken.NULL) reader.nextLong() else { reader.nextNull(); 0L }
        "durationFormatted" -> durationFormatted = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "size" -> size = if (reader.peek() != android.util.JsonToken.NULL) reader.nextLong() else { reader.nextNull(); 0L }
        "sizeFormatted" -> sizeFormatted = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "dateModified" -> dateModified = if (reader.peek() != android.util.JsonToken.NULL) reader.nextLong() else { reader.nextNull(); 0L }
        "dateAdded" -> dateAdded = if (reader.peek() != android.util.JsonToken.NULL) reader.nextLong() else { reader.nextNull(); 0L }
        "mimeType" -> mimeType = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "video/*" }
        "bucketId" -> bucketId = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "bucketDisplayName" -> bucketDisplayName = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        "width" -> width = if (reader.peek() != android.util.JsonToken.NULL) reader.nextInt() else { reader.nextNull(); 0 }
        "height" -> height = if (reader.peek() != android.util.JsonToken.NULL) reader.nextInt() else { reader.nextNull(); 0 }
        "fps" -> fps = if (reader.peek() != android.util.JsonToken.NULL) reader.nextDouble().toFloat() else { reader.nextNull(); 0f }
        "resolution" -> resolution = if (reader.peek() != android.util.JsonToken.NULL) {
          val r = reader.nextString()
          if (r.isEmpty()) "--" else r
        } else { reader.nextNull(); "--" }
        "hasEmbeddedSubtitles" -> hasEmbeddedSubtitles = if (reader.peek() != android.util.JsonToken.NULL) reader.nextBoolean() else { reader.nextNull(); false }
        "subtitleCodec" -> subtitleCodec = if (reader.peek() != android.util.JsonToken.NULL) reader.nextString() else { reader.nextNull(); "" }
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return Video(
      id = id,
      title = title,
      displayName = displayName,
      path = path,
      uri = Uri.parse(uriStr),
      duration = duration,
      durationFormatted = durationFormatted,
      size = size,
      sizeFormatted = sizeFormatted,
      dateModified = dateModified,
      dateAdded = dateAdded,
      mimeType = mimeType,
      bucketId = bucketId,
      bucketDisplayName = bucketDisplayName,
      width = width,
      height = height,
      fps = fps,
      resolution = resolution,
      hasEmbeddedSubtitles = hasEmbeddedSubtitles,
      subtitleCodec = subtitleCodec,
    )
  }

  private fun writeVideo(writer: JsonWriter, v: Video) {
    writer.beginObject()
    writer.name("id").value(v.id)
    writer.name("title").value(v.title)
    writer.name("displayName").value(v.displayName)
    writer.name("path").value(v.path)
    writer.name("uri").value(v.uri.toString())
    writer.name("duration").value(v.duration)
    writer.name("durationFormatted").value(v.durationFormatted)
    writer.name("size").value(v.size)
    writer.name("sizeFormatted").value(v.sizeFormatted)
    writer.name("dateModified").value(v.dateModified)
    writer.name("dateAdded").value(v.dateAdded)
    writer.name("mimeType").value(v.mimeType)
    writer.name("bucketId").value(v.bucketId)
    writer.name("bucketDisplayName").value(v.bucketDisplayName)
    writer.name("width").value(v.width.toLong())
    writer.name("height").value(v.height.toLong())
    writer.name("fps").value(if (v.fps.isNaN() || v.fps.isInfinite()) 0.0 else v.fps.toDouble())
    writer.name("resolution").value(v.resolution)
    writer.name("hasEmbeddedSubtitles").value(v.hasEmbeddedSubtitles)
    writer.name("subtitleCodec").value(v.subtitleCodec)
    writer.endObject()
  }
}
