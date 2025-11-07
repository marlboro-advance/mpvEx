package app.sfsakhawat999.mpvrex.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.sfsakhawat999.mpvrex.presentation.Screen
import app.sfsakhawat999.mpvrex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import java.io.File

/**
 * @deprecated Use MediaUtils.playFile() instead for consistency
 * This screen is kept for backward compatibility but should not be used in new code
 */
@Deprecated(
  message = "Use MediaUtils.playFile() instead",
  replaceWith =
    ReplaceWith(
      "MediaUtils.playFile(source, context, launchSource)",
      "app.sfsakhawat999.mpvrex.utils.media.MediaUtils",
    ),
)
@Serializable
data class PlayerScreen(
  val source: String,
  val launchSource: String? = null,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    LocalBackStack.current

    LaunchedEffect(source) {
      val uri = resolveToUri(source)
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          data = uri
          setClass(context, PlayerActivity::class.java)
          addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          launchSource?.let { putExtra("launch_source", it) }
        }
      context.startActivity(intent)
    }
  }
}

@SuppressLint("UseKtx")
private fun resolveToUri(source: String): Uri {
  val parsed = Uri.parse(source)
  return if (parsed.scheme.isNullOrEmpty()) {
    Uri.fromFile(File(source))
  } else {
    parsed
  }
}
