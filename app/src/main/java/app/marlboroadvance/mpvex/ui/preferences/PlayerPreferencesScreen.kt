package app.marlboroadvance.mpvex.ui.preferences

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.PlayerOrientation
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.toFixed
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object PlayerPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val preferences = koinInject<PlayerPreferences>()
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_player),
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
          // General Section
          item {
            PreferenceSectionHeader(title = "General")
          }
          
          item {
            PreferenceCard {
              val orientation by preferences.orientation.collectAsState()
              ListPreference(
                value = orientation,
                onValueChange = preferences.orientation::set,
                values = PlayerOrientation.entries,
                valueToText = { AnnotatedString(context.getString(it.titleRes)) },
                title = { Text(text = stringResource(id = R.string.pref_player_orientation)) },
                summary = { 
                  Text(
                    text = stringResource(id = orientation.titleRes),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
              )
              
              PreferenceDivider()
              
              val savePositionOnQuit by preferences.savePositionOnQuit.collectAsState()
              SwitchPreference(
                value = savePositionOnQuit,
                onValueChange = preferences.savePositionOnQuit::set,
                title = { Text(stringResource(R.string.pref_player_save_position_on_quit)) },
              )
              
              PreferenceDivider()
              
              val closeAfterEndOfVideo by preferences.closeAfterReachingEndOfVideo.collectAsState()
              SwitchPreference(
                value = closeAfterEndOfVideo,
                onValueChange = preferences.closeAfterReachingEndOfVideo::set,
                title = { Text(stringResource(id = R.string.pref_player_close_after_eof)) },
              )
              
              PreferenceDivider()
              
              val autoplayNextVideo by preferences.autoplayNextVideo.collectAsState()
              SwitchPreference(
                value = autoplayNextVideo,
                onValueChange = preferences.autoplayNextVideo::set,
                title = { Text(text = "Autoplay next video") },
                summary = {
                  Text(
                    text = if (autoplayNextVideo)
                      "Automatically play next video when current ends"
                    else
                      "Stay on current video when it ends",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
              
              PreferenceDivider()
              
              val playlistMode by preferences.playlistMode.collectAsState()
              SwitchPreference(
                value = playlistMode,
                onValueChange = preferences.playlistMode::set,
                title = { Text(text = "Enable next/previous navigation") },
                summary = {
                  Text(
                    text = if (playlistMode)
                      "Show next/previous buttons for all videos in folder"
                    else
                      "Play videos individually (select multiple for playlist)",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
              
              PreferenceDivider()

              val rememberBrightness by preferences.rememberBrightness.collectAsState()
              SwitchPreference(
                value = rememberBrightness,
                onValueChange = preferences.rememberBrightness::set,
                title = { Text(text = stringResource(R.string.pref_player_remember_brightness)) },
              )

              PreferenceDivider()

              val autoPiPOnNavigation by preferences.autoPiPOnNavigation.collectAsState()
              SwitchPreference(
                value = autoPiPOnNavigation,
                onValueChange = preferences.autoPiPOnNavigation::set,
                title = { Text("Auto Picture-in-Picture") },
                summary = {
                  Text(
                    text = "Automatically enter PIP mode when pressing home or back",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }
          // Seeking Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_seeking_title))
          }
          
          item {
            PreferenceCard {
              val horizontalSeekGesture by preferences.horizontalSeekGesture.collectAsState()
              SwitchPreference(
                value = horizontalSeekGesture,
                onValueChange = preferences.horizontalSeekGesture::set,
                title = { Text(stringResource(R.string.pref_player_gestures_seek)) },
              )
              
              PreferenceDivider()
              
              val showSeekbarWhenSeeking by preferences.showSeekBarWhenSeeking.collectAsState()
              SwitchPreference(
                value = showSeekbarWhenSeeking,
                onValueChange = preferences.showSeekBarWhenSeeking::set,
                title = { Text(stringResource(R.string.pref_player_show_seekbar_when_seeking)) },
              )
              
              PreferenceDivider()
              
              val showDoubleTapOvals by preferences.showDoubleTapOvals.collectAsState()
              SwitchPreference(
                value = showDoubleTapOvals,
                onValueChange = preferences.showDoubleTapOvals::set,
                title = { Text(stringResource(R.string.show_splash_ovals_on_double_tap_to_seek)) },
              )
              
              PreferenceDivider()
              
              val showSeekTimeWhileSeeking by preferences.showSeekTimeWhileSeeking.collectAsState()
              SwitchPreference(
                value = showSeekTimeWhileSeeking,
                onValueChange = preferences.showSeekTimeWhileSeeking::set,
                title = { Text(stringResource(R.string.show_time_on_double_tap_to_seek)) },
              )
              
              PreferenceDivider()
              
              val usePreciseSeeking by preferences.usePreciseSeeking.collectAsState()
              SwitchPreference(
                value = usePreciseSeeking,
                onValueChange = preferences.usePreciseSeeking::set,
                title = { Text(stringResource(R.string.pref_player_use_precise_seeking)) },
              )

              PreferenceDivider()

              val seekSensitivity by preferences.seekSensitivity.collectAsState()
              SliderPreference(
                value = seekSensitivity,
                onValueChange = { preferences.seekSensitivity.set(it.toFixed(2)) },
                title = { Text(stringResource(R.string.pref_player_seek_sensitivity)) },
                valueRange = 0.01f..0.3f,
                summary = {
                  Text(
                    "%.2f".format(seekSensitivity),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.seekSensitivity.set(it.toFixed(2)) },
                sliderValue = seekSensitivity,
              )
            }
          }
          // Gestures Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_gestures))
          }
          
          item {
            PreferenceCard {
              val brightnessGesture by preferences.brightnessGesture.collectAsState()
              SwitchPreference(
                value = brightnessGesture,
                onValueChange = preferences.brightnessGesture::set,
                title = { Text(stringResource(R.string.pref_player_gestures_brightness)) },
              )
              
              PreferenceDivider()
              
              val volumeGesture by preferences.volumeGesture.collectAsState()
              SwitchPreference(
                value = volumeGesture,
                onValueChange = preferences.volumeGesture::set,
                title = { Text(stringResource(R.string.pref_player_gestures_volume)) },
              )
              
              PreferenceDivider()
              
              val pinchToZoomGesture by preferences.pinchToZoomGesture.collectAsState()
              SwitchPreference(
                value = pinchToZoomGesture,
                onValueChange = preferences.pinchToZoomGesture::set,
                title = { Text(stringResource(R.string.pref_player_gestures_pinch_to_zoom)) },
              )
              
              PreferenceDivider()
              
              val holdForMultipleSpeed by preferences.holdForMultipleSpeed.collectAsState()
              SliderPreference(
                value = holdForMultipleSpeed,
                onValueChange = { preferences.holdForMultipleSpeed.set(it.toFixed(2)) },
                title = { Text(stringResource(R.string.pref_player_gestures_hold_for_multiple_speed)) },
                valueRange = 0f..6f,
                summary = {
                  Text(
                    if (holdForMultipleSpeed == 0F) {
                      stringResource(R.string.generic_disabled)
                    } else {
                      "%.2fx".format(holdForMultipleSpeed)
                    },
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.holdForMultipleSpeed.set(it.toFixed(2)) },
                sliderValue = holdForMultipleSpeed,
              )
              
              PreferenceDivider()
              
              val showDynamicSpeedOverlay by preferences.showDynamicSpeedOverlay.collectAsState()
              SwitchPreference(
                value = showDynamicSpeedOverlay,
                onValueChange = preferences.showDynamicSpeedOverlay::set,
                title = { Text("Dynamic Speed Overlay") },
                summary = { 
                  Text(
                    "Show advance overlay for speed control during long press and swipe",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                }
              )
            }
          }
          // Controls Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_controls))
          }
          
          item {
            PreferenceCard {
              val allowGesturesInPanels by preferences.allowGesturesInPanels.collectAsState()
              SwitchPreference(
                value = allowGesturesInPanels,
                onValueChange = preferences.allowGesturesInPanels::set,
                title = {
                  Text(
                    text = stringResource(id = R.string.pref_player_controls_allow_gestures_in_panels),
                  )
                },
              )
              
              PreferenceDivider()
              
              val swapVolumeAndBrightness by preferences.swapVolumeAndBrightness.collectAsState()
              SwitchPreference(
                value = swapVolumeAndBrightness,
                onValueChange = preferences.swapVolumeAndBrightness::set,
                title = { Text(stringResource(R.string.swap_the_volume_and_brightness_slider)) },
              )
              
              PreferenceDivider()
              
              val showLoadingCircle by preferences.showLoadingCircle.collectAsState()
              SwitchPreference(
                value = showLoadingCircle,
                onValueChange = preferences.showLoadingCircle::set,
                title = { Text(stringResource(R.string.pref_player_controls_show_loading_circle)) },
              )
            }
          }
          // Display Section
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_display))
          }
          
          item {
            PreferenceCard {
              val showSystemStatusBar by preferences.showSystemStatusBar.collectAsState()
              SwitchPreference(
                value = showSystemStatusBar,
                onValueChange = preferences.showSystemStatusBar::set,
                title = { Text(stringResource(R.string.pref_player_display_show_status_bar)) },
              )

              PreferenceDivider()

              val showSystemNavigationBar by preferences.showSystemNavigationBar.collectAsState()
              SwitchPreference(
                value = showSystemNavigationBar,
                onValueChange = preferences.showSystemNavigationBar::set,
                title = { Text("Show navigation bar with controls") },
              )
              
              PreferenceDivider()
              
              val reduceMotion by preferences.reduceMotion.collectAsState()
              SwitchPreference(
                value = reduceMotion,
                onValueChange = preferences.reduceMotion::set,
                title = { Text(stringResource(R.string.pref_player_display_reduce_player_animation)) },
              )
            }
          }
        }
      }
    }
  }
}
