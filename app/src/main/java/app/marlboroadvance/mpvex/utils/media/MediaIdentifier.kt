package app.marlboroadvance.mpvex.utils.media

import java.io.File

/**
 * Builds the stable identifier used as the playback-state key
 * ([app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity.mediaTitle])
 * for local video files.
 *
 * The key combines the file's display name with a hash of its full path so two
 * files with the same name in different directories get distinct resume history.
 * This MUST match how `PlayerActivity` computes the identifier when saving and
 * loading playback state, so cleanup on deletion targets the right row.
 */
object MediaIdentifier {
  /**
   * Identifier for a local file, given its absolute path.
   *
   * For local content:// and file:// URIs, PlayerActivity resolves a stable
   * absolute path (MediaStore DATA / uri.path); that resolved value equals the
   * absolute file path used here, so this reproduces the same key.
   */
  fun forLocalPath(absolutePath: String): String {
    val fileName = File(absolutePath).name
    return "${fileName}_${absolutePath.hashCode()}"
  }
}
