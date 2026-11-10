package app.marlboroadvance.mpvex.utils.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for FolderBlacklistManager path normalization,
 * strict directory-boundary matching, and recursive filtering.
 */
class FolderBlacklistManagerTest {

    // =========================================================================
    // Phase 13 Path Matching Specification Matrix
    // =========================================================================

    @Test
    fun testPathMatching_Phase13_Case1_SubdirectoryIsHidden() {
        // Blacklist: /storage/emulated/0/Movies
        // Candidate: /storage/emulated/0/Movies/Action
        // Expected: Hidden
        val blacklist = setOf("/storage/emulated/0/Movies")
        val candidate = "/storage/emulated/0/Movies/Action"
        assertTrue(
            "Child directory under blacklisted folder must be hidden",
            FolderBlacklistManager.isPathBlacklisted(candidate, blacklist)
        )
    }

    @Test
    fun testPathMatching_Phase13_Case2_DirectFileIsHidden() {
        // Blacklist: /storage/emulated/0/Movies
        // Candidate: /storage/emulated/0/Movies/a.mp4
        // Expected: Hidden
        val blacklist = setOf("/storage/emulated/0/Movies")
        val candidate = "/storage/emulated/0/Movies/a.mp4"
        assertTrue(
            "Direct file in blacklisted folder must be hidden",
            FolderBlacklistManager.isFileInBlacklistedFolder(candidate, blacklist)
        )
    }

    @Test
    fun testPathMatching_Phase13_Case3_NestedFileIsHidden() {
        // Blacklist: /storage/emulated/0/Movies
        // Candidate: /storage/emulated/0/Movies/Action/a.mp4
        // Expected: Hidden
        val blacklist = setOf("/storage/emulated/0/Movies")
        val candidate = "/storage/emulated/0/Movies/Action/a.mp4"
        assertTrue(
            "Nested descendant file in blacklisted folder must be hidden",
            FolderBlacklistManager.isFileInBlacklistedFolder(candidate, blacklist)
        )
    }

    @Test
    fun testPathMatching_Phase13_Case4_SimilarNamePrefixMustRemainVisible() {
        // Blacklist: /storage/emulated/0/Movies
        // Candidate: /storage/emulated/0/Movies_Backup/a.mp4
        // Expected: Visible (NOT hidden)
        val blacklist = setOf("/storage/emulated/0/Movies")
        val candidate = "/storage/emulated/0/Movies_Backup/a.mp4"
        assertFalse(
            "Folder with similar prefix name (Movies_Backup) must NOT be hidden",
            FolderBlacklistManager.isFileInBlacklistedFolder(candidate, blacklist)
        )
        assertFalse(
            "Folder with similar prefix name must not match as blacklisted path",
            FolderBlacklistManager.isPathBlacklisted("/storage/emulated/0/Movies_Backup", blacklist)
        )
    }

    @Test
    fun testPathMatching_Phase13_Case5_UnrelatedFolderMustRemainVisible() {
        // Blacklist: /storage/emulated/0/DCIM
        // Candidate: /storage/emulated/0/Movies/a.mp4
        // Expected: Visible (NOT hidden)
        val blacklist = setOf("/storage/emulated/0/DCIM")
        val candidate = "/storage/emulated/0/Movies/a.mp4"
        assertFalse(
            "Unrelated directory file must NOT be hidden",
            FolderBlacklistManager.isFileInBlacklistedFolder(candidate, blacklist)
        )
    }

    // =========================================================================
    // Normalization Tests
    // =========================================================================

    @Test
    fun testNormalizePath() {
        assertEquals("/storage/emulated/0/Movies", FolderBlacklistManager.normalizePath("/storage/emulated/0/Movies/"))
        assertEquals("/storage/emulated/0/Movies", FolderBlacklistManager.normalizePath("/storage/emulated/0/Movies///"))
        assertEquals("/storage/emulated/0/Movies", FolderBlacklistManager.normalizePath("  /storage/emulated/0/Movies/  "))
        assertEquals("/storage/emulated/0/Movies", FolderBlacklistManager.normalizePath("/storage//emulated///0//Movies"))
        assertEquals("/", FolderBlacklistManager.normalizePath("/"))
        assertEquals("", FolderBlacklistManager.normalizePath(""))
    }

    @Test
    fun testStrictBoundaryMatching() {
        val base = "/storage/emulated/0/Movies"

        // Positive matches (exact and children)
        assertTrue(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/Movies", base))
        assertTrue(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/Movies/Action", base))
        assertTrue(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/Movies/Action/2026/Anime", base))

        // Negative matches (sibling prefixes, parent, unrelated)
        assertFalse(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/Movies_Extra", base))
        assertFalse(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/MyMovies", base))
        assertFalse(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0", base))
        assertFalse(FolderBlacklistManager.isSubpathOrEqual("/storage/emulated/0/Download", base))
    }

    // =========================================================================
    // Collection Filtering Tests
    // =========================================================================

    @Test
    fun testFilterFolders() {
        data class TestFolder(val path: String)

        val folders = listOf(
            TestFolder("/storage/emulated/0/Movies"),
            TestFolder("/storage/emulated/0/Movies/Action"),
            TestFolder("/storage/emulated/0/Movies_Backup"),
            TestFolder("/storage/emulated/0/Download"),
            TestFolder("/storage/emulated/0/DCIM/Camera")
        )

        val blacklist = setOf("/storage/emulated/0/Movies")
        val filtered = FolderBlacklistManager.filterFolders(folders, blacklist) { it.path }

        assertEquals(3, filtered.size)
        assertEquals("/storage/emulated/0/Movies_Backup", filtered[0].path)
        assertEquals("/storage/emulated/0/Download", filtered[1].path)
        assertEquals("/storage/emulated/0/DCIM/Camera", filtered[2].path)
    }

    @Test
    fun testFilterVideos() {
        data class TestVideo(val path: String)

        val videos = listOf(
            TestVideo("/storage/emulated/0/Movies/film1.mp4"),
            TestVideo("/storage/emulated/0/Movies/Action/film2.mkv"),
            TestVideo("/storage/emulated/0/Movies_Backup/film3.mp4"),
            TestVideo("/storage/emulated/0/Download/clip.mp4")
        )

        val blacklist = setOf("/storage/emulated/0/Movies")
        val filtered = FolderBlacklistManager.filterVideos(videos, blacklist) { it.path }

        assertEquals(2, filtered.size)
        assertEquals("/storage/emulated/0/Movies_Backup/film3.mp4", filtered[0].path)
        assertEquals("/storage/emulated/0/Download/clip.mp4", filtered[1].path)
    }

    // =========================================================================
    // Redundant Entries Pruning
    // =========================================================================

    @Test
    fun testPruneRedundantEntries() {
        val input = setOf(
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Movies/Action",
            "/storage/emulated/0/Movies/Action/Sub",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Download/Telegram"
        )

        val pruned = FolderBlacklistManager.pruneRedundantEntries(input)

        assertEquals(2, pruned.size)
        assertTrue(pruned.contains("/storage/emulated/0/Movies"))
        assertTrue(pruned.contains("/storage/emulated/0/Download"))
        assertFalse(pruned.contains("/storage/emulated/0/Movies/Action"))
        assertFalse(pruned.contains("/storage/emulated/0/Download/Telegram"))
    }
}
