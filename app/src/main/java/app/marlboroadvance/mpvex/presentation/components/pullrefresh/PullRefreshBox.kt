package app.marlboroadvance.mpvex.presentation.components.pullrefresh

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A reusable Box composable that wraps content with pull-to-refresh functionality.
 *
 * Uses the Material 3 Expressive pull-to-refresh API with the same
 * [LoadingIndicator] used by the player buffering spinner, wrapped in
 * [PullToRefreshDefaults.IndicatorBox] for the pull offset/container. While
 * pulling, the indicator's progress tracks the pull distance; while refreshing
 * it switches to its indeterminate spinning state (morphing through the default
 * expressive shape set, matching the player).
 *
 * Automatically handles:
 * - Material 3 theming for the refresh indicator
 * - Progress-driven indicator while pulling, spinning while refreshing
 * - Delay after refresh for visual feedback
 * - Only activates when scrolled to top (when listState is provided)
 *
 * @param isRefreshing State that tracks whether refresh is in progress
 * @param onRefresh Lambda to invoke when refresh is triggered
 * @param modifier Modifier to apply to the Box
 * @param enabled Whether pull-to-refresh is enabled
 * @param listState Optional LazyListState to check if at top (enables pull-to-refresh only at top)
 * @param delayAfterRefresh Delay (in milliseconds) to show indicator after refresh completes
 * @param content Content to display inside the Box
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullRefreshBox(
  isRefreshing: MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  listState: LazyListState? = null,
  delayAfterRefresh: Long = 800L,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val pullToRefreshState = rememberPullToRefreshState()

  // Only enable pull-to-refresh when at the top of the list
  val canRefresh by remember(listState) {
    derivedStateOf {
      listState?.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
  }

  val refreshEnabled = enabled && (listState == null || canRefresh)

  PullToRefreshBox(
    modifier = modifier,
    isRefreshing = isRefreshing.value,
    onRefresh = {
      if (refreshEnabled) {
        isRefreshing.value = true
        coroutineScope.launch {
          onRefresh()
          delay(delayAfterRefresh)
          isRefreshing.value = false
        }
      }
    },
    state = pullToRefreshState,
    indicator = {
      PullToRefreshDefaults.IndicatorBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing.value,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        // Same Expressive LoadingIndicator used by the player buffering spinner.
        if (isRefreshing.value) {
          // Indeterminate: morphs through the default expressive shape set.
          LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
          // Determinate: progress tracks the pull distance.
          LoadingIndicator(
            progress = { pullToRefreshState.distanceFraction.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    content = content,
  )
}
