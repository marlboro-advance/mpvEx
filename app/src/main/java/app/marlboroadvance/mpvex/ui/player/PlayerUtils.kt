package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.marlboroadvance.mpvex.ui.player.PlayerActivity.Companion.TAG
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.serialization.json.Json

internal fun Uri.openContentFd(context: Context): String? {
  return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {
    Utils.findRealPath(it)?.also { _ ->
      ParcelFileDescriptor.adoptFd(it).close()
    } ?: "fd://$it"
  }
}

internal fun Uri.resolveUri(context: Context): String? {
  val filepath = when (scheme) {
    "file" -> path
    "content" -> openContentFd(context)
    "data" -> "data://$schemeSpecificPart"
    in Utils.PROTOCOLS -> toString()
    else -> null
  }

  if (filepath == null) Log.e(TAG, "unknown scheme: $scheme")
  return filepath
}

inline fun <reified T> MPVNode.toObject(json: Json): T = json.decodeFromString<T>(toJson())
