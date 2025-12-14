package app.marlboroadvance.mpvex.utils.sort

import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.VideoSortType

object SortUtils {
  /**
   * Sort videos by the specified type and order
   */
  fun sortVideos(
    videos: List<Video>,
    sortType: VideoSortType,
    sortOrder: SortOrder,
  ): List<Video> {
    val sorted =
      when (sortType) {
        VideoSortType.Title -> videos.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.displayName, t2.displayName) }
        VideoSortType.Duration -> videos.sortedBy { it.duration }
        VideoSortType.Date -> videos.sortedBy { it.dateAdded }
        VideoSortType.Size -> videos.sortedBy { it.size }
      }
    return if (sortOrder.isAscending) sorted else sorted.reversed()
  }

  /**
   * Sort folders by the specified type and order
   */
  fun sortFolders(
    folders: List<VideoFolder>,
    sortType: FolderSortType,
    sortOrder: SortOrder,
  ): List<VideoFolder> {
    val sorted =
      when (sortType) {
        FolderSortType.Title -> folders.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
        FolderSortType.Date -> folders.sortedBy { it.lastModified }
        FolderSortType.Size -> folders.sortedBy { it.totalSize }
        FolderSortType.VideoCount -> folders.sortedBy { it.videoCount }
      }
    return if (sortOrder.isAscending) sorted else sorted.reversed()
  }

  /**
   * Sort filesystem items (folders and videos) by the specified type and order
   * Folders are always shown first, then videos
   */
  fun sortFileSystemItems(
    items: List<FileSystemItem>,
    sortType: FolderSortType,
    sortOrder: SortOrder,
  ): List<FileSystemItem> {
    // Separate folders and videos
    val folders = items.filterIsInstance<FileSystemItem.Folder>()
    val videos = items.filterIsInstance<FileSystemItem.VideoFile>()

    // Sort folders
    val sortedFolders =
      when (sortType) {
        FolderSortType.Title -> folders.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
        FolderSortType.Date -> folders.sortedBy { it.lastModified }
        FolderSortType.Size -> folders.sortedBy { it.totalSize }
        FolderSortType.VideoCount -> folders.sortedBy { it.videoCount }
      }

    // Sort videos (by corresponding properties)
    val sortedVideos =
      when (sortType) {
        FolderSortType.Title -> videos.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
        FolderSortType.Date -> videos.sortedBy { it.lastModified }
        FolderSortType.Size -> videos.sortedBy { it.video.size }
        FolderSortType.VideoCount -> videos.sortedBy { it.video.duration } // Use duration for videos
      }

    // Apply sort order
    val orderedFolders = if (sortOrder.isAscending) sortedFolders else sortedFolders.reversed()
    val orderedVideos = if (sortOrder.isAscending) sortedVideos else sortedVideos.reversed()

    // Return folders first, then videos
    return orderedFolders + orderedVideos
  }

  // Legacy string-based sorting (for backward compatibility)
  @Deprecated(
    "Use enum-based sortVideos instead",
    ReplaceWith(
      "sortVideos(videos, VideoSortType.valueOf(sortType), if (sortOrderAsc) SortOrder.Ascending else SortOrder.Descending)",
    ),
  )
  fun sortVideos(
    videos: List<Video>,
    sortType: String,
    sortOrderAsc: Boolean,
  ): List<Video> {
    val type = VideoSortType.entries.find { it.displayName == sortType } ?: VideoSortType.Title
    val order = if (sortOrderAsc) SortOrder.Ascending else SortOrder.Descending
    return sortVideos(videos, type, order)
  }

  @Deprecated(
    "Use enum-based sortFolders instead",
    ReplaceWith(
      "sortFolders(folders, FolderSortType.valueOf(sortType), if (sortOrderAsc) SortOrder.Ascending else SortOrder.Descending)",
    ),
  )
  fun sortFolders(
    folders: List<VideoFolder>,
    sortType: String,
    sortOrderAsc: Boolean,
  ): List<VideoFolder> {
    val type = FolderSortType.entries.find { it.displayName == sortType } ?: FolderSortType.Title
    val order = if (sortOrderAsc) SortOrder.Ascending else SortOrder.Descending
    return sortFolders(folders, type, order)
  }

  class NaturalOrderComparator(
    private val ignoreCase: Boolean,
    private val shouldSkip: (Char) -> Boolean,
  ) : Comparator<String> {

    companion object {
      val DEFAULT = NaturalOrderComparator(
        ignoreCase = true,
        shouldSkip = { it.isWhitespace() },
      )
    }

    override fun compare(a: String, b: String): Int {
      var ia = 0
      var ib = 0

      while (true) {
        // Skip ignored characters
        while (ia < a.length && shouldSkip(a[ia])) ia++
        while (ib < b.length && shouldSkip(b[ib])) ib++

        // One or both strings ended => shorter string is smaller
        if (ia >= a.length || ib >= b.length) {
          return when {
            ia >= a.length && ib >= b.length -> 0
            ia >= a.length -> -1
            else -> 1
          }
        }

        val numA = parseNumber(a, ia)
        val numB = parseNumber(b, ib)

        when {
          numA != null && numB != null -> {
            // Both numeric
            val cmp = numA.value.compareTo(numB.value)
            if (cmp != 0) return cmp
            // Numbers equal => advance past them and continue
            ia = numA.exclusiveEndIndex
            ib = numB.exclusiveEndIndex
          }
          else -> {
            // Compare single character
            val ca = if (ignoreCase) a[ia].lowercaseChar() else a[ia]
            val cb = if (ignoreCase) b[ib].lowercaseChar() else b[ib]
            val cmp = ca.compareTo(cb)
            if (cmp != 0) return cmp
            ia++
            ib++
          }
        }
      }
    }

    private data class ParsedNumber(val value: Int, val exclusiveEndIndex: Int)

    private fun parseNumber(s: String, start: Int): ParsedNumber? {
      var i = start

      var hasDigit = false

      while (i < s.length) {
        val c = s[i]
        if (c.isDigit()) {
          hasDigit = true
          i++
        } else {
          break
        }
      }

      if (!hasDigit) return null

      val numStr = s.substring(start, i)
      return try {
        ParsedNumber(numStr.toInt(), i)
      } catch (_: Exception) {
        null
      }
    }
  }
}
