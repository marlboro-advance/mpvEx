package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import `is`.xyz.mpv.Utils
import org.koin.java.KoinJavaComponent.inject

object MediaUtils {

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  fun playFile(video: Video, context: Context) {
    // Don't save here - let PlayerActivity handle it with proper launch source tracking
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
