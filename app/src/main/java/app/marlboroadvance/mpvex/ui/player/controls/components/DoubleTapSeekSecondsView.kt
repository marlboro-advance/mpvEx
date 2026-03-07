package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.icons.Icon
import app.marlboroadvance.mpvex.ui.icons.Icons

/**
 * View that shows the arrows animation when double tapping to seek
 * Originally from https://github.com/aniyomiorg/aniyomi
 * Thanks @Quickdesh for allowing me to use it
 */
@Composable
fun DoubleTapSeekTriangles(
  isForward: Boolean,
  modifier: Modifier = Modifier,
) {
  val animationDuration = 750L

  val alpha1 = remember { Animatable(0f) }
  val alpha2 = remember { Animatable(0f) }
  val alpha3 = remember { Animatable(0f) }

  LaunchedEffect(animationDuration) {
    while (true) {
      alpha1.animateTo(1f, animationSpec = tween((animationDuration / 5).toInt()))
      alpha2.animateTo(1f, animationSpec = tween((animationDuration / 5).toInt()))
      alpha3.animateTo(1f, animationSpec = tween((animationDuration / 5).toInt()))
      alpha1.animateTo(0f, animationSpec = tween((animationDuration / 5).toInt()))
      alpha2.animateTo(0f, animationSpec = tween((animationDuration / 5).toInt()))
      alpha3.animateTo(0f, animationSpec = tween((animationDuration / 5).toInt()))
    }
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      if (isForward) {
        modifier
      } else {
        modifier.scale(scaleX = -1f, scaleY = 1f)
      },
  ) {
    DoubleTapArrow(alpha1.value)
    DoubleTapArrow(alpha2.value)
    DoubleTapArrow(alpha3.value)
  }
}

@Composable
private fun DoubleTapArrow(alpha: Float) {
  Icon(
    imageVector = Icons.Default.PlayArrow,
    contentDescription = null,
    modifier =
      Modifier
        .size(18.dp)
        .alpha(alpha = alpha),
    tint = Color.White,
  )
}
