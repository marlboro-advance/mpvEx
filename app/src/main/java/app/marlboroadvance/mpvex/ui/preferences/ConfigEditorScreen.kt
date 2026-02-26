package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
data class ConfigEditorScreen(
  val configType: ConfigType
) : Screen {

  enum class ConfigType {
    MPV_CONF,
    INPUT_CONF
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context      = LocalContext.current
    val backStack    = LocalBackStack.current
    val preferences  = koinInject<AdvancedPreferences>()
    val scope        = rememberCoroutineScope()

    val (fileName, initialValue) = when (configType) {
      ConfigType.MPV_CONF   -> "mpv.conf"   to preferences.mpvConf.get()
      ConfigType.INPUT_CONF -> "input.conf" to preferences.inputConf.get()
    }
    val screenTitle = when (configType) {
      ConfigType.MPV_CONF   -> "Edit mpv.conf"
      ConfigType.INPUT_CONF -> "Edit input.conf"
    }

    var configText       by remember { mutableStateOf(initialValue) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()

    // Load from external storage if a folder is configured
    LaunchedEffect(mpvConfStorageLocation) {
      if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
      withContext(Dispatchers.IO) {
        val tempFile = createTempFile()
        runCatching {
          val tree       = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          val configFile = tree?.findFile(fileName)
          if (configFile != null && configFile.exists()) {
            context.contentResolver.openInputStream(configFile.uri)?.copyTo(tempFile.outputStream())
            val content = tempFile.readLines().joinToString("\n")
            withContext(Dispatchers.Main) { configText = content }
          }
        }
        tempFile.deleteIfExists()
      }
    }

    fun saveConfig() {
      scope.launch(Dispatchers.IO) {
        try {
          when (configType) {
            ConfigType.MPV_CONF   -> preferences.mpvConf.set(configText)
            ConfigType.INPUT_CONF -> preferences.inputConf.set(configText)
          }
          File(context.filesDir, fileName).writeText(configText)

          if (mpvConfStorageLocation.isNotBlank()) {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree == null) {
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "No storage location set", Toast.LENGTH_LONG).show()
              }
              return@launch
            }
            val existing = tree.findFile(fileName)
            val confFile = existing ?: tree.createFile("text/plain", fileName)?.also { it.renameTo(fileName) }
            val uri = confFile?.uri ?: run {
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_LONG).show()
              }
              return@launch
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
              out.write(configText.toByteArray())
              out.flush()
            }
          }

          withContext(Dispatchers.Main) {
            hasUnsavedChanges = false
            Toast.makeText(context, "$fileName saved", Toast.LENGTH_SHORT).show()
            backStack.removeLastOrNull()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text  = screenTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
              )
              if (hasUnsavedChanges) {
                Text(
                  text  = "Unsaved changes",
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
            IconButton(
              onClick  = { saveConfig() },
              enabled  = hasUnsavedChanges,
              modifier = Modifier.padding(horizontal = 12.dp).size(40.dp),
              colors   = IconButtonDefaults.iconButtonColors(
                containerColor        = if (hasUnsavedChanges) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                contentColor          = if (hasUnsavedChanges) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                disabledContentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(Icons.Default.Check, contentDescription = "Save")
            }
          },
        )
      },
    ) { padding ->
      val scrollState = rememberScrollState()
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .imePadding()
      ) {
        BasicTextField(
          value = configText,
          onValueChange = { configText = it; hasUnsavedChanges = true },
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
  }
}
