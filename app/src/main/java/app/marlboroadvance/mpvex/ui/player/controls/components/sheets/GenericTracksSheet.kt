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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    header: @Composable () -> Unit = {},
    track: @Composable (T) -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    PlayerSheet(onDismissRequest) {
        Column(modifier) {
            header()
            LazyColumn {
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
        actions()
    }
}

@Composable
fun getTrackTitle(
    track: TrackNode,
    externalSubtitleMetadata: Map<String, String> = emptyMap(),
): String {
    // For external subtitles, use cached metadata for proper filename display
    if (track.isSubtitle && track.external == true && track.externalFilename != null) {
        externalSubtitleMetadata[track.externalFilename]?.let { cachedName ->
            return stringResource(R.string.player_sheets_track_title_wo_lang, track.id, cachedName)
        }
    }

    val hasTitle = !track.title.isNullOrBlank()
    val hasLang = !track.lang.isNullOrBlank()

    return when {
        hasTitle && hasLang -> stringResource(R.string.player_sheets_track_title_w_lang, track.id, track.title, track.lang)
        hasTitle -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, track.title)
        hasLang -> stringResource(R.string.player_sheets_track_lang_wo_title, track.id, track.lang)
        track.isSubtitle -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
        track.isAudio -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
        else -> ""
    }
}
