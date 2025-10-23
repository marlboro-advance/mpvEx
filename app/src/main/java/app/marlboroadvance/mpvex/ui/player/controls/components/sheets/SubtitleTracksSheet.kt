package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import me.zhanghai.compose.preference.FooterPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (Int) -> Unit,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GenericTracksSheet(
    tracks,
    onDismissRequest = onDismissRequest,
    header = {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_sub),
        onAddSubtitle,
        actions = {
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
        title = getTrackTitle(track),
        selected = track.mainSelection?.toInt() ?: -1,
        onClick = { onSelect(track.id) },
      )
    },
    footer = {
      ProvidePreferenceLocals {
        FooterPreference(
          summary = {
            Text(stringResource(R.string.player_sheets_subtitles_footer_secondary_sid_no_styles))
          },
        )
      }
    },
    modifier = modifier,
  )
}

@Composable
fun SubtitleTrackRow(
  title: String,
  selected: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(start = MaterialTheme.spacing.smaller, end = MaterialTheme.spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      selected > -1,
      onCheckedChange = { _ -> onClick() },
    )
    Text(
      title,
      fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
    )
    Spacer(modifier = Modifier.weight(1f))
    if (selected != -1) {
      Text(
        "#${selected + 1}",
        fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
        fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
      )
    }
  }
}
