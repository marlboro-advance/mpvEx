package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.allPlayerButtons
import app.marlboroadvance.mpvex.preferences.getPlayerButtonLabel
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Serializable
data class ControlLayoutEditorScreen(
  val region: ControlRegion,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()

    // Get all 4 preferences as a List
    val prefs =
      remember(region) {
        when (region) {
          ControlRegion.TOP_RIGHT ->
            listOf(
              preferences.topRightControls,
              preferences.topLeftControls,
              preferences.bottomRightControls,
              preferences.bottomLeftControls,
            )
          ControlRegion.BOTTOM_RIGHT ->
            listOf(
              preferences.bottomRightControls,
              preferences.topLeftControls,
              preferences.topRightControls,
              preferences.bottomLeftControls,
            )
          ControlRegion.BOTTOM_LEFT ->
            listOf(
              preferences.bottomLeftControls,
              preferences.topLeftControls,
              preferences.topRightControls,
              preferences.bottomRightControls,
            )
          ControlRegion.PORTRAIT_BOTTOM ->
            listOf(
              preferences.portraitBottomControls,
              // Portrait bottom is independent - no cross-checking with other regions
            )
        }
      }

    // Destructure the list correctly
    val prefToEdit: Preference<String> = prefs[0]

    // State for buttons used in *other* regions (these are disabled)
    // For PORTRAIT_BOTTOM, this will be empty since it's independent
    val disabledButtons by remember {
      mutableStateOf(
        if (region == ControlRegion.PORTRAIT_BOTTOM) {
          emptySet() // Portrait bottom can use any button
        } else {
          val otherPref1: Preference<String> = prefs[1]
          val otherPref2: Preference<String> = prefs[2]
          val otherPref3: Preference<String> = prefs[3]
          (otherPref1.get().split(',') + otherPref2.get().split(',') + otherPref3.get().split(','))
            .filter(String::isNotBlank)
            .mapNotNull {
              try {
                PlayerButton.valueOf(it)
              } catch (_: Exception) {
                null
              }
            }.toSet()
        },
      )
    }

    // State for the *current* selection
    var selectedButtons by remember {
      mutableStateOf(
        prefToEdit
          .get()
          .split(',')
          .filter(String::isNotBlank)
          .mapNotNull {
            try {
              PlayerButton.valueOf(it)
            } catch (_: Exception) {
              null
            }
          },
      )
    }

    // Automatically save when the user leaves the screen
    DisposableEffect(Unit) {
      onDispose {
        prefToEdit.set(selectedButtons.joinToString(","))
      }
    }

    // --- Dynamic Title based on region ---
    val title =
      remember(region) {
        when (region) {
          ControlRegion.TOP_RIGHT -> "Edit Top Right" // TODO: strings
          ControlRegion.BOTTOM_RIGHT -> "Edit Bottom Right" // TODO: strings
          ControlRegion.BOTTOM_LEFT -> "Edit Bottom Left" // TODO: strings
          ControlRegion.PORTRAIT_BOTTOM -> "Edit Portrait Bottom" // TODO: strings
        }
      }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = title) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
              .padding(padding)
              .verticalScroll(rememberScrollState()),
        ) {
          // --- 1. Selected Controls ---
          PreferenceCategory(title = { Text("Selected") })
          FlowRow(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (selectedButtons.isEmpty()) {
              Text(
                text = "Click buttons from the 'Available' list below to add them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
              )
            }
            selectedButtons.forEach { button ->
              PlayerButtonChip(
                button = button,
                enabled = true,
                onClick = {
                  // Remove from selected list
                  selectedButtons = selectedButtons - button
                },
                badgeIcon = Icons.Default.RemoveCircle,
                badgeColor = Color(0xFFEF5350), // Material Design Red 400 - warning red
              )
            }
          }

          // --- 2. Available Controls ---
          PreferenceCategory(title = { Text("Available") })
          FlowRow(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val availableButtons = allPlayerButtons.filter { it !in selectedButtons }
            availableButtons.forEach { button ->
              val isEnabled = button !in disabledButtons
              PlayerButtonChip(
                button = button,
                enabled = isEnabled,
                onClick = {
                  // Add to selected list
                  selectedButtons = selectedButtons + button
                },
                badgeIcon = Icons.Default.AddCircle,
                badgeColor =
                  if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurface
                      .copy(
                        alpha = 0.38f,
                      )
                  },
              )
            }
          }
        }
      }
    }
  }
}

/**
 * A simple "Quick Settings" style chip for a player button.
 * Renders text or icons based on the button type.
 */
@Composable
private fun PlayerButtonChip(
  button: PlayerButton,
  enabled: Boolean,
  onClick: () -> Unit,
  badgeIcon: ImageVector,
  badgeColor: Color,
) {
  val label = getPlayerButtonLabel(button) // Kept for accessibility

  Box(
    modifier = Modifier.padding(4.dp), // Padding for the badge
  ) {
    Card(
      modifier = Modifier, // Let the card wrap its content
      shape = MaterialTheme.shapes.medium,
      elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp),
      colors =
        CardDefaults.cardColors(
          containerColor =
            if (enabled) {
              MaterialTheme.colorScheme.surfaceVariant
            } else {
              MaterialTheme.colorScheme.surfaceVariant
                .copy(
                  alpha = 0.4f,
                )
            },
          contentColor =
            if (enabled) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
                .copy(
                  alpha = 0.4f,
                )
            },
        ),
      onClick = onClick,
      enabled = enabled,
    ) {
      // Use a Box to center content and set size constraints
      Box(
        modifier =
          Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp) // Smaller min size
            .padding(horizontal = 12.dp, vertical = 8.dp),
        // Padding inside the card
        contentAlignment = Alignment.Center,
      ) {
        when (button) {
          PlayerButton.VIDEO_TITLE -> {
            Text(
              text = "Video Title", // TODO: strings
              fontSize = 15.sp, // Increased font size
              textAlign = TextAlign.Center,
              lineHeight = 14.sp,
            )
          }
          PlayerButton.CURRENT_CHAPTER -> {
            // --- UPDATED: Use a Row ---
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = button.icon, // This will now be Bookmarks
                contentDescription = label,
                modifier = Modifier.size(24.dp), // Smaller icon
              )
              Text(
                text = "1:06 • Chapter 1", // TODO: strings
                fontSize = 15.sp, // Increased font size
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 8.dp), // Add padding between icon and text
              )
            }
          }
          else -> {
            // Default: Icon only
            Icon(
              imageVector = button.icon,
              contentDescription = label,
              modifier = Modifier.size(24.dp), // Smaller icon
            )
          }
        }
      }
    }

    // Badge Icon Overlay
    Icon(
      imageVector = badgeIcon,
      contentDescription = null, // Decorative
      tint = badgeColor,
      modifier =
        Modifier
          .size(20.dp)
          .align(Alignment.BottomEnd)
          .background(MaterialTheme.colorScheme.surface, CircleShape),
    )
  }
}
