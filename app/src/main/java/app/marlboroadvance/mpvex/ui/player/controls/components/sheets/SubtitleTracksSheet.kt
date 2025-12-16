package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onSelect: (Int) -> Unit,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onRemoveSubtitle: (Int) -> Unit,
  onDismissRequest: () -> Unit,
  onSelectSecondary: (Int) -> Unit = {},
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
        title = getTrackTitle(track, tracks),
        // Pass 1 for Primary, 2 for Secondary, null for None
        selected = track.mainSelection?.toInt() ?: -1,
        isExternal = track.external == true,
        onClick = { onSelect(track.id) },
        onLongClick = { onSelectSecondary(track.id) },
        onRemove = { onRemoveSubtitle(track.id) },
      )
    },
    modifier = modifier,
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtitleTrackRow(
  title: String,
  selected: Int,
  isExternal: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Determine states based on encoded selection value
  val isSelected = selected > -1
  val isPrimary = selected == 1
  val isSecondary = selected == 2

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick
        )
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Checkbox(
      checked = isSelected,
      onCheckedChange = { onClick() },
    )

    // CHANGE LOG: Added column to display track title and Primary/Secondary label
    // Impact: Clearly indicates which subtitle is which, reducing confusion
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        fontStyle = if (isSelected) FontStyle.Italic else FontStyle.Normal,
        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
      )

      if (isSelected) {
        Text(
          text = if (isPrimary) "Primary" else "Secondary",
          style = MaterialTheme.typography.labelSmall,
          color = if (isPrimary) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.secondary
        )
      }
    }

    if (isExternal) {
      IconButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, contentDescription = null)
      }
    }
  }
}
