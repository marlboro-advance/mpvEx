package app.marlboroadvance.mpvex.ui.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack

@Suppress("CompositionLocalAllowlist")
val LocalBackStack =
  compositionLocalOf<NavBackStack> {
    error("LocalBackStack not initialized!")
  }
