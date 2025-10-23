package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import `is`.xyz.mpv.Utils
import org.koin.java.KoinJavaComponent.inject

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

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  /**
   * Play a video from the video list
   */
  fun playFile(video: Video, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, video.uri)
    intent.setClass(context, PlayerActivity::class.java)
    context.startActivity(intent)
  }

  /**
   * Play a file from a URI string or file path
   * @param source URI string or file path
   * @param launchSource Optional source identifier for analytics
   */
  fun playFile(source: String, context: Context, launchSource: String? = null) {
    val uri = source.toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setClass(context, PlayerActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchSource?.let { intent.putExtra("launch_source", it) }
    context.startActivity(intent)
  }

  suspend fun getRecentlyPlayedFile(): String? {
    return recentlyPlayedRepository.getLastPlayed()?.filePath
  }

  suspend fun hasRecentlyPlayedFile(): Boolean {
    return recentlyPlayedRepository.getLastPlayed() != null
  }

  fun isURLValid(url: String): Boolean {
    val uri = url.toUri()

    val isValidStructure = uri.isHierarchical &&
      !uri.isRelative &&
      (!uri.host.isNullOrBlank() || !uri.path.isNullOrBlank())

    val hasValidProtocol = Utils.PROTOCOLS.contains(uri.scheme)

    return isValidStructure && hasValidProtocol
  }
}
