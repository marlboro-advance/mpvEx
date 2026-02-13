package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun LuaScriptsPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = koinInject<app.marlboroadvance.mpvex.preferences.AdvancedPreferences>()
    
    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
    val selectedScripts by preferences.selectedLuaScripts.collectAsState()
    
    var availableScripts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load scripts
    LaunchedEffect(mpvConfStorageLocation) {
        if (mpvConfStorageLocation.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        
        withContext(Dispatchers.IO) {
            val scripts = mutableListOf<String>()
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
                if (tree != null && tree.exists()) {
                    tree.listFiles().forEach { file ->
                        if (file.isFile && file.name?.endsWith(".lua") == true) {
                            file.name?.let { scripts.add(it) }
                        }
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error loading scripts: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            withContext(Dispatchers.Main) {
                availableScripts = scripts.sorted()
                isLoading = false
            }
        }
    }
    
    fun toggleScriptSelection(scriptName: String) {
        val newSelection = if (selectedScripts.contains(scriptName)) {
            Toast.makeText(context, "$scriptName disabled. Video restart required.", Toast.LENGTH_LONG).show()
            selectedScripts - scriptName
        } else {
            selectedScripts + scriptName
        }
        preferences.selectedLuaScripts.set(newSelection)
    }

    DraggablePanel(
        modifier = modifier,
        header = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
            ) {
                Text(
                    "Lua Scripts",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(32.dp))
                }
            }
        }
    ) {
        Column(
            Modifier.padding(horizontal = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            
            if (isLoading) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (availableScripts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "No Lua scripts found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Place .lua files in your mpv configuration directory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    availableScripts.forEach { scriptName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    toggleScriptSelection(scriptName)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                           Checkbox(
                                checked = selectedScripts.contains(scriptName),
                                onCheckedChange = { toggleScriptSelection(scriptName) },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = scriptName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
