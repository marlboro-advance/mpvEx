package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

object MediaUtils {

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)
  private const val TAG = "MediaUtils"

  fun playFile(video: Video, context: Context) {
    // Save the recently played file with the actual file path for proper folder matching
    CoroutineScope(Dispatchers.IO).launch {
      Log.d(TAG, "Saving recently played video: filePath='${video.path}', fileName='${video.displayName}'")
      recentlyPlayedRepository.addRecentlyPlayed(video.path, video.displayName)
    }

    val intent = Intent(Intent.ACTION_VIEW, video.uri)
    intent.setClass(context, PlayerActivity::class.java)
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
