package app.marlboroadvance.mpvex.ui.player.controls

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.controlColor
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject

@SuppressLint("NewApi")
@Composable
fun BottomRightPlayerControls(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val aspect by playerPreferences.videoAspect.collectAsState()
  val currentZoom by viewModel.videoZoom.collectAsState()

  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    val activity = LocalActivity.current as PlayerActivity

    ControlsGroup {
      ControlsButton(
        Icons.Default.Camera,
        onClick = { viewModel.sheetShown.update { Sheets.FrameNavigation } },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      if (currentZoom != 0f) {
        ControlsButton(
          text = "%.2fx".format(currentZoom),
          onClick = { viewModel.sheetShown.update { Sheets.VideoZoom } },
          onLongClick = { viewModel.sheetShown.update { Sheets.VideoZoom } },
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        )
      } else {
        ControlsButton(
          Icons.Default.ZoomIn,
          onClick = { viewModel.sheetShown.update { Sheets.VideoZoom } },
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        )
      }

      ControlsButton(
        Icons.Default.PictureInPictureAlt,
        onClick = { activity.enterPipModeHidingOverlay() },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )
      ControlsButton(
        icon = when (aspect) {
          VideoAspect.Fit -> Icons.Default.AspectRatio
          VideoAspect.Stretch -> Icons.Default.ZoomOutMap
          VideoAspect.Crop -> Icons.Default.FitScreen
        },
        onClick = {
          when (aspect) {
            VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Crop)
            VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Stretch)
            VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Fit)
          }
        },
        onLongClick = { viewModel.sheetShown.update { Sheets.AspectRatios } },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
