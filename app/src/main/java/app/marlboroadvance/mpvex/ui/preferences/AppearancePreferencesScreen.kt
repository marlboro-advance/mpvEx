package app.marlboroadvance.mpvex.ui.preferences

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.MultiChoiceSegmentedButton
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object AppearancePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val browserPreferences = koinInject<BrowserPreferences>()
    val gesturePreferences = koinInject<GesturePreferences>()
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_appearance_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            ) 
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
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
        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          item {
            PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_theme))
          }
          
          item {
            PreferenceCard {
              val darkMode by preferences.darkMode.collectAsState()
              
              Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MultiChoiceSegmentedButton(
                  choices = DarkMode.entries.map { context.getString(it.titleRes) }.toImmutableList(),
                  selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
                  onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
                )
              }
              
              PreferenceDivider()
              
              val amoledMode by preferences.amoledMode.collectAsState()
              SwitchPreference(
                value = amoledMode,
                onValueChange = { preferences.amoledMode.set(it) },
                title = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_appearance_amoled_mode_summary),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  ) 
                },
                enabled = darkMode != DarkMode.Light
              )
              
              PreferenceDivider()
              
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
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                },
                enabled = isMaterialYouAvailable
              )
              
              // Removed full names preference - moved to File Browser section
            }
          }

          item {
            PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_file_browser))
          }
          
          item {
            PreferenceCard {
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
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
              
              PreferenceDivider()
              
              val showHiddenFiles by preferences.showHiddenFiles.collectAsState()
              SwitchPreference(
                value = showHiddenFiles,
                onValueChange = { preferences.showHiddenFiles.set(it) },
                title = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_hidden_files_title),
                  )
                },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_hidden_files_summary),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
              
              PreferenceDivider()
              
              val showUnplayedOldVideoLabel by preferences.showUnplayedOldVideoLabel.collectAsState()
              SwitchPreference(
                value = showUnplayedOldVideoLabel,
                onValueChange = { preferences.showUnplayedOldVideoLabel.set(it) },
                title = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_title),
                  )
                },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_summary),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
              
              PreferenceDivider() // Added divider between label and threshold
              
              val unplayedOldVideoDays by preferences.unplayedOldVideoDays.collectAsState()
              SliderPreference(
                value = unplayedOldVideoDays.toFloat(),
                onValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                title = { Text(text = stringResource(id = R.string.pref_appearance_unplayed_old_video_days_title)) },
                valueRange = 1f..30f,
                summary = {
                  Text(
                    text = stringResource(
                      id = R.string.pref_appearance_unplayed_old_video_days_summary,
                      unplayedOldVideoDays,
                    ),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                },
                onSliderValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                sliderValue = unplayedOldVideoDays.toFloat(),
                enabled = showUnplayedOldVideoLabel
              )
              
              PreferenceDivider()
              
              val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
              SwitchPreference(
                value = autoScrollToLastPlayed,
                onValueChange = { browserPreferences.autoScrollToLastPlayed.set(it) },
                title = {
                  Text(text = "Auto-scroll to last played")
                },
                summary = {
                  Text(
                    text = "Automatically scroll to the last played video when opening video lists",
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
              
              PreferenceDivider()
              
              val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
              SwitchPreference(
                value = tapThumbnailToSelect,
                onValueChange = { gesturePreferences.tapThumbnailToSelect.set(it) },
                title = {
                  Text(
                    text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_title),
                  )
                },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_summary),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
              
              PreferenceDivider()
              
              val showNetworkThumbnails by preferences.showNetworkThumbnails.collectAsState()
              SwitchPreference(
                value = showNetworkThumbnails,
                onValueChange = { preferences.showNetworkThumbnails.set(it) },
                title = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_title),
                  )
                },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_summary),
                    color = MaterialTheme.colorScheme.outline, // Fainter color
                  )
                }
              )
            }
          }

        }
      }
    }
  }
}
