package app.sfsakhawat999.mpvrex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.sfsakhawat999.mpvrex.BuildConfig
import app.sfsakhawat999.mpvrex.domain.media.model.Video
import app.sfsakhawat999.mpvrex.ui.player.PlayerActivity
import app.sfsakhawat999.mpvrex.utils.history.RecentlyPlayedOps
import `is`.xyz.mpv.Utils
import java.io.File

/**
 * Central entry point for video playback operations.
 *
 * ## Architecture
 *
 * **MediaUtils.playFile()** - High-level API (this class)
 * - Called by UI components (Video List, FAB buttons, dialogs)
 * - Creates Intent and launches PlayerActivity
 * - Handles Video objects, URI strings, and file paths
 *
 * **BaseMPVView.playFile()** - Low-level MPV control (library)
 * - Called internally by PlayerActivity.onCreate()
 * - **Do not call directly from UI code**
 *
 * ## Flow
 * ```
 * UI → MediaUtils.playFile() → Intent → PlayerActivity → BaseMPVView.playFile() → MPV
 * ```
 *
 * ## Special Cases
 * External apps use ACTION_SEND/ACTION_VIEW intents directly to PlayerActivity,
 * bypassing MediaUtils.
 */
object MediaUtils {
  /**
   * Play video content from any source.
   *
   * Supports:
   * - Video objects (from media library)
   * - URI strings (http://, content://, file://)
   * - File paths (absolute or relative)
   *
   * @param source Video object, URI string, android.net.Uri, or file path
   * @param launchSource Analytics identifier (e.g., "open_file", "recently_played")
   */
  fun playFile(
    source: Any,
    context: Context,
    launchSource: String? = null,
  ) {
    val uri =
      when (source) {
        is Video -> {
          val intent = Intent(Intent.ACTION_VIEW, source.uri)
          intent.setClass(context, PlayerActivity::class.java)
          intent.putExtra("internal_launch", true) // Enables subtitle autoload
          launchSource?.let { intent.putExtra("launch_source", it) }
          context.startActivity(intent)
          return
        }

        is String -> {
          if (source.isBlank()) return
          val parsedUri = source.toUri()
          parsedUri.scheme?.let { parsedUri } ?: "file://$source".toUri()
        }

        is android.net.Uri -> source
        else -> {
          android.util.Log.e("MediaUtils", "Unsupported source type: ${source::class.java}")
          return
        }
      }

    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setClass(context, PlayerActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchSource?.let { intent.putExtra("launch_source", it) }
    context.startActivity(intent)
  }

  /**
   * @deprecated Use RecentlyPlayedOps.getLastPlayed() directly
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.getLastPlayed() directly",
    replaceWith =
      ReplaceWith(
        "RecentlyPlayedOps.getLastPlayed()",
        "app.sfsakhawat999.mpvrex.utils.history.RecentlyPlayedOps",
      ),
  )
  suspend fun getRecentlyPlayedFile(): String? = RecentlyPlayedOps.getLastPlayed()

  /**
   * @deprecated Use RecentlyPlayedOps.hasRecentlyPlayed() directly
   */
  @Deprecated(
    message = "Use RecentlyPlayedOps.hasRecentlyPlayed() directly",
    replaceWith =
      ReplaceWith(
        "RecentlyPlayedOps.hasRecentlyPlayed()",
        "app.sfsakhawat999.mpvrex.utils.history.RecentlyPlayedOps",
      ),
  )
  suspend fun hasRecentlyPlayedFile(): Boolean = RecentlyPlayedOps.hasRecentlyPlayed()

  /**
   * Validate URL structure and protocol support.
   * Checks only URL format and MPV protocol support (http, https, rtsp, rtmp, etc.).
   * Network errors are detected when MPV attempts to open the stream.
   */
  fun isURLValid(url: String): Boolean =
    url.toUri().let { uri ->
      val structureOk =
        uri.isHierarchical && !uri.isRelative && (!uri.host.isNullOrBlank() || !uri.path.isNullOrBlank())
      structureOk && Utils.PROTOCOLS.contains(uri.scheme)
    }

  /**
   * Share videos via system share sheet.
   *
   * Uses ACTION_SEND for single video, ACTION_SEND_MULTIPLE for multiple videos.
   */
  fun shareVideos(
    context: Context,
    videos: List<Video>,
  ) {
    if (videos.isEmpty()) {
      android.util.Log.w("MediaUtils", "Cannot share: video list is empty")
      return
    }

    fun toSharableUri(v: Video): android.net.Uri =
      v.uri.takeIf { it.scheme.equals("content", true) } ?: run {
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", File(v.path))
      }

    val uris = videos.map { toSharableUri(it) }

    if (uris.isEmpty()) {
      android.util.Log.w("MediaUtils", "Cannot share: no valid URIs generated")
      return
    }

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
