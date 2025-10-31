package app.marlboroadvance.mpvex.domain.subtitle.repository

import android.content.Context
import android.net.Uri
import app.marlboroadvance.mpvex.database.dao.ExternalSubtitleDao
import app.marlboroadvance.mpvex.database.entities.ExternalSubtitleEntity
import java.io.File

class ExternalSubtitleRepository(
  private val context: Context,
  private val dao: ExternalSubtitleDao,
) {
  companion object {
    private const val SUBTITLE_CACHE_DIR = "subtitle_cache"
    private val INVALID_CHARS_REGEX = "[^a-zA-Z0-9._-]".toRegex()
  }

  /**
   * Cache an external subtitle file and store its metadata
   */
  suspend fun cacheSubtitle(
    uri: Uri,
    fileName: String,
    mediaTitle: String,
  ): Result<String> =
    runCatching {
      val cacheDir = File(context.filesDir, SUBTITLE_CACHE_DIR).apply { mkdirs() }
      val timestamp = System.currentTimeMillis()
      val sanitizedFileName = fileName.replace(INVALID_CHARS_REGEX, "_")
      val cachedFile = File(cacheDir, "${timestamp}_$sanitizedFileName")

      context.contentResolver.openInputStream(uri)?.use { it.copyTo(cachedFile.outputStream()) }
        ?: error("Failed to open input stream")

      val cachedPath = cachedFile.absolutePath

      // Store in database
      val entity =
        ExternalSubtitleEntity(
          originalUri = uri.toString(),
          originalFileName = fileName,
          cachedFilePath = cachedPath,
          mediaTitle = mediaTitle,
        )
      dao.insert(entity)

      cachedPath
    }

  /**
   * Get all cached subtitles for a specific media file
   */
  suspend fun getSubtitlesForMedia(mediaTitle: String): List<ExternalSubtitleEntity> =
    runCatching {
      dao.getSubtitlesForMedia(mediaTitle)
    }.getOrElse { emptyList() }

  /**
   * Delete a cached subtitle by its cached file path
   */
  suspend fun deleteSubtitle(cachedFilePath: String): Result<Unit> =
    runCatching {
      // Delete physical file
      File(cachedFilePath).takeIf { it.exists() }?.delete()
      // Remove from database
      dao.deleteByCachedPath(cachedFilePath)
    }
}
