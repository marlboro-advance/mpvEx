package app.marlboroadvance.mpvex.domain.subtitle.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import app.marlboroadvance.mpvex.domain.subtitle.SubdlApiService
import app.marlboroadvance.mpvex.domain.subtitle.SubdlSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Repository for Subdl.com subtitle service.
 * Handles subtitle search and download operations with permanent storage integration.
 */
class SubdlRepository(
  private val context: Context,
  private val apiService: SubdlApiService,
  private val okHttpClient: OkHttpClient,
  private val externalSubtitleRepository: ExternalSubtitleRepository,
) {
  companion object {
    private const val TAG = "SubdlRepository"
    private const val SUBTITLE_DIR = "subtitles"
    private const val DOWNLOAD_BASE_URL = "https://dl.subdl.com"
    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "vtt", "sub")
    private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")
  }

  /**
   * Search for subtitles by film name.
   * @param query The search query (movie/TV show name)
   * @param apiKey User's Subdl API key
   * @param languages Comma-separated language codes (default: "EN")
   * @return Result containing list of subtitles or error
   */
  suspend fun searchSubtitles(
    query: String,
    apiKey: String,
    languages: String = "EN",
  ): Result<List<SubdlSubtitle>> =
    withContext(Dispatchers.IO) {
      try {
        require(apiKey.isNotBlank()) {
          "API key not set. Please add your Subdl API key in subtitle preferences."
        }

        Log.d(TAG, "Searching for: $query, languages: $languages")

        val response =
          apiService.searchSubtitles(
            apiKey = apiKey,
            filmName = query,
            languages = languages,
            subsPerPage = 30,
          )

        when {
          response.status && !response.subtitles.isNullOrEmpty() -> {
            Log.d(TAG, "Found ${response.subtitles.size} subtitles")
            Result.success(response.subtitles)
          }

          else -> {
            val errorMsg = response.error ?: "No subtitles found"
            Log.w(TAG, "Search failed: $errorMsg")
            Result.failure(Exception(errorMsg))
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Search error", e)
        Result.failure(e)
      }
    }

  /**
   * Download a subtitle file and store it permanently.
   * @param subtitle The subtitle to download
   * @param mediaTitle The media title for database tracking
   * @param apiKey User's Subdl API key
   * @return Result containing the downloaded file or error
   */
  suspend fun downloadSubtitle(
    subtitle: SubdlSubtitle,
    mediaTitle: String,
    apiKey: String,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      try {
        require(apiKey.isNotBlank()) {
          "API key not set. Please add your Subdl API key in subtitle preferences."
        }

        Log.d(TAG, "Downloading subtitle: ${subtitle.name} from ${subtitle.url}")

        val downloadUrl = buildDownloadUrl(subtitle.url)
        Log.d(TAG, "Full download URL: $downloadUrl")

        val subtitleFile =
          downloadAndExtractSubtitle(downloadUrl, subtitle.name)
            ?: throw Exception("Failed to extract subtitle from ZIP")

        Log.d(TAG, "Subtitle downloaded successfully: ${subtitleFile.absolutePath}")

        registerSubtitleInDatabase(subtitleFile, mediaTitle)

        Result.success(subtitleFile)
      } catch (e: Exception) {
        Log.e(TAG, "Download error", e)
        Result.failure(e)
      }
    }

  /**
   * Build the full download URL from relative or absolute path.
   */
  private fun buildDownloadUrl(url: String): String = if (url.startsWith("http")) url else "$DOWNLOAD_BASE_URL$url"

  /**
   * Download ZIP file and extract subtitle.
   */
  private fun downloadAndExtractSubtitle(
    url: String,
    subtitleName: String?,
  ): File? {
    val request = Request.Builder().url(url).build()
    val response = okHttpClient.newCall(request).execute()

    if (!response.isSuccessful) {
      throw Exception("Failed to download: HTTP ${response.code}")
    }

    val responseBody = response.body ?: throw Exception("Empty response body")
    val subtitlesDir = getSubtitlesDirectory()

    return extractSubtitleFromZip(
      inputStream = responseBody.byteStream(),
      outputDir = subtitlesDir,
      subtitleName = subtitleName ?: "subtitle",
    )
  }

  /**
   * Extract subtitle file from ZIP archive.
   * Only extracts the first subtitle file found.
   */
  private fun extractSubtitleFromZip(
    inputStream: java.io.InputStream,
    outputDir: File,
    subtitleName: String,
  ): File? =
    try {
      ZipInputStream(inputStream).use { zipStream ->
        generateSequence { zipStream.nextEntry }
          .firstNotNullOfOrNull { entry ->
            val extension = entry.name.substringAfterLast(".", "")
            if (extension.lowercase() in SUBTITLE_EXTENSIONS) {
              extractEntry(zipStream, outputDir, subtitleName, extension)
            } else {
              zipStream.closeEntry()
              null
            }
          }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error extracting ZIP", e)
      null
    }

  /**
   * Extract a single ZIP entry to file.
   */
  private fun extractEntry(
    zipStream: ZipInputStream,
    outputDir: File,
    subtitleName: String,
    extension: String,
  ): File {
    val timestamp = System.currentTimeMillis()
    val sanitizedName = subtitleName.replace(FILENAME_SANITIZE_REGEX, "_")
    val outputFile = File(outputDir, "${timestamp}_$sanitizedName.$extension")

    FileOutputStream(outputFile).use { outputStream ->
      zipStream.copyTo(outputStream)
    }

    zipStream.closeEntry()
    return outputFile
  }

  /**
   * Register subtitle in the external subtitle repository database.
   */
  private suspend fun registerSubtitleInDatabase(
    file: File,
    mediaTitle: String,
  ) {
    val uri = Uri.fromFile(file)
    externalSubtitleRepository
      .cacheSubtitle(
        uri = uri,
        fileName = file.name,
        mediaTitle = mediaTitle,
      ).onFailure { e ->
        Log.w(TAG, "Failed to register subtitle in database: ${e.message}")
      }
  }

  /**
   * Get or create the subtitles directory.
   */
  private fun getSubtitlesDirectory(): File = File(context.filesDir, SUBTITLE_DIR).apply { mkdirs() }
}
