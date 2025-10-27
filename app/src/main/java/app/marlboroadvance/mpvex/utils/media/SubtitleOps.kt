package app.marlboroadvance.mpvex.utils.media

import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Simple utility for automatically loading subtitle files
 * Finds subtitles in the same directory that match the video filename
 */
object SubtitleOps {
  private const val TAG = "SubtitleOps"

  suspend fun autoloadSubtitles(
    videoFilePath: String,
    videoFileName: String,
  ) = withContext(Dispatchers.IO) {
    try {
      if (videoFilePath.startsWith("fd://") || videoFilePath.startsWith("content://")) return@withContext

      val videoFile = File(videoFilePath)
      val videoDirectory = videoFile.parentFile ?: return@withContext
      val baseName = videoFileName.substringBeforeLast('.')

      val subtitles =
        videoDirectory.listFiles()?.filter { file ->
          file.isFile &&
            isSubtitleFile(file.name) &&
            file.nameWithoutExtension.startsWith(baseName, ignoreCase = true)
        } ?: emptyList()

      if (subtitles.isNotEmpty()) {
        withContext(Dispatchers.Main) {
          subtitles.forEach { subtitle ->
            // MPV command format: sub-add <url> [<flags> [<title>]]
            // Set the title to the subtitle filename for proper display
            MPVLib.command("sub-add", subtitle.absolutePath, "auto", subtitle.name)
            Log.d(TAG, "Loaded subtitle: ${subtitle.name}")
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading subtitles", e)
    }
  }

  private fun isSubtitleFile(fileName: String): Boolean {
    val lowerName = fileName.lowercase(Locale.getDefault())
    return lowerName.endsWith(".srt") ||
      lowerName.endsWith(".ass") ||
      lowerName.endsWith(".ssa") ||
      lowerName.endsWith(".vtt") ||
      lowerName.endsWith(".sub")
  }
}
