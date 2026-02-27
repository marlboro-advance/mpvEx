package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.basicMarquee
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
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class OnlineSubtitleItem {
  data class OnlineTrack(val subtitle: WyzieSubtitle) : OnlineSubtitleItem()
  data class Header(val title: String) : OnlineSubtitleItem()
  object Divider : OnlineSubtitleItem()
}

@Composable
fun OnlineSubtitleSearchSheet(
  onDismissRequest: () -> Unit,
  onDownloadOnline: (WyzieSubtitle) -> Unit,
  isSearching: Boolean = false,
  isDownloading: Boolean = false,
  searchResults: ImmutableList<WyzieSubtitle> = emptyList<WyzieSubtitle>().toImmutableList(),
  isOnlineSectionExpanded: Boolean = true,
  onToggleOnlineSection: () -> Unit = {},
  modifier: Modifier = Modifier,
  mediaTitle: String = "",
  // Autocomplete & Series Selection
  mediaSearchResults: ImmutableList<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult> = emptyList<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult>().toImmutableList(),
  isSearchingMedia: Boolean = false,
  onSearchMedia: (String) -> Unit = {},
  onSelectMedia: (app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult) -> Unit = {},
  selectedTvShow: app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails? = null,
  isFetchingTvDetails: Boolean = false,
  selectedSeason: app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason? = null,
  onSelectSeason: (app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason) -> Unit = {},
  seasonEpisodes: ImmutableList<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode> = emptyList<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>().toImmutableList(),
  isFetchingEpisodes: Boolean = false,
  selectedEpisode: app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode? = null,
  onSelectEpisode: (app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode) -> Unit = {},
  onClearMediaSelection: () -> Unit = {}
) {
  val items = remember(searchResults, isSearching, isOnlineSectionExpanded) {
    val list = mutableListOf<OnlineSubtitleItem>()
    
    // Online Search Results section
    if (searchResults.isNotEmpty() || isSearching) {
        list.add(OnlineSubtitleItem.Header("Online Results (${searchResults.size})"))
        if (isOnlineSectionExpanded) {
            list.addAll(searchResults.map { OnlineSubtitleItem.OnlineTrack(it) })
        }
    }

    list.toImmutableList()
  }

  GenericTracksSheet(
    tracks = items,
    onDismissRequest = onDismissRequest,
    header = {
      val keyboardController = LocalSoftwareKeyboardController.current
      var searchQuery by remember { mutableStateOf("") }
      val mediaInfo = remember(mediaTitle) { MediaInfoParser.parse(mediaTitle) }
      
      Column(
        modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { 
            searchQuery = it
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
          placeholder = { Text(stringResource(R.string.pref_subtitles_search_online)) },
          leadingIcon = {
            IconButton(onClick = { 
              searchQuery = mediaInfo.title
              onSearchMedia(mediaInfo.title)
            }) {
              Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
            }
          },
          trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { 
                  searchQuery = ""
                  onClearMediaSelection()
                }) {
                  Icon(Icons.Default.Close, null)
                }
              }
              if (isSearching || isDownloading || isSearchingMedia) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
              }
              IconButton(onClick = {
                val q = if (searchQuery.isNotBlank()) searchQuery else mediaInfo.title
                searchQuery = q
                onSearchMedia(q)
                keyboardController?.hide()
              }) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
              }
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = {
            val q = if (searchQuery.isNotBlank()) searchQuery else mediaInfo.title
            searchQuery = q
            onSearchMedia(q)
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

        // Autocomplete Results
        if (mediaSearchResults.isNotEmpty()) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MaterialTheme.spacing.medium)
              .heightIn(max = 200.dp),
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
          ) {
            androidx.compose.foundation.lazy.LazyColumn {
              items(mediaSearchResults.size) { index ->
                val result = mediaSearchResults[index]
                TmdbResultRow(
                  result = result,
                  onClick = { 
                    searchQuery = result.title
                    onSelectMedia(result)
                    keyboardController?.hide()
                  }
                )
                if (index < mediaSearchResults.size - 1) {
                  HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
              }
            }
          }
        }

        // Series / Season / Episode Selection UI
        if (selectedTvShow != null) {
          SeriesDetailsSection(
            tvShow = selectedTvShow,
            isFetchingSeasons = isFetchingTvDetails,
            selectedSeason = selectedSeason,
            onSelectSeason = onSelectSeason,
            isFetchingEpisodes = isFetchingEpisodes,
            episodes = seasonEpisodes,
            selectedEpisode = selectedEpisode,
            onSelectEpisode = onSelectEpisode,
            onClose = onClearMediaSelection
          )
        }
      }
      if (isSearching) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium).height(2.dp),
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    track = { item ->
      when (item) {
        is OnlineSubtitleItem.OnlineTrack -> {
            WyzieSubtitleRow(
                subtitle = item.subtitle,
                onDownload = { onDownloadOnline(item.subtitle) },
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = 2.dp)
            )
        }
        is OnlineSubtitleItem.Header -> {
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
        OnlineSubtitleItem.Divider -> {
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
fun TmdbResultRow(
    result: app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${result.mediaType.uppercase()} ${result.releaseYear ?: ""}".trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SeriesDetailsSection(
    tvShow: app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails,
    isFetchingSeasons: Boolean,
    selectedSeason: app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason?,
    onSelectSeason: (app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason) -> Unit,
    isFetchingEpisodes: Boolean,
    episodes: ImmutableList<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>,
    selectedEpisode: app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode?,
    onSelectEpisode: (app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(bottom = MaterialTheme.spacing.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tvShow.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Season Dropdown
                val seasonDropdownExpanded = remember { mutableStateOf(false) }
                Box {
                  FilledTonalButton(
                      onClick = { seasonDropdownExpanded.value = true },
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                      modifier = Modifier.height(38.dp),
                      shape = RoundedCornerShape(8.dp)
                  ) {
                      Text(
                          text = selectedSeason?.let { "S${it.season_number}" } ?: "Season",
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1
                      )
                      Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                  }
                  DropdownMenu(
                      expanded = seasonDropdownExpanded.value,
                      onDismissRequest = { seasonDropdownExpanded.value = false },
                      modifier = Modifier.heightIn(max = 300.dp),
                      shape = RoundedCornerShape(12.dp),
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                  ) {
                      tvShow.seasons.forEach { season ->
                          DropdownMenuItem(
                              text = { 
                                Text(
                                  "Season ${season.season_number}",
                                  style = MaterialTheme.typography.bodyLarge
                                ) 
                              },
                              onClick = {
                                  onSelectSeason(season)
                                  seasonDropdownExpanded.value = false
                              }
                          )
                      }
                  }
                }

                // Episode Dropdown
                val episodeDropdownExpanded = remember { mutableStateOf(false) }
                Box {
                  FilledTonalButton(
                      onClick = { episodeDropdownExpanded.value = true },
                      enabled = selectedSeason != null && !isFetchingEpisodes,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                      modifier = Modifier.height(38.dp),
                      shape = RoundedCornerShape(8.dp)
                  ) {
                      if (isFetchingEpisodes) {
                          CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), 
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                          )
                          Spacer(Modifier.width(6.dp))
                      }
                      Text(
                          text = selectedEpisode?.let { "E${it.episode_number}" } ?: "Ep",
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1
                      )
                      Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                  }
                  DropdownMenu(
                      expanded = episodeDropdownExpanded.value,
                      onDismissRequest = { episodeDropdownExpanded.value = false },
                      modifier = Modifier.heightIn(max = 300.dp).widthIn(min = 200.dp),
                      shape = RoundedCornerShape(12.dp),
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                  ) {
                      episodes.forEach { episode ->
                          DropdownMenuItem(
                              text = { 
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                  Text(
                                    "Ep ${episode.episode_number}", 
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                  )
                                  episode.name?.let { 
                                    Text(
                                      it, 
                                      style = MaterialTheme.typography.bodySmall,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      maxLines = 1,
                                      modifier = Modifier.basicMarquee()
                                    ) 
                                  }
                                }
                              },
                              onClick = {
                                  onSelectEpisode(episode)
                                  episodeDropdownExpanded.value = false
                              }
                          )
                      }
                  }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
