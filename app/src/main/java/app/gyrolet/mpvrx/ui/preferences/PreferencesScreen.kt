package app.gyrolet.mpvrx.ui.preferences

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backstack.popSafely() }) {
              Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {

          // ── Search bar ────────────────────────────────────────────────────
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { backstack.add(SettingsSearchScreen) },
              shape = RoundedCornerShape(28.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          // ── 1. Appearance ─────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Appearance") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_appearance_title)) },
                summary = { Text(stringResource(R.string.pref_appearance_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(AppearancePreferencesScreen) },
              )
            }
          }

          // ── 2. Playback ───────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Playback") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_player)) },
                summary = { Text(stringResource(R.string.pref_player_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Default.Slideshow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(PlayerPreferencesScreen) },
              )
              PreferenceDivider()
              Preference(
                title = { Text(stringResource(R.string.pref_decoder)) },
                summary = { Text(stringResource(R.string.pref_decoder_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(DecoderPreferencesScreen) },
              )
              PreferenceDivider()
              Preference(
                title = { Text(stringResource(R.string.pref_audio)) },
                summary = { Text(stringResource(R.string.pref_audio_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(AudioPreferencesScreen) },
              )
            }
          }

          // ── 3. Gestures & Controls ────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Gestures & Controls") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_gesture)) },
                summary = { Text(stringResource(R.string.pref_gesture_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(GesturePreferencesScreen) },
              )
              PreferenceDivider()
              Preference(
                title = { Text(stringResource(R.string.pref_layout_title)) },
                summary = { Text(stringResource(R.string.pref_layout_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(PlayerControlsPreferencesScreen) },
              )
            }
          }

          // ── 4. Subtitles ──────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Subtitles") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_subtitles)) },
                summary = { Text(stringResource(R.string.pref_subtitles_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(SubtitlesPreferencesScreen) },
              )
            }
          }

          // ── 5. Storage ────────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Storage") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_folders_title)) },
                summary = { Text("Base folder, media paths, hidden folders", color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(FoldersPreferencesScreen) },
              )
            }
          }

          // ── 6. AI Integration ──────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "AI") }
          item {
            PreferenceCard {
              Preference(
                title = { Text("AI Integration") },
                summary = { Text("AI rename, subtitle translation, speech-to-text, offline models (Gemini/Groq/OpenAI)", color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(AiIntegrationScreen) },
              )
            }
          }

          // ── 7. Advanced ───────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "Advanced") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_advanced)) },
                summary = { Text(stringResource(R.string.pref_advanced_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Alternatives.AdvancedSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(AdvancedPreferencesScreen) },
              )
            }
          }

          // ── 7. About ──────────────────────────────────────────────────────
          item { PreferenceSectionHeader(title = "About") }
          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_about_title)) },
                summary = { Text(stringResource(R.string.pref_about_summary), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backstack.add(AboutScreen) },
              )
            }
          }
        }
      }
    }
  }
}
