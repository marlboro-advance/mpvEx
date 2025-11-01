package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.domain.subtitle.SubdlSubtitle
import app.marlboroadvance.mpvex.domain.subtitle.repository.SubdlRepository
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Bottom sheet for searching and downloading subtitles from Subdl.com.
 * Features landscape-optimized layout with individual loading indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleDownloadSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  viewModel: PlayerViewModel,
  initialQuery: String = "",
) {
  if (!visible) return

  val repository: SubdlRepository = koinInject()
  val preferences: SubtitlesPreferences = koinInject()
  val scope = rememberCoroutineScope()

  val apiKey by preferences.subdlApiKey.collectAsState()

  var searchQuery by remember { mutableStateOf(initialQuery) }
  var searchResults by remember { mutableStateOf<List<SubdlSubtitle>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var downloadingSubtitleUrl by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun performSearch() {
    if (searchQuery.isBlank()) {
      errorMessage = "Please enter a search query"
      return
    }

    scope.launch {
      isSearching = true
      errorMessage = null

      repository
        .searchSubtitles(searchQuery, apiKey)
        .onSuccess { subtitles ->
          searchResults = subtitles
          if (subtitles.isEmpty()) {
            errorMessage = "No subtitles found"
          }
        }.onFailure { exception ->
          errorMessage = exception.message ?: "Search failed"
          searchResults = emptyList()
        }

      isSearching = false
    }
  }

  fun downloadSubtitle(subtitle: SubdlSubtitle) {
    scope.launch {
      downloadingSubtitleUrl = subtitle.url
      errorMessage = null

      val mediaTitle = viewModel.getCurrentMediaTitle()
      repository
        .downloadSubtitle(subtitle, mediaTitle, apiKey)
        .onSuccess { file ->
          viewModel.addDownloadedSubtitle(file.absolutePath, file.name)
        }.onFailure { exception ->
          errorMessage = exception.message ?: "Download failed"
        }

      downloadingSubtitleUrl = null
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.9f)
          .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Download Subtitles",
          style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Search bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("Movie/TV show name") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          enabled = !isSearching && downloadingSubtitleUrl == null,
        )

        Button(
          onClick = { performSearch() },
          enabled = !isSearching && downloadingSubtitleUrl == null && searchQuery.isNotBlank(),
        ) {
          if (isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Text("Search")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Attribution
      Text(
        text = "Powered by Subdl.com",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Error message
      errorMessage?.let { error ->
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = error,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Results list
      LazyColumn(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(searchResults) { subtitle ->
          SubtitleResultCard(
            subtitle = subtitle,
            isDownloading = downloadingSubtitleUrl == subtitle.url,
            onDownload = { downloadSubtitle(subtitle) },
            enabled = downloadingSubtitleUrl == null,
          )
        }
      }
    }
  }
}

/**
 * Card displaying a single subtitle result with metadata and download action.
 */
@Composable
private fun SubtitleResultCard(
  subtitle: SubdlSubtitle,
  isDownloading: Boolean,
  onDownload: () -> Unit,
  enabled: Boolean,
) {
  Card(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) { onDownload() },
    shape = RoundedCornerShape(8.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isDownloading) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
      ),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        // Title
        Text(
          text = subtitle.releaseName ?: subtitle.name ?: "Unknown",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Metadata row
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          subtitle.lang?.let { lang ->
            MetadataBadge(
              text = lang.uppercase(),
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            )
          }

          if (subtitle.hearingImpaired == true) {
            MetadataBadge(
              text = "HI",
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            )
          }

          subtitle.downloadCount?.let { downloads ->
            if (downloads > 0) {
              Text(
                text = "↓ $downloads",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          subtitle.author?.takeIf { it != "none" }?.let { author ->
            Text(
              text = "• $author",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Download indicator/button
      Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        when {
          isDownloading ->
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )

          else ->
            Icon(
              imageVector = Icons.Default.CloudDownload,
              contentDescription = "Download",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
        }
      }
    }
  }
}

/**
 * Compact badge for displaying metadata like language or HI indicator.
 */
@Composable
private fun MetadataBadge(
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(4.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
