package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.utils.storage.FolderBlacklistManager

/**
 * Preferences for folder management.
 * Stores and manages user-configured blacklisted directories.
 */
class FoldersPreferences(
  preferenceStore: PreferenceStore,
) {
  // Set of folder paths that should be hidden from the folder list
  val blacklistedFolders = preferenceStore.getStringSet("blacklisted_folders", emptySet())

  /**
   * Adds multiple folder paths to the blacklist with path normalization.
   */
  fun addFolders(paths: Collection<String>) {
    if (paths.isEmpty()) return
    val current = blacklistedFolders.get()
    val updated = current.toMutableSet()
    for (path in paths) {
      val normalized = FolderBlacklistManager.normalizePath(path)
      if (normalized.isNotEmpty()) {
        updated.add(normalized)
      }
    }
    blacklistedFolders.set(FolderBlacklistManager.pruneRedundantEntries(updated))
  }

  /**
   * Removes a folder path from the blacklist.
   */
  fun removeFolder(path: String) {
    val normalized = FolderBlacklistManager.normalizePath(path)
    val current = blacklistedFolders.get()
    val updated = current.toMutableSet()
    updated.remove(path)
    updated.remove(normalized)
    blacklistedFolders.set(updated)
  }

  /**
   * Removes multiple folder paths from the blacklist.
   */
  fun removeFolders(paths: Collection<String>) {
    if (paths.isEmpty()) return
    val normalizedToRemove = paths.map { FolderBlacklistManager.normalizePath(it) }.toSet()
    val current = blacklistedFolders.get()
    val updated = current.filterNot { 
      it in paths || FolderBlacklistManager.normalizePath(it) in normalizedToRemove 
    }.toSet()
    blacklistedFolders.set(updated)
  }

  /**
   * Clears all blacklisted folders.
   */
  fun clearAll() {
    blacklistedFolders.set(emptySet())
  }
}
