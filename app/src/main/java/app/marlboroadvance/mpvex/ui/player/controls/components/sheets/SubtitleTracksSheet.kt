package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class SubtitleItem {
  data class Track(val node: TrackNode) : SubtitleItem()
  object Divider : SubtitleItem()
}

@Composable
fun SubtitlesSheet(
  tracks: ImmutableList<TrackNode>,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  onAddSubtitle: () -> Unit,
  onOpenSubtitleSettings: () -> Unit,
  onOpenSubtitleDelay: () -> Unit,
  onRemoveSubtitle: (Int) -> Unit,
  onSearchOnline: (String?) -> Unit,
  onDismissRequest: () -> Unit,
  isSearching: Boolean = false,
  modifier: Modifier = Modifier,
  mediaTitle: String = "",
) {
  val items = remember(tracks) {
    val internal = tracks.filter { it.external != true }
    val external = tracks.filter { it.external == true }
    val list = mutableListOf<SubtitleItem>()
    list.addAll(internal.map { SubtitleItem.Track(it) })
    if (internal.isNotEmpty() && external.isNotEmpty()) {
      list.add(SubtitleItem.Divider)
    }
    list.addAll(external.map { SubtitleItem.Track(it) })
    list.toImmutableList()
  }

  GenericTracksSheet(
    tracks = items,
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
      var searchQuery by remember { mutableStateOf("") }
      val mediaInfo = remember(mediaTitle) { MediaInfoParser.parse(mediaTitle) }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
        placeholder = { Text(stringResource(R.string.pref_subtitles_search_online)) },
        leadingIcon = {
          IconButton(onClick = { 
            searchQuery = mediaInfo.title 
          }) {
            Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
          }
        },
        trailingIcon = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Close, null)
              }
            }
            IconButton(onClick = { 
              if (searchQuery.isNotBlank()) {
                onSearchOnline(searchQuery)
              }
            }) {
              Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
          if (searchQuery.isNotBlank()) {
            onSearchOnline(searchQuery)
          } else {
            onSearchOnline(null)
          }
        }),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          focusedIndicatorColor = MaterialTheme.colorScheme.primary,
          unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )
      if (isSearching) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .height(2.dp),
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    track = { item ->
      when (item) {
        is SubtitleItem.Track -> {
          val track = item.node
          SubtitleTrackRow(
            title = getTrackTitle(track),
            isSelected = isSubtitleSelected(track.id),
            isExternal = track.external == true,
            onToggle = { onToggleSubtitle(track.id) },
            onRemove = { onRemoveSubtitle(track.id) },
          )
        }
        SubtitleItem.Divider -> {
          Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small)) {
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              thickness = 1.dp
            )
          }
        }
      }
    },
    modifier = modifier,
  )
}

@Composable
fun SubtitleTrackRow(
  title: String,
  isSelected: Boolean,
  isExternal: Boolean,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Checkbox(
      checked = isSelected,
      onCheckedChange = { onToggle() },
    )
    Text(
      title,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )
    if (isExternal) {
      IconButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, contentDescription = null)
      }
    }
  }
}
