package app.marlboroadvance.mpvex.ui.player

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class PlayerScreen(val source: String) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current

    LaunchedEffect(source) {
      val uri = resolveToUri(source)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri
        setClass(context, PlayerActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(intent)
      // Remove this trampoline screen from backstack
      backstack.removeLastOrNull()
    }
  }
}

private fun resolveToUri(source: String): Uri {
  val parsed = Uri.parse(source)
  return if (parsed.scheme.isNullOrEmpty()) {
    Uri.fromFile(File(source))
  } else parsed
}
