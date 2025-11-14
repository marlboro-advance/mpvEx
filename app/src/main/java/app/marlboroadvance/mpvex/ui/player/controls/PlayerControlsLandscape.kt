package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
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
fun TopLeftPlayerControlsLandscape(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  viewModel: PlayerViewModel,
) {
  Row(
    modifier = Modifier.width(IntrinsicSize.Max),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    // Back Arrow - Always shown
    ControlsButton(
      icon = Icons.AutoMirrored.Default.ArrowBack,
      onClick = onBackPress,
      color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(45.dp),
    )

    // Video Title - Always shown
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
      modifier = Modifier.height(45.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier
            .padding(
              start = MaterialTheme.spacing.medium,
              end = MaterialTheme.spacing.medium,
              top = MaterialTheme.spacing.small,
              bottom = MaterialTheme.spacing.small,
            )
            .fillMaxWidth(1f),
      ) {
        Text(
          mediaTitle ?: "",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontWeight = FontWeight.Bold,
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
}

@Composable
fun TopRightPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
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
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
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
        buttonSize = 45.dp,
      )
    }
  }
}

@Composable
fun BottomRightPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
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
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
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
        buttonSize = 45.dp,
      )
    }
  }
}

@Composable
fun BottomLeftPlayerControlsLandscape(
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
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
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    buttons.forEach { button ->
      RenderPlayerButton(
        button = button,
        chapters = chapters,
        currentChapter = currentChapter,
        isPortrait = false,
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
        buttonSize = 45.dp,
      )
    }
  }
}


fun landscapeAnimationEnter(reduceMotion: Boolean) =
  if (!reduceMotion) {
    slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
      fadeIn(playerControlsEnterAnimationSpec())
  } else {
    fadeIn(playerControlsEnterAnimationSpec())
  }

fun landscapeAnimationExit(reduceMotion: Boolean) =
  if (!reduceMotion) {
    slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
      fadeOut(playerControlsExitAnimationSpec())
  } else {
    fadeOut(playerControlsExitAnimationSpec())
  }
