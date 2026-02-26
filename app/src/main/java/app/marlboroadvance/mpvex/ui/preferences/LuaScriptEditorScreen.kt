package app.marlboroadvance.mpvex.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
data class LuaScriptEditorScreen(
  val scriptName: String?
) : Screen {
  
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val scope = rememberCoroutineScope()
    
    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
    
    val isNewScript = scriptName == null
    val title = if (isNewScript) "Create Lua Script" else "Edit Lua Script"
    
    var scriptContent by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf(scriptName?.removeSuffix(".lua") ?: "") }
    var hasUnsavedChanges by remember { mutableStateOf(isNewScript) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Load script content if editing existing script
    LaunchedEffect(scriptName, mpvConfStorageLocation) {
      if (scriptName != null && mpvConfStorageLocation.isNotBlank()) {
        withContext(Dispatchers.IO) {
          val tempFile = kotlin.io.path.createTempFile()
          runCatching {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree != null && tree.exists()) {
              // Try to find "scripts" subdirectory first (case-insensitive)
              val scriptsDir = tree.listFiles().firstOrNull { 
                  it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true 
              } ?: tree

              val scriptFile = scriptsDir.findFile(scriptName)
              if (scriptFile != null && scriptFile.exists()) {
                context.contentResolver.openInputStream(scriptFile.uri)?.copyTo(tempFile.outputStream())
                val content = tempFile.readLines().joinToString("\n")
                withContext(Dispatchers.Main) {
                  scriptContent = content
                  hasUnsavedChanges = false
                }
              }
            }
          }
          tempFile.deleteIfExists()
        }
      }
    }
    
    fun saveScript() {
      if (fileName.isBlank()) {
        Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
        return
      }
      
      val finalFileName = "$fileName.lua"
      
      scope.launch(Dispatchers.IO) {
        try {
          if (mpvConfStorageLocation.isBlank()) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No storage location set", Toast.LENGTH_LONG).show()
            }
            return@launch
          }
          
          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree == null) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No storage location set", Toast.LENGTH_LONG).show()
            }
            return@launch
          }
          
          // Try to find "scripts" subdirectory first (case-insensitive)
          val scriptsDir = tree.listFiles().firstOrNull { 
              it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true 
          } ?: tree

          // If renaming, delete old file
          if (!isNewScript && scriptName != null && scriptName != finalFileName) {
            scriptsDir.findFile(scriptName)?.delete()
          }

          val existing = scriptsDir.findFile(finalFileName)
          val scriptFile = existing ?: scriptsDir.createFile("text/plain", finalFileName)?.also { it.renameTo(finalFileName) }
          val uri = scriptFile?.uri ?: run {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Failed to create file", Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(scriptContent.toByteArray())
            out.flush()
          } ?: run {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Failed to open output stream", Toast.LENGTH_LONG).show()
            }
            return@launch
          }
          
          withContext(Dispatchers.Main) {
            hasUnsavedChanges = false
            Toast.makeText(context, "$finalFileName saved successfully", Toast.LENGTH_SHORT).show()
            backStack.removeLastOrNull()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
    
    fun shareScript() {
      if (isNewScript || scriptName == null) {
        Toast.makeText(context, "Save the script first before sharing", Toast.LENGTH_SHORT).show()
        return
      }
      
      scope.launch(Dispatchers.IO) {
        try {
          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree != null && tree.exists()) {
            // Try to find "scripts" subdirectory first (case-insensitive)
            val scriptsDir = tree.listFiles().firstOrNull { 
                it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true 
            } ?: tree

            val scriptFile = scriptsDir.findFile(scriptName)
            if (scriptFile != null && scriptFile.exists()) {
              // Copy to cache directory for sharing
              val cacheFile = File(context.cacheDir, scriptName)
              context.contentResolver.openInputStream(scriptFile.uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                  input.copyTo(output)
                }
              }
              
              // Get content URI using FileProvider
              val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
              )
              
              withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_STREAM, contentUri)
                  putExtra(Intent.EXTRA_SUBJECT, scriptName)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share $scriptName"))
              }
            }
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(
              context,
              "Failed to share: ${e.message}",
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }
    
    fun deleteScript() {
      if (isNewScript || scriptName == null) {
        backStack.removeLastOrNull()
        return
      }
      
      scope.launch(Dispatchers.IO) {
        try {
          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree != null && tree.exists()) {
              // Try to find "scripts" subdirectory first (case-insensitive)
              val scriptsDir = tree.listFiles().firstOrNull { 
                  it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true 
              } ?: tree

              val scriptFile = scriptsDir.findFile(scriptName)
              if (scriptFile != null && scriptFile.exists()) {
                val deleted = scriptFile.delete()
                
                if (deleted) {
                  // Remove from selected scripts if it was selected
                  val selectedScripts = preferences.selectedLuaScripts.get()
                  if (selectedScripts.contains(scriptName)) {
                    preferences.selectedLuaScripts.set(selectedScripts - scriptName)
                  }
                  
                  withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$scriptName deleted", Toast.LENGTH_SHORT).show()
                    backStack.removeLastOrNull()
                  }
                }
              }
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(
              context,
              "Failed to delete: ${e.message}",
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }
    
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              androidx.compose.foundation.text.BasicTextField(
                value = fileName,
                onValueChange = {
                  fileName = it
                  hasUnsavedChanges = true
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                  fontWeight = FontWeight.ExtraBold,
                  color = MaterialTheme.colorScheme.primary
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                  Box {
                    if (fileName.isEmpty()) {
                      Text(
                        text = "Script name",
                        style = MaterialTheme.typography.headlineSmall.copy(
                          fontWeight = FontWeight.ExtraBold,
                          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                      )
                    }
                    innerTextField()
                  }
                }
              )
              if (hasUnsavedChanges) {
                Text(
                  text = "Unsaved changes",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
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
          actions = {
            // Share button (only for existing scripts)
            if (!isNewScript) {
              IconButton(
                onClick = { shareScript() },
                modifier = Modifier
                  .padding(horizontal = 4.dp)
                  .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant,
                  contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
              ) {
                Icon(
                  Icons.Default.Share,
                  contentDescription = "Share",
                )
              }
            }
            
            // Delete button (only for existing scripts)
            if (!isNewScript) {
              IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                  .padding(horizontal = 4.dp)
                  .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(8.dp),
              ) {
                Icon(
                  Icons.Default.Delete,
                  contentDescription = "Delete",
                )
              }
            }
            
            // Save button
            IconButton(
              onClick = { saveScript() },
              enabled = hasUnsavedChanges && fileName.isNotBlank(),
              modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(40.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                },
                contentColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(
                Icons.Default.Check,
                contentDescription = "Save",
              )
            }
          },
        )
      }
    ) { padding ->
      val scrollState = rememberScrollState()
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .imePadding()
      ) {
        BasicTextField(
          value = scriptContent,
          onValueChange = {
            scriptContent = it
            hasUnsavedChanges = true
          },
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
          ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
      }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
      ConfirmDialog(
        title = "Delete Script?",
        subtitle = "Are you sure you want to delete \"${scriptName ?: fileName}\"? This action cannot be undone.",
        onConfirm = {
          deleteScript()
          showDeleteDialog = false
        },
        onCancel = {
          showDeleteDialog = false
        },
      )
    }
  }
}
