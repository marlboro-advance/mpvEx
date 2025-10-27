package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils

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
   * Directly load a file in the currently running MPV player.
   *
   * This bypasses the Intent/Activity route and directly calls MPVLib.command().
   *
   * **Important:** This should only be called when a PlayerActivity is already running
   * with MPV initialized. Calling this without an active player will have no effect
   * or may cause errors.
   *
   * @param filePath The file path or URI to load (must be resolvable by MPV)
   * @param mode Load mode:
   *   - "replace" (default): Replace current file
   *   - "append": Add to playlist
   *   - "append-play": Add to playlist and play immediately
   * @return True if command was sent successfully, false if filePath is blank
   *
   * @see MPVLib.command
   */
  fun loadFileDirectly(
    filePath: String,
    mode: String = "replace",
  ): Boolean {
    if (filePath.isBlank()) {
      android.util.Log.e("MediaUtils", "Cannot load file: filePath is empty")
      return false
    }

    return try {
      when (mode) {
        "replace" -> MPVLib.command("loadfile", filePath)
        "append", "append-play" -> MPVLib.command("loadfile", filePath, mode)
        else -> {
          android.util.Log.w("MediaUtils", "Unknown mode '$mode', using 'replace'")
          MPVLib.command("loadfile", filePath)
        }
      }
      android.util.Log.d("MediaUtils", "Loaded file directly: $filePath (mode: $mode)")
      true
    } catch (e: Exception) {
      android.util.Log.e("MediaUtils", "Error loading file directly: ${e.message}", e)
      false
    }
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
