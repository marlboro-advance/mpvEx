package app.gyrolet.mpvrx.ui.player.controls.components.sheets

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AiProvider
import app.gyrolet.mpvrx.presentation.components.PlayerSheet
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

sealed class SubtitleItem {
  data class Track(val node: TrackNode) : SubtitleItem()
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
  onOpenOnlineSearch: () -> Unit,
  onDismissRequest: () -> Unit,
  onTranslateSubtitle: (TrackNode, String) -> Unit,
  onGenerateSubtitle: () -> Unit,
  onCancelTranslation: () -> Unit,
  isTranslating: Boolean,
  translationProgress: Float,
  translationStatus: String,
  translationEnabled: Boolean,
  isGeneratingSubtitles: Boolean,
  subtitleGenerationProgress: Float,
  subtitleGenerationStatus: String,
  translatingTrackId: Int? = null,
  translatingTrackName: String = "",
  provider: AiProvider = AiProvider.GEMINI,
  autoTranslateLanguages: String = "",
  modifier: Modifier = Modifier,
) {
  val items = remember(tracks) {
    val list = mutableListOf<SubtitleItem>()
    val internal = tracks.filter { it.external != true }
    val external = tracks.filter { it.external == true }

    if (internal.isNotEmpty() || external.isNotEmpty()) {
      list.add(SubtitleItem.Header(if (internal.isNotEmpty()) "Embedded Subtitles" else "Local Subtitles"))
      list.addAll(internal.map { SubtitleItem.Track(it) })
      if (internal.isNotEmpty() && external.isNotEmpty()) {
        list.add(SubtitleItem.Header("External Subtitles"))
      }
      list.addAll(external.map { SubtitleItem.Track(it) })
    }

    list.toImmutableList()
  }

  val isOnlineProvider = provider != AiProvider.LOCAL

  val configuredLanguages = remember(autoTranslateLanguages) {
    autoTranslateLanguages.split(",").filter { it.isNotBlank() }
  }

  val allLanguages = remember {
    listOf(
      "Afrikaans", "Arabic", "Bengali", "Bulgarian", "Catalan",
      "Chinese (Simplified)", "Chinese (Traditional)", "Croatian", "Czech",
      "Danish", "Dutch", "English", "Estonian", "Finnish", "French",
      "German", "Greek", "Gujarati", "Hebrew", "Hindi", "Hungarian",
      "Indonesian", "Italian", "Japanese", "Kannada", "Korean", "Latvian",
      "Lithuanian", "Malay", "Malayalam", "Marathi", "Norwegian", "Persian",
      "Polish", "Portuguese", "Punjabi", "Romanian", "Russian", "Serbian",
      "Slovak", "Slovenian", "Spanish", "Swahili", "Swedish", "Tamil",
      "Telugu", "Thai", "Turkish", "Ukrainian", "Urdu", "Vietnamese"
    )
  }

  val codeToName = remember {
    mapOf(
      "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
      "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "zh" to "Chinese (Simplified)",
      "ja" to "Japanese", "ko" to "Korean", "ar" to "Arabic", "hi" to "Hindi",
      "bn" to "Bengali", "vi" to "Vietnamese", "te" to "Telugu", "ta" to "Tamil",
      "ur" to "Urdu", "tr" to "Turkish", "pl" to "Polish", "uk" to "Ukrainian",
      "nl" to "Dutch", "el" to "Greek", "hu" to "Hungarian", "sv" to "Swedish",
      "cs" to "Czech", "ro" to "Romanian", "da" to "Danish", "fi" to "Finnish",
      "no" to "Norwegian", "he" to "Hebrew", "id" to "Indonesian", "th" to "Thai",
      "ms" to "Malay", "fa" to "Persian", "sk" to "Slovak", "bg" to "Bulgarian",
      "hr" to "Croatian", "sr" to "Serbian", "sl" to "Slovenian", "et" to "Estonian",
      "lv" to "Latvian", "lt" to "Lithuanian", "af" to "Afrikaans", "sw" to "Swahili",
    )
  }

  var langSearch by remember { mutableStateOf("") }
  var showLanguagePicker by remember { androidx.compose.runtime.mutableStateOf<TrackNode?>(null) }

  if (showLanguagePicker != null) {
    val languagesToShow = remember(configuredLanguages, langSearch) {
      val source = if (configuredLanguages.size >= 2) {
        configuredLanguages.mapNotNull { codeToName[it] }
      } else {
        allLanguages
      }
      if (langSearch.isBlank()) source
      else source.filter { it.contains(langSearch, ignoreCase = true) }
    }
    androidx.compose.material3.AlertDialog(
      onDismissRequest = {
        showLanguagePicker = null
        langSearch = ""
      },
      title = { Text("Translate to...") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
          OutlinedTextField(
            value = langSearch,
            onValueChange = { langSearch = it },
            placeholder = { Text("Search language…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
          )
          LazyColumn(modifier = Modifier.height(280.dp)) {
            items(languagesToShow) { lang ->
              Text(
                text = lang,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onTranslateSubtitle(showLanguagePicker!!, lang)
                    showLanguagePicker = null
                    langSearch = ""
                  }
                  .padding(MaterialTheme.spacing.medium)
              )
            }
            if (languagesToShow.isEmpty()) {
              item { Text("No languages found", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(MaterialTheme.spacing.medium)) }
            }
          }
        }
      },
      confirmButton = {
        androidx.compose.material3.TextButton(onClick = {
          showLanguagePicker = null
          langSearch = ""
        }) {
          Text("Cancel")
        }
      }
    )
  }

  PlayerSheet(onDismissRequest) {
    Column(modifier) {
      AddTrackRow(
        stringResource(R.string.player_sheets_add_ext_sub),
        onAddSubtitle,
        actions = {
          IconButton(onClick = onOpenOnlineSearch) {
            Icon(Icons.Default.Search, null)
          }
          if (isOnlineProvider) {
            IconButton(onClick = onGenerateSubtitle) {
              Icon(Icons.Default.Subtitles, "Generate subtitles")
            }
          }
          IconButton(onClick = onOpenSubtitleSettings) {
            Icon(Icons.Default.Palette, null)
          }
          IconButton(onClick = onOpenSubtitleDelay) {
            Icon(Icons.Default.AvTimer, null)
          }
        },
      )

      if (isTranslating) {
        Column(
          modifier = Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              "${translationStatus.ifBlank { "Translating" }} ${translatingTrackName}... ${(translationProgress * 100).toInt()}%",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            FilledTonalIconButton(
              onClick = onCancelTranslation,
              modifier = Modifier.size(36.dp),
              colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel translation",
                modifier = Modifier.size(20.dp),
              )
            }
          }
          LinearProgressIndicator(
            progress = { translationProgress },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      if (isGeneratingSubtitles) {
        androidx.compose.foundation.layout.Column(
          modifier = Modifier.padding(MaterialTheme.spacing.medium),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
          Text(
            "${subtitleGenerationStatus.ifBlank { "Generating subtitles" }}... ${(subtitleGenerationProgress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
          )
          LinearProgressIndicator(
            progress = { subtitleGenerationProgress },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      LazyColumn {
        items(items) { item ->
          when (item) {
            is SubtitleItem.Track -> {
              val track = item.node
              SubtitleTrackRow(
                title = getTrackTitle(track),
                isSelected = isSubtitleSelected(track.id),
                isExternal = track.external == true,
                onToggle = { onToggleSubtitle(track.id) },
                onRemove = { onRemoveSubtitle(track.id) },
                onTranslate = {
                  if (translationEnabled && isOnlineProvider) {
                    if (configuredLanguages.size == 1) {
                      val langName = codeToName[configuredLanguages.first()] ?: configuredLanguages.first()
                      onTranslateSubtitle(track, langName)
                    } else {
                      showLanguagePicker = track
                    }
                  }
                },
                translationEnabled = translationEnabled && isOnlineProvider,
                isCurrentlyTranslating = track.id == translatingTrackId,
              )
            }
            is SubtitleItem.Header -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            SubtitleItem.Divider -> {
              HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              )
            }
          }
        }
        item {
          Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        }
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
  onTranslate: () -> Unit,
  translationEnabled: Boolean,
  isCurrentlyTranslating: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
    
    if (isCurrentlyTranslating) {
      androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(MaterialTheme.spacing.large),
        strokeWidth = MaterialTheme.spacing.smaller,
      )
    }
    
    if (isExternal) {
      if (translationEnabled) {
        IconButton(onClick = onTranslate) { Icon(Icons.Default.Translate, contentDescription = "Translate") }
      }
      IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = null) }
    }
  }
}
