package app.marlboroadvance.mpvex.utils.storage

import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import java.io.File

/**
 * Centralized manager responsible for recursive folder blacklist filtering,
 * strict directory-boundary path matching, and path normalization.
 */
object FolderBlacklistManager {

    private val MULTIPLE_SLASHES = Regex("/+")

    /**
     * Normalizes a file or folder path by:
     * - Trimming whitespace
     * - Collapsing multiple consecutive slashes into a single slash
     * - Removing trailing slashes (preserving the root directory "/")
     */
    fun normalizePath(path: String): String {
        if (path.isEmpty()) return ""
        val trimmed = path.trim()
        if (trimmed == "/" || trimmed.isEmpty()) return "/"

        var normalized = if (trimmed.contains("//")) {
            trimmed.replace(MULTIPLE_SLASHES, "/")
        } else {
            trimmed
        }
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    /**
     * Checks if [candidatePath] is equal to or a subpath (child/descendant) of [baseFolderPath].
     * Uses strict directory boundary checking to prevent false positives:
     * e.g., "/storage/emulated/0/Movies" matches "/storage/emulated/0/Movies/Action"
     * but NEVER matches "/storage/emulated/0/Movies_Backup".
     */
    fun isSubpathOrEqual(candidatePath: String, baseFolderPath: String): Boolean {
        val normCandidate = normalizePath(candidatePath)
        val normBase = normalizePath(baseFolderPath)
        return isNormalizedSubpathOrEqual(normCandidate, normBase)
    }

    /**
     * Fast-path check when both paths are already normalized.
     */
    internal fun isNormalizedSubpathOrEqual(normCandidate: String, normBase: String): Boolean {
        if (normBase.isEmpty() || normCandidate.isEmpty()) return false
        if (normBase == "/") return true
        if (normCandidate == normBase) return true

        return normCandidate.startsWith("$normBase/")
    }

    /**
     * Checks if a folder path is blacklisted directly or is a descendant of any blacklisted folder.
     */
    fun isPathBlacklisted(path: String, blacklist: Set<String>): Boolean {
        if (blacklist.isEmpty() || path.isEmpty()) return false
        val normPath = normalizePath(path)
        return blacklist.any { blacklisted ->
            val normBlacklisted = normalizePath(blacklisted)
            normBlacklisted.isNotEmpty() && isNormalizedSubpathOrEqual(normPath, normBlacklisted)
        }
    }

    /**
     * Checks if a file (e.g. video file) resides inside any blacklisted folder at any nesting depth.
     */
    fun isFileInBlacklistedFolder(filePath: String, blacklist: Set<String>): Boolean {
        if (blacklist.isEmpty() || filePath.isEmpty()) return false
        val normFilePath = normalizePath(filePath)
        val parentFolder = File(normFilePath).parent ?: return false
        return isPathBlacklisted(parentFolder, blacklist)
    }

    /**
     * Unified generic filter for collections of items containing a path.
     * Excludes items matching or nested within any blacklisted path.
     */
    fun <T> filter(
        items: List<T>,
        blacklist: Set<String>,
        pathExtractor: (T) -> String
    ): List<T> {
        if (blacklist.isEmpty() || items.isEmpty()) return items
        val normBlacklist = blacklist.map(::normalizePath).filter(String::isNotEmpty)
        if (normBlacklist.isEmpty()) return items

        return items.filterNot { item ->
            val p = normalizePath(pathExtractor(item))
            normBlacklist.any { isNormalizedSubpathOrEqual(p, it) }
        }
    }

    // Convenient one-line aliases for semantic clarity
    fun <T> filterFolders(folders: List<T>, blacklist: Set<String>, pathExtractor: (T) -> String): List<T> =
        filter(folders, blacklist, pathExtractor)

    fun <T> filterVideos(videos: List<T>, blacklist: Set<String>, pathExtractor: (T) -> String): List<T> =
        filter(videos, blacklist, pathExtractor)

    fun filterFileSystemItems(items: List<FileSystemItem>, blacklist: Set<String>): List<FileSystemItem> =
        filter(items, blacklist) { it.path }

    /**
     * Prunes redundant child entries from a blacklist set.
     * If parent "/storage/emulated/0/Movies" is in the set, child "/storage/emulated/0/Movies/Action"
     * is redundant and can be omitted without altering filtering semantics.
     */
    fun pruneRedundantEntries(blacklist: Set<String>): Set<String> {
        if (blacklist.size <= 1) return blacklist
        val normalizedList = blacklist.map(::normalizePath)
            .filter(String::isNotEmpty)
            .distinct()
            .sortedBy(String::length)

        val result = mutableSetOf<String>()
        for (candidate in normalizedList) {
            val isAlreadyCovered = result.any { parent -> isNormalizedSubpathOrEqual(candidate, parent) }
            if (!isAlreadyCovered) {
                result.add(candidate)
            }
        }
        return result
    }
}
