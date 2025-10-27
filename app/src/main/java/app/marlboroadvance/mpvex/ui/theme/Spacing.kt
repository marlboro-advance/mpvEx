package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
  val extraSmall: Dp = 4.dp,
  val smaller: Dp = 8.dp,
  val small: Dp = 12.dp,
  val medium: Dp = 16.dp,
  val large: Dp = 24.dp,
  val larger: Dp = 32.dp,
  val extraLarge: Dp = 48.dp,
  val largest: Dp = 64.dp,
)

@Suppress("CompositionLocalAllowlist")
val LocalSpacing = compositionLocalOf { Spacing() }

val MaterialTheme.spacing
  @Composable
  @ReadOnlyComposable
  get() = LocalSpacing.current
