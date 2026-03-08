package app.marlboroadvance.mpvex.utils.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailUtils {
  private const val TAG = "ThumbnailUtils"

  /**
   * Load thumbnail from MediaStore for a video file.
   * 
   * @param context Android context
   * @param uri Video URI (content:// or file://)
   * @param size Thumbnail size (default 512x512)
   * @return Bitmap thumbnail or null if not found
   */
  suspend fun loadThumbnail(
    context: Context,
    uri: Uri,
    size: Size = Size(512, 512)
  ): Bitmap? = withContext(Dispatchers.IO) {
    try {
      when (uri.scheme) {
        "content" -> loadFromContentUri(context, uri, size)
        "file" -> loadFromFilePath(context, uri.path, size)
        else -> null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load thumbnail for $uri", e)
      null
    }
  }

  private fun loadFromContentUri(
    context: Context,
    uri: Uri,
    size: Size
  ): Bitmap? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, size, null)
      } else {
        @Suppress("DEPRECATION")
        loadLegacyThumbnail(context, uri)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load thumbnail from content URI: $uri", e)
      null
    }
  }

  private fun loadFromFilePath(
    context: Context,
    filePath: String?,
    size: Size
  ): Bitmap? {
    if (filePath == null) return null

    return try {
      // Query MediaStore to find the video by file path
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
          val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
          val contentUri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id
          )
          loadFromContentUri(context, contentUri, size)
        } else {
          null
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load thumbnail from file path: $filePath", e)
      null
    }
  }

  @Suppress("DEPRECATION")
  private fun loadLegacyThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
      val videoId = ContentUris.parseId(uri)
      MediaStore.Video.Thumbnails.getThumbnail(
        context.contentResolver,
        videoId,
        MediaStore.Video.Thumbnails.MINI_KIND,
        null
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load legacy thumbnail", e)
      null
    }
  }
}
