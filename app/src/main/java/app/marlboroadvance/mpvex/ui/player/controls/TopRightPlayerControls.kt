package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.Decoder
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.controlColor
import org.koin.compose.koinInject

@Composable
fun TopRightPlayerControls(
  // decoder
  decoder: Decoder,
  onDecoderClick: () -> Unit,
  onDecoderLongClick: () -> Unit,
  // chapters
  isChaptersVisible: Boolean,
  onChaptersClick: () -> Unit,
  // more
  onMoreClick: () -> Unit,
  onMoreLongClick: () -> Unit,
  // New parameters
  playbackSpeed: Float,
  onPlaybackSpeedClick: () -> Unit,
  onPlaybackSpeedLongClick: () -> Unit,
  onRotationClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()

  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      // 1. Bookmarks/Chapters
      if (isChaptersVisible) {
        ControlsButton(
          Icons.Default.Bookmarks,
          onClick = onChaptersClick,
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        )
      }

      // 2. Playback Speed
      ControlsButton(
        text = stringResource(R.string.player_speed, playbackSpeed),
        onClick = onPlaybackSpeedClick,
        onLongClick = onPlaybackSpeedLongClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 3. Decoder
      ControlsButton(
        decoder.title,
        onClick = onDecoderClick,
        onLongClick = onDecoderLongClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 4. Screen Orientation
      ControlsButton(
        icon = Icons.Default.ScreenRotation,
        onClick = onRotationClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 5. More Options
      ControlsButton(
        Icons.Default.MoreVert,
        onClick = onMoreClick,
        onLongClick = onMoreLongClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
