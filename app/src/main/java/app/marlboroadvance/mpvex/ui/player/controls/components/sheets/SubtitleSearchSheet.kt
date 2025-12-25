package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.utils.SubtitleItem
import app.marlboroadvance.mpvex.utils.SubtitleManager
import kotlinx.coroutines.launch

@Composable
fun SubtitleSearchSheet(
    initialQuery: String,
    onDownloadSubtitle: (String, String) -> Unit, // url, filename
    onDismissRequest: () -> Unit,
    preferredLanguage: String? = null,
    onSearch: suspend (String) -> List<SubtitleItem>,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-search on open if query is not empty
    LaunchedEffect(Unit) {
        if (query.isNotEmpty()) {
            isLoading = true
            results = onSearch(query)
            isLoading = false
        }
    }

    PlayerSheet(onDismissRequest) {
        Column(modifier.padding(MaterialTheme.spacing.medium)) {
            // Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search Subtitles") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        results = onSearch(query)
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            // Results
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = MaterialTheme.spacing.medium)
                ) {
                    items(results) { item ->
                        SubtitleResultRow(
                            item = item,
                            onClick = {
                                scope.launch {
                                    // Pass direct detailsUrl (or custom scheme) to ViewModel to handle resolution
                                    onDownloadSubtitle(item.detailsUrl, item.title)
                                    // The caller handles logic.
                                    // We might want to show loading here if resolution takes time?
                                    // But current ViewModel implementation is async inside launch. 
                                    // Ideally, onDownloadSubtitle should be fire-and-forget or we show global loading.
                                    // Existing app likely shows Toast.
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleResultRow(
    item: SubtitleItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Icon(Icons.Default.CloudDownload, contentDescription = null)
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
