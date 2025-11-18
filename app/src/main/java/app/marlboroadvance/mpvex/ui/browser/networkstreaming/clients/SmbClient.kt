package app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients

import android.net.Uri
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.util.Properties

class SmbClient(private val connection: NetworkConnection) : NetworkClient {
  private var context: CIFSContext? = null
  private var baseUrl: String = ""
  private var resolvedHostIp: String = "" // Store the resolved IP

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {

        // Configure jCIFS for SMB 3.0+
        val props = Properties()
        props.setProperty("jcifs.smb.client.minVersion", "SMB300")  // Minimum SMB 3.0
        props.setProperty("jcifs.smb.client.maxVersion", "SMB311")  // Maximum SMB 3.1.1
        props.setProperty("jcifs.resolveOrder", "DNS")
        props.setProperty("jcifs.smb.client.dfs.disabled", "true") // Disable DFS for faster operations
        props.setProperty("jcifs.smb.client.responseTimeout", "30000")
        props.setProperty("jcifs.smb.client.connTimeout", "10000")
        props.setProperty("jcifs.smb.client.soTimeout", "35000")
        // Enable SMB 3.0 features
        props.setProperty("jcifs.smb.client.enableSMB2", "true")
        props.setProperty("jcifs.smb.client.useSMB2Negotiation", "true")

        val config = PropertyConfiguration(props)
        val baseContext = BaseContext(config)

        // Create authentication
        val auth =
          if (connection.isAnonymous) {
            NtlmPasswordAuthenticator()
          } else {
            NtlmPasswordAuthenticator(
              connection.username,
              connection.password,
            )
          }

        // Resolve and verify the host
        val resolvedAddress = try {
          withTimeout(5000) {
            java.net.InetAddress.getByName(connection.host)
          }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
          return@withContext Result.failure(Exception("Host resolution timeout for ${connection.host}"))
        } catch (e: java.net.UnknownHostException) {
          return@withContext Result.failure(Exception("Host not found: ${connection.host}"))
        }

        // Check if the resolved host is reachable
        val isHostReachable = try {
          withTimeout(3000) {
            resolvedAddress.isReachable(2000)
          }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
          false
        } catch (e: Exception) {
          true // Continue anyway if ping fails
        }

        if (!isHostReachable) {
          return@withContext Result.failure(Exception("Host ${connection.host} is not reachable on the network"))
        }

        // Create context with credentials
        context = baseContext.withCredentials(auth)

        // Use the resolved IP address to ensure we connect to the exact host
        val hostForUrl = resolvedAddress.hostAddress ?: connection.host
        resolvedHostIp = hostForUrl
        baseUrl =
          "smb://${hostForUrl}${if (connection.port != 445) ":${connection.port}" else ""}${connection.path}"

        // Test the connection
        val testFile = SmbFile(baseUrl, context)

        // Verify the connection with a timeout
        val connectionResult = try {
          withTimeout(10000) {
            val exists = testFile.exists()

            if (!exists) {
              Result.failure<Unit>(Exception("Path does not exist"))
            } else {
              val isDir = testFile.isDirectory

              if (!isDir) {
                Result.failure<Unit>(Exception("Path is not a directory"))
              } else {
                // Verify authentication by listing files
                try {
                  testFile.listFiles()

                  if (!connection.isAnonymous) {
                    testFile.type // Access share attributes to verify auth
                  }

                  Result.success(Unit)
                } catch (e: jcifs.smb.SmbAuthException) {
                  Result.failure<Unit>(Exception("Authentication failed. Check username and password."))
                } catch (e: jcifs.smb.SmbException) {
                  if (e.message?.contains("Access is denied", ignoreCase = true) == true ||
                    e.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true
                  ) {
                    Result.failure<Unit>(Exception("Authentication failed. Check username and password."))
                  } else {
                    Result.failure<Unit>(Exception("Connection failed: ${e.message}"))
                  }
                }
              }
            }
          }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
          Result.failure(Exception("Connection timeout. Server not responding."))
        } catch (e: jcifs.smb.SmbAuthException) {
          Result.failure(Exception("Authentication failed. Check username and password."))
        } catch (e: jcifs.smb.SmbException) {
          Result.failure(Exception("Connection failed: ${e.message ?: "Unknown SMB error"}"))
        } catch (e: Exception) {
          Result.failure(Exception("Connection failed: ${e.message ?: "Unknown error"}"))
        }

        connectionResult
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      context = null
      baseUrl = ""
      resolvedHostIp = ""
    }
  }

  override fun isConnected(): Boolean = context != null

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val ctx = context ?: return@withContext Result.failure(Exception("Not connected"))

        // Verify we're using the correct base URL for this connection
        if (baseUrl.isEmpty() || resolvedHostIp.isEmpty()) {
          return@withContext Result.failure(Exception("Connection not properly initialized"))
        }

        // Build the full path
        val fullPath = if (path.startsWith("smb://")) {
          path
        } else if (path == "/" || path.isEmpty()) {
          baseUrl
        } else {
          "$baseUrl${if (path.startsWith("/")) path else "/$path"}"
        }

        val smbFile = SmbFile(fullPath, ctx)

        val exists = smbFile.exists()

        if (!exists) {
          return@withContext Result.failure(Exception("Path does not exist"))
        }

        val isDir = smbFile.isDirectory

        if (!isDir) {
          return@withContext Result.failure(Exception("Path is not a directory"))
        }

        // Use a timeout for listFiles operation
        val rawFiles: Array<SmbFile>? = try {
          withTimeout(15000) {
            smbFile.listFiles()
          }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
          return@withContext Result.failure(Exception("Operation timed out. The server may be slow or unresponsive."))
        }

        if (rawFiles == null || rawFiles.isEmpty()) {
          return@withContext Result.success(emptyList())
        }

        val files = rawFiles.mapNotNull { file ->
          try {
            val fileName = file.name.trimEnd('/')

            // Skip special entries
            if (fileName == "." || fileName == "..") {
              return@mapNotNull null
            }

            // Skip Windows administrative/system shares
            if (fileName.endsWith("$", ignoreCase = true)) {
              return@mapNotNull null
            }

            // Skip IPC$ and other system shares
            if (fileName.equals("IPC", ignoreCase = true) ||
              fileName.equals("print", ignoreCase = true) ||
              fileName.equals("print$", ignoreCase = true)
            ) {
              return@mapNotNull null
            }

            NetworkFile(
              name = fileName,
              path = file.path,
              isDirectory = file.isDirectory,
              size = if (file.isDirectory) 0 else file.length(),
              lastModified = file.lastModified(),
              mimeType = if (!file.isDirectory) getMimeType(fileName) else null,
            )
          } catch (e: Exception) {
            null // Skip files that can't be accessed
          }
        }

        Result.success(files)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileStream(path: String): Result<InputStream> =
    withContext(Dispatchers.IO) {
      try {
        val ctx = context ?: return@withContext Result.failure(Exception("Not connected"))
        val fullPath =
          if (path.startsWith("smb://")) path else "$baseUrl${if (path.startsWith("/")) path else "/$path"}"

        val smbFile = SmbFile(fullPath, ctx)

        if (!smbFile.exists()) {
          return@withContext Result.failure(Exception("File does not exist"))
        }

        Result.success(smbFile.inputStream)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        // For SMB, we'll use the smb:// URI with credentials embedded
        val fullPath =
          if (path.startsWith("smb://")) path else "$baseUrl${if (path.startsWith("/")) path else "/$path"}"

        // Build URI with credentials for mpv
        val uriString =
          if (connection.isAnonymous) {
            fullPath
          } else {
            val hostPart = "${connection.host}${if (connection.port != 445) ":${connection.port}" else ""}"
            val pathPart = if (path.startsWith("/")) path else "/$path"
            "smb://${connection.username}:${connection.password}@$hostPart${connection.path}$pathPart"
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
