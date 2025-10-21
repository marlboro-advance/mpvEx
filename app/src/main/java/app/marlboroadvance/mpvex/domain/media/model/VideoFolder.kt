package app.marlboroadvance.mpvex.domain.media.model

data class VideoFolder(
  val bucketId: String,
  val name: String,
  val path: String,
  val videoCount: Int,
  val totalSize: Long = 0L,
  val lastModified: Long = 0L,
)
