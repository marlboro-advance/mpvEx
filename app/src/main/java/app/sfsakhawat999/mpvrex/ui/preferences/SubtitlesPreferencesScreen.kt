package app.sfsakhawat999.mpvrex.ui.preferences

import android.content.Intent
import android.graphics.Typeface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.SubtitlesPreferences
import app.sfsakhawat999.mpvrex.preferences.preference.collectAsState
import app.sfsakhawat999.mpvrex.presentation.Screen
import app.sfsakhawat999.mpvrex.ui.utils.LocalBackStack
import app.sfsakhawat999.mpvrex.utils.media.CustomFontEntry
import app.sfsakhawat999.mpvrex.utils.media.copyFontsFromDirectory
import app.sfsakhawat999.mpvrex.utils.media.loadCustomFontEntries
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject

@Serializable
object SubtitlesPreferencesScreen : Screen {
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
            IconButton(
              onClick = backstack::removeLastOrNull,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val fontsFolder by preferences.fontsFolder.collectAsState()
        val selectedFont by preferences.font.collectAsState()
        var availableFonts by remember { mutableStateOf<List<String>>(emptyList()) }
        var customFontEntries by remember { mutableStateOf<List<CustomFontEntry>>(emptyList()) }
        var fontLoadTrigger by remember { mutableIntStateOf(0) }
        var isLoadingFonts by remember { mutableStateOf(false) }

        val locationPicker =
          rememberLauncherForActivityResult(
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
          val customEntries = loadCustomFontEntries(context)
          customFontEntries = customEntries
          val customFonts = customEntries.map { it.familyName }

          // Add only default font, custom fonts can be added by user
          val defaultFonts = listOf("Sans Serif (Default)")

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
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(padding),
        ) {
          // === GENERAL SECTION ===
          PreferenceCategory(
            title = { Text(stringResource(R.string.general), style = MaterialTheme.typography.titleMedium) },
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
                Text(stringResource(R.string.not_set_video_default))
              }
            },
            textField = { value, onValueChange, _ ->
              Column {
                Text(stringResource(R.string.enter_language_codes))
                TextField(
                  value,
                  onValueChange,
                  modifier = Modifier.fillMaxWidth(),
                  placeholder = { Text(stringResource(R.string.language_codes_placeholder)) },
                )
              }
            },
          )

          val autoload by preferences.autoloadMatchingSubtitles.collectAsState()
          SwitchPreference(
            value = autoload,
            onValueChange = { preferences.autoloadMatchingSubtitles.set(it) },
            title = { Text(stringResource(R.string.pref_subtitles_autoload_title)) },
            summary = { Text(stringResource(R.string.pref_subtitles_autoload_summary)) },
          )

          val subdlApiKey by preferences.subdlApiKey.collectAsState()
          TextFieldPreference(
            value = subdlApiKey,
            onValueChange = preferences.subdlApiKey::set,
            textToValue = { it },
            title = { Text("Subdl API Key") },
            summary = {
              if (subdlApiKey.isNotBlank()) {
                Text("API key set (${subdlApiKey.take(8)}...)")
              } else {
                Text("Not set - Required for online subtitle downloads")
              }
            },
            textField = { value, onValueChange, _ ->
              Column(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                Text(
                  text = "Get your free API key from subdl",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                  value = value,
                  onValueChange = onValueChange,
                  modifier = Modifier.fillMaxWidth(),
                  placeholder = { Text("Enter API key") },
                  singleLine = true,
                )
              }
            },
          )

          // Directory picker preference with reload and clear icons on the right
          Box(
            modifier =
              Modifier
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
                    stringResource(R.string.not_set_system_fonts),
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
                      stringResource(R.string.fonts_loaded, availableFonts.size),
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
                        contentDescription = stringResource(R.string.reload_fonts),
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
                      contentDescription = stringResource(R.string.clear_font_directory),
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
            val isCustomFontSelected =
              selectedFont.isNotBlank() &&
                availableFonts.contains(selectedFont) &&
                selectedFont != "Sans Serif (Default)"

            // Font selection with clear icon
            Box(
              modifier =
                Modifier
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
                      contentDescription = stringResource(R.string.reset_to_default_font),
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
                      val typeface: Typeface? =
                        when (font) {
                          "Sans Serif (Default)" -> Typeface.SANS_SERIF
                          else -> {
                            // Custom font from user directory
                            val entry = customFontEntries.firstOrNull { it.familyName == font }
                            entry?.let {
                              runCatching {
                                Typeface.createFromFile(it.file)
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
                          style =
                            if (font == selectedFont) {
                              MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = typeface?.let { FontFamily(it) },
                              )
                            } else {
                              MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = typeface?.let { FontFamily(it) },
                              )
                            },
                        )
                      }
                    }
                  }
                },
                confirmButton = {
                  androidx.compose.material3.TextButton(onClick = { showFontPicker = false }) {
                    Text(stringResource(R.string.generic_cancel))
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}
