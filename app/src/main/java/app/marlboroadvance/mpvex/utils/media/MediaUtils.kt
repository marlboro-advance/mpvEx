package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import `is`.xyz.mpv.Utils
import java.io.File

/**
 * Utility object for media playback operations.
 *
 * ## Playback Architecture
 *
 * This is the **single entry point** for starting video playback from the UI.
 *
 * ### Two-Layer Architecture:
 *
 * **1. MediaUtils.playFile() (High-level API - THIS CLASS)**
 * - Called by all app UI components (Video List, FAB buttons, Play Link dialog)
 * - Creates an Intent with ACTION_VIEW and launches PlayerActivity
 * - Handles launch source tracking for analytics
 * - Two overloads: one for Video objects, one for URI/path strings
 *
 * **2. BaseMPVView.playFile() (Low-level MPV control - Library method)**
 * - Called internally by PlayerActivity.onCreate()
 * - Queues a file for playback once MPV surface is ready
 * - Part of the mpv-android-lib library
 * - **Should never be called directly from UI code**
 *
 * **3. MediaUtils.loadFileDirectly() (Direct MPV command)**
 * - Directly calls MPVLib.command() to load a file in current player
 * - Use when player is already running and you want to load a new file
 * - Can append to playlist or replace current file
 *
 * ### Playback Flow:
 * ```
 * UI Component → MediaUtils.playFile() → Intent → PlayerActivity.onCreate()
 *   → player.playFile() (BaseMPVView) → MPV playback starts
 *
 * OR (for direct loading in existing player):
 *
 * UI Component → MediaUtils.loadFileDirectly() → MPVLib.command(["loadfile", ...])
 * ```
 *
 * ### Usage Guidelines:
 * - **Always use MediaUtils.playFile()** when starting playback from UI
 * - **Never call player.playFile() directly** - it's for internal PlayerActivity use only
 * - **Use MediaUtils.loadFileDirectly()** to load new files in an already-running player
 *
 * ### Special Cases:
 * - **Share Intent (ACTION_SEND)**: External apps send directly to PlayerActivity
 * - **ACTION_VIEW Intent**: External apps launching player directly
 * - Both skip MediaUtils and go straight to PlayerActivity
 */
object MediaUtils {
  /**
   * Play a video from the video list.
   *
   * @param video The Video object to play
   * @param context Android context for launching the activity
   */
  fun playFile(
    video: Video,
    context: Context,
  ) {
    val intent = Intent(Intent.ACTION_VIEW, video.uri)
    intent.setClass(context, PlayerActivity::class.java)
    intent.putExtra("internal_launch", true) // Mark as internal launch for subtitle autoload
    context.startActivity(intent)
  }

  /**
   * Play a file from a URI string or file path.
   *
   * Validates the source and converts it to a proper URI before launching PlayerActivity.
   * If the source has no scheme, attempts to treat it as a file path.
   *
   * @param source URI string (e.g., "http://...", "content://...") or file path
   * @param context Android context for launching the activity
   * @param launchSource Optional source identifier for analytics (e.g., "open_file", "recently_played_button")
   */
  fun playFile(
    source: String,
    context: Context,
    launchSource: String? = null,
  ) {
    if (source.isBlank()) return
    val uri = source.toUri()
    val playableUri = uri.scheme?.let { uri } ?: "file://$source".toUri()
    playFileWithIntent(playableUri, context, launchSource)
  }

  /**
   * Internal helper to launch PlayerActivity with the given URI.
   *
   * @param uri The URI to play
   * @param context Android context for launching the activity
   * @param launchSource Optional source identifier for analytics
   */
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
   * Get the most recently played file path.
   *
   * @deprecated Use RecentlyPlayedOps.getLastPlayed() directly
   * @return The file path of the last played video, or null if none
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.getLastPlayed() directly",
    replaceWith =
      ReplaceWith(
        "RecentlyPlayedOps.getLastPlayed()",
        "app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps",
      ),
  )
  suspend fun getRecentlyPlayedFile(): String? = RecentlyPlayedOps.getLastPlayed()

  /**
   * Check if there's a recently played file.
   *
   * @deprecated Use RecentlyPlayedOps.hasRecentlyPlayed() directly
   * @return True if there is a recently played file, false otherwise
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.hasRecentlyPlayed() directly",
    replaceWith =
      ReplaceWith(
        "RecentlyPlayedOps.hasRecentlyPlayed()",
        "app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps",
      ),
  )
  suspend fun hasRecentlyPlayedFile(): Boolean = RecentlyPlayedOps.hasRecentlyPlayed()

  /**
   * Validate URL for supported protocols.
   *
   * Checks if the URL has a valid structure and uses a protocol supported by MPV.
   * Supported protocols include: http, https, rtsp, rtmp, and others defined in Utils.PROTOCOLS.
   *
   * **Note:** This only validates the URL structure and protocol support.
   * It does NOT check:
   * - Network reachability
   * - SSL/TLS certificate validity
   * - Server availability
   * - Authentication requirements
   *
   * Network errors (SSL handshake failures, timeouts, etc.) will only be detected
   * when MPV attempts to actually open the stream.
   *
   * @param url The URL string to validate
   * @return True if the URL is valid and uses a supported protocol, false otherwise
   */
  fun isURLValid(url: String): Boolean =
    url.toUri().let { uri ->
      val structureOk =
        uri.isHierarchical && !uri.isRelative && (!uri.host.isNullOrBlank() || !uri.path.isNullOrBlank())
      structureOk && Utils.PROTOCOLS.contains(uri.scheme)
    }

  // --- Sharing ---

  /**
   * Share one or more videos via ACTION_SEND or ACTION_SEND_MULTIPLE.
   *
   * Creates a system share sheet to allow the user to share video files with other apps.
   * Uses ACTION_SEND for a single video and ACTION_SEND_MULTIPLE for multiple videos.
   *
   * @param context Android context for launching the share intent
   * @param videos List of Video objects to share (must not be empty)
   */
  fun shareVideos(
    context: Context,
    videos: List<Video>,
  ) {
    if (videos.isEmpty()) return

    fun toSharableUri(v: Video): android.net.Uri =
      v.uri.takeIf { it.scheme.equals("content", true) } ?: run {
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", File(v.path))
      }

    val uris = videos.map { toSharableUri(it) }
    val intent =
      if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
          type = "video/*"
          putExtra(Intent.EXTRA_STREAM, uris.first())
          putExtra(Intent.EXTRA_SUBJECT, videos.first().displayName)
          putExtra(Intent.EXTRA_TITLE, videos.first().displayName)
          clipData = android.content.ClipData.newRawUri(videos.first().displayName, uris.first())
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "video/*"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
          putExtra(Intent.EXTRA_SUBJECT, "Sharing ${videos.size} videos")
          val clip = android.content.ClipData.newRawUri(videos.first().displayName, uris.first())
          uris.drop(1).forEach { u -> clip.addItem(android.content.ClipData.Item(u)) }
          clipData = clip
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
