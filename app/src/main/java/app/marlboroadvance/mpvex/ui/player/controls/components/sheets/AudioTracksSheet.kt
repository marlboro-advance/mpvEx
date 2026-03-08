package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioChannels
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
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
  val audioPreferences = koinInject<AudioPreferences>()
  val audioChannels by audioPreferences.audioChannels.collectAsState()

  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_audio),
        onAddAudioTrack,
        actions = {
          IconButton(onClick = onOpenDelayPanel) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = {
      AudioTrackRow(
        title = getTrackTitle(it),
        isSelected = it.isSelected,
        onClick = { onSelect(it) },
      )
    },
    footer = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(MaterialTheme.spacing.medium)
      ) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Text(
          text = stringResource(id = R.string.pref_audio_channels),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.smaller))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          items(AudioChannels.entries) {
            FilterChip(
              selected = audioChannels == it,
              onClick = {
                audioPreferences.audioChannels.set(it)
                if (it == AudioChannels.ReverseStereo) {
                  MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                } else {
                  MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
                }
                MPVLib.setPropertyString(it.property, it.value)
              },
              label = { Text(text = stringResource(id = it.title)) },
              leadingIcon = null,
            )
          }
        }
      }
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
