package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

data class PlaylistItem(
  val uri: Uri,
  val title: String,
  val index: Int,
  val isPlaying: Boolean,
)

@Composable
fun PlaylistSheet(
  playlist: ImmutableList<PlaylistItem>,
  onDismissRequest: () -> Unit,
  onItemClick: (PlaylistItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  GenericTracksSheet(
    playlist,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    track = { item ->
      PlaylistTrack(
        item = item,
        onClick = { onItemClick(item) },
      )
    },
  )
}

@Composable
fun PlaylistTrack(
  item: PlaylistItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val textStyle = if (item.isPlaying) {
    MaterialTheme.typography.bodyLarge.copy(
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
    )
  } else {
    MaterialTheme.typography.bodyLarge
  }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(
          vertical = MaterialTheme.spacing.small,
          horizontal = MaterialTheme.spacing.medium,
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    if (item.isPlaying) {
      Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    } else {
      Text(
        text = "${item.index + 1}.",
        style = textStyle,
        modifier = Modifier.width(20.dp),
        maxLines = 1,
      )
    }

    Text(
            text = item.title,
            style = textStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

    if (item.isPlaying) {
      Text(
        text = "Playing",
        style = MaterialTheme.typography.bodySmall.copy(
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        ),
      )
    }
  }
}
