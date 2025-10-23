package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.AudioDelayPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.FrameNavigationPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.SubtitleDelayPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.SubtitleSettingsPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.VideoSettingsPanel
import org.koin.compose.koinInject

@Composable
fun PlayerPanels(
  panelShown: Panels,
  viewModel: app.marlboroadvance.mpvex.ui.player.PlayerViewModel,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedContent(
    targetState = panelShown,
    label = "panels",
    contentAlignment = Alignment.CenterEnd,
    contentKey = { it.name },
    transitionSpec = {
      fadeIn() + slideInHorizontally { it / 3 } togetherWith fadeOut() + slideOutHorizontally { it / 2 }
    },
    modifier = modifier,
  ) { currentPanel ->
    when (currentPanel) {
      Panels.None -> {
        Box(Modifier.fillMaxHeight())
      }
      Panels.SubtitleSettings -> {
        SubtitleSettingsPanel(onDismissRequest)
      }
      Panels.SubtitleDelay -> {
        SubtitleDelayPanel(onDismissRequest)
      }
      Panels.AudioDelay -> {
        AudioDelayPanel(onDismissRequest)
      }
      Panels.VideoFilters -> {
        VideoSettingsPanel(onDismissRequest)
      }
      Panels.FrameNavigation -> {
        val currentFrame by viewModel.currentFrame.collectAsState()
        val totalFrames by viewModel.totalFrames.collectAsState()
        FrameNavigationPanel(
          currentFrame = currentFrame,
          totalFrames = totalFrames,
          onUpdateFrameInfo = viewModel::updateFrameInfo,
          onPause = viewModel::pause,
          onUnpause = viewModel::unpause,
          onPauseUnpause = viewModel::pauseUnpause,
          onSeekTo = { position, _ -> viewModel.seekTo(position) },
          onDismissRequest = onDismissRequest,
        )
      }
    }
  }
}

val CARDS_MAX_WIDTH = 420.dp
val panelCardsColors: @Composable () -> CardColors = {
  val playerPreferences = koinInject<PlayerPreferences>()
  val transparency = playerPreferences.panelTransparency.get()

  val colors = CardDefaults.cardColors()
  colors.copy(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = transparency),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceDim.copy(alpha = transparency),
  )
}
