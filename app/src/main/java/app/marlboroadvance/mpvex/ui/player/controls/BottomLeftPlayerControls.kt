package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.player.controls.components.CurrentChapter
import app.marlboroadvance.mpvex.ui.theme.controlColor
import dev.vivvvek.seeker.Segment
import org.koin.compose.koinInject

@Composable
fun BottomLeftPlayerControls(
  currentChapter: Segment?,
  showChapterIndicator: Boolean,
  onLockControls: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  // New parameters
  onAudioClick: () -> Unit,
  onAudioLongClick: () -> Unit,
  onSubtitlesClick: () -> Unit,
  onSubtitlesLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      // 1. Lock Control
      ControlsButton(
        Icons.Default.LockOpen,
        onClick = onLockControls,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 2. Audio Track
      ControlsButton(
        Icons.Default.Audiotrack,
        onClick = onAudioClick,
        onLongClick = onAudioLongClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 3. Subtitles
      ControlsButton(
        Icons.Default.Subtitles,
        onClick = onSubtitlesClick,
        onLongClick = onSubtitlesLongClick,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      )

      // 4. Current Chapter Indicator
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
