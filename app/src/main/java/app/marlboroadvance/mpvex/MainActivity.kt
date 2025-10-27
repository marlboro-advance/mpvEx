package app.marlboroadvance.mpvex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)
      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      MpvexTheme {
        Surface {
          Navigator()
        }
      }
    }
  }

  override fun onDestroy() {
    try {
      super.onDestroy()
    } catch (e: Exception) {
      Log.e("MainActivity", "Error during onDestroy", e)
    }
  }

  @Composable
  fun Navigator() {
    val backstack = rememberNavBackStack(FolderListScreen)

    @Suppress("UNCHECKED_CAST")
    val typedBackstack = backstack as NavBackStack<Screen>
    CompositionLocalProvider(LocalBackStack provides typedBackstack) {
      NavDisplay<Screen>(
        backStack = typedBackstack,
        onBack = { typedBackstack.removeLastOrNull() },
        entryProvider = { route -> NavEntry(route) { route.Content() } },
        popTransitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              slideIn(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
          ) togetherWith
            (
              fadeOut(animationSpec = tween(220)) +
                slideOut(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
            )
        },
        transitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              slideIn(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
          ) togetherWith
            (
              fadeOut(animationSpec = tween(220)) +
                slideOut(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
            )
        },
        predictivePopTransitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              scaleIn(
                animationSpec = tween(220, delayMillis = 30),
                initialScale = .9f,
                TransformOrigin(-1f, .5f),
              )
          ) togetherWith
            (
              fadeOut(animationSpec = tween(220)) +
                scaleOut(
                  animationSpec = tween(220, delayMillis = 30),
                  targetScale = .9f,
                  TransformOrigin(-1f, .5f),
                )
            )
        },
      )
    }
  }
}
