package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import `is`.xyz.mpv.Utils

/**
 * Utility object for media playback operations.
 *
 * ## Playback Architecture
 *
 * There are two `playFile` methods in the codebase:
 *
 * 1. **MediaUtils.playFile()** (THIS CLASS - High-level API)
 *    - Used by app UI components (Video List, FAB buttons, Play Link dialog)
 *    - Creates an Intent and launches PlayerActivity
 *    - Handles launch source tracking for analytics
 *    - Two overloads: one for Video objects, one for URI/path strings
 *
 * 2. **BaseMPVView.playFile()** (Library method - Low-level MPV control)
 *    - Called internally by PlayerActivity.onCreate()
 *    - Queues a file for playback once MPV surface is ready
 *    - Part of the mpv-android-lib library
 *
 * ### Usage Flow:
 * ```
 * UI Component → MediaUtils.playFile() → Intent → PlayerActivity.onCreate()
 *   → player.playFile() (BaseMPVView) → MPV playback starts
 * ```
 *
 * ### When to use which:
 * - **Use MediaUtils.playFile()**: When starting playback from UI (list clicks, buttons, etc.)
 * - **Don't call player.playFile() directly**: This is only for internal PlayerActivity use
 *
 * ### Special Cases:
 * - **Share Intent (ACTION_SEND)**: Goes directly to PlayerActivity, skips MediaUtils
 * - **ACTION_VIEW Intent**: External apps launching player, skips MediaUtils
 */
object MediaUtils {
  /**
   * Play a video from the video list
   */
  fun playFile(
    video: Video,
    context: Context,
  ) {
    val intent = Intent(Intent.ACTION_VIEW, video.uri)
    intent.setClass(context, PlayerActivity::class.java)
    context.startActivity(intent)
  }

  /**
   * Play a file from a URI string or file path
   * @param source URI string or file path
   * @param launchSource Optional source identifier for analytics
   */
  fun playFile(
    source: String,
    context: Context,
    launchSource: String? = null,
  ) {
    // Validate the source string is not empty
    if (source.isBlank()) {
      android.util.Log.e("MediaUtils", "Cannot play file: source is empty")
      return
    }

    val uri = source.toUri()

    // Validate that the URI has a scheme
    if (uri.scheme == null) {
      android.util.Log.e("MediaUtils", "Cannot play file: URI has no scheme: $source")
      // Try to treat it as a file path
      val fileUri = "file://$source".toUri()
      if (fileUri.scheme != null) {
        playFileWithIntent(fileUri, context, launchSource)
      }
      return
    }

    playFileWithIntent(uri, context, launchSource)
  }

  private fun playFileWithIntent(
    uri: android.net.Uri,
    context: Context,
    launchSource: String?,
  ) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setClass(context, PlayerActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchSource?.let { intent.putExtra("launch_source", it) }
    context.startActivity(intent)
  }

  /**
   * Get the most recently played file path
   * @deprecated Use RecentlyPlayedOps.getLastPlayed() directly
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.getLastPlayed() directly",
    replaceWith = ReplaceWith(
      "RecentlyPlayedOps.getLastPlayed()",
      "app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps",
    ),
  )
  suspend fun getRecentlyPlayedFile(): String? = RecentlyPlayedOps.getLastPlayed()

  /**
   * Check if there's a recently played file
   * @deprecated Use RecentlyPlayedOps.hasRecentlyPlayed() directly
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.hasRecentlyPlayed() directly",
    replaceWith = ReplaceWith(
      "RecentlyPlayedOps.hasRecentlyPlayed()",
      "app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps",
    ),
  )
  suspend fun hasRecentlyPlayedFile(): Boolean = RecentlyPlayedOps.hasRecentlyPlayed()

  /**
   * Validate URL for supported protocols
   */
  fun isURLValid(url: String): Boolean {
    val uri = url.toUri()

    val isValidStructure =
      uri.isHierarchical &&
        !uri.isRelative &&
        (!uri.host.isNullOrBlank() || !uri.path.isNullOrBlank())

    val hasValidProtocol = Utils.PROTOCOLS.contains(uri.scheme)

    return isValidStructure && hasValidProtocol
  }

  /**
   * Share one or more videos via ACTION_SEND / ACTION_SEND_MULTIPLE
   */
  fun shareVideos(context: Context, videos: List<Video>) {
    if (videos.isEmpty()) return

    val intent =
      if (videos.size == 1) {
        val video = videos.first()
        Intent(Intent.ACTION_SEND).apply {
          type = "video/*"
          putExtra(Intent.EXTRA_STREAM, video.uri)
          putExtra(Intent.EXTRA_SUBJECT, video.displayName)
          putExtra(Intent.EXTRA_TITLE, video.displayName)
          clipData = android.content.ClipData.newRawUri(video.displayName, video.uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "video/*"
          val uris = ArrayList(videos.map { it.uri })
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
          putExtra(Intent.EXTRA_SUBJECT, "Sharing ${videos.size} videos")
          val clipData = android.content.ClipData.newRawUri(videos.first().displayName, videos.first().uri)
          videos.drop(1).forEach { video ->
            clipData.addItem(android.content.ClipData.Item(video.uri))
          }
          setClipData(clipData)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      }

    context.startActivity(
      Intent.createChooser(
        intent,
        if (videos.size == 1) "Share video" else "Share ${videos.size} videos",
      ),
    )
  }
}
