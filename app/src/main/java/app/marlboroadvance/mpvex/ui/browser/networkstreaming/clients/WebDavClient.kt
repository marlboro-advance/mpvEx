package app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients

import android.net.Uri
import android.util.Log
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.DavResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class WebDavClient(private val connection: NetworkConnection) : NetworkClient {
  companion object {
    private const val TAG = "WebDavClient"
  }

  // Note: Sardine-Android uses OkHttp which properly handles UTF-8 encoding by default
  private var sardine: Sardine? = null
  private var baseUrl: String = ""

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        // Support both HTTP and HTTPS
        val protocol = if (connection.port == 443) "https" else "http"
        baseUrl = "$protocol://${connection.host}:${connection.port}${connection.path}"

        val client = OkHttpSardine()
        if (!connection.isAnonymous) {
          client.setCredentials(connection.username, connection.password)
        }

        // Test connection
        client.exists(baseUrl)

        sardine = client
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      sardine = null
    }
  }

  override fun isConnected(): Boolean = sardine != null

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        // Construct URL properly to avoid double slashes
        val url = if (path.startsWith("http")) {
          path
        } else {
          // Remove trailing slash from baseUrl to prevent double slashes
          val cleanBaseUrl = baseUrl.trimEnd('/')
          // Ensure path starts with / for proper URL construction
          val cleanPath = if (path.startsWith("/")) path else "/$path"
          "$cleanBaseUrl$cleanPath"
        }

        val resources = client.list(url)

        val files =
          resources
            .drop(1) // Skip the directory itself
            .map { resource: DavResource ->
              val resourceName = resource.name ?: ""
              val filePath = if (path.endsWith("/")) {
                "$path$resourceName"
              } else if (path.isEmpty() || path == "/") {
                "/$resourceName"
              } else {
                "$path/$resourceName"
              }

              NetworkFile(
                name = resourceName,
                path = filePath,
                isDirectory = resource.isDirectory,
                size = resource.contentLength ?: 0,
                lastModified = resource.modified?.time ?: 0,
                mimeType = if (!resource.isDirectory) getMimeType(resourceName) else null,
              )
            }

        Result.success(files)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Get file size for a specific file path
   * This is useful for the proxy server to support range requests
   */
  suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        val protocol = if (connection.port == 443) "https" else "http"
        val url = if (path.startsWith("http")) {
          path
        } else {
          // Properly construct URL without double slashes
          val cleanBasePath = connection.path.trimEnd('/')
          val cleanFilePath = if (path.startsWith("/")) path else "/$path"
          "$protocol://${connection.host}:${connection.port}$cleanBasePath$cleanFilePath"
        }

        // Use PROPFIND to get file properties including size
        val resources = client.list(url, 0) // depth 0 = only the resource itself
        if (resources.isNotEmpty() && !resources[0].isDirectory) {
          val size = resources[0].contentLength ?: -1L
          Result.success(size)
        } else {
          Result.failure(Exception("File not found or is a directory"))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileStream(path: String): Result<InputStream> =
    withContext(Dispatchers.IO) {
      try {
        // Create a fresh Sardine client for this stream to avoid connection conflicts
        val streamClient = OkHttpSardine()

        if (!connection.isAnonymous) {
          streamClient.setCredentials(connection.username, connection.password)
        }

        val protocol = if (connection.port == 443) "https" else "http"
        val url = if (path.startsWith("http")) {
          path
        } else {
          // Properly construct URL without double slashes
          val cleanBasePath = connection.path.trimEnd('/')
          val cleanFilePath = if (path.startsWith("/")) path else "/$path"
          "$protocol://${connection.host}:${connection.port}$cleanBasePath$cleanFilePath"
        }

        val rawStream = streamClient.get(url)

        if (rawStream == null) {
          return@withContext Result.failure(Exception("Failed to open WebDAV stream"))
        }

        // Wrap the stream
        val wrappedStream = object : InputStream() {
          override fun read(): Int = rawStream.read()

          override fun read(b: ByteArray): Int = rawStream.read(b)

          override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)

          override fun available(): Int = rawStream.available()

          override fun close() {
            try {
              rawStream.close()
            } catch (e: Exception) {
              // Ignore
            }
          }
        }

        Result.success(wrappedStream)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        // Properly construct URL without double slashes
        val url = if (path.startsWith("http")) {
          path
        } else {
          val cleanBaseUrl = baseUrl.trimEnd('/')
          val cleanPath = if (path.startsWith("/")) path else "/$path"
          "$cleanBaseUrl$cleanPath"
        }

        // Build WebDAV URI for mpv
        val uriString =
          if (connection.isAnonymous) {
            url
          } else {
            val protocol = if (connection.port == 443) "https" else "http"
            val cleanBasePath = connection.path.trimEnd('/')
            val cleanFilePath = if (path.startsWith("/")) path else "/$path"
            "$protocol://${connection.username}:${connection.password}@${connection.host}:${connection.port}$cleanBasePath$cleanFilePath"
          }

        Result.success(Uri.parse(uriString))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getMimeType(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "mpeg", "mpg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "ts" -> "video/mp2t"
      else -> null
    }
  }
}
