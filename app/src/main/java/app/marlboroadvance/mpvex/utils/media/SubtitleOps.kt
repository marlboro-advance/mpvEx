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
      // Skip file descriptor URIs (these don't have a parent directory concept)
      if (videoFilePath.startsWith("fd://")) return@withContext

      // For content:// URIs, we can't autoload (no access to parent directory)
      if (videoFilePath.startsWith("content://")) return@withContext

      // Check if this is a network stream (http, https, ftp, ftps, smb, webdav, etc.)
      val isNetworkStream = videoFilePath.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))

      if (isNetworkStream) {
        // For network streams, try to load subtitles with common extensions
        autoloadNetworkSubtitles(videoFilePath, videoFileName)
      } else {
        // For local files, scan the directory
        autoloadLocalSubtitles(videoFilePath, videoFileName)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading subtitles", e)
    }
  }

  private suspend fun autoloadLocalSubtitles(
    videoFilePath: String,
    videoFileName: String,
  ) {
    val videoFile = File(videoFilePath)
    val videoDirectory = videoFile.parentFile ?: return
    val baseName = videoFileName.substringBeforeLast('.')

    val subtitles =
      videoDirectory.listFiles()?.filter { file ->
        file.isFile &&
          isSubtitleFile(file.name) &&
          file.nameWithoutExtension.startsWith(baseName, ignoreCase = true)
      } ?: emptyList()

    if (subtitles.isNotEmpty()) {
      withContext(Dispatchers.Main) {
        subtitles.forEachIndexed { index, subtitle ->
          // MPV command format: sub-add <url> [<flags> [<title>]]
          // Use "select" for the first autoloaded subtitle so it is enabled by default
          val flag = if (index == 0) "select" else "auto"
          MPVLib.command("sub-add", subtitle.absolutePath, flag, subtitle.name)
          Log.d(TAG, "Loaded local subtitle: ${subtitle.name} (flag=$flag)")
        }
      }
    }
  }

  private suspend fun autoloadNetworkSubtitles(
    videoFilePath: String,
    videoFileName: String,
  ) {
    // Get base name without extension
    val baseName = videoFileName.substringBeforeLast('.')

    // Get the base URL (path without the filename)
    val lastSlashIndex = videoFilePath.lastIndexOf('/')
    if (lastSlashIndex == -1) return

    val baseUrl = videoFilePath.substring(0, lastSlashIndex + 1)

    // Common subtitle extensions to try
    val subtitleExtensions = listOf("srt", "ass", "ssa", "vtt", "sub")

    withContext(Dispatchers.Main) {
      // Try each subtitle extension
      subtitleExtensions.forEachIndexed { index, ext ->
        val subtitleUrl = "$baseUrl$baseName.$ext"
        try {
          // Try to add the subtitle - MPV will handle if it doesn't exist
          // Use "auto" flag so MPV doesn't select it if it's not found
          // Only use "select" for the first one (.srt)
          val flag = if (index == 0) "select" else "auto"
          MPVLib.command("sub-add", subtitleUrl, flag, "$baseName.$ext")
          Log.d(TAG, "Attempting to load network subtitle: $subtitleUrl (flag=$flag)")
        } catch (e: Exception) {
          Log.d(TAG, "Could not load network subtitle $subtitleUrl: ${e.message}")
        }
      }
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
