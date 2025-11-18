package app.marlboroadvance.mpvex.domain.network

/**
 * Represents a file or directory on a network share
 */
data class NetworkFile(
  val name: String,
  val path: String,
  val size: Long,
  val isDirectory: Boolean,
  val lastModified: Long = 0,
  val mimeType: String? = null,
)
