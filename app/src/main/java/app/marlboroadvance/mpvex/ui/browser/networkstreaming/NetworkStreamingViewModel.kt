package app.marlboroadvance.mpvex.ui.browser.networkstreaming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.marlboroadvance.mpvex.domain.network.ConnectionStatus
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.repository.NetworkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel for managing network connections
 * Follows MVVM pattern with proper separation of concerns
 */
class NetworkStreamingViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  private val repository: NetworkRepository by inject()

  /**
   * Observable list of all saved network connections
   */
  val connections: StateFlow<List<NetworkConnection>> =
    repository
      .getAllConnections()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  /**
   * Observable connection statuses
   */
  val connectionStatuses: StateFlow<Map<Long, ConnectionStatus>> = repository.connectionStatuses

  /**
   * Add a new network connection
   */
  fun addConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.addConnection(connection)
    }
  }

  /**
   * Update an existing connection
   */
  fun updateConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.updateConnection(connection)
    }
  }

  /**
   * Delete a connection
   */
  fun deleteConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.deleteConnection(connection)
    }
  }

  /**
   * Connect to a network share
   */
  fun connect(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.connect(connection)
    }
  }

  /**
   * Disconnect from a network share
   */
  fun disconnect(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.disconnect(connection)
    }
  }

  override fun onCleared() {
    super.onCleared()
    // Clean up all connections when ViewModel is destroyed
    viewModelScope.launch {
      repository.disconnectAll()
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          NetworkStreamingViewModel(application)
        }
      }
  }
}
