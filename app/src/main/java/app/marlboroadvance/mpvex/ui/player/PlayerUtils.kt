package app.marlboroadvance.mpvex.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.ui.player.PlayerActivity.Companion.TAG
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Storage path constants for Android's various storage locations.
 */
private object StoragePaths {
  const val PRIMARY_PREFIX = "primary:"
  const val RAW_PREFIX = "raw:"
  const val PRIMARY_STORAGE = "/storage/emulated/0"
  const val EXTERNAL_STORAGE = "/storage"
  const val MEDIA_RW = "/mnt/media_rw"
}

/**
 * Resolves content:// URIs to paths MPV can play.
 *
 * Tries multiple resolution strategies because Android's storage system varies by:
 * - Android version (pre-10, 10+, 11+ with scoped storage)
 * - Storage type (internal, external SD, SAF documents)
 * - Content provider implementation
 *
 * Falls back to file descriptor if real path cannot be determined.
 */
internal fun Uri.openContentFd(context: Context): String? =
  tryFileDescriptorPath(context)
    ?: tryMediaStoreQuery(context)
    ?: tryDocumentUriParsing(context)
    ?: tryFileDescriptorFallback(context)

/**
 * Method 1: Extract real filesystem path from file descriptor.
 * Works best for most content URIs on modern Android.
 */
private fun Uri.tryFileDescriptorPath(context: Context): String? =
  runCatching {
    context.contentResolver.openFileDescriptor(this, "r")?.use { pfd ->
      Utils.findRealPath(pfd.fd)?.also {
        Log.d(TAG, "Resolved via file descriptor: $it")
      }
    }
  }.getOrNull()

/**
 * Method 2: Query MediaStore for direct file path.
 * Works for media files indexed by MediaStore (videos, music, images).
 */
private fun Uri.tryMediaStoreQuery(context: Context): String? =
  runCatching {
    context.contentResolver
      .query(this, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
          if (columnIndex != -1) {
            cursor
              .getString(columnIndex)
              ?.takeIf { path ->
                path.isNotBlank() && File(path).exists()
              }?.also {
                Log.d(TAG, "Resolved via MediaStore: $it")
              }
          } else {
            null
          }
        } else {
          null
        }
      }
  }.onFailure { e ->
    Log.d(TAG, "MediaStore query failed: ${e.message}")
  }.getOrNull()

/**
 * Method 3: Parse DocumentsContract URIs manually.
 *
 * Document IDs have format: "storageType:path"
 * Examples:
 * - "primary:DCIM/video.mp4" → /storage/emulated/0/DCIM/video.mp4
 * - "raw:/storage/1234-5678/Movies/file.mp4" → /storage/1234-5678/Movies/file.mp4
 * - "1234-5678:Movies/video.mp4" → External SD card path
 */
private fun Uri.tryDocumentUriParsing(context: Context): String? {
  if (!DocumentsContract.isDocumentUri(context, this)) return null

  return runCatching {
    val docId = DocumentsContract.getDocumentId(this)
    Log.d(TAG, "Parsing document ID: $docId")

    when {
      docId.startsWith(StoragePaths.PRIMARY_PREFIX) -> {
        tryPrimaryStoragePath(docId)
      }
      docId.startsWith(StoragePaths.RAW_PREFIX) -> {
        tryRawPath(docId)
      }

      docId.contains(":") -> {
        tryExternalStoragePaths(docId)
      }

      else -> null
    }
  }.onFailure { e ->
    Log.d(TAG, "Document URI parsing failed: ${e.message}")
  }.getOrNull()
}

private fun tryPrimaryStoragePath(docId: String): String? {
  val path = docId.substringAfter(StoragePaths.PRIMARY_PREFIX)
  val fullPath = "${StoragePaths.PRIMARY_STORAGE}/$path"
  return fullPath.takeIf { File(it).exists() }?.also {
    Log.d(TAG, "Resolved document URI to primary storage: $it")
  }
}

private fun tryRawPath(docId: String): String? {
  val rawPath = docId.substringAfter(StoragePaths.RAW_PREFIX)
  return rawPath.takeIf { File(it).exists() }?.also {
    Log.d(TAG, "Resolved document URI from raw path: $it")
  }
}

/**
 * Tries multiple common mount points for external storage.
 * External SD cards can be mounted at different locations depending on manufacturer.
 */
private fun tryExternalStoragePaths(docId: String): String? {
  val path = docId.substringAfter(":")
  val possiblePaths =
    listOf(
      "${StoragePaths.PRIMARY_STORAGE}/$path",
      "${StoragePaths.EXTERNAL_STORAGE}/$path",
      "${StoragePaths.MEDIA_RW}/$path",
    )

  return possiblePaths.firstOrNull { File(it).exists() }?.also {
    Log.d(TAG, "Resolved document URI to: $it")
  }
}

/**
 * Fallback: Return file descriptor URI.
 * MPV can play directly from fd:// when filesystem path is unavailable.
 * Common with scoped storage on Android 11+.
 */
@SuppressLint("Recycle")
private fun Uri.tryFileDescriptorFallback(context: Context): String? =
  runCatching {
    context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let { fd ->
      "fd://$fd".also {
        Log.d(TAG, "Using file descriptor fallback: $it")
      }
    }
  }.getOrNull()

/**
 * Resolves any URI to a format MPV can play.
 *
 * Returns null if URI scheme is null or unsupported.
 */
internal fun Uri.resolveUri(context: Context): String? {
  if (scheme == null) {
    Log.e(TAG, "URI has null scheme: $this")
    return null
  }

  return when (scheme) {
    "file" -> path
    "content" -> openContentFd(context)
    "data" -> "data://$schemeSpecificPart"
    in Utils.PROTOCOLS -> toString()
    else -> {
      Log.e(TAG, "Unsupported URI scheme: $scheme")
      null
    }
  }
}

/**
 * Sanitizes JSON strings from MPV by fixing invalid escape sequences.
 * 
 * MPV's C library may generate JSON with unescaped backslashes (e.g., in file paths
 * like "Signs\Songs"). This function fixes invalid escape sequences by properly
 * escaping backslashes that aren't part of valid JSON escape sequences.
 * 
 * Valid JSON escape sequences: \" \\ \/ \b \f \n \r \t \uXXXX
 */
fun sanitizeJsonString(jsonString: String): String {
  val result = StringBuilder(jsonString.length)
  var i = 0
  var inString = false
  
  while (i < jsonString.length) {
    val char = jsonString[i]
    
    when {
      // Track if we're inside a string literal
      char == '"' && (i == 0 || jsonString[i - 1] != '\\') -> {
        inString = !inString
        result.append(char)
        i++
      }
      // Handle backslashes inside string literals
      char == '\\' && inString && i + 1 < jsonString.length -> {
        val nextChar = jsonString[i + 1]
        
        // Check if this is a valid escape sequence
        val isValidEscape = when (nextChar) {
          '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> true
          'u' -> i + 5 < jsonString.length // \uXXXX format
          else -> false
        }
        
        if (isValidEscape) {
          // Valid escape sequence, keep as-is
          result.append(char)
          i++
        } else {
          // Invalid escape sequence, escape the backslash
          result.append("\\\\")
          i++
        }
      }
      else -> {
        result.append(char)
        i++
      }
    }
  }
  
  return result.toString()
}

/**
 * Deserializes MPV's native node structure to Kotlin data classes.
 * MPV uses C-style tree structures (MPVNode) which we convert to typed objects.
 * 
 * Sanitizes the JSON before parsing to handle invalid escape sequences from MPV.
 */
inline fun <reified T> MPVNode.toObject(json: Json): T {
  val jsonString = toJson()
  val sanitizedJson = sanitizeJsonString(jsonString)
  return json.decodeFromString<T>(sanitizedJson)
}
