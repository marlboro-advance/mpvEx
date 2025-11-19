package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_preferences)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          preference(
            key = "appearance",
            title = { Text(text = stringResource(id = R.string.pref_appearance_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_appearance_summary)) },
            icon = { Icon(Icons.Outlined.Palette, null) },
            onClick = { backstack.add(AppearancePreferencesScreen) },
          )
          preference(
            key = "player",
            title = { Text(text = stringResource(id = R.string.pref_player)) },
            summary = { Text(text = stringResource(id = R.string.pref_player_summary)) },
            icon = { Icon(Icons.Outlined.PlayCircle, null) },
            onClick = { backstack.add(PlayerPreferencesScreen) },
          )
          preference(
            key = "player_controls",
            title = { Text(text = stringResource(id = R.string.pref_layout_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_layout_summary)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.ViewQuilt, null) },
            onClick = { backstack.add(PlayerControlsPreferencesScreen) },
          )
          preference(
            key = "list",
            title = { Text(text = stringResource(id = R.string.pref_browser_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_browser_summary)) },
            icon = { Icon(Icons.Outlined.Folder, null) },
            onClick = { backstack.add(ListPreferencesScreen) },
          )
          preference(
            key = "gesture",
            title = { Text(text = stringResource(id = R.string.pref_gesture)) },
            summary = { Text(text = stringResource(id = R.string.pref_gesture_summary)) },
            icon = { Icon(Icons.Outlined.Gesture, null) },
            onClick = { backstack.add(GesturePreferencesScreen) },
          )
          preference(
            key = "decoder",
            title = { Text(text = stringResource(id = R.string.pref_decoder)) },
            summary = { Text(text = stringResource(id = R.string.pref_decoder_summary)) },
            icon = { Icon(Icons.Outlined.Memory, null) },
            onClick = { backstack.add(DecoderPreferencesScreen) },
          )
          preference(
            key = "subtitles",
            title = { Text(text = stringResource(id = R.string.pref_subtitles)) },
            summary = { Text(text = stringResource(id = R.string.pref_subtitles_summary)) },
            icon = { Icon(Icons.Outlined.Subtitles, null) },
            onClick = { backstack.add(SubtitlesPreferencesScreen) },
          )
          preference(
            key = "audio",
            title = { Text(text = stringResource(id = R.string.pref_audio)) },
            summary = { Text(text = stringResource(id = R.string.pref_audio_summary)) },
            icon = { Icon(Icons.Outlined.Audiotrack, null) },
            onClick = { backstack.add(AudioPreferencesScreen) },
          )
          preference(
            key = "advanced",
            title = { Text(text = stringResource(R.string.pref_advanced)) },
            summary = { Text(text = stringResource(id = R.string.pref_advanced_summary)) },
            icon = { Icon(Icons.Outlined.Code, null) },
            onClick = { backstack.add(AdvancedPreferencesScreen) },
          )
          preference(
            key = "about",
            title = { Text(text = stringResource(id = R.string.pref_about_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_about_summary)) },
            icon = { Icon(Icons.Outlined.Info, null) },
            onClick = { backstack.add(AboutScreen) },
          )
        }
      }
    }
  }
}
