package app.marlboroadvance.mpvex.ui.preferences

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.CustomFontEntry
import app.marlboroadvance.mpvex.utils.media.copyFontsFromDirectory
import app.marlboroadvance.mpvex.utils.media.loadCustomFontEntries
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
          title = {
            Text(
              text = stringResource(R.string.pref_subtitles),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = backstack::removeLastOrNull,
            ) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val fontsFolder by preferences.fontsFolder.collectAsState()
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
          customFontEntries = loadCustomFontEntries(context)
          availableFonts = listOf("Default") + customFontEntries.map { it.familyName }
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

        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          // === GENERAL SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.general))
          }

          item {
            PreferenceCard {

              val preferredLanguages by preferences.preferredLanguages.collectAsState()
              TextFieldPreference(
                value = preferredLanguages,
                onValueChange = preferences.preferredLanguages::set,
                textToValue = { it },
                title = { Text(stringResource(R.string.pref_preferred_languages)) },
                summary = {
                  if (preferredLanguages.isNotBlank()) {
                    Text(
                      preferredLanguages,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  } else {
                    Text(
                      stringResource(R.string.not_set_video_default),
                      color = MaterialTheme.colorScheme.outline,
                    )
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
              
              PreferenceDivider()

              val autoload by preferences.autoloadMatchingSubtitles.collectAsState()
              SwitchPreference(
                value = autoload,
                onValueChange = { preferences.autoloadMatchingSubtitles.set(it) },
                title = { Text(stringResource(R.string.pref_subtitles_autoload_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_subtitles_autoload_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

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
            }
          }
        }
      }
    }
  }
}
