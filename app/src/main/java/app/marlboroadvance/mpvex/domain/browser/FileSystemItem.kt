package app.marlboroadvance.mpvex.domain.browser

import app.marlboroadvance.mpvex.domain.media.model.Video

/**
 * Represents an item in the filesystem browser (either a folder or a video file)
 */
sealed class FileSystemItem {
  abstract val name: String
  abstract val path: String
  abstract val lastModified: Long

  data class Folder(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val videoCount: Int = 0,
    val totalSize: Long = 0L,
    val totalDuration: Long = 0L,
    val hasSubfolders: Boolean = false,
  ) : FileSystemItem()

  data class VideoFile(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val video: Video,
    val hasSubtitle: Boolean = false,
  ) : FileSystemItem()
}

/**
 * Represents a path component in the breadcrumb navigation
 */
data class PathComponent(
  val name: String,
  val fullPath: String,
)
