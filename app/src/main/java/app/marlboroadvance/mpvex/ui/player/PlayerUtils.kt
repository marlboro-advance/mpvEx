package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.ui.player.PlayerActivity.Companion.TAG
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Try to get the real file path from a content:// URI
 * Tries multiple methods before falling back to file descriptor
 */
internal fun Uri.openContentFd(context: Context): String? {
  // Method 1: Try to get real path from file descriptor
  context.contentResolver.openFileDescriptor(this, "r")?.use { pfd ->
    Utils.findRealPath(pfd.fd)?.let { realPath ->
      Log.d(TAG, "Resolved content URI to real path via fd: $realPath")
      return realPath
    }
  }

  // Method 2: Try to query _data column (MediaStore files)
  try {
    context.contentResolver
      .query(
        this,
        arrayOf(MediaStore.MediaColumns.DATA),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
          if (columnIndex != -1) {
            val path = cursor.getString(columnIndex)
            if (!path.isNullOrBlank() && File(path).exists()) {
              Log.d(TAG, "Resolved content URI via MediaStore query: $path")
              return path
            }
          }
        }
      }
  } catch (e: Exception) {
    Log.d(TAG, "Failed to query MediaStore: ${e.message}")
  }

  // Method 3: Try to parse document URI path
  if (DocumentsContract.isDocumentUri(context, this)) {
    try {
      val docId = DocumentsContract.getDocumentId(this)
      Log.d(TAG, "Document ID: $docId")

      // Parse document ID (format: "primary:path/to/file" or "raw:/storage/path")
      when {
        docId.startsWith("primary:") -> {
          val path = docId.substringAfter("primary:")
          val primaryPath = "/storage/emulated/0/$path"
          if (File(primaryPath).exists()) {
            Log.d(TAG, "Resolved document URI to primary storage: $primaryPath")
            return primaryPath
          }
        }

        docId.startsWith("raw:") -> {
          val rawPath = docId.substringAfter("raw:")
          if (File(rawPath).exists()) {
            Log.d(TAG, "Resolved document URI from raw path: $rawPath")
            return rawPath
          }
        }

        docId.contains(":") -> {
          // Try other storage types (e.g., "1234-5678:path/to/file")
          val path = docId.substringAfter(":")
          // Try common storage locations
          val possiblePaths =
            listOf(
              "/storage/emulated/0/$path",
              "/storage/$path",
              "/mnt/media_rw/$path",
            )
          for (possiblePath in possiblePaths) {
            if (File(possiblePath).exists()) {
              Log.d(TAG, "Resolved document URI to: $possiblePath")
              return possiblePath
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.d(TAG, "Failed to parse document URI: ${e.message}")
    }
  }

  // Fallback: Use file descriptor
  return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let { fd ->
    Log.d(TAG, "Could not resolve content URI to real path, using fd://$fd")
    "fd://$fd"
  }
}

internal fun Uri.resolveUri(context: Context): String? {
  // Handle null scheme case first
  if (scheme == null) {
    Log.e(TAG, "URI has null scheme, cannot resolve: $this")
    return null
  }

  val filepath =
    when (scheme) {
      "file" -> path
      "content" -> openContentFd(context)
      "data" -> "data://$schemeSpecificPart"
      in Utils.PROTOCOLS -> toString()
      else -> null
    }

  if (filepath == null) Log.e(TAG, "unknown scheme: $scheme")
  return filepath
}

inline fun <reified T> MPVNode.toObject(json: Json): T = json.decodeFromString<T>(toJson())
