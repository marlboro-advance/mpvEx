package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

object MediaUtils {

  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)

  fun playFile(filepath: String, context: Context) {
    // Save the recently played file in the database
    CoroutineScope(Dispatchers.IO).launch {
      val fileName = extractFileName(filepath)
      recentlyPlayedRepository.addRecentlyPlayed(filepath, fileName)
    }

    val intent = Intent(Intent.ACTION_VIEW, filepath.toUri())
    intent.setClass(context, PlayerActivity::class.java)
    context.startActivity(intent)
  }

  private fun extractFileName(filepath: String): String {
    return try {
      val uri = filepath.toUri()
      uri.lastPathSegment ?: filepath
    } catch (_: Exception) {
      filepath
    }
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
