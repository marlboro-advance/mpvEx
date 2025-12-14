package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.koinInject

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (Int) -> Unit,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onRemoveSubtitle: (Int) -> Unit,
  onOpenOnlineSearch: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  externalSubtitleMetadata: Map<String, String> = emptyMap(),
) {
  val preferences = koinInject<SubtitlesPreferences>()
  val subdlApiKey by preferences.subdlApiKey.collectAsState()
  val download = null

  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_sub),
        onAddSubtitle,
        actions = {
          // Only show cloud download icon if API key is set
          if (subdlApiKey.isNotBlank()) {
            IconButton(onClick = onOpenOnlineSearch) {
              Icon(Icons.Default.CloudDownload, download)
            }
          }
          IconButton(onClick = onOpenSubtitleSettings) {
            Icon(Icons.Default.Palette, null)
          }
          IconButton(onClick = onOpenSubtitleDelay) {
            Icon(Icons.Default.MoreTime, null)
          }
        },
      )
    },
    track = { track ->
      SubtitleTrackRow(
        title = getTrackTitle(track, tracks, externalSubtitleMetadata),
        selected = track.mainSelection?.toInt() ?: -1,
        isExternal = track.external == true,
        onClick = { onSelect(track.id) },
        onRemove = { onRemoveSubtitle(track.id) },
      )
    },
    modifier = modifier,
  )
}

@Composable
fun SubtitleTrackRow(
  title: String,
  selected: Int,
  isExternal: Boolean,
  onClick: () -> Unit,
  onRemove: () -> Unit,
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
      selected = selected > -1,
      onClick = onClick,
    )
    Text(
      title,
      fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )
    if (isExternal) {
      IconButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, contentDescription = null)
      }
    }
  }
}
