package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.player.controls.components.CurrentChapter
import dev.vivvvek.seeker.Segment

@Composable
fun BottomLeftPlayerControls(
  playbackSpeed: Float,
  currentChapter: Segment?,
  showChapterIndicator: Boolean,
  onLockControls: () -> Unit,
  onCycleRotation: () -> Unit,
  onPlaybackSpeedChange: (Float) -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      ControlsButton(
        Icons.Default.LockOpen,
        onClick = onLockControls,
      )
      ControlsButton(
        icon = Icons.Default.ScreenRotation,
        onClick = onCycleRotation,
      )

      ControlsButton(
        text = stringResource(R.string.player_speed, playbackSpeed),
        onClick = { onPlaybackSpeedChange(if (playbackSpeed >= 2) 0.25f else playbackSpeed + 0.25f) },
        onLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
      )
      AnimatedVisibility(
        showChapterIndicator && currentChapter != null,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        currentChapter?.let { chapter ->
          CurrentChapter(
            chapter = chapter,
            onClick = { onOpenSheet(Sheets.Chapters) },
          )
        }
      }
    }
  }
}
