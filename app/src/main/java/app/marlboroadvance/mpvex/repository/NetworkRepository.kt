package app.marlboroadvance.mpvex.repository

import app.marlboroadvance.mpvex.database.dao.NetworkConnectionDao
import app.marlboroadvance.mpvex.domain.network.ConnectionStatus
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients.NetworkClient
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients.NetworkClientFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing network connections and file browsing
 */
class NetworkRepository(
  private val dao: NetworkConnectionDao,
) {
  // Active network clients
  private val activeClients = mutableMapOf<Long, NetworkClient>()

  // Connection statuses
  private val _connectionStatuses = MutableStateFlow<Map<Long, ConnectionStatus>>(emptyMap())
  val connectionStatuses: StateFlow<Map<Long, ConnectionStatus>> = _connectionStatuses.asStateFlow()

  /**
   * Get all saved connections as a Flow
   */
  fun getAllConnections(): Flow<List<NetworkConnection>> = dao.getAllConnections()

  /**
   * Get a connection by ID
   */
  suspend fun getConnectionById(id: Long): NetworkConnection? = dao.getConnectionById(id)

  /**
   * Add a new connection
   */
  suspend fun addConnection(connection: NetworkConnection): Long = dao.insert(connection)

  /**
   * Update an existing connection
   */
  suspend fun updateConnection(connection: NetworkConnection) {
    dao.update(connection)
    // Disconnect and remove cached client if it exists
    // This ensures the next connection uses the updated credentials
    activeClients[connection.id]?.let { client ->
      try {
        client.disconnect()
      } catch (e: Exception) {
        // Ignore errors during cleanup
      }
      activeClients.remove(connection.id)
      // Update status to disconnected
      updateConnectionStatus(
        connection.id,
        ConnectionStatus(
          connectionId = connection.id,
          isConnected = false,
          isConnecting = false,
        ),
      )
    }
  }

  /**
   * Delete a connection
   */
  suspend fun deleteConnection(connection: NetworkConnection) {
    dao.delete(connection)
    // Clean up connection status
    _connectionStatuses.value -= connection.id
    // Disconnect if active
    activeClients[connection.id]?.let { client ->
      try {
        client.disconnect()
      } catch (e: Exception) {
        // Ignore errors during cleanup
      }
      activeClients.remove(connection.id)
    }
  }

  /**
   * Connect to a network share
   */
  suspend fun connect(connection: NetworkConnection): Result<Unit> {
    // Update status to connecting
    updateConnectionStatus(
      connection.id,
      ConnectionStatus(
        connectionId = connection.id,
        isConnecting = true,
      ),
    )

    return try {
      // Create client for this connection
      val client = NetworkClientFactory.createClient(connection)

      // Attempt to connect
      client.connect().onSuccess {
        // Store the active client
        activeClients[connection.id] = client

        // Update last connected time
        dao.updateLastConnected(connection.id, System.currentTimeMillis())

        // Update status to connected
        updateConnectionStatus(
          connection.id,
          ConnectionStatus(
            connectionId = connection.id,
            isConnected = true,
            isConnecting = false,
          ),
        )
      }.onFailure { e ->
        // Update status with error
        updateConnectionStatus(
          connection.id,
          ConnectionStatus(
            connectionId = connection.id,
            isConnected = false,
            isConnecting = false,
            error = e.message ?: "Connection failed",
          ),
        )
        throw e
      }
      Result.success(Unit)
    } catch (e: Exception) {
      // Update status with error
      updateConnectionStatus(
        connection.id,
        ConnectionStatus(
          connectionId = connection.id,
          isConnected = false,
          isConnecting = false,
          error = e.message ?: "Connection failed",
        ),
      )
      Result.failure(e)
    }
  }

  /**
   * Disconnect from a network share
   */
  suspend fun disconnect(connection: NetworkConnection): Result<Unit> =
    try {
      // Get and disconnect the client
      activeClients[connection.id]?.let { client ->
        client.disconnect()
        activeClients.remove(connection.id)
      }

      // Update status to disconnected
      updateConnectionStatus(
        connection.id,
        ConnectionStatus(
          connectionId = connection.id,
          isConnected = false,
          isConnecting = false,
        ),
      )
      Result.success(Unit)
    } catch (e: Exception) {
      // Even if disconnect fails, update status
      updateConnectionStatus(
        connection.id,
        ConnectionStatus(
          connectionId = connection.id,
          isConnected = false,
          isConnecting = false,
          error = e.message,
        ),
      )
      Result.failure(e)
    }

  /**
   * List files in a directory on a network share
   */
  suspend fun listFiles(
    connection: NetworkConnection,
    path: String,
  ): Result<List<NetworkFile>> =
    try {
      // Always fetch the latest connection from database to ensure we have current credentials
      val latestConnection = dao.getConnectionById(connection.id) ?: connection

      // Check if we have an active client
      val existingClient = activeClients[connection.id]

      // If no client exists, or if connection details have changed, create a new one
      val client = if (existingClient == null) {
        // Create new client with latest connection settings
        NetworkClientFactory.createClient(latestConnection).also { newClient ->
          newClient.connect().getOrThrow()
          activeClients[connection.id] = newClient
        }
      } else {
        existingClient
      }

      // List files
      client.listFiles(path)
    } catch (e: Exception) {
      Result.failure(e)
    }

  /**
   * Get an active client for a connection
   */
  fun getActiveClient(connectionId: Long): NetworkClient? = activeClients[connectionId]

  /**
   * Check if a connection is active
   */
  fun isConnected(connectionId: Long): Boolean = activeClients.containsKey(connectionId)

  /**
   * Disconnect all active connections
   */
  suspend fun disconnectAll() {
    activeClients.values.forEach { client ->
      try {
        client.disconnect()
      } catch (e: Exception) {
        // Ignore errors during cleanup
      }
    }
    activeClients.clear()
    _connectionStatuses.value = emptyMap()
  }

  private fun updateConnectionStatus(
    connectionId: Long,
    status: ConnectionStatus,
  ) {
    _connectionStatuses.value += (connectionId to status)
  }
}
