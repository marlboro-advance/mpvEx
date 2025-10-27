package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object SubtitleOps {
  private const val TAG = "SubtitleOps"

  private var folderUri: Uri? = null
  private var onFolderAccessNeeded: (() -> Unit)? = null

  /**
   * Set the folder URI for subtitle scanning using FSAF
   */
  fun setFolderUri(uri: Uri) {
    folderUri = uri
  }

  /**
   * Set callback for when folder access is needed
   */
  fun setOnFolderAccessNeeded(callback: () -> Unit) {
    onFolderAccessNeeded = callback
  }

  /**
   * Automatically load subtitles that match the video filename
   * Uses FSAF to scan directories for matching subtitle files
   * Subtitles are loaded directly without caching (current session only)
   */
  suspend fun autoloadSubtitles(
    context: Context,
    videoUri: Uri,
    videoFileName: String,
  ) = withContext(Dispatchers.IO) {
    try {
      val baseName = getBaseFileName(videoFileName)
      if (baseName.isBlank()) return@withContext

      // First, try to find subtitles in the same directory as the video
      val videoDirectory = getVideoDirectory(context, videoUri)
      if (videoDirectory != null) {
        val localSubtitles = findSubtitlesInDirectory(videoDirectory, baseName)
        if (localSubtitles.isNotEmpty()) {
          loadSubtitlesToMPV(localSubtitles)
          return@withContext
        }
      }

      // If no local subtitles found and we have folder access, scan the configured folder
      if (folderUri != null) {
        val configuredDir = DocumentFile.fromTreeUri(context, folderUri!!)
        if (configuredDir != null && configuredDir.isDirectory) {
          val folderSubtitles = findSubtitlesInDocumentDirectory(configuredDir, baseName)
          if (folderSubtitles.isNotEmpty()) {
            loadSubtitlesToMPV(folderSubtitles)
            return@withContext
          }
        }
      }

      // If still no subtitles and no folder access, request access
      if (folderUri == null && onFolderAccessNeeded != null) {
        withContext(Dispatchers.Main) {
          onFolderAccessNeeded?.invoke()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error during subtitle autoload", e)
    }
  }

  /**
   * Get the directory containing the video file
   */
  private fun getVideoDirectory(
    context: Context,
    videoUri: Uri,
  ): File? {
    return when (videoUri.scheme) {
      "file" -> {
        val file = File(videoUri.path ?: return null)
        file.parentFile
      }

      "content" -> {
        try {
          val documentFile = DocumentFile.fromSingleUri(context, videoUri)
          documentFile?.parentFile?.takeIf { it.isDirectory }
          null
        } catch (e: Exception) {
          null
        }
      }

      else -> null
    }
  }

  /**
   * Find subtitle files in a regular File directory
   */
  private fun findSubtitlesInDirectory(
    directory: File,
    baseName: String,
  ): List<SubtitleFile> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()

    return directory.listFiles { file ->
      file.isFile && isSubtitleFile(file.name) && matchesVideoFile(file.name, baseName)
    }?.mapNotNull { file ->
      SubtitleFile(
        uri = Uri.fromFile(file),
        fileName = file.name,
        path = file.absolutePath,
      )
    } ?: emptyList()
  }

  /**
   * Find subtitle files in a DocumentFile directory (FSAF)
   */
  private fun findSubtitlesInDocumentDirectory(
    directory: DocumentFile,
    baseName: String,
  ): List<SubtitleFile> {
    if (!directory.isDirectory) return emptyList()

    return directory.listFiles().filter { documentFile ->
      !documentFile.isDirectory &&
        documentFile.name?.let { name ->
          isSubtitleFile(name) && matchesVideoFile(name, baseName)
        } ?: false
    }.mapNotNull { documentFile ->
      documentFile.uri?.let { uri ->
        SubtitleFile(
          uri = uri,
          fileName = documentFile.name ?: "",
          path = uri.toString(),
        )
      }
    }
  }

  /**
   * Load subtitles directly to MPV player without caching
   */
  private fun loadSubtitlesToMPV(subtitles: List<SubtitleFile>) {
    subtitles.forEach { subtitle ->
      try {
        val subtitlePath = when (subtitle.uri.scheme) {
          "file" -> subtitle.path
          else -> subtitle.uri.toString()
        }

        MPVLib.command("sub-add", subtitlePath, "auto")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load subtitle: ${subtitle.fileName}", e)
      }
    }
  }

  /**
   * Extract base filename without extension
   */
  private fun getBaseFileName(fileName: String): String {
    val lastDotIndex = fileName.lastIndexOf('.')
    return if (lastDotIndex > 0) {
      fileName.substring(0, lastDotIndex)
    } else {
      fileName
    }
  }

  /**
   * Check if a filename represents a subtitle file
   */
  private fun isSubtitleFile(fileName: String): Boolean {
    val lowerName = fileName.lowercase(Locale.getDefault())
    return lowerName.endsWith(".srt") ||
      lowerName.endsWith(".ass") ||
      lowerName.endsWith(".ssa") ||
      lowerName.endsWith(".vtt") ||
      lowerName.endsWith(".sub") ||
      lowerName.endsWith(".idx")
  }

  /**
   * Check if a subtitle filename matches the video filename
   */
  private fun matchesVideoFile(
    subtitleFileName: String,
    videoBaseName: String,
  ): Boolean {
    val subtitleBaseName = getBaseFileName(subtitleFileName)
    return subtitleBaseName.equals(videoBaseName, ignoreCase = true) ||
      subtitleBaseName.startsWith(videoBaseName, ignoreCase = true)
  }

  /**
   * Data class for subtitle file information
   */
  private data class SubtitleFile(
    val uri: Uri,
    val fileName: String,
    val path: String,
  )
}
