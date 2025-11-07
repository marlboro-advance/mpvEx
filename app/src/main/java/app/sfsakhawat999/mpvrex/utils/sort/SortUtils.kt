package app.sfsakhawat999.mpvrex.utils.sort

import app.sfsakhawat999.mpvrex.domain.media.model.Video
import app.sfsakhawat999.mpvrex.domain.media.model.VideoFolder
import app.sfsakhawat999.mpvrex.preferences.FolderSortType
import app.sfsakhawat999.mpvrex.preferences.SortOrder
import app.sfsakhawat999.mpvrex.preferences.VideoSortType

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
