package app.marlboroadvance.mpvex.utils.media

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object HttpUtils {
  private const val TAG = "HttpUtils"
  private const val CONNECTION_TIMEOUT = 3000
  private const val READ_TIMEOUT = 3000

  suspend fun extractFilenameFromUrl(url: String): String? = withContext(Dispatchers.IO) {
    try {
      val uri = Uri.parse(url)
      val filenameFromHeaders = getFilenameFromHttpHeaders(url)
      if (filenameFromHeaders != null) {
        Log.d(TAG, "Extracted filename from headers: $filenameFromHeaders")
        return@withContext filenameFromHeaders
      }
      val filenameFromUrl = extractFilenameFromUrlPath(uri)
      Log.d(TAG, "Extracted filename from URL: $filenameFromUrl")
      return@withContext filenameFromUrl
    } catch (e: Exception) {
      Log.e(TAG, "Error extracting filename: ${e.message}")
      null
    }
  }

  private fun getFilenameFromHttpHeaders(url: String): String? {
    var connection: HttpURLConnection? = null
    try {
      connection = URL(url).openConnection() as HttpURLConnection
      connection.requestMethod = "HEAD"
      connection.connectTimeout = CONNECTION_TIMEOUT
      connection.readTimeout = READ_TIMEOUT
      connection.setRequestProperty("User-Agent", "mpvex/1.0")
      connection.instanceFollowRedirects = true
      connection.connect()

      val responseCode = connection.responseCode
      if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
        Log.w(TAG, "HTTP response: $responseCode")
      }

      connection.getHeaderField("Content-Disposition")?.let {
        parseContentDisposition(it)?.let { filename -> return filename }
      }

      connection.getHeaderField("Content-Location")?.let {
        return extractFilenameFromUrlPath(Uri.parse(it))
      }

      return null
    } catch (e: IOException) {
      Log.e(TAG, "IO error: ${e.message}")
      return null
    } catch (e: Exception) {
      Log.e(TAG, "Error: ${e.message}")
      return null
    } finally {
      connection?.disconnect()
    }
  }

  private fun parseContentDisposition(contentDisposition: String): String? {
    try {
      val filenameStarPattern = Regex("""filename\*=(?:UTF-8|utf-8)?''?"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
      filenameStarPattern.find(contentDisposition)?.let { match ->
        val encodedFilename = match.groupValues[1]
        return try {
          URLDecoder.decode(encodedFilename, "UTF-8")
        } catch (e: Exception) {
          encodedFilename
        }
      }

      val filenamePattern = Regex("""filename="?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
      filenamePattern.find(contentDisposition)?.let { match ->
        return match.groupValues[1].trim()
      }

      return null
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing Content-Disposition: ${e.message}")
      return null
    }
  }

  private fun extractFilenameFromUrlPath(uri: Uri): String {
    val path = uri.path ?: return uri.host ?: "Network Stream"
    val lastSegment = path.substringAfterLast("/")

    if (lastSegment.isNotBlank()) {
      return try {
        URLDecoder.decode(lastSegment, "UTF-8")
          .substringBefore("?")
          .substringBefore("#")
          .takeIf { it.isNotBlank() } ?: uri.host ?: "Network Stream"
      } catch (e: Exception) {
        lastSegment.substringBefore("?").substringBefore("#")
      }
    }

    return uri.host ?: "Network Stream"
  }

  fun isNetworkStream(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return try {
      val uri = Uri.parse(url)
      val scheme = uri.scheme?.lowercase()
      scheme in listOf("http", "https", "rtmp", "rtmps", "rtsp", "rtsps", "mms", "mmsh", "ftp", "ftps")
    } catch (e: Exception) {
      false
    }
  }

  fun isNetworkStream(uri: Uri?): Boolean {
    if (uri == null) return false
    val scheme = uri.scheme?.lowercase()
    return scheme in listOf("http", "https", "rtmp", "rtmps", "rtsp", "rtsps", "mms", "mmsh", "ftp", "ftps")
  }
}
