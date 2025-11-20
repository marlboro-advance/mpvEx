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
        VideoSortType.Title -> videos.sortedBy { it.displayName.lowercase() }
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
        FolderSortType.Title -> folders.sortedBy { it.name.lowercase() }
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
        FolderSortType.Title -> folders.sortedBy { it.name.lowercase() }
        FolderSortType.Date -> folders.sortedBy { it.lastModified }
        FolderSortType.Size -> folders.sortedBy { it.totalSize }
        FolderSortType.VideoCount -> folders.sortedBy { it.videoCount }
      }

    // Sort videos (by corresponding properties)
    val sortedVideos =
      when (sortType) {
        FolderSortType.Title -> videos.sortedBy { it.name.lowercase() }
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
}
