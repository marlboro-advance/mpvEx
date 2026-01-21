package app.marlboroadvance.mpvex.presentation.components

import android.view.MotionEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RepeatingIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  maxDelayMillis: Long = 300,
  minDelayMillis: Long = 20,
  delayDecayFactor: Float = .25f,
  content: @Composable () -> Unit,
) {
  val currentClickListener by rememberUpdatedState(onClick)
  var pressed by remember { mutableStateOf(false) }

  FilledTonalIconButton(
    modifier =
      modifier.pointerInteropFilter {
        pressed =
          when (it.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
            else -> pressed
          }

        true
      },
    onClick = {},
    enabled = enabled,
    interactionSource = interactionSource,
    content = content,
  )

  LaunchedEffect(pressed, enabled) {
    var currentDelayMillis = maxDelayMillis

    while (enabled && pressed) {
      currentClickListener()
      delay(currentDelayMillis)
      currentDelayMillis =
        (currentDelayMillis - (currentDelayMillis * delayDecayFactor))
          .toLong()
          .coerceAtLeast(minDelayMillis)
    }
  }
}
