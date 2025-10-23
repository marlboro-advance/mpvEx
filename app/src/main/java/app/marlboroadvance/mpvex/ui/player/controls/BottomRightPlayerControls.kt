package app.marlboroadvance.mpvex.ui.player.controls

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.theme.spacing
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

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    val activity = LocalActivity.current as PlayerActivity

    if (currentZoom != 0f) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        modifier =
          Modifier
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable { viewModel.sheetShown.update { Sheets.VideoZoom } }
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.smaller),
      ) {
        Icon(
          imageVector = Icons.Default.ZoomIn,
          contentDescription = null,
          modifier =
            Modifier
              .padding(end = MaterialTheme.spacing.extraSmall)
              .size(16.dp),
          tint = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "%.2fx".format(currentZoom),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    } else {
      ControlsButton(
        Icons.Default.ZoomIn,
        onClick = {
          viewModel.sheetShown.update { Sheets.VideoZoom }
        },
      )
    }

    if (activity.isPipSupported) {
      ControlsButton(
        Icons.Default.PictureInPictureAlt,
        onClick = {
          activity.enterPipModeHidingOverlay()
        },
      )
    }
    ControlsButton(
      Icons.Default.AspectRatio,
      onClick = {
        when (aspect) {
          VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
          VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
          VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
        }
      },
    )
  }
}
