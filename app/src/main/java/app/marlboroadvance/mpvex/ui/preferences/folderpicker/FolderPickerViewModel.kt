package app.marlboroadvance.mpvex.ui.preferences.folderpicker

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.browser.PathComponent
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.utils.storage.FolderBlacklistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale

/**
 * Represents a directory row item in the folder picker.
 */
data class DirectoryItem(
  val file: File,
  val name: String,
  val path: String,
  val isAlreadyBlacklisted: Boolean = false,
)

/**
 * ViewModel managing the dedicated, navigable folder picker session.
 * Enforces strict separation between directory navigation and checkbox selection.
 */
class FolderPickerViewModel(
  application: Application,
) : ViewModel(), KoinComponent {

  private val foldersPreferences: FoldersPreferences by inject()

  companion object {
    private const val TAG = "FolderPickerViewModel"
    const val ROOT_PATH = "/storage/emulated/0"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          FolderPickerViewModel(application) as T
      }
  }

  // Current active browsing directory path
  private val _currentPath = MutableStateFlow(ROOT_PATH)
  val currentPath: StateFlow<String> = _currentPath.asStateFlow()

  // Immediate child directories of the current path (lazy, single-level)
  private val _directories = MutableStateFlow<List<DirectoryItem>>(emptyList())
  val directories: StateFlow<List<DirectoryItem>> = _directories.asStateFlow()

  // Central set of selected folder paths across the entire session
  // Survives arbitrary directory navigation and reloading
  private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
  val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

  // Breadcrumbs representing the navigation chain
  private val _breadcrumbs = MutableStateFlow<List<PathComponent>>(emptyList())
  val breadcrumbs: StateFlow<List<PathComponent>> = _breadcrumbs.asStateFlow()

  // Loading state
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Error message for inaccessible/permission-denied directories
  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  // Submission state to prevent double execution
  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  // Job for directory scanning to allow immediate cancellation on rapid navigation
  private var scanJob: Job? = null

  init {
    loadDirectory(ROOT_PATH)
  }

  /**
   * Navigates into a directory and loads its immediate children.
   * Preserves all previously selected folders.
   */
  fun navigateTo(path: String) {
    val normalized = FolderBlacklistManager.normalizePath(path)
    if (normalized.isEmpty()) return
    loadDirectory(normalized)
  }

  /**
   * Navigates to the parent directory.
   * Returns true if navigated up, false if already at root.
   */
  fun navigateUp(): Boolean {
    val current = FolderBlacklistManager.normalizePath(_currentPath.value)
    if (current == ROOT_PATH || current == "/" || current.isEmpty()) {
      return false
    }

    val parentFile = File(current).parentFile
    val parentPath = parentFile?.absolutePath?.let(FolderBlacklistManager::normalizePath)

    return if (parentPath != null && parentPath.isNotEmpty() && parentPath.startsWith(ROOT_PATH)) {
      loadDirectory(parentPath)
      true
    } else if (current != ROOT_PATH) {
      loadDirectory(ROOT_PATH)
      true
    } else {
      false
    }
  }

  /**
   * Toggles the selection state of a folder checkbox.
   * Does NOT navigate into the folder.
   */
  fun toggleSelection(path: String) {
    val normalized = FolderBlacklistManager.normalizePath(path)
    if (normalized.isEmpty()) return

    val currentSelections = _selectedPaths.value.toMutableSet()
    if (currentSelections.contains(normalized)) {
      currentSelections.remove(normalized)
    } else {
      currentSelections.add(normalized)
    }
    _selectedPaths.value = currentSelections
  }

  /**
   * Selects all visible child directories in the current folder.
   */
  fun selectAllInCurrentDirectory() {
    val currentSelections = _selectedPaths.value.toMutableSet()
    for (dir in _directories.value) {
      currentSelections.add(dir.path)
    }
    _selectedPaths.value = currentSelections
  }

  /**
   * Deselects all visible child directories in the current folder.
   */
  fun deselectAllInCurrentDirectory() {
    val currentSelections = _selectedPaths.value.toMutableSet()
    for (dir in _directories.value) {
      currentSelections.remove(dir.path)
    }
    _selectedPaths.value = currentSelections
  }

  /**
   * Clears all session selections.
   */
  fun clearAllSelections() {
    _selectedPaths.value = emptySet()
  }

  /**
   * Submits all selected folders to the blacklist.
   */
  fun commitSelectedToBlacklist(onSuccess: (count: Int) -> Unit) {
    if (_isSubmitting.value) return
    val toAdd = _selectedPaths.value
    if (toAdd.isEmpty()) return

    _isSubmitting.value = true
    viewModelScope.launch(Dispatchers.IO) {
      try {
        foldersPreferences.addFolders(toAdd)
        val addedCount = toAdd.size
        _selectedPaths.value = emptySet()
        withContext(Dispatchers.Main) {
          onSuccess(addedCount)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to commit folders to blacklist", e)
        _errorMessage.value = "Failed to add folders: ${e.message}"
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  /**
   * Loads immediate children of [path] without expensive N+1 descendant scans.
   */
  private fun loadDirectory(path: String) {
    scanJob?.cancel()
    _currentPath.value = path
    _errorMessage.value = null
    _isLoading.value = true

    updateBreadcrumbs(path)

    scanJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val dir = File(path)
        if (!dir.exists()) {
          _errorMessage.value = "Directory does not exist"
          _directories.value = emptyList()
          return@launch
        }

        if (!dir.canRead()) {
          _errorMessage.value = "Permission denied for directory"
          _directories.value = emptyList()
          return@launch
        }

        val blacklistedSet = foldersPreferences.blacklistedFolders.get()
        val children = dir.listFiles { file -> file.isDirectory } ?: emptyArray()

        // Pure single-pass mapping: NO N+1 listFiles() calls on children
        val items = children.map { childFile ->
          val childPath = FolderBlacklistManager.normalizePath(childFile.absolutePath)
          val isBlacklisted = FolderBlacklistManager.isPathBlacklisted(childPath, blacklistedSet)

          DirectoryItem(
            file = childFile,
            name = childFile.name,
            path = childPath,
            isAlreadyBlacklisted = isBlacklisted,
          )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }

        _directories.value = items
      } catch (e: SecurityException) {
        Log.e(TAG, "Permission denied scanning $path", e)
        _errorMessage.value = "Access denied: ${e.message}"
        _directories.value = emptyList()
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning $path", e)
        _errorMessage.value = "Error scanning directory: ${e.message}"
        _directories.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Builds the breadcrumb components list.
   */
  private fun updateBreadcrumbs(path: String) {
    val components = mutableListOf<PathComponent>()
    val normalizedRoot = FolderBlacklistManager.normalizePath(ROOT_PATH)
    val normalized = FolderBlacklistManager.normalizePath(path)

    components.add(PathComponent(name = "Internal Storage", fullPath = normalizedRoot))

    if (normalized != normalizedRoot && normalized.startsWith(normalizedRoot)) {
      val relative = normalized.removePrefix(normalizedRoot).trimStart('/')
      val parts = relative.split("/").filter { it.isNotEmpty() }

      var currentAcc = normalizedRoot
      for (part in parts) {
        currentAcc += "/$part"
        components.add(PathComponent(name = part, fullPath = currentAcc))
      }
    }
    _breadcrumbs.value = components
  }
}
