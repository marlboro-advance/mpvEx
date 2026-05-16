package app.gyrolet.mpvrx.ui.player.controls

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.PlayerButton
import app.gyrolet.mpvrx.ui.player.Panels
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import app.gyrolet.mpvrx.ui.player.Sheets
import app.gyrolet.mpvrx.ui.player.VideoAspect
import app.gyrolet.mpvrx.ui.player.controls.components.ControlsButton
import app.gyrolet.mpvrx.ui.player.controls.components.ControlsGroup
import app.gyrolet.mpvrx.ui.theme.controlColor
import app.gyrolet.mpvrx.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun TopPlayerControlsPortrait(
  mediaTitle: String?,
  hideBackground: Boolean,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  viewModel: PlayerViewModel,
  isTranslatingSub: Boolean = false,
  isRealtimeSubsActive: Boolean = false,
  realtimeSubsLanguage: String = "",
  translationStatus: String = "",
  translatingTrackName: String = "",
) {
  val playlistModeEnabled = viewModel.hasPlaylistSupport()
  val clickEvent = LocalPlayerButtonsClickEvent.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = MaterialTheme.spacing.medium)
      .padding(horizontal = MaterialTheme.spacing.medium),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ControlsGroup {
        ControlsButton(
          icon = Icons.Default.ArrowBack,
          onClick = onBackPress,
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(45.dp),
        )

        Column(
          modifier = Modifier.padding(start = 4.dp),
        ) {
          val titleInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            onClick = {
              clickEvent()
              onOpenSheet(Sheets.Playlist)
            },
            enabled = playlistModeEnabled,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.height(45.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 14.dp),
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
                  style = MaterialTheme.typography.bodySmall,
                  color = LocalContentColor.current.copy(alpha = 0.7f),
                )
              }
            }
          }
        }
      }
    }

    androidx.compose.animation.AnimatedVisibility(
      visible = isTranslatingSub || isRealtimeSubsActive,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 14.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Translate,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
          text = if (isRealtimeSubsActive) {
            "Real-time subs: ${realtimeSubsLanguage.ifBlank { "?" }} ${translationStatus.ifBlank { "" }}"
          } else {
            "Translating ${translatingTrackName.ifBlank { "subs" }} ${translationStatus.ifBlank { "" }}"
          },
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.tertiary,
        )
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
  decoder: app.gyrolet.mpvrx.ui.player.Decoder,
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
      .horizontalScroll(rememberScrollState())
      .padding(bottom = MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
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
        buttonSize = 44.dp, // Slightly more compact size
      )
    }
  }
}




