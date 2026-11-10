package app.marlboroadvance.mpvex.ui.preferences.folderpicker

import app.marlboroadvance.mpvex.utils.storage.FolderBlacklistManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for FolderPicker selection, multi-level navigation logic,
 * and state preservation.
 */
class FolderPickerViewModelTest {

    @Test
    fun testIndependentCheckboxSelectionAcrossMultipleLevels() {
        // Simulates central session selection across different directory depths
        val sessionSelections = mutableSetOf<String>()

        // 1. At /storage/emulated/0, user selects Movies
        val moviesPath = FolderBlacklistManager.normalizePath("/storage/emulated/0/Movies")
        sessionSelections.add(moviesPath)

        // 2. User navigates into Movies, selects Action
        val actionPath = FolderBlacklistManager.normalizePath("/storage/emulated/0/Movies/Action")
        sessionSelections.add(actionPath)

        // 3. User navigates into Download, selects Telegram
        val telegramPath = FolderBlacklistManager.normalizePath("/storage/emulated/0/Download/Telegram")
        sessionSelections.add(telegramPath)

        // All 3 selections must be preserved independently
        assertEquals(3, sessionSelections.size)
        assertTrue(sessionSelections.contains("/storage/emulated/0/Movies"))
        assertTrue(sessionSelections.contains("/storage/emulated/0/Movies/Action"))
        assertTrue(sessionSelections.contains("/storage/emulated/0/Download/Telegram"))

        // Deselection of one path leaves the other two intact
        sessionSelections.remove(actionPath)
        assertEquals(2, sessionSelections.size)
        assertTrue(sessionSelections.contains("/storage/emulated/0/Movies"))
        assertTrue(sessionSelections.contains("/storage/emulated/0/Download/Telegram"))
        assertFalse(sessionSelections.contains("/storage/emulated/0/Movies/Action"))
    }

    @Test
    fun testParentDirectoryAscensionPath() {
        val root = FolderPickerViewModel.ROOT_PATH
        val nested = "/storage/emulated/0/Movies/Action/Anime"

        var current: String = FolderBlacklistManager.normalizePath(nested)

        // Step 1: Up from Anime -> Action
        current = File(current).parentFile?.absolutePath?.let { FolderBlacklistManager.normalizePath(it) } ?: root
        assertEquals("/storage/emulated/0/Movies/Action", current)

        // Step 2: Up from Action -> Movies
        current = File(current).parentFile?.absolutePath?.let { FolderBlacklistManager.normalizePath(it) } ?: root
        assertEquals("/storage/emulated/0/Movies", current)

        // Step 3: Up from Movies -> Root
        current = File(current).parentFile?.absolutePath?.let { FolderBlacklistManager.normalizePath(it) } ?: root
        assertEquals("/storage/emulated/0", current)
        assertEquals(root, current)
    }

    @Test
    fun testCommitRedundantPruning() {
        val selections = setOf(
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Movies/Action",
            "/storage/emulated/0/Download"
        )

        val pruned = FolderBlacklistManager.pruneRedundantEntries(selections)
        assertEquals(2, pruned.size)
        assertTrue(pruned.contains("/storage/emulated/0/Movies"))
        assertTrue(pruned.contains("/storage/emulated/0/Download"))
    }
}
