package app.marlboroadvance.mpvex.utils.storage

import java.io.File
import java.util.Locale

/**
 * File Type Utilities
 * Handles video file detection and MIME type resolution.
 */
object FileTypeUtils {

  val VIDEO_EXTENSIONS: Set<String> = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
    "mpg", "mpeg", "m2v", "ogv", "ts", "mts", "m2ts", "vob", "divx", "xvid",
    "f4v", "rm", "rmvb", "asf"
  )

  /**
   * Checks if a file is a video based on extension
   */
  fun isVideoFile(file: File): Boolean {
    val extension = file.extension.lowercase(Locale.getDefault())
    return VIDEO_EXTENSIONS.contains(extension)
  }

  /**
   * Checks if an extension represents a video file
   */
  fun isVideoExtension(extension: String): Boolean {
    return VIDEO_EXTENSIONS.contains(extension.lowercase(Locale.getDefault()))
  }

  /**
   * Gets MIME type from file extension
   */
  fun getMimeTypeFromExtension(extension: String): String =
    when (extension.lowercase(Locale.getDefault())) {
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "webm" -> "video/webm"
      "flv" -> "video/x-flv"
      "wmv" -> "video/x-ms-wmv"
      "m4v" -> "video/x-m4v"
      "3gp" -> "video/3gpp"
      "mpg", "mpeg" -> "video/mpeg"
      else -> "video/*"
    }
}
