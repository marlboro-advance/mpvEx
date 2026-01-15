package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <T> GenericTracksSheet(
  tracks: ImmutableList<T>,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  lazyListState: LazyListState? = null,
  header: @Composable () -> Unit = {},
  track: @Composable (T) -> Unit = {},
  footer: @Composable () -> Unit = {},
) {
  val listState = lazyListState ?: rememberLazyListState()
  
  PlayerSheet(onDismissRequest) {
    Column(modifier) {
      header()
      LazyColumn(state = listState) {
        items(tracks) {
          track(it)
        }
        item {
          footer()
        }
      }
    }
  }
}

@Composable
fun AddTrackRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .height(56.dp)
        .padding(horizontal = MaterialTheme.spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Icon(
      Icons.Default.Add,
      contentDescription = null,
      modifier = Modifier.size(24.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions()
    }
  }
}

/**
 * Get a displayable title for a track node.
 * Uses title, language, or a default substitute.
 */
@Composable
fun getTrackTitle(
  track: TrackNode,
  allTracks: ImmutableList<TrackNode>,
): String {
  // Handle external subtitles
  if (track.isSubtitle && track.external == true && track.externalFilename != null) {
    val fileName = track.externalFilename.substringAfterLast("/")
    return stringResource(R.string.player_sheets_track_title_wo_lang, track.id, fileName)
  }

  // Build title from available metadata
  val hasTitle = !track.title.isNullOrBlank()
  val hasLang = !track.lang.isNullOrBlank()

  return when {
    hasTitle && hasLang ->
      stringResource(
        R.string.player_sheets_track_title_w_lang,
        track.id,
        track.title,
        track.lang,
      )
    hasTitle -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, track.title)
    hasLang -> stringResource(R.string.player_sheets_track_lang_wo_title, track.id, track.lang)
    track.isSubtitle -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
    track.isAudio -> stringResource(R.string.player_sheets_chapter_title_substitute_audio, track.id)
    else -> ""
  }
}


