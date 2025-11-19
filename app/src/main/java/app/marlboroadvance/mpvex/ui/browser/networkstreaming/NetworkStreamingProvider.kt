package app.marlboroadvance.mpvex.ui.browser.networkstreaming

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients.NetworkClientFactory
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider for streaming network files to MPV player
 */
class NetworkStreamingProvider : ContentProvider() {
  companion object {
    private const val TAG = "NetworkStreamingProvider"

    // Cache for active connections
    private val connectionCache = mutableMapOf<Long, NetworkConnection>()
    private val clientCache =
      mutableMapOf<Long, app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients.NetworkClient>()

    fun getUri(context: android.content.Context, connectionId: Long, filePath: String): Uri {
      val authority = "${context.packageName}.networkstreaming"
      return Uri.parse("content://$authority/$connectionId/${Uri.encode(filePath)}")
    }

    fun setConnection(connectionId: Long, connection: NetworkConnection) {
      connectionCache[connectionId] = connection
    }

    fun clearCache() {
      runBlocking {
        clientCache.values.forEach { client ->
          try {
            client.disconnect()
          } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting client", e)
          }
        }
      }
      clientCache.clear()
      connectionCache.clear()
    }
  }

  override fun onCreate(): Boolean {
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))

    try {
      val pathSegments = uri.pathSegments
      if (pathSegments.size >= 2) {
        val fileName = Uri.decode(pathSegments.last())
        cursor.addRow(arrayOf(fileName, -1L)) // Size unknown for network files
      }
    } catch (e: Exception) {
      // Ignore
    }

    return cursor
  }

  override fun getType(uri: Uri): String? {
    return "video/*"
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    try {
      val pathSegments = uri.pathSegments

      if (pathSegments.size < 2) {
        return null
      }

      val connectionId = pathSegments[0].toLongOrNull()
      if (connectionId == null) {
        return null
      }

      val filePath = Uri.decode(pathSegments.drop(1).joinToString("/"))

      val connection = connectionCache[connectionId]
      if (connection == null) {
        return null
      }

      // Get or create client
      val client = clientCache.getOrPut(connectionId) {
        NetworkClientFactory.createClient(connection)
      }

      // Create a pipe for streaming
      val pipe = ParcelFileDescriptor.createPipe()
      val readFd = pipe[0]
      val writeFd = pipe[1]

      // Stream the file in a background thread
      Thread {
        try {
          // Ensure connected
          runBlocking {
            if (!client.isConnected()) {
              client.connect().getOrThrow()
            }
          }

          // Get file stream and copy to pipe
          runBlocking {
            client.getFileStream(filePath).onSuccess { inputStream ->
              ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { output ->
                inputStream.use { input ->
                  val buffer = ByteArray(8192)
                  var bytesRead: Int

                  while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                  }
                }
              }
            }.onFailure { error ->
              writeFd.closeWithError(error.message ?: "Unknown error")
            }
          }
        } catch (e: Exception) {
          try {
            writeFd.closeWithError(e.message ?: "Unknown error")
          } catch (closeError: Exception) {
            // Ignore
          }
        }
      }.start()


      return readFd
    } catch (e: Exception) {
      Log.e(TAG, "Error opening file", e)
      return null
    }
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}
