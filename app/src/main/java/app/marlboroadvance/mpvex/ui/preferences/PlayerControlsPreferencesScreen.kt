package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
// import androidx.compose.material.icons.outlined.VideoLabel // No longer needed here
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

// Enum to identify which region we are editing
@Serializable
enum class ControlRegion {
  TOP_RIGHT,
  BOTTOM_RIGHT,
  BOTTOM_LEFT,
  PORTRAIT_BOTTOM,
}

@Serializable
object PlayerControlsPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val appearancePrefs = koinInject<AppearancePreferences>()
    val playerPrefs = koinInject<PlayerPreferences>()

    // Get the current state for all four regions
    val topRState by appearancePrefs.topRightControls.collectAsState()
    val bottomRState by appearancePrefs.bottomRightControls.collectAsState()
    val bottomLState by appearancePrefs.bottomLeftControls.collectAsState()
    val portraitBottomState by appearancePrefs.portraitBottomControls.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
      ConfirmDialog(
        title = stringResource(id = R.string.pref_layout_reset_title),
        subtitle = stringResource(id = R.string.pref_layout_reset_summary),
        onConfirm = {
          // Don't reset topLeftControls as it contains constant buttons
          appearancePrefs.topRightControls.delete()
          appearancePrefs.bottomRightControls.delete()
          appearancePrefs.bottomLeftControls.delete()
          appearancePrefs.portraitBottomControls.delete()
        },
        onCancel = {
        },
      )
    }

    val (topRightButtons, bottomRightButtons, bottomLeftButtons) =
      remember(
        topRState,
        bottomRState,
        bottomLState,
      ) {
        val usedButtons = mutableSetOf<PlayerButton>()
        val topR = appearancePrefs.parseButtons(topRState, usedButtons)
        val bottomR = appearancePrefs.parseButtons(bottomRState, usedButtons)
        val bottomL = appearancePrefs.parseButtons(bottomLState, usedButtons)
        listOf(topR, bottomR, bottomL)
      }

    // Top left buttons are constant (BACK_ARROW, VIDEO_TITLE) and used only for preview
    val topLeftButtons = remember {
      listOf(PlayerButton.BACK_ARROW, PlayerButton.VIDEO_TITLE)
    }

    val portraitBottomButtons = remember(portraitBottomState) {
      appearancePrefs.parseButtons(portraitBottomState, mutableSetOf())
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_layout_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            IconButton(onClick = { }) {
              Icon(Icons.Outlined.Restore, contentDescription = stringResource(id = R.string.pref_layout_reset_default))
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
          // Top Left is not shown here since it only contains constant buttons (BACK_ARROW, VIDEO_TITLE)
          // that cannot be customized

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_top_right_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.TOP_RIGHT))
              },
            )
            PreferenceIconSummary(buttons = topRightButtons)
          }

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_bottom_right_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_RIGHT))
              },
            )
            PreferenceIconSummary(buttons = bottomRightButtons)
          }

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_bottom_left_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_LEFT))
              },
            )
            PreferenceIconSummary(buttons = bottomLeftButtons)
          }

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_portrait_bottom_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.PORTRAIT_BOTTOM))
              },
            )
            PreferenceIconSummary(buttons = portraitBottomButtons)
          }

          item {
            PreferenceCategory(title = { Text(stringResource(id = R.string.pref_layout_preview)) })
            ControlLayoutPreview(
              modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
              topLeftButtons = topLeftButtons,
              topRightButtons = topRightButtons,
              bottomRightButtons = bottomRightButtons,
              bottomLeftButtons = bottomLeftButtons,
              portraitBottomButtons = portraitBottomButtons,
            )
          }

          item {
            PreferenceCategory(
              title = { Text(stringResource(R.string.pref_player_controls)) },
            )
          }

          item {
            val allowGesturesInPanels by playerPrefs.allowGesturesInPanels.collectAsState()
            SwitchPreference(
              value = allowGesturesInPanels,
              onValueChange = playerPrefs.allowGesturesInPanels::set,
              title = {
                Text(
                  text = stringResource(id = R.string.pref_player_controls_allow_gestures_in_panels),
                )
              },
            )
          }

          item {
            val displayVolumeAsPercentage by playerPrefs.displayVolumeAsPercentage.collectAsState()
            SwitchPreference(
              value = displayVolumeAsPercentage,
              onValueChange = playerPrefs.displayVolumeAsPercentage::set,
              title = { Text(stringResource(R.string.pref_player_controls_display_volume_as_percentage)) },
            )
          }

          item {
            val swapVolumeAndBrightness by playerPrefs.swapVolumeAndBrightness.collectAsState()
            SwitchPreference(
              value = swapVolumeAndBrightness,
              onValueChange = playerPrefs.swapVolumeAndBrightness::set,
              title = { Text(stringResource(R.string.swap_the_volume_and_brightness_slider)) },
            )
          }

          item {
            val showLoadingCircle by playerPrefs.showLoadingCircle.collectAsState()
            SwitchPreference(
              value = showLoadingCircle,
              onValueChange = playerPrefs.showLoadingCircle::set,
              title = { Text(stringResource(R.string.pref_player_controls_show_loading_circle)) },
            )
          }

          item {
            PreferenceCategory(
              title = { Text(stringResource(R.string.pref_player_display)) },
            )
          }

          item {
            val showSystemStatusBar by playerPrefs.showSystemStatusBar.collectAsState()
            SwitchPreference(
              value = showSystemStatusBar,
              onValueChange = playerPrefs.showSystemStatusBar::set,
              title = { Text(stringResource(R.string.pref_player_display_show_status_bar)) },
            )
          }

          item {
            val reduceMotion by playerPrefs.reduceMotion.collectAsState()
            SwitchPreference(
              value = reduceMotion,
              onValueChange = playerPrefs.reduceMotion::set,
              title = { Text(stringResource(R.string.pref_player_display_reduce_player_animation)) },
            )
          }

          item {
            val playerTimeToDisappear by playerPrefs.playerTimeToDisappear.collectAsState()
            ListPreference(
              value = playerTimeToDisappear,
              onValueChange = playerPrefs.playerTimeToDisappear::set,
              values = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000),
              valueToText = { AnnotatedString("$it ms") },
              title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
              summary = { Text(text = "$playerTimeToDisappear ms") },
            )
          }

          item {
            val panelTransparency by playerPrefs.panelTransparency.collectAsState()
            SliderPreference(
              value = panelTransparency,
              onValueChange = { playerPrefs.panelTransparency.set(it) },
              title = { Text(stringResource(R.string.pref_player_display_panel_opacity)) },
              valueRange = 0f..1f,
              summary = {
                Text(
                  text =
                    stringResource(
                      id = R.string.pref_player_display_panel_transparency_summary,
                      panelTransparency.times(100).toInt(),
                    ),
                )
              },
              onSliderValueChange = { playerPrefs.panelTransparency.set(it) },
              sliderValue = panelTransparency,
            )
          }
        }
      }
    }
  }

  /**
   * Custom composable for the category header with an Edit button.
   */
  @Composable
  private fun PreferenceCategoryWithEditButton(
    title: String,
    onClick: () -> Unit,
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
      // Apply padding to Row
      verticalAlignment = Alignment.CenterVertically, // Align items vertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f), // Text takes all available space, pushing button to end
      )
      IconButton(onClick = onClick) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit $title",
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }

  /**
   * Custom composable to show a row of icons for the summary.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun PreferenceIconSummary(buttons: List<PlayerButton>) {
    FlowRow(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp), // Increased spacing
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), // <-- FIXED
    ) {
      if (buttons.isEmpty()) {
        Text(
          "None", // TODO: strings
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        buttons.forEach { button ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (button != PlayerButton.VIDEO_TITLE) {
              Icon(
                imageVector = button.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            // --- NEW LOGIC ---
            when (button) {
              PlayerButton.VIDEO_TITLE -> {
                Text(
                  "Video Title", // TODO: strings
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              PlayerButton.CURRENT_CHAPTER -> {
                Text(
                  "1:06 • C1", // TODO: strings
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              else -> {
                // Do nothing, just show the icon
              }
            }
            // --- END NEW LOGIC ---
          }
        }
      }
    }
  }
}
