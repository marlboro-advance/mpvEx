package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.PlayerOrientation
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.toFixed
import app.marlboroadvance.mpvex.ui.player.controls.components.SeekbarWithTimers
import app.marlboroadvance.mpvex.preferences.SeekbarStyle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.PreferenceCategory
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
          title = { Text(text = stringResource(id = R.string.pref_player)) },
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
          val orientation by preferences.orientation.collectAsState()
          ListPreference(
            value = orientation,
            onValueChange = preferences.orientation::set,
            values = PlayerOrientation.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(id = R.string.pref_player_orientation)) },
            summary = { Text(text = stringResource(id = orientation.titleRes)) },
          )
          val savePositionOnQuit by preferences.savePositionOnQuit.collectAsState()
          SwitchPreference(
            value = savePositionOnQuit,
            onValueChange = preferences.savePositionOnQuit::set,
            title = { Text(stringResource(R.string.pref_player_save_position_on_quit)) },
          )
          val closeAfterEndOfVideo by preferences.closeAfterReachingEndOfVideo.collectAsState()
          SwitchPreference(
            value = closeAfterEndOfVideo,
            onValueChange = preferences.closeAfterReachingEndOfVideo::set,
            title = { Text(stringResource(id = R.string.pref_player_close_after_eof)) },
          )
          val playlistMode by preferences.playlistMode.collectAsState()
          SwitchPreference(
            value = playlistMode,
            onValueChange = preferences.playlistMode::set,
            title = { Text(text = "Playlist Mode") },
            summary = { 
              Text(
                text = if (playlistMode)
                  "Automatically enable next/previous navigation for all videos in folder"
                else
                  "Play videos individually (select multiple for playlist)"
              )
            },
          )
          val rememberBrightness by preferences.rememberBrightness.collectAsState()
          SwitchPreference(
            value = rememberBrightness,
            onValueChange = preferences.rememberBrightness::set,
            title = { Text(text = stringResource(R.string.pref_player_remember_brightness)) },
          )
          PreferenceCategory(
            title = { Text(stringResource(R.string.pref_player_seeking_title)) },
          )
          val horizontalSeekGesture by preferences.horizontalSeekGesture.collectAsState()
          SwitchPreference(
            value = horizontalSeekGesture,
            onValueChange = preferences.horizontalSeekGesture::set,
            title = { Text(stringResource(R.string.pref_player_gestures_seek)) },
          )
          val showSeekbarWhenSeeking by preferences.showSeekBarWhenSeeking.collectAsState()
          SwitchPreference(
            value = showSeekbarWhenSeeking,
            onValueChange = preferences.showSeekBarWhenSeeking::set,
            title = { Text(stringResource(R.string.pref_player_show_seekbar_when_seeking)) },
          )
          val showDoubleTapOvals by preferences.showDoubleTapOvals.collectAsState()
          SwitchPreference(
            value = showDoubleTapOvals,
            onValueChange = preferences.showDoubleTapOvals::set,
            title = { Text(stringResource(R.string.show_splash_ovals_on_double_tap_to_seek)) },
          )
          val showSeekTimeWhileSeeking by preferences.showSeekTimeWhileSeeking.collectAsState()
          SwitchPreference(
            value = showSeekTimeWhileSeeking,
            onValueChange = preferences.showSeekTimeWhileSeeking::set,
            title = { Text(stringResource(R.string.show_time_on_double_tap_to_seek)) },
          )
          val usePreciseSeeking by preferences.usePreciseSeeking.collectAsState()
          SwitchPreference(
            value = usePreciseSeeking,
            onValueChange = preferences.usePreciseSeeking::set,
            title = { Text(stringResource(R.string.pref_player_use_precise_seeking)) },
          )
          val seekbarStyle by preferences.seekbarStyle.collectAsState()
          androidx.compose.material3.Text(
            text = "Seekbar Style",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
          )
          app.marlboroadvance.mpvex.preferences.MultiChoiceSegmentedButton(
              choices = SeekbarStyle.entries.map { it.name }.toImmutableList(),
              selectedIndices = persistentListOf(SeekbarStyle.entries.indexOf(seekbarStyle)),
              onClick = { preferences.seekbarStyle.set(SeekbarStyle.entries[it]) },
              modifier = Modifier.padding(horizontal = 16.dp)
          )
          
          Text(text = "Preview", modifier = Modifier.padding(start = 16.dp, top = 8.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
          Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            var previewPosition by remember { androidx.compose.runtime.mutableFloatStateOf(30f) }
            SeekbarWithTimers(
                position = previewPosition,
                duration = 100f,
                onValueChange = { previewPosition = it },
                onValueChangeFinished = {},
                timersInverted = Pair(false, false),
                positionTimerOnClick = {},
                durationTimerOnCLick = {},
                chapters = persistentListOf(),
                paused = true,
                seekbarStyle = seekbarStyle
            )
          }
          PreferenceCategory(
            title = { Text(stringResource(R.string.pref_player_gestures)) },
          )
          val brightnessGesture by preferences.brightnessGesture.collectAsState()
          SwitchPreference(
            value = brightnessGesture,
            onValueChange = preferences.brightnessGesture::set,
            title = { Text(stringResource(R.string.pref_player_gestures_brightness)) },
          )
          val volumeGesture by preferences.volumeGesture.collectAsState()
          SwitchPreference(
            value = volumeGesture,
            onValueChange = preferences.volumeGesture::set,
            title = { Text(stringResource(R.string.pref_player_gestures_volume)) },
          )
          val pinchToZoomGesture by preferences.pinchToZoomGesture.collectAsState()
          SwitchPreference(
            value = pinchToZoomGesture,
            onValueChange = preferences.pinchToZoomGesture::set,
            title = { Text(stringResource(R.string.pref_player_gestures_pinch_to_zoom)) },
          )
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
              )
            },
            onSliderValueChange = { preferences.holdForMultipleSpeed.set(it.toFixed(2)) },
            sliderValue = holdForMultipleSpeed,
          )
          val showDynamicSpeedOverlay by preferences.showDynamicSpeedOverlay.collectAsState()
          SwitchPreference(
            value = showDynamicSpeedOverlay,
            onValueChange = preferences.showDynamicSpeedOverlay::set,
            title = { Text("Dynamic Speed Overlay") },
            summary = { Text("Show advance overlay for speed control during long press and swipe") }
          )
          PreferenceCategory(
            title = { Text(stringResource(R.string.pref_player_controls)) },
          )
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
          val displayVolumeAsPercentage by preferences.displayVolumeAsPercentage.collectAsState()
          SwitchPreference(
            value = displayVolumeAsPercentage,
            onValueChange = preferences.displayVolumeAsPercentage::set,
            title = { Text(stringResource(R.string.pref_player_controls_display_volume_as_percentage)) },
          )
          val swapVolumeAndBrightness by preferences.swapVolumeAndBrightness.collectAsState()
          SwitchPreference(
            value = swapVolumeAndBrightness,
            onValueChange = preferences.swapVolumeAndBrightness::set,
            title = { Text(stringResource(R.string.swap_the_volume_and_brightness_slider)) },
          )
          val showLoadingCircle by preferences.showLoadingCircle.collectAsState()
          SwitchPreference(
            value = showLoadingCircle,
            onValueChange = preferences.showLoadingCircle::set,
            title = { Text(stringResource(R.string.pref_player_controls_show_loading_circle)) },
          )
          PreferenceCategory(
            title = { Text(stringResource(R.string.pref_player_display)) },
          )
          val showSystemStatusBar by preferences.showSystemStatusBar.collectAsState()
          SwitchPreference(
            value = showSystemStatusBar,
            onValueChange = preferences.showSystemStatusBar::set,
            title = { Text(stringResource(R.string.pref_player_display_show_status_bar)) },
          )
          val reduceMotion by preferences.reduceMotion.collectAsState()
          SwitchPreference(
            value = reduceMotion,
            onValueChange = preferences.reduceMotion::set,
            title = { Text(stringResource(R.string.pref_player_display_reduce_player_animation)) },
          )
          val playerTimeToDisappear by preferences.playerTimeToDisappear.collectAsState()
          ListPreference(
            value = playerTimeToDisappear,
            onValueChange = preferences.playerTimeToDisappear::set,
            values = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000),
            valueToText = { AnnotatedString("$it ms") },
            title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
            summary = { Text(text = "$playerTimeToDisappear ms") },
          )
        }
      }
    }
  }
}
