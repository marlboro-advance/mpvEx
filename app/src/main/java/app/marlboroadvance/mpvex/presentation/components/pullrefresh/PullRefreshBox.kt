package app.marlboroadvance.mpvex.presentation.components.pullrefresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A reusable Box composable that wraps content with pull-to-refresh functionality.
 *
 * Automatically handles:
 * - Material Design theming for the refresh indicator
 * - Configurable refresh offset and threshold
 * - Delay after refresh for visual feedback
 *
 * @param isRefreshing State that tracks whether refresh is in progress
 * @param onRefresh Lambda to invoke when refresh is triggered
 * @param modifier Modifier to apply to the Box
 * @param refreshingOffset Distance that the indicator can be pulled beyond the trigger point
 * @param refreshThreshold Distance needed to pull to trigger the refresh
 * @param delayAfterRefresh Delay (in milliseconds) to show indicator after refresh completes
 * @param content Content to display inside the Box
 */
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun PullRefreshBox(
  isRefreshing: MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  modifier: Modifier = Modifier,
  refreshingOffset: Dp = 80.dp,
  refreshThreshold: Dp = 72.dp,
  delayAfterRefresh: Long = 800L,
  content: @Composable BoxScope.() -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()

  val pullRefreshState =
    rememberPullRefreshState(
      refreshing = isRefreshing.value,
      onRefresh = {
        isRefreshing.value = true
        coroutineScope.launch {
          onRefresh()
          delay(delayAfterRefresh)
          isRefreshing.value = false
        }
      },
      refreshingOffset = refreshingOffset,
      refreshThreshold = refreshThreshold,
    )

  Box(
    modifier = modifier.pullRefresh(pullRefreshState),
  ) {
    content()

    PullRefreshIndicator(
      refreshing = isRefreshing.value,
      state = pullRefreshState,
      modifier = Modifier.align(Alignment.TopCenter),
      backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
      contentColor = MaterialTheme.colorScheme.primary,
    )
  }
}
