package app.marlboroadvance.mpvex

import android.os.Bundle
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.repository.NetworkRepository
import app.marlboroadvance.mpvex.ui.UpdateDialog
import app.marlboroadvance.mpvex.ui.UpdateViewModel
import app.marlboroadvance.mpvex.ui.browser.MainScreen
import app.marlboroadvance.mpvex.ui.compose.LocalLazyGridState
import app.marlboroadvance.mpvex.ui.compose.LocalLazyListState
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Main entry point for the application
 */
class MainActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()
  private val networkRepository by inject<NetworkRepository>()
  
  // Create a coroutine scope tied to the activity lifecycle
  private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Register proxy lifecycle observer for network streaming
    lifecycle.addObserver(app.marlboroadvance.mpvex.ui.browser.networkstreaming.proxy.ProxyLifecycleObserver())

    setContent {
      // Set up theme and edge-to-edge display
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)
      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      // Auto-connect to saved network connections
      LaunchedEffect(Unit) {
        autoConnectToNetworks()
      }

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

  /**
   * Auto-connect to network connections that are marked for auto-connection
   */
  private suspend fun autoConnectToNetworks() {
    // Delay auto-connect to let UI settle first
    kotlinx.coroutines.delay(500)
    
    // Use coroutineScope for properly structured concurrency
    withContext(Dispatchers.IO) {
      try {
        val autoConnectConnections = networkRepository.getAutoConnectConnections()
        autoConnectConnections.forEach { connection ->
          withContext(Dispatchers.Main) {
            Log.d("MainActivity", "Auto-connecting to: ${connection.name}")
          }
          networkRepository.connect(connection)
            .onSuccess {
              withContext(Dispatchers.Main) {
                Log.d("MainActivity", "Auto-connected successfully: ${connection.name}")
              }
            }
            .onFailure { e ->
              withContext(Dispatchers.Main) {
                Log.e("MainActivity", "Auto-connect failed for ${connection.name}: ${e.message}")
              }
            }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Log.e("MainActivity", "Error during auto-connect", e)
        }
      }
    }
  }

  /**
   * Navigator that handles screen transitions and provides shared states
   */
  @Composable
  fun Navigator() {
    val backstack = rememberNavBackStack(MainScreen)

    // Create shared LazyListState and LazyGridState that will be used by all screens
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    @Suppress("UNCHECKED_CAST")
    val typedBackstack = backstack as NavBackStack<Screen>

    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = viewModel(context as ComponentActivity)
    val updateState by updateViewModel.updateState.collectAsState()
    val isDownloading by updateViewModel.isDownloading.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")

    // Provide both LocalBackStack and the LazyList/Grid states to all screens
    CompositionLocalProvider(
      LocalBackStack provides typedBackstack,
      LocalLazyListState provides lazyListState,
      LocalLazyGridState provides lazyGridState
    ) {
      NavDisplay(
        backStack = typedBackstack,
        onBack = { typedBackstack.removeLastOrNull() },
        entryProvider = { route -> NavEntry(route) { route.Content() } },
        popTransitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              slideIn(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
          ) togetherWith (
              fadeOut(animationSpec = tween(220)) +
                slideOut(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
          )
        },
        transitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              slideIn(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
          ) togetherWith (
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
          ) togetherWith (
              fadeOut(animationSpec = tween(220)) +
                scaleOut(
                  animationSpec = tween(220, delayMillis = 30),
                  targetScale = .9f,
                  TransformOrigin(-1f, .5f),
                )
          )
        },
      )

      // Display Update Dialog when appropriate
      when (updateState) {
        is UpdateViewModel.UpdateState.Available -> {
          val release = (updateState as UpdateViewModel.UpdateState.Available).release
          UpdateDialog(
            release = release,
            isDownloading = isDownloading,
            progress = downloadProgress,
            actionLabel = if (isDownloading) "Downloading..." else "Download",
            currentVersion = currentVersion,
            onDismiss = { updateViewModel.dismiss() },
            onAction = { updateViewModel.downloadUpdate(release) },
            onIgnore = { updateViewModel.ignoreVersion(release.tagName.removePrefix("v")) }
          )
        }
        is UpdateViewModel.UpdateState.ReadyToInstall -> {
          val release = (updateState as UpdateViewModel.UpdateState.ReadyToInstall).release
          UpdateDialog(
            release = release,
            isDownloading = isDownloading,
            progress = downloadProgress,
            actionLabel = "Install",
            currentVersion = currentVersion,
            onDismiss = { updateViewModel.dismiss() },
            onAction = { updateViewModel.installUpdate(release) },
            onIgnore = { updateViewModel.ignoreVersion(release.tagName.removePrefix("v")) }
          )
        }
        else -> {}
      }
    }
  }
}
