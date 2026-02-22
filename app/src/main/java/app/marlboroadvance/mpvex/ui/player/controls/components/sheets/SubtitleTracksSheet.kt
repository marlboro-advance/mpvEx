package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSubtitle
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class SubtitleItem {
  data class Track(val node: TrackNode) : SubtitleItem()
  data class OnlineTrack(val subtitle: WyzieSubtitle) : SubtitleItem()
  data class Header(val title: String) : SubtitleItem()
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
  onDownloadOnline: (WyzieSubtitle) -> Unit,
  onDismissRequest: () -> Unit,
  isSearching: Boolean = false,
  isDownloading: Boolean = false,
  searchResults: ImmutableList<WyzieSubtitle> = emptyList<WyzieSubtitle>().toImmutableList(),
  isOnlineSectionExpanded: Boolean = true,
  onToggleOnlineSection: () -> Unit = {},
  modifier: Modifier = Modifier,
  mediaTitle: String = "",
) {
  val items = remember(tracks, searchResults, isSearching, isOnlineSectionExpanded) {
    val list = mutableListOf<SubtitleItem>()
    
    // Internal/Local tracks section
    val internal = tracks.filter { it.external != true }
    val external = tracks.filter { it.external == true }
    
    if (internal.isNotEmpty() || external.isNotEmpty()) {
        list.add(SubtitleItem.Header(if (internal.isNotEmpty()) "Embedded Subtitles" else "Local Subtitles"))
        list.addAll(internal.map { SubtitleItem.Track(it) })
        if (internal.isNotEmpty() && external.isNotEmpty()) {
          list.add(SubtitleItem.Divider)
        }
        list.addAll(external.map { SubtitleItem.Track(it) })
    }

    // Online Search Results section
    if (searchResults.isNotEmpty() || isSearching) {
        if (list.isNotEmpty()) list.add(SubtitleItem.Divider)
        list.add(SubtitleItem.Header("Online Results (${searchResults.size})"))
        if (isOnlineSectionExpanded) {
            list.addAll(searchResults.map { SubtitleItem.OnlineTrack(it) })
        }
    }

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
      val keyboardController = LocalSoftwareKeyboardController.current
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
          IconButton(onClick = { searchQuery = mediaInfo.title }) {
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
            if (isSearching || isDownloading) {
                 CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                 Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { 
              if (searchQuery.isNotBlank()) onSearchOnline(searchQuery) else onSearchOnline(null)
              keyboardController?.hide()
            }) {
              Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
          onSearchOnline(if (searchQuery.isNotBlank()) searchQuery else null)
          keyboardController?.hide()
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
          modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium).height(2.dp),
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
        is SubtitleItem.OnlineTrack -> {
            WyzieSubtitleRow(
                subtitle = item.subtitle,
                onDownload = { onDownloadOnline(item.subtitle) },
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = 2.dp)
            )
        }
        is SubtitleItem.Header -> {
            val isOnlineHeader = item.title.startsWith("Online Results")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isOnlineHeader) Modifier.clickable { onToggleOnlineSection() } else Modifier)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (isOnlineHeader) {
                    Icon(
                        imageVector = if (isOnlineSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        SubtitleItem.Divider -> {
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
      }
    },
    modifier = modifier,
  )
}

@Composable
fun WyzieSubtitleRow(
    subtitle: WyzieSubtitle,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onDownload() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtitle.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = subtitle.displayLanguage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    subtitle.source?.let { Text(text = " • $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    subtitle.format?.let { Text(text = " • ${it.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                }
            }
            IconButton(onClick = onDownload) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
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
    modifier = modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
    if (isExternal) {
      IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = null) }
    }
  }
}
