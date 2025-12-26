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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.utils.subtitles.SubtitleSearchResult
import kotlinx.coroutines.launch

@Composable
fun SubtitleSearchSheet(
    initialQuery: String,
    onDownloadSubtitle: (String, String) -> Unit, // url, filename
    onDismissRequest: () -> Unit,
    preferredLanguage: String? = null,
    onSearch: suspend (String) -> List<SubtitleSearchResult>,
    modifier: Modifier = Modifier,
) {
    // Start with empty query, don't auto-populate
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Keyboard and focus management
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    
    // Store the movie name for insert button
    val movieName = remember { initialQuery }

    // Function to perform search
    val performSearch: () -> Unit = {
        if (query.isNotBlank()) {
            // Hide keyboard
            keyboardController?.hide()
            focusManager.clearFocus()
            
            scope.launch {
                isLoading = true
                results = onSearch(query)
                isLoading = false
                hasSearched = true
            }
        }
    }

    PlayerSheet(onDismissRequest) {
        Column(modifier.padding(MaterialTheme.spacing.medium)) {
            // Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search Subtitles") },
                    placeholder = { Text("Enter movie name...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { performSearch() }
                    ),
                    trailingIcon = {
                        Row {
                            // Clear button - only show when there's text
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Insert movie name button
                            if (movieName.isNotBlank()) {
                                IconButton(onClick = { query = movieName }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Insert movie name",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                )
                // Search button
                IconButton(onClick = performSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            // Results
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(500.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(500.dp).padding(top = MaterialTheme.spacing.medium)
                ) {
                    if (results.isEmpty() && query.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                            ) {
                                Text(
                                    text = "Enter a movie name to search for subtitles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (movieName.isNotBlank()) {
                                    // Clickable row to insert movie name
                                    Row(
                                        modifier = Modifier
                                            .clickable { query = movieName }
                                            .padding(MaterialTheme.spacing.small),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = "Tap to use: ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = movieName.take(40) + if (movieName.length > 40) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else if (results.isEmpty() && hasSearched) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No subtitles found for \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Try different search terms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = MaterialTheme.spacing.small)
                                )
                            }
                        }
                    } else {
                        items(results) { item ->
                            SubtitleResultRow(
                                item = item,
                                onClick = {
                                    // Fire and forget - don't wait for coroutine
                                    onDownloadSubtitle(item.detailsUrl, item.title)
                                    // Dismiss sheet immediately to avoid refresh
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleResultRow(
    item: SubtitleSearchResult,
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
