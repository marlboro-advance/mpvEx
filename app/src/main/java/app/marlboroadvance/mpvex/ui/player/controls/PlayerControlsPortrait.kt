package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.controlColor
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopPlayerControlsPortrait(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  viewModel: PlayerViewModel,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ControlsGroup {
        ControlsButton(
          icon = Icons.AutoMirrored.Default.ArrowBack,
          onClick = onBackPress,
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        )

        Surface(
          shape = CircleShape,
          color =
            if (hideBackground) {
              Color.Transparent
            } else {
              MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = 0.55f,
              )
            },
          contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border =
            if (hideBackground) {
              null
            } else {
              BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
              )
            },
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier
                .padding(
                  horizontal = MaterialTheme.spacing.medium,
                  vertical = MaterialTheme.spacing.small,
                ),
          ) {
            Text(
              mediaTitle ?: "",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f, fill = false),
            )
            viewModel.getPlaylistInfo()?.let { playlistInfo ->
              Text(
                " • $playlistInfo",
                maxLines = 1,
                overflow = TextOverflow.Visible,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun BottomPlayerControlsPortrait(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: app.marlboroadvance.mpvex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = MaterialTheme.spacing.large)
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      buttons.forEach { button ->
        RenderPlayerButton(
          button = button,
          chapters = chapters,
          currentChapter = currentChapter,
          isPortrait = true,
          isSpeedNonOne = isSpeedNonOne,
          currentZoom = currentZoom,
          aspect = aspect,
          mediaTitle = mediaTitle,
          hideBackground = hideBackground,
          onBackPress = onBackPress,
          onOpenSheet = onOpenSheet,
          onOpenPanel = onOpenPanel,
          viewModel = viewModel,
          activity = activity,
          decoder = decoder,
          playbackSpeed = playbackSpeed,
          buttonSize = 48.dp,
        )
      }
    }
  }
}

