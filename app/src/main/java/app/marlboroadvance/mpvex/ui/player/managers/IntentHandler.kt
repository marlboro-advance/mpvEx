package app.marlboroadvance.mpvex.ui.player.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.MILLISECONDS_TO_SECONDS
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.POSITION_NOT_SET
import app.marlboroadvance.mpvex.ui.player.managers.PlayerConstants.TAG
import app.marlboroadvance.mpvex.ui.player.openContentFd
import app.marlboroadvance.mpvex.ui.player.resolveUri
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils

/**
 * Handles all intent parsing, URI extraction, and intent extras processing.
 */
class IntentHandler(
  private val context: Context,
) {

  /**
   * Extracts and processes a playable URI from an intent.
   * @return The URI string ready for playback, or null if no valid URI found
   */
  fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent) ?: return null
    return if (uri.startsWith("content://")) {
      uri.toUri().openContentFd(context)
    } else {
      uri
    }
  }

  /**
   * Extracts file name from the intent.
   */
  fun getFileName(intent: Intent): String {
    val uri = extractUriFromIntent(intent) ?: return ""

    // Try to get display name from content resolver
    getDisplayNameFromUri(uri)?.let { return it }

    // Fallback to path segment
    return uri.lastPathSegment?.substringAfterLast("/") ?: uri.path ?: ""
  }

  /**
   * Applies all intent extras to MPV (position, subtitles, headers).
   * @return true if a position was set from intent extras
   */
  fun applyIntentExtras(intent: Intent): Boolean {
    val extras = intent.extras ?: return false
    var positionSetFromIntent = false

    // Set time position if provided
    extras.getInt("position", POSITION_NOT_SET).takeIf { it != POSITION_NOT_SET }?.let {
      Log.d(TAG, "Setting position from intent extra: ${it / MILLISECONDS_TO_SECONDS} seconds")
      MPVLib.setPropertyInt("time-pos", it / MILLISECONDS_TO_SECONDS)
      positionSetFromIntent = true
    }

    // Add subtitles
    addSubtitlesFromExtras(extras)

    // Set HTTP headers
    setHttpHeadersFromExtras(extras)

    return positionSetFromIntent
  }

  private fun parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(context)
      Intent.ACTION_SEND -> parsePathFromSendIntent(intent)
      else -> intent.getStringExtra("uri")
    }
  }

  @Suppress("DEPRECATION")
  private fun parsePathFromSendIntent(intent: Intent): String? {
    return if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.resolveUri(context)
    } else {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
        val uri = text.trim().toUri()
        if (uri.isHierarchical && !uri.isRelative) {
          uri.resolveUri(context)
        } else {
          null
        }
      }
    }
  }

  private fun extractUriFromIntent(intent: Intent): Uri? {
    return if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
    } else {
      @Suppress("DEPRECATION")
      intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  }

  private fun getDisplayNameFromUri(uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }
    }.onFailure { e ->
      Log.e(TAG, "Error getting display name from URI", e)
    }.getOrNull()
  }

  private fun addSubtitlesFromExtras(extras: Bundle) {
    if (!extras.containsKey("subs")) return

    val subList = Utils.getParcelableArray<Uri>(extras, "subs")
    val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

    for (suburi in subList) {
      val subfile = suburi.resolveUri(context) ?: continue
      val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

      Log.v(TAG, "Adding subtitles from intent extras: $subfile")
      MPVLib.command("sub-add", subfile, flag)
    }
  }

  private fun setHttpHeadersFromExtras(extras: Bundle) {
    extras.getStringArray("headers")?.let { headers ->
      if (headers.isEmpty()) return

      if (headers[0].startsWith("User-Agent", ignoreCase = true)) {
        MPVLib.setPropertyString("user-agent", headers[1])
      }

      if (headers.size > 2) {
        val headersString = headers.asSequence()
          .drop(2)
          .chunked(2)
          .filter { it.size == 2 }
          .associate { it[0] to it[1] }
          .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
          .joinToString(",")

        if (headersString.isNotEmpty()) {
          MPVLib.setPropertyString("http-header-fields", headersString)
        }
      }
    }
  }

  /**
   * Creates a result intent with playback position and duration.
   */
  fun createResultIntent(position: Int?, duration: Int?): Intent {
    return Intent(PlayerConstants.RESULT_INTENT).apply {
      position?.let { putExtra("position", it * MILLISECONDS_TO_SECONDS) }
      duration?.let { putExtra("duration", it * MILLISECONDS_TO_SECONDS) }
    }
  }
}
