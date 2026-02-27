package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.content.Intent
import java.io.File
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object LuaScriptsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val scope = rememberCoroutineScope()
    
    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
    val selectedScripts by preferences.selectedLuaScripts.collectAsState()
    val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
    
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
            // Look for scripts/ subfolder first (case-insensitive), fall back to root
            val scriptsDir = tree.listFiles().firstOrNull {
              it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
            } ?: tree
            val scriptExtensions = setOf("lua", "js")
            scriptsDir.listFiles().forEach { file ->
              if (file.isFile) {
                val name = file.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in scriptExtensions) {
                  scripts.add(name)
                }
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
          val sortedScripts = scripts.sorted()
          availableScripts = sortedScripts
          isLoading = false

          // Prune selected scripts that no longer exist
          val currentSelection = preferences.selectedLuaScripts.get()
          val validSelection = currentSelection.filter { it in sortedScripts }
          if (validSelection.size != currentSelection.size) {
            preferences.selectedLuaScripts.set(validSelection.toSet())
          }
        }
      }
    }
    
    fun toggleScriptSelection(scriptName: String) {
      val newSelection = if (selectedScripts.contains(scriptName)) {
        selectedScripts - scriptName
      } else {
        selectedScripts + scriptName
      }
      preferences.selectedLuaScripts.set(newSelection)
    }
    
    fun shareScript(scriptName: String) {
      if (mpvConfStorageLocation.isBlank()) {
        Toast.makeText(context, "No storage location configured", Toast.LENGTH_SHORT).show()
        return
      }
      
      runCatching {
        val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
        if (tree != null && tree.exists()) {
          val scriptsDir = tree.listFiles().firstOrNull {
            it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
          } ?: tree
          
          val scriptFile = scriptsDir.listFiles().firstOrNull { 
            it.isFile && it.name == scriptName 
          }
          
          if (scriptFile != null) {
            // Copy to cache directory for sharing
            val cacheFile = File(context.cacheDir, scriptName)
            context.contentResolver.openInputStream(scriptFile.uri)?.use { input ->
              cacheFile.outputStream().use { output ->
                input.copyTo(output)
              }
            }
            
            val shareUri = FileProvider.getUriForFile(
              context,
              "${context.packageName}.provider",
              cacheFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_STREAM, shareUri)
              putExtra(Intent.EXTRA_SUBJECT, scriptName)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share script"))
          } else {
            Toast.makeText(context, "Script file not found", Toast.LENGTH_SHORT).show()
          }
        }
      }.onFailure { e ->
        Toast.makeText(context, "Error sharing script: ${e.message}", Toast.LENGTH_LONG).show()
      }
    }
    
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Lua Scripts",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            backStack.add(LuaScriptEditorScreen(scriptName = null))
          },
          containerColor = MaterialTheme.colorScheme.primary,
        ) {
          Icon(
            Icons.Default.Add,
            contentDescription = "Create new script",
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
      ) {
        items(availableScripts) { scriptName ->
          Column(
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                  toggleScriptSelection(scriptName) 
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
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
              
              Row {
                IconButton(
                  onClick = { shareScript(scriptName) }
                ) {
                  Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = {
                    backStack.add(LuaScriptEditorScreen(scriptName = scriptName))
                  }
                ) {
                  Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp)
            )
          }
        }
      }
    }
  }
}
