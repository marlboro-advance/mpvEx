package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.CurrentChapter
import app.marlboroadvance.mpvex.ui.theme.controlColor
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import kotlinx.coroutines.flow.update

@Composable
fun RenderPlayerButton(
  button: PlayerButton,
  chapters: List<Segment>,
  currentChapter: Int?,
  isPortrait: Boolean,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  buttonSize: Dp = 40.dp,
) {
  when (button) {
    PlayerButton.BACK_ARROW -> {
      ControlsButton(
        icon = Icons.AutoMirrored.Default.ArrowBack,
        onClick = onBackPress,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.VIDEO_TITLE -> {
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
        tonalElevation = if (hideBackground) 0.dp else 2.dp,
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
        modifier = Modifier.height(buttonSize),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .padding(
                horizontal = MaterialTheme.spacing.extraSmall,
                vertical = MaterialTheme.spacing.small,
              )
              .fillMaxWidth(1f),
        ) {
          Text(
            mediaTitle ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
          )
          viewModel.getPlaylistInfo()?.let { playlistInfo ->
            Text(
              " • $playlistInfo",
              maxLines = 1,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    PlayerButton.BOOKMARKS_CHAPTERS -> {
      if (chapters.isNotEmpty()) {
        ControlsButton(
          Icons.Default.Bookmarks,
          onClick = { onOpenSheet(Sheets.Chapters) },
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.PLAYBACK_SPEED -> {
      ControlsButton(
        icon = Icons.Default.Speed,
        onClick = { onOpenSheet(Sheets.PlaybackSpeed) },
        color = if (isSpeedNonOne) MaterialTheme.colorScheme.primary else if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.DECODER -> {
      ControlsButton(
        icon = Icons.Default.Memory,
        onClick = { onOpenSheet(Sheets.Decoders) },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.SCREEN_ROTATION -> {
      ControlsButton(
        icon = Icons.Default.ScreenRotation,
        onClick = viewModel::cycleScreenRotations,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.FRAME_NAVIGATION -> {
      ControlsButton(
        Icons.Default.Camera,
        onClick = { viewModel.sheetShown.update { Sheets.FrameNavigation } },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.VIDEO_ZOOM -> {
      ControlsButton(
        Icons.Default.ZoomIn,
        onClick = { viewModel.sheetShown.update { Sheets.VideoZoom } },
        color = if (currentZoom != 0f) MaterialTheme.colorScheme.primary else if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.PICTURE_IN_PICTURE -> {
      ControlsButton(
        Icons.Default.PictureInPictureAlt,
        onClick = { activity.enterPipModeHidingOverlay() },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.ASPECT_RATIO -> {
      ControlsButton(
        icon =
          when (aspect) {
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
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.LOCK_CONTROLS -> {
      ControlsButton(
        Icons.Default.LockOpen,
        onClick = viewModel::lockControls,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.AUDIO_TRACK -> {
      ControlsButton(
        Icons.Default.Audiotrack,
        onClick = { onOpenSheet(Sheets.AudioTracks) },
        onLongClick = { onOpenPanel(Panels.AudioDelay) },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.SUBTITLES -> {
      ControlsButton(
        Icons.Default.Subtitles,
        onClick = { onOpenSheet(Sheets.SubtitleTracks) },
        onLongClick = { onOpenPanel(Panels.SubtitleSettings) },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.MORE_OPTIONS -> {
      ControlsButton(
        Icons.Default.MoreVert,
        onClick = { onOpenSheet(Sheets.More) },
        onLongClick = { onOpenPanel(Panels.VideoFilters) },
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.CURRENT_CHAPTER -> {
      if (isPortrait) {
        // In portrait mode, do nothing (chapter icon is already shown in consolidated controls)
      } else {
        // In landscape mode, show the full current chapter component
        AnimatedVisibility(
          chapters.getOrNull(currentChapter ?: 0) != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          chapters.getOrNull(currentChapter ?: 0)?.let { chapter ->
            CurrentChapter(
              chapter = chapter,
              onClick = { onOpenSheet(Sheets.Chapters) },
            )
          }
        }
      }
    }

    PlayerButton.NONE -> { /* Do nothing */
    }
  }
}
