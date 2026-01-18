package app.marlboroadvance.mpvex.utils.media

import android.util.Log
import app.marlboroadvance.mpvex.repository.NetworkRepository
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale

/**
 * Simple utility for automatically loading subtitle files
 * Finds subtitles in the same directory that match the video filename
 */
object SubtitleOps : KoinComponent {
  private const val TAG = "SubtitleOps"
  private val networkRepository: NetworkRepository by inject()

  private fun shouldSkipNetworkSubtitleAutoload(videoFilePath: String, videoFileName: String): Boolean {
    val p = videoFilePath.lowercase(Locale.getDefault())
    val n = videoFileName.lowercase(Locale.getDefault())

    val looksLikePlaylist =
      p.endsWith(".m3u") || p.endsWith(".m3u8") ||
        p.contains(".m3u?") || p.contains(".m3u8?") ||
        n.endsWith(".m3u") || n.endsWith(".m3u8")

    val genericName = n.isBlank() || n == "network stream"

    return looksLikePlaylist || genericName
  }

  suspend fun autoloadSubtitles(
    videoFilePath: String,
    videoFileName: String,
    networkConnectionId: Long = -1L,
  ) = withContext(Dispatchers.IO) {
    try {
      // Skip file descriptor URIs (these don't have a parent directory concept)
      if (videoFilePath.startsWith("fd://")) return@withContext

      // For content:// URIs, we can't autoload (no access to parent directory)
      if (videoFilePath.startsWith("content://")) return@withContext

      // Check if this is a network file with connection ID (SMB/FTP/WebDAV via proxy)
      if (networkConnectionId != -1L) {
        // For network files, scan the directory using network client
        autoloadNetworkFileSubtitles(videoFilePath, videoFileName, networkConnectionId)
        return@withContext
      }

      // Check if this is a network stream (http, https, ftp, ftps, smb, webdav, etc.)
      val isNetworkStream = videoFilePath.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))

      if (isNetworkStream) {
        if (shouldSkipNetworkSubtitleAutoload(videoFilePath, videoFileName)) {
          Log.d(TAG, "Skipping network subtitle autoload for: $videoFilePath")
          return@withContext
        }
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

  /**
   * Autoload subtitles for network files (SMB/FTP/WebDAV)
   * Lists files in the same directory and loads matching subtitle files via proxy
   */
  private suspend fun autoloadNetworkFileSubtitles(
    videoFilePath: String,
    videoFileName: String,
    networkConnectionId: Long,
  ) {
    try {
      Log.d(TAG, "Autoloading subtitles for network file: $videoFilePath")
      
      // Get the network connection
      val connection = networkRepository.getConnectionById(networkConnectionId)
      if (connection == null) {
        Log.w(TAG, "Network connection not found: $networkConnectionId")
        return
      }

      // Get the directory path (parent of the video file)
      val directoryPath = videoFilePath.substringBeforeLast('/', "")
      if (directoryPath.isEmpty()) {
        Log.w(TAG, "Could not determine directory path from: $videoFilePath")
        return
      }

      Log.d(TAG, "Scanning directory: $directoryPath")

      // Get base name without extension
      val baseName = videoFileName.substringBeforeLast('.')

      // List files in the directory
      val filesResult = networkRepository.listFiles(connection, directoryPath)
      if (filesResult.isFailure) {
        Log.w(TAG, "Failed to list network directory: ${filesResult.exceptionOrNull()?.message}")
        return
      }

      val files = filesResult.getOrNull() ?: emptyList()
      
      // Filter for subtitle files that match the video base name
      val subtitles = files.filter { file ->
        !file.isDirectory &&
          isSubtitleFile(file.name) &&
          file.name.substringBeforeLast('.').startsWith(baseName, ignoreCase = true)
      }

      if (subtitles.isEmpty()) {
        Log.d(TAG, "No matching subtitle files found for: $baseName")
        return
      }

      Log.d(TAG, "Found ${subtitles.size} subtitle file(s)")

      // Load subtitles via proxy
      val proxy = NetworkStreamingProxy.getInstance()
      
      // Keep mpv calls off the main thread for network-backed subtitle sources to avoid ANRs.
      subtitles.forEachIndexed { index, subtitle ->
        try {
          // Extract just the filename without path for display
          // Handle both forward slashes and backslashes
          val displayName = subtitle.name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .takeIf { it.isNotBlank() } ?: subtitle.name

          Log.d(TAG, "Processing subtitle - name: '${subtitle.name}', displayName: '$displayName', path: '${subtitle.path}'")

          // Create a URL-safe filename for the streamId
          val urlSafeFilename = displayName
            .replace(" ", ".")
            .replace(Regex("[^a-zA-Z0-9._-]"), "")

          // Register subtitle stream with proxy using the filename in streamId
          val streamId = urlSafeFilename
          val proxyUrl = proxy.registerStream(
            streamId = streamId,
            connection = connection,
            filePath = subtitle.path,
            fileSize = subtitle.size,
            mimeType = "text/plain",
          )

          // Get current subtitle track count before adding
          val trackCountBefore = MPVLib.getPropertyInt("track-list/count") ?: 0

          // Use "select" for the first subtitle, "auto" for others
          val flag = if (index == 0) "select" else "auto"
          MPVLib.command("sub-add", proxyUrl, flag)

          // Set the title for the newly added subtitle track
          val trackCountAfter = MPVLib.getPropertyInt("track-list/count") ?: 0
          if (trackCountAfter > trackCountBefore) {
            val newTrackIndex = trackCountAfter - 1
            MPVLib.setPropertyString("track-list/$newTrackIndex/title", displayName)
            Log.d(TAG, "Loaded network subtitle: '$displayName' (track $newTrackIndex) via proxy (flag=$flag)")
          } else {
            Log.d(TAG, "Loaded network subtitle: '$displayName' via proxy (flag=$flag)")
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load subtitle ${subtitle.name}: ${e.message}", e)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error autoloading network subtitles", e)
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

    // Keep mpv calls off the main thread for network URLs to avoid ANRs.
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

  private fun isSubtitleFile(fileName: String): Boolean {
    val lowerName = fileName.lowercase(Locale.getDefault())
    return lowerName.endsWith(".srt") ||
      lowerName.endsWith(".ass") ||
      lowerName.endsWith(".ssa") ||
      lowerName.endsWith(".vtt") ||
      lowerName.endsWith(".sub")
  }
}
