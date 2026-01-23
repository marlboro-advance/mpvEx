package app.marlboroadvance.mpvex.ui.preferences

// import androidx.compose.material.icons.outlined.VideoLabel // No longer needed here
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SeekbarStyle
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.controls.components.SeekbarPreview
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import app.marlboroadvance.mpvex.ui.preferences.components.PlayerButtonChip
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

    val topRightButtons = remember(topRState) {
      appearancePrefs.parseButtons(topRState, mutableSetOf())
    }

    val bottomRightButtons = remember(bottomRState) {
      appearancePrefs.parseButtons(bottomRState, mutableSetOf())
    }

    val bottomLeftButtons = remember(bottomLState) {
      appearancePrefs.parseButtons(bottomLState, mutableSetOf())
    }

    val portraitBottomButtons = remember(portraitBottomState) {
      appearancePrefs.parseButtons(portraitBottomState, mutableSetOf())
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_layout_title),
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
          // Landscape Controls Section
          item {
            PreferenceSectionHeader(title = "Landscape Controls")
          }
          
          item {
            PreferenceCard {
              PreferenceCategoryWithEditButton(
                title = stringResource(id = R.string.pref_layout_top_right_controls),
                onClick = {
                  backstack.add(ControlLayoutEditorScreen(ControlRegion.TOP_RIGHT))
                },
              )
              PreferenceIconSummary(buttons = topRightButtons)
              
              PreferenceDivider()
              
              PreferenceCategoryWithEditButton(
                title = stringResource(id = R.string.pref_layout_bottom_right_controls),
                onClick = {
                  backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_RIGHT))
                },
              )
              PreferenceIconSummary(buttons = bottomRightButtons)
              
              PreferenceDivider()
              
              PreferenceCategoryWithEditButton(
                title = stringResource(id = R.string.pref_layout_bottom_left_controls),
                onClick = {
                  backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_LEFT))
                },
              )
              PreferenceIconSummary(buttons = bottomLeftButtons)
            }
          }
          
          // Portrait Controls Section
          item {
            PreferenceSectionHeader(title = "Portrait Controls")
          }

          item {
            PreferenceCard {

            
              PreferenceCategoryWithEditButton(
                title = stringResource(id = R.string.pref_layout_portrait_bottom_controls),
                onClick = {
                  backstack.add(ControlLayoutEditorScreen(ControlRegion.PORTRAIT_BOTTOM))
                },
              )
              PreferenceIconSummary(buttons = portraitBottomButtons)
            }
          }
          
          // Seekbar Section
          item {
            PreferenceSectionHeader(title = "Seekbar Style")
          }

          item {
            val seekbarStyle by appearancePrefs.seekbarStyle.collectAsState()
            
            PreferenceCard {
              SeekbarStyle.entries.forEachIndexed { index, style ->
                ListItem(
                  headlineContent = {
                    Text(text = style.name)
                  },
                  supportingContent = {
                    SeekbarPreview(
                      style = style,
                      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                  },
                  trailingContent = {
                    RadioButton(
                      selected = seekbarStyle == style,
                      onClick = null
                    )
                  },
                  colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  ),
                  modifier = Modifier
                    .clickable { appearancePrefs.seekbarStyle.set(style) }
                )
                if (index < SeekbarStyle.entries.size - 1) {
                  PreferenceDivider()
                }
              }
            }
          }
          
          // Appearance Section
          item {
            PreferenceSectionHeader(title = "Appearance")
          }
          
          item {
            val hidePlayerButtonsBackground by appearancePrefs.hidePlayerButtonsBackground.collectAsState()
            val playerTimeToDisappear by playerPrefs.playerTimeToDisappear.collectAsState()
            val predefinedTimeValues = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000)
            val isCustomTimeValue = !predefinedTimeValues.contains(playerTimeToDisappear)
            
            var showCustomTimeDialog by remember { mutableStateOf(false) }
            var customTimeValue by remember { mutableStateOf("") }
            
            PreferenceCard {
              SwitchPreference(
                value = hidePlayerButtonsBackground,
                onValueChange = { appearancePrefs.hidePlayerButtonsBackground.set(it) },
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
              
              PreferenceDivider()
              
              ListPreference(
                value = if (isCustomTimeValue) -1 else playerTimeToDisappear,
                onValueChange = { newValue ->
                  if (newValue == -1) {
                    customTimeValue = playerTimeToDisappear.toString()
                    showCustomTimeDialog = true
                  } else {
                    playerPrefs.playerTimeToDisappear.set(newValue)
                  }
                },
                values = predefinedTimeValues + listOf(-1),
                valueToText = { value ->
                  if (value == -1) {
                    AnnotatedString("Custom")
                  } else {
                    AnnotatedString("$value ms")
                  }
                },
                title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
                summary = {
                  Text(
                    text = if (isCustomTimeValue) {
                      "Custom ($playerTimeToDisappear ms)"
                    } else {
                      "$playerTimeToDisappear ms"
                    },
                  )
                },
              )
            }
            
            if (showCustomTimeDialog) {
              AlertDialog(
                onDismissRequest = { showCustomTimeDialog = false },
                title = { Text(text = stringResource(R.string.pref_player_display_hide_player_control_time)) },
                text = {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .verticalScroll(rememberScrollState()),
                  ) {
                    Text(
                      text = "Enter custom hide time in milliseconds",
                      modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                      value = customTimeValue,
                      onValueChange = { customTimeValue = it },
                      label = { Text("Milliseconds") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true,
                    )
                  }
                },
                confirmButton = {
                  TextButton(
                    onClick = {
                      val value = customTimeValue.toIntOrNull()
                      if (value != null && value in 100..1000000000000) {
                        playerPrefs.playerTimeToDisappear.set(value)
                        showCustomTimeDialog = false
                      }
                    },
                  ) {
                    Text(stringResource(R.string.generic_ok))
                  }
                },
                dismissButton = {
                  TextButton(onClick = { showCustomTimeDialog = false }) {
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
          .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      // Apply padding to Row - minimal padding for tighter appearance
      verticalAlignment = Alignment.CenterVertically, // Align items vertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f), // Text takes all available space, pushing button to end
      )
      IconButton(onClick = onClick) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit $title",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
      horizontalArrangement = Arrangement.spacedBy(8.dp), // Increased spacing
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
      if (buttons.isEmpty()) {
        Text(
          "None", // TODO: strings
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      } else {
        buttons.forEach { button ->
          // Use the chip in "preview mode" (no badge, enabled=true but no onClick)
          PlayerButtonChip(
            button = button,
            enabled = true,
            onClick = null, 
            badgeIcon = null,
            badgeColor = null
          )
        }
      }
    }
  }
}
