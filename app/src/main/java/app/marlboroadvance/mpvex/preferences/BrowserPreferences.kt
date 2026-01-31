package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum

/**
 * Preferences for the video browser (folder and video lists)
 */
class BrowserPreferences(
  preferenceStore: PreferenceStore,
) {
  // Folder sorting preferences
  val folderSortType = preferenceStore.getEnum("folder_sort_type", FolderSortType.Title)
  val folderSortOrder = preferenceStore.getEnum("folder_sort_order", SortOrder.Ascending)

  // Video sorting preferences
  val videoSortType = preferenceStore.getEnum("video_sort_type", VideoSortType.Title)
  val videoSortOrder = preferenceStore.getEnum("video_sort_order", SortOrder.Ascending)

  val folderViewMode = preferenceStore.getEnum("folder_view_mode", FolderViewMode.AlbumView)


  val folderGridColumns = preferenceStore.getInt("folder_grid_columns", 3)
  val videoGridColumns = preferenceStore.getInt("video_grid_columns", 2)

  // Visibility preferences for video card chips
  val showVideoThumbnails = preferenceStore.getBoolean("show_video_thumbnails", true)
  val showSizeChip = preferenceStore.getBoolean("show_size_chip", true)
  val showResolutionChip = preferenceStore.getBoolean("show_resolution_chip", true)
  val showFramerateInResolution = preferenceStore.getBoolean("show_framerate_in_resolution", true)
  val showProgressBar = preferenceStore.getBoolean("show_progress_bar", true)
  val showSubtitleIndicator = preferenceStore.getBoolean("show_subtitle_indicator", true)
  val mediaLayoutMode = preferenceStore.getEnum("media_layout_mode", MediaLayoutMode. LIST)

  // Visibility preferences for folder card chips
  val showTotalVideosChip = preferenceStore.getBoolean("show_total_videos_chip", true)
  val showTotalDurationChip = preferenceStore.getBoolean("show_total_duration_chip", true)
  val showTotalSizeChip = preferenceStore.getBoolean("show_total_size_chip", true)
  val showDateChip = preferenceStore.getBoolean("show_date_chip", false)
  val showFolderPath = preferenceStore.getBoolean("show_folder_path", true)

  // Auto-scroll to last played media preference (like MX Player)
  val autoScrollToLastPlayed = preferenceStore.getBoolean("auto_scroll_to_last_played", false)
}

/**
 * Sort order options
 */
enum class SortOrder {
  Ascending,
  Descending,
  ;

  val isAscending: Boolean
    get() = this == Ascending
}

/**
 * Folder sorting options
 */
enum class FolderSortType {
  Title,
  Date,
  Size,
  VideoCount,
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Date -> "Date"
        Size -> "Size"
        VideoCount -> "Count"
      }
}

/**
 * Video sorting options
 */
enum class VideoSortType {
  Title,
  Duration,
  Date,
  Size,
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Duration -> "Duration"
        Date -> "Date"
        Size -> "Size"
      }
}

/**
 * Folder view mode options
 */
enum class FolderViewMode {
  AlbumView,
  FileManager,
  ;

  val displayName: String
    get() =
      when (this) {
        AlbumView -> "Folder View"
        FileManager -> "Tree View"
      }
}

enum class MediaLayoutMode {
  LIST,
  GRID,
  ;

  val displayName:  String
    get() = when (this) {
      LIST -> "List"
      GRID -> "Grid"
    }
}
