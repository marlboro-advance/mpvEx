package app.sfsakhawat999.mpvrex.ui.preferences

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.AppearancePreferences
import app.sfsakhawat999.mpvrex.preferences.MultiChoiceSegmentedButton
import app.sfsakhawat999.mpvrex.preferences.preference.collectAsState
import app.sfsakhawat999.mpvrex.presentation.Screen
import app.sfsakhawat999.mpvrex.ui.theme.DarkMode
import app.sfsakhawat999.mpvrex.ui.utils.LocalBackStack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject

@Serializable
object AppearancePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_appearance_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(padding),
        ) {
          PreferenceCategory(
            title = { Text(text = stringResource(id = R.string.pref_appearance_category_theme)) },
          )
          val darkMode by preferences.darkMode.collectAsState()
          MultiChoiceSegmentedButton(
            choices = DarkMode.entries.map { context.getString(it.titleRes) }.toImmutableList(),
            selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
            onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
          )
          val highContrastMode by preferences.highContrastMode.collectAsState()
          SwitchPreference(
            value = highContrastMode,
            onValueChange = { preferences.highContrastMode.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_appearance_high_contrast_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_appearance_high_contrast_summary)) },
            enabled = darkMode != DarkMode.Light,
          )
          val materialYou by preferences.materialYou.collectAsState()
          val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          SwitchPreference(
            value = materialYou,
            onValueChange = { preferences.materialYou.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_appearance_material_you_title)) },
            summary = {
              Text(
                text =
                  stringResource(
                    if (isMaterialYouAvailable) {
                      R.string.pref_appearance_material_you_summary
                    } else {
                      R.string.pref_appearance_material_you_summary_disabled
                    },
                  ),
              )
            },
            enabled = isMaterialYouAvailable,
          )
          val unlimitedNameLines by preferences.unlimitedNameLines.collectAsState()
          SwitchPreference(
            value = unlimitedNameLines,
            onValueChange = { preferences.unlimitedNameLines.set(it) },
            title = {
              Text(
                text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_title),
              )
            },
            summary = {
              Text(
                text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_summary),
              )
            },
          )
          val hidePlayerButtonsBackground by preferences.hidePlayerButtonsBackground.collectAsState()
          SwitchPreference(
            value = hidePlayerButtonsBackground,
            onValueChange = { preferences.hidePlayerButtonsBackground.set(it) },
            title = {
              Text(
                text = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_title),
              )
            },
            summary = {
              Text(
                text = stringResource(id = R.string.pref_appearance_hide_player_buttons_background_summary),
              )
            },
          )

          // --- New Player Control Settings ---
          PreferenceCategory(
            title = { Text(text = "Player Controls") }, // TODO: Add to strings.xml
          )
          Preference(
            title = { Text(text = "Available Buttons") }, // TODO: Add to strings.xml
            summary = {
              Text(
                text = "Use these values (comma-separated, max 4 per section): \n" +
                  preferences.allPlayerButtons.joinToString(),
              )
            },
          )

          // Top Right Controls
          var topRightControls by remember { mutableStateOf(preferences.topRightControls.get()) }
          TextFieldPreference(
            value = topRightControls,
            onValueChange = { topRightControls = it },
            title = { Text(text = "Top Right Controls") }, // TODO: Add to strings.xml
            summary = { Text(text = "Fixed: MORE_OPTIONS") },
            textToValue = {
              preferences.topRightControls.set(it)
              it // Return the value to commit it
            },
          )

          // Bottom Right Controls
          var bottomRightControls by remember { mutableStateOf(preferences.bottomRightControls.get()) }
          TextFieldPreference(
            value = bottomRightControls,
            onValueChange = { bottomRightControls = it },
            title = { Text(text = "Bottom Right Controls") }, // TODO: Add to strings.xml
            textToValue = {
              preferences.bottomRightControls.set(it)
              it
            },
          )

          // Bottom Left Controls
          var bottomLeftControls by remember { mutableStateOf(preferences.bottomLeftControls.get()) }
          TextFieldPreference(
            value = bottomLeftControls,
            onValueChange = { bottomLeftControls = it },
            title = { Text(text = "Bottom Left Controls") }, // TODO: Add to strings.xml
            summary = { Text(text = "Fixed: CURRENT_CHAPTER") },
            textToValue = {
              preferences.bottomLeftControls.set(it)
              it
            },
          )
        }
      }
    }
  }
}
