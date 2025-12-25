package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Data class representing a parsed M3U playlist entry
 */
data class M3UPlaylistItem(
  val url: String,
  val title: String? = null,
  val duration: Int = -1, // Duration in seconds, -1 if unknown
  val tvgLogo: String? = null, // Logo URL if present in EXTINF
  val groupTitle: String? = null, // Group title for categorization
)

/**
 * Result of M3U playlist parsing
 */
sealed class M3UParseResult {
  data class Success(val playlistName: String, val items: List<M3UPlaylistItem>) : M3UParseResult()
  data class Error(val message: String, val exception: Throwable? = null) : M3UParseResult()
}

/**
 * Parser for M3U and M3U8 playlist files
 * Supports both simple M3U format and extended M3U format with EXTINF tags
 */
object M3UParser {
  private const val TAG = "M3UParser"
  private const val TIMEOUT_MS = 15000
  
  /**
   * Parse an M3U/M3U8 playlist from a URL
   */
  suspend fun parseFromUrl(url: String): M3UParseResult = withContext(Dispatchers.IO) {
    try {
      Log.d(TAG, "Parsing M3U playlist from URL: $url")
      
      val urlObj = URL(url)
      val connection = urlObj.openConnection() as HttpURLConnection
      connection.connectTimeout = TIMEOUT_MS
      connection.readTimeout = TIMEOUT_MS
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "mpvEx/1.0")
      
      val responseCode = connection.responseCode
      if (responseCode != HttpURLConnection.HTTP_OK) {
        return@withContext M3UParseResult.Error("HTTP error: $responseCode")
      }
      
      val content = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
        reader.readText()
      }
      
      connection.disconnect()
      
      parseContent(content, url)
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing M3U playlist", e)
      M3UParseResult.Error("Failed to parse playlist: ${e.message}", e)
    }
  }
  
  /**
   * Parse an M3U/M3U8 playlist from a local file URI
   */
  suspend fun parseFromUri(context: Context, uri: Uri): M3UParseResult = withContext(Dispatchers.IO) {
    try {
      Log.d(TAG, "Parsing M3U playlist from URI: $uri")
      
      val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
          reader.readText()
        }
      } ?: return@withContext M3UParseResult.Error("Failed to open file")
      
      // Get filename for playlist name
      val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
          cursor.getString(nameIndex)
        } else null
      } ?: uri.lastPathSegment ?: "Local M3U Playlist"
      
      parseContent(content, filename)
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing M3U playlist from URI", e)
      M3UParseResult.Error("Failed to parse playlist: ${e.message}", e)
    }
  }
  
  /**
   * Parse M3U/M3U8 content from string
   */
  fun parseContent(content: String, sourceUrl: String? = null): M3UParseResult {
    try {
      val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
      
      if (lines.isEmpty()) {
        return M3UParseResult.Error("Playlist is empty")
      }
      
      // Check if it's an extended M3U format
      val isExtended = lines.firstOrNull()?.startsWith("#EXTM3U") == true
      
      val items = mutableListOf<M3UPlaylistItem>()
      var currentTitle: String? = null
      var currentDuration: Int = -1
      var currentTvgLogo: String? = null
      var currentGroupTitle: String? = null
      
      val baseUrl = sourceUrl?.let { extractBaseUrl(it) }
      
      for (line in lines) {
        when {
          line.startsWith("#EXTM3U") -> {
            // Playlist header, skip
            continue
          }
          
          line.startsWith("#EXTINF:") -> {
            // Extended info line: #EXTINF:duration,title
            val info = line.substring(8) // Remove "#EXTINF:"
            val parts = info.split(",", limit = 2)
            
            // Parse duration
            currentDuration = parts.firstOrNull()?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull() ?: -1
            
            // Parse title (second part after comma)
            currentTitle = if (parts.size > 1) parts[1].trim() else null
            
            // Parse additional attributes from the duration part (tvg-logo, group-title, etc.)
            if (parts.isNotEmpty()) {
              val attributesPart = parts[0]
              currentTvgLogo = extractAttribute(attributesPart, "tvg-logo")
              currentGroupTitle = extractAttribute(attributesPart, "group-title")
            }
          }
          
          line.startsWith("#EXT-X-") -> {
            // HLS-specific tags, skip for now
            continue
          }
          
          line.startsWith("#") -> {
            // Other comment lines, skip
            continue
          }
          
          else -> {
            // This is a media URL
            var mediaUrl = line
            
            // If URL is relative and we have a base URL, make it absolute
            if (!mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://") && baseUrl != null) {
              mediaUrl = resolveRelativeUrl(baseUrl, mediaUrl)
            }
            
            // Generate a title if none was provided
            val title = currentTitle ?: extractTitleFromUrl(mediaUrl)
            
            items.add(
              M3UPlaylistItem(
                url = mediaUrl,
                title = title,
                duration = currentDuration,
                tvgLogo = currentTvgLogo,
                groupTitle = currentGroupTitle
              )
            )
            
            // Reset current info for next entry
            currentTitle = null
            currentDuration = -1
            currentTvgLogo = null
            currentGroupTitle = null
          }
        }
      }
      
      if (items.isEmpty()) {
        return M3UParseResult.Error("No valid media URLs found in playlist")
      }
      
      // Extract playlist name from source URL/filename or use default
      val playlistName = sourceUrl?.let { 
        // Check if it's a URL or a filename
        if (it.startsWith("http://") || it.startsWith("https://")) {
          extractPlaylistNameFromUrl(it)
        } else {
          // It's a filename, extract name without extension
          it.substringBeforeLast('.', it)
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifEmpty { "M3U Playlist" }
        }
      } ?: "M3U Playlist"
      
      Log.d(TAG, "Successfully parsed M3U playlist with ${items.size} items")
      return M3UParseResult.Success(playlistName, items)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing M3U content", e)
      return M3UParseResult.Error("Failed to parse playlist content: ${e.message}", e)
    }
  }
  
  /**
   * Extract attribute value from EXTINF line
   * Example: tvg-logo="http://example.com/logo.png"
   */
  private fun extractAttribute(line: String, attributeName: String): String? {
    val pattern = """$attributeName="([^"]+)"""".toRegex()
    return pattern.find(line)?.groupValues?.getOrNull(1)
  }
  
  /**
   * Extract base URL from a full URL
   */
  private fun extractBaseUrl(url: String): String {
    return try {
      val urlObj = URL(url)
      val path = urlObj.path
      val lastSlash = path.lastIndexOf('/')
      val basePath = if (lastSlash >= 0) path.substring(0, lastSlash + 1) else "/"
      "${urlObj.protocol}://${urlObj.host}${if (urlObj.port != -1) ":${urlObj.port}" else ""}$basePath"
    } catch (e: Exception) {
      url.substringBeforeLast('/') + "/"
    }
  }
  
  /**
   * Resolve a relative URL against a base URL
   */
  private fun resolveRelativeUrl(baseUrl: String, relativeUrl: String): String {
    return try {
      URL(URL(baseUrl), relativeUrl).toString()
    } catch (e: Exception) {
      // Fallback to simple concatenation
      if (relativeUrl.startsWith("/")) {
        val base = URL(baseUrl)
        "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$relativeUrl"
      } else {
        baseUrl + relativeUrl
      }
    }
  }
  
  /**
   * Extract a readable title from a URL
   */
  private fun extractTitleFromUrl(url: String): String {
    return try {
      val urlObj = URL(url)
      val path = urlObj.path
      val filename = path.substringAfterLast('/')
      
      // Remove extension and decode
      val nameWithoutExt = filename.substringBeforeLast('.')
      java.net.URLDecoder.decode(nameWithoutExt, "UTF-8")
        .replace('_', ' ')
        .replace('-', ' ')
    } catch (e: Exception) {
      url.substringAfterLast('/').take(50)
    }
  }
  
  /**
   * Extract playlist name from URL
   */
  private fun extractPlaylistNameFromUrl(url: String): String {
    return try {
      val urlObj = URL(url)
      val path = urlObj.path
      val filename = path.substringAfterLast('/')
      
      // Remove extension
      val nameWithoutExt = filename.substringBeforeLast('.')
      java.net.URLDecoder.decode(nameWithoutExt, "UTF-8")
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
      "M3U Playlist"
    }
  }
  
  /**
   * Validate if URL points to an M3U/M3U8 file
   */
  fun isValidM3UUrl(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return lowerUrl.endsWith(".m3u") || 
           lowerUrl.endsWith(".m3u8") || 
           lowerUrl.contains(".m3u?") ||
           lowerUrl.contains(".m3u8?")
  }
}
