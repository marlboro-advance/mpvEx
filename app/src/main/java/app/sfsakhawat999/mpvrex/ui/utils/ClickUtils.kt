package app.sfsakhawat999.mpvrex.ui.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Debounced combined click handler that supports both click and long click
 * @param debounceTime Time in milliseconds to wait before allowing another click (default 500ms)
 * @param onClick The callback to invoke on click
 * @param onLongClick The callback to invoke on long click (optional)
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.debouncedCombinedClickable(
  debounceTime: Long = 500L,
  enabled: Boolean = true,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource? = null,
  indication: Indication? = null,
): Modifier =
  composed {
    val debouncer = remember { ClickDebouncer(debounceTime) }
    val source = interactionSource ?: remember { MutableInteractionSource() }

    this.combinedClickable(
      enabled = enabled,
      onClick = { debouncer.processClick(onClick) },
      onLongClick = onLongClick,
      interactionSource = source,
      indication = indication,
    )
  }

/**
 * Click debouncer class that tracks the last click time
 */
private class ClickDebouncer(
  private val debounceTime: Long,
) {
  private var lastClickTime = 0L

  fun processClick(action: () -> Unit) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastClickTime >= debounceTime) {
      lastClickTime = currentTime
      action()
    }
  }
}
