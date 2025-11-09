package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject

@Composable
fun AudioTracksSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (TrackNode) -> Unit,
  onAddAudioTrack: () -> Unit,
  onOpenDelayPanel: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val isBackgroundPlayback by playerPreferences.automaticBackgroundPlayback.collectAsState()

  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_audio),
        onAddAudioTrack,
        actions = {
          IconToggleButton(
            checked = isBackgroundPlayback,
            onCheckedChange = { playerPreferences.automaticBackgroundPlayback.set(it) },
          ) {
            Icon(
              imageVector = if (isBackgroundPlayback) Icons.Filled.Headset else Icons.Filled.HeadsetOff,
              contentDescription = stringResource(R.string.background_playback_title),
            )
          }
          IconButton(onClick = onOpenDelayPanel) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = {
      AudioTrackRow(
        title = getTrackTitle(it, tracks, emptyMap()),
        isSelected = it.isSelected,
        onClick = { onSelect(it) },
      )
    },
    modifier = modifier,
  )
}

@Composable
fun AudioTrackRow(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    RadioButton(
      isSelected,
      onClick,
    )
    Text(
      title,
      fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
      fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
    )
  }
}
