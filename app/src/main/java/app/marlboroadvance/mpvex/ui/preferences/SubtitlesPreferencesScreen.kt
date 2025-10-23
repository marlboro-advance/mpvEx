package app.marlboroadvance.mpvex.ui.preferences

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitleJustification
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.SubtitlesBorderStyle
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject
import java.io.File

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @SuppressLint("DefaultLocale")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()
    val fileManager = koinInject<FileManager>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.pref_subtitles)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val fontsFolder by preferences.fontsFolder.collectAsState()
        val selectedFont by preferences.font.collectAsState()
        var availableFonts by remember { mutableStateOf<List<String>>(emptyList()) }
        var fontLoadTrigger by remember { mutableIntStateOf(0) }
        var isLoadingFonts by remember { mutableStateOf(false) }

        val locationPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
          if (uri == null) return@rememberLauncherForActivityResult

          val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
          context.contentResolver.takePersistableUriPermission(uri, flags)
          preferences.fontsFolder.set(uri.toString())

          // Copy fonts immediately in background
          kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            isLoadingFonts = true
            copyFontsFromDirectory(context, fileManager, uri.toString())
            withContext(Dispatchers.Main) {
              fontLoadTrigger++
              isLoadingFonts = false
            }
          }
        }

        // Load fonts when folder changes or trigger is fired
        LaunchedEffect(fontsFolder, fontLoadTrigger) {
          val customFonts = loadAvailableFonts(context, fileManager)

          // Add default Android system fonts
          val defaultFonts = listOf(
            "Sans Serif (Default)",
            "Serif",
            "Monospace",
            "Casual",
            "Cursive",
          )

          // Combine default fonts with custom fonts
          availableFonts = defaultFonts + customFonts
        }

        // Auto-refresh fonts on app restart if directory is set
        LaunchedEffect(Unit) {
          if (fontsFolder.isNotBlank()) {
            isLoadingFonts = true
            withContext(Dispatchers.IO) {
              copyFontsFromDirectory(context, fileManager, fontsFolder)
            }
            fontLoadTrigger++
            isLoadingFonts = false
          }
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          // === GENERAL SECTION ===
          PreferenceCategory(
            title = { Text("General", style = MaterialTheme.typography.titleMedium) },
          )

          val preferredLanguages by preferences.preferredLanguages.collectAsState()
          TextFieldPreference(
            value = preferredLanguages,
            onValueChange = preferences.preferredLanguages::set,
            textToValue = { it },
            title = { Text(stringResource(R.string.pref_preferred_languages)) },
            summary = {
              if (preferredLanguages.isNotBlank()) {
                Text(preferredLanguages)
              } else {
                Text("Not set (will use video default)")
              }
            },
            textField = { value, onValueChange, _ ->
              Column {
                Text("Enter language codes separated by commas (e.g., eng,jpn,spa)")
                TextField(
                  value,
                  onValueChange,
                  modifier = Modifier.fillMaxWidth(),
                  placeholder = { Text("eng,jpn,spa") },
                )
              }
            },
          )

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

          // === FONT & STYLE SECTION ===
          PreferenceCategory(
            title = { Text("Font & Style", style = MaterialTheme.typography.titleMedium) },
          )

          // Directory picker preference with reload and clear icons on the right
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { locationPicker.launch(null) }
              .padding(vertical = 16.dp, horizontal = 16.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              // Left side: Title + summary
              Column(
                modifier = Modifier.weight(1f),
              ) {
                Text(
                  stringResource(R.string.pref_subtitles_fonts_dir),
                  style = MaterialTheme.typography.titleMedium,
                )
                if (fontsFolder.isBlank()) {
                  Text(
                    "Not set (using system fonts)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  Text(
                    getSimplifiedPathFromUri(fontsFolder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (availableFonts.isNotEmpty()) {
                    Text(
                      "${availableFonts.size} fonts loaded",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                  }
                }
              }

              // Right side: Action icons
              if (fontsFolder.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  // Refresh icon or loading spinner
                  if (isLoadingFonts) {
                    Box(
                      modifier = Modifier.size(48.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  } else {
                    IconButton(
                      onClick = {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                          isLoadingFonts = true
                          copyFontsFromDirectory(context, fileManager, fontsFolder)
                          withContext(Dispatchers.Main) {
                            fontLoadTrigger++
                            isLoadingFonts = false
                          }
                        }
                      },
                    ) {
                      Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reload fonts",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }

                  // Clear icon (always visible when directory is set)
                  IconButton(
                    onClick = {
                      preferences.fontsFolder.set("")
                      fontLoadTrigger++
                    },
                  ) {
                    Icon(
                      Icons.Default.Clear,
                      contentDescription = "Clear font directory",
                      tint = MaterialTheme.colorScheme.tertiary,
                    )
                  }
                }
              }
            }
          }

          if (availableFonts.isNotEmpty()) {
            // Font picker dialog state
            var showFontPicker by remember { mutableStateOf(false) }

            // Check if selected font is actually available and not default
            val isCustomFontSelected = selectedFont.isNotBlank() &&
              availableFonts.contains(selectedFont) &&
              selectedFont != "Sans Serif (Default)"

            // Font selection with clear icon
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showFontPicker = true }
                .padding(vertical = 16.dp, horizontal = 16.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                // Left side: Title + selected font
                Column(
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    stringResource(R.string.player_sheets_sub_typography_font),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    if (isCustomFontSelected) selectedFont else "Default (Sans Serif)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                // Right side: Clear icon (only shown if custom font is actually selected and available)
                if (isCustomFontSelected) {
                  IconButton(
                    onClick = {
                      preferences.font.set("")
                    },
                  ) {
                    Icon(
                      Icons.Default.Clear,
                      contentDescription = "Reset to default font",
                      tint = MaterialTheme.colorScheme.tertiary,
                    )
                  }
                }
              }
            }

            // Font picker dialog
            if (showFontPicker) {
              androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFontPicker = false },
                title = { Text(stringResource(R.string.player_sheets_sub_typography_font)) },
                text = {
                  androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    items(availableFonts.size) { index ->
                      val font = availableFonts[index]

                      // Try to load and render the font
                      val fontFamily = remember(font) {
                        // Check if it's a default system font
                        when (font) {
                          "Sans Serif (Default)" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                          "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                          "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                          "Casual" -> androidx.compose.ui.text.font.FontFamily.Cursive
                          "Cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                          else -> {
                            // Try to load custom font from file
                            runCatching {
                              val fontsDir = File(context.filesDir.path + "/fonts")
                              val fontFile = fontsDir.listFiles()?.firstOrNull { file ->
                                file.name.lowercase().matches(".*\\.[ot]tf$".toRegex()) &&
                                  runCatching {
                                    TTFFile.open(file.inputStream()).families.values.first() == font
                                  }.getOrDefault(false)
                              }

                              fontFile?.let {
                                androidx.compose.ui.text.font.FontFamily(
                                  androidx.compose.ui.text.font.Font(it),
                                )
                              }
                            }.getOrNull()
                          }
                        }
                      }

                      androidx.compose.material3.TextButton(
                        onClick = {
                          preferences.font.set(font)
                          showFontPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Text(
                          font,
                          modifier = Modifier.fillMaxWidth(),
                          color = MaterialTheme.colorScheme.onSurface,
                          fontFamily = fontFamily,  // Render in actual font
                          style = if (font == selectedFont) {
                            MaterialTheme.typography.bodyLarge.copy(
                              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                          } else {
                            MaterialTheme.typography.bodyLarge
                          },
                        )
                      }
                    }
                  }
                },
                confirmButton = {
                  androidx.compose.material3.TextButton(onClick = { showFontPicker = false }) {
                    Text("Cancel")
                  }
                },
              )
            }
          }

          val fontSize by preferences.fontSize.collectAsState()
          SliderPreference(
            value = fontSize.toFloat(),
            onValueChange = { preferences.fontSize.set(it.toInt()) },
            sliderValue = fontSize.toFloat(),
            onSliderValueChange = { preferences.fontSize.set(it.toInt()) },
            valueRange = 10f..100f,
            valueSteps = 89,
            title = { Text("Font Size") },
            summary = { Text("$fontSize (default: 55)") },
          )

          val subScale by preferences.subScale.collectAsState()
          SliderPreference(
            value = subScale,
            onValueChange = { preferences.subScale.set(it) },
            sliderValue = subScale,
            onSliderValueChange = { preferences.subScale.set(it) },
            valueRange = 0.5f..2f,
            valueSteps = 29,
            title = { Text("Subtitle Scale") },
            summary = { Text("${String.format("%.2f", subScale)}x (default: 1.0x)") },
          )

          val bold by preferences.bold.collectAsState()
          SwitchPreference(
            value = bold,
            onValueChange = { preferences.bold.set(it) },
            title = { Text("Bold") },
            summary = { Text("Make subtitle text bold") },
          )

          val italic by preferences.italic.collectAsState()
          SwitchPreference(
            value = italic,
            onValueChange = { preferences.italic.set(it) },
            title = { Text("Italic") },
            summary = { Text("Make subtitle text italic") },
          )

          val justification by preferences.justification.collectAsState()
          ListPreference(
            value = justification,
            onValueChange = { preferences.justification.set(it) },
            title = { Text("Text Alignment") },
            valueToText = { androidx.compose.ui.text.AnnotatedString(it.name) },
            values = SubtitleJustification.entries,
            type = ListPreferenceType.DROPDOWN_MENU,
            summary = { Text(justification.name) },
          )

          val borderStyle by preferences.borderStyle.collectAsState()
          ListPreference(
            value = borderStyle,
            onValueChange = { preferences.borderStyle.set(it) },
            title = { Text("Border Style") },
            valueToText = { androidx.compose.ui.text.AnnotatedString(it.name.replace("And", " & ")) },
            values = SubtitlesBorderStyle.entries,
            type = ListPreferenceType.DROPDOWN_MENU,
            summary = { Text(borderStyle.name.replace("And", " & ")) },
          )

          val borderSize by preferences.borderSize.collectAsState()
          SliderPreference(
            value = borderSize.toFloat(),
            onValueChange = { preferences.borderSize.set(it.toInt()) },
            sliderValue = borderSize.toFloat(),
            onSliderValueChange = { preferences.borderSize.set(it.toInt()) },
            valueRange = 0f..10f,
            valueSteps = 9,
            title = { Text("Border Size") },
            summary = { Text("$borderSize (default: 3)") },
          )
        }
      }
    }
  }

  /**
   * Loads available font families from the app's fonts directory
   */
  private suspend fun loadAvailableFonts(
    context: android.content.Context,
    fileManager: FileManager,
  ): List<String> = withContext(Dispatchers.IO) {
    val fontsDir = fileManager.fromPath(context.filesDir.path + "/fonts")
    if (fileManager.exists(fontsDir)) {
      fileManager.listFiles(fontsDir)
        .filter { file ->
          fileManager.isFile(file) && fileManager.getName(file).lowercase().matches(".*\\.[ot]tf$".toRegex())
        }
        .mapNotNull { file ->
          runCatching {
            TTFFile.open(fileManager.getInputStream(file)!!).families.values.first()
          }.getOrNull()
        }
        .distinct()
        .sorted()
    } else {
      emptyList()
    }
  }

  /**
   * Copies font files from the selected directory to app's internal storage
   */
  private fun copyFontsFromDirectory(
    context: android.content.Context,
    fileManager: FileManager,
    uriString: String,
  ) {
    runCatching {
      val destinationPath = context.filesDir.path + "/fonts"
      val destinationDir = fileManager.fromPath(destinationPath)

      // Ensure destination directory exists
      if (!fileManager.exists(destinationDir)) {
        File(destinationPath).mkdirs()
      }

      val sourceDir = fileManager.fromUri(uriString.toUri())
      if (sourceDir != null && fileManager.exists(sourceDir)) {
        // Copy all font files from source to destination
        fileManager.listFiles(sourceDir).forEach { file ->
          if (fileManager.isFile(file)) {
            val fileName = fileManager.getName(file)
            if (fileName.lowercase().matches(".*\\.[ot]tf$".toRegex())) {
              val inputStream = fileManager.getInputStream(file) ?: return@forEach
              val outputFile = File(destinationPath, fileName)
              outputFile.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
              }
            }
          }
        }
      }
    }.onFailure { e ->
      android.util.Log.e("SubtitlesPreferences", "Error copying fonts", e)
    }
  }

  /**
   * Extracts a simplified readable path from a URI
   */
  private fun getSimplifiedPathFromUri(uriString: String): String {
    return runCatching {
      val uri = uriString.toUri()

      // Handle document tree URIs
      if (uriString.contains("/tree/")) {
        val treeId = uri.lastPathSegment ?: return@runCatching uriString

        // Extract readable path from tree ID
        // Format: "primary:Documents/Fonts" or "1234-5678:Folder/Subfolder"
        val parts = treeId.split(":")
        if (parts.size >= 2) {
          val pathPart = parts[1]
          val segments = pathPart.split("/").filter { it.isNotBlank() }

          // Show last 2-3 segments
          return when {
            segments.size > 2 -> ".../" + segments.takeLast(2).joinToString("/")
            segments.isNotEmpty() -> segments.joinToString("/")
            else -> parts[0] // Just show storage name if no path
          }
        }
      }

      // Fallback to regular path handling
      val path = uri.path ?: return@runCatching uriString
      val segments = path.split("/").filter { it.isNotBlank() }

      when {
        segments.size > 2 -> ".../" + segments.takeLast(2).joinToString("/")
        segments.isNotEmpty() -> segments.joinToString("/")
        else -> uriString
      }
    }.getOrDefault("Selected directory")
  }
}
