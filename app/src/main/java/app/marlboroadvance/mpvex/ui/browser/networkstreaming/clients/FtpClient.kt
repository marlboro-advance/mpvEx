package app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients

import android.net.Uri
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.InputStream
import java.time.Duration

class FtpClient(private val connection: NetworkConnection) : NetworkClient {
  private var ftpClient: FTPClient? = null

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val client = FTPClient()

        // Set UTF-8 encoding for proper handling of non-English characters
        client.controlEncoding = "UTF-8"

        // Increase timeouts to prevent broken pipe
        client.connectTimeout = 15000 // 15 seconds
        client.setDataTimeout(Duration.ofMillis(60000)) // 60 seconds
        client.setDefaultTimeout(60000) // 60 seconds default
        client.setControlKeepAliveTimeout(Duration.ofSeconds(300)) // 5 minutes keep-alive

        // Enable keep-alive to prevent connection drops
        client.setControlKeepAliveReplyTimeout(Duration.ofMillis(10000)) // 10 seconds for keep-alive replies

        // Connect to server
        client.connect(connection.host, connection.port)

        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
          client.disconnect()
          return@withContext Result.failure(Exception("FTP server refused connection"))
        }

        // Login
        val success =
          if (connection.isAnonymous) {
            client.login("anonymous", "")
          } else {
            client.login(connection.username, connection.password)
          }

        if (!success) {
          client.disconnect()
          return@withContext Result.failure(Exception("Login failed"))
        }

        // Set binary mode for file transfers
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.enterLocalPassiveMode()

        // Try to enable UTF-8 mode on the server (RFC 2640)
        // This sends "OPTS UTF8 ON" command if the server supports it
        try {
          client.sendCommand("OPTS UTF8 ON")
        } catch (_: Exception) {
          // Server may not support UTF-8 mode, continue anyway
        }

        // Set buffer size for better performance
        client.bufferSize = 1024 * 64 // 64KB buffer

        // Change to initial directory if specified
        if (connection.path != "/" && connection.path.isNotEmpty()) {
          val changed = client.changeWorkingDirectory(connection.path)
          if (!changed) {
            android.util.Log.w(
              "FtpClient",
              "Failed to change directory: ${client.replyCode}",
            )
          }
        }

        ftpClient = client
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      ftpClient?.let { client ->
        try {
          if (client.isConnected) {
            client.logout()
            client.disconnect()
          }
        } catch (_: Exception) {
          // Ignore disconnect errors
        }
      }
      ftpClient = null
    }
  }

  override fun isConnected(): Boolean = ftpClient?.isConnected == true

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Not connected"))

        // Check if connection is still alive
        if (!client.isConnected) {
          return@withContext Result.failure(Exception("Connection lost"))
        }

        // Send NOOP to check connection health
        try {
          client.sendNoOp()
        } catch (e: Exception) {
          // Try to reconnect
          val reconnectResult = connect()
          if (reconnectResult.isFailure) {
            return@withContext Result.failure(Exception("Connection broken and reconnect failed"))
          }
        }

        val files =
          client.listFiles(path).mapNotNull { file ->
            try {
              // Skip . and .. entries
              if (file.name == "." || file.name == "..") return@mapNotNull null

              NetworkFile(
                name = file.name,
                path = if (path.endsWith("/")) "$path${file.name}" else "$path/${file.name}",
                isDirectory = file.isDirectory,
                size = file.size,
                lastModified = file.timestamp?.timeInMillis ?: 0,
                mimeType = if (!file.isDirectory) getMimeType(file.name) else null,
              )
            } catch (_: Exception) {
              null
            }
          }

        Result.success(files)
      } catch (e: Exception) {
        // If it's a broken pipe error, the connection is dead
        if (e.message?.contains("pipe", ignoreCase = true) == true) {
          ftpClient = null
        }
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
        val client = ftpClient ?: return@withContext Result.failure(Exception("Not connected"))

        // Try to get file info
        val files = client.listFiles(path)
        if (files.isNotEmpty() && !files[0].isDirectory) {
          Result.success(files[0].size)
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
        // Create a fresh FTP client for this stream to avoid connection conflicts
        val streamClient = FTPClient()

        // Set UTF-8 encoding for proper handling of non-English characters
        streamClient.controlEncoding = "UTF-8"

        // Increase timeouts to prevent broken pipe during streaming
        streamClient.connectTimeout = 15000 // 15 seconds
        streamClient.setDataTimeout(Duration.ofMillis(120000)) // 120 seconds (2 minutes) for video streaming
        streamClient.setDefaultTimeout(120000)
        streamClient.setControlKeepAliveTimeout(Duration.ofSeconds(600)) // 10 minutes keep-alive
        streamClient.setControlKeepAliveReplyTimeout(Duration.ofMillis(15000))

        // Connect to server
        streamClient.connect(connection.host, connection.port)

        val reply = streamClient.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
          streamClient.disconnect()
          return@withContext Result.failure(Exception("FTP server refused connection (code: $reply)"))
        }

        // Login
        val success =
          if (connection.isAnonymous) {
            streamClient.login("anonymous", "")
          } else {
            streamClient.login(connection.username, connection.password)
          }

        if (!success) {
          streamClient.disconnect()
          return@withContext Result.failure(Exception("Login failed"))
        }

        // Set binary mode and passive mode
        streamClient.setFileType(FTP.BINARY_FILE_TYPE)
        streamClient.enterLocalPassiveMode()

        // Try to enable UTF-8 mode on the server (RFC 2640)
        try {
          streamClient.sendCommand("OPTS UTF8 ON")
        } catch (_: Exception) {
          // Server may not support UTF-8 mode, continue anyway
        }

        streamClient.bufferSize = 1024 * 64 // 64KB buffer

        // Change to initial directory if specified (matching the connect() behavior)
        if (connection.path != "/" && connection.path.isNotEmpty()) {
          val changed = streamClient.changeWorkingDirectory(connection.path)
          if (!changed) {
            android.util.Log.w(
              "FtpClient",
              "Failed to change to base directory: ${streamClient.replyCode}",
            )
          }
        }

        // Try different path variations
        val pathsToTry = mutableListOf<String>()
        pathsToTry.add(path)

        // Try without leading slash
        if (path.startsWith("/")) {
          pathsToTry.add(path.substring(1))
        }

        // Try relative to connection.path if it's set and path contains it
        if (connection.path != "/" && connection.path.isNotEmpty() && path.startsWith(connection.path)) {
          val relativePath = path.substring(connection.path.length).trimStart('/')
          if (relativePath.isNotEmpty()) {
            pathsToTry.add(relativePath)
          }
        }

        var lastError: String = ""

        for (filePath in pathsToTry) {
          try {
            val rawStream = streamClient.retrieveFileStream(filePath)

            if (rawStream != null) {
              // Wrap the stream to handle cleanup when closed
              val wrappedStream = object : InputStream() {
                override fun read(): Int = rawStream.read()

                override fun read(b: ByteArray): Int = rawStream.read(b)

                override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)

                override fun available(): Int = rawStream.available()

                override fun close() {
                  try {
                    rawStream.close()
                  } catch (e: Exception) {
                    android.util.Log.e("FtpClient", "Error closing stream", e)
                  }
                  try {
                    if (streamClient.isConnected) {
                      streamClient.completePendingCommand()
                      streamClient.logout()
                      streamClient.disconnect()
                    }
                  } catch (e: Exception) {
                    android.util.Log.e("FtpClient", "Error disconnecting", e)
                  }
                }
              }

              return@withContext Result.success(wrappedStream)
            } else {
              lastError = "No stream returned"
            }
          } catch (e: Exception) {
            lastError = e.message ?: e.toString()
          }
        }

        // If we get here, all paths failed
        try {
          streamClient.disconnect()
        } catch (_: Exception) {
        }

        return@withContext Result.failure(Exception("Failed to open FTP file stream. $lastError"))
      } catch (e: Exception) {
        android.util.Log.e("FtpClient", "Exception getting file stream", e)
        return@withContext Result.failure(e)
      }
    }

  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        // Build FTP URI for mpv
        val uriString =
          if (connection.isAnonymous) {
            "ftp://${connection.host}:${connection.port}$path"
          } else {
            "ftp://${connection.username}:${connection.password}@${connection.host}:${connection.port}$path"
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
