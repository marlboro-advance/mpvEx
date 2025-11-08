package app.sfsakhawat999.mpvrex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import app.sfsakhawat999.mpvrex.preferences.AppearancePreferences
import app.sfsakhawat999.mpvrex.preferences.PlayerButton
import app.sfsakhawat999.mpvrex.preferences.allPlayerButtons
import app.sfsakhawat999.mpvrex.preferences.getPlayerButtonLabel
import app.sfsakhawat999.mpvrex.presentation.Screen
import app.sfsakhawat999.mpvrex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Serializable
data class ControlLayoutEditorScreen(val region: ControlRegion) : Screen {

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()

    // Get all 3 preferences
    val (prefToEdit, otherPref1, otherPref2) = remember(region) {
      when (region) {
        ControlRegion.TOP_RIGHT -> Triple(
          preferences.topRightControls,
          preferences.bottomRightControls,
          preferences.bottomLeftControls,
        )
        ControlRegion.BOTTOM_RIGHT -> Triple(
          preferences.bottomRightControls,
          preferences.topRightControls,
          preferences.bottomLeftControls,
        )
        ControlRegion.BOTTOM_LEFT -> Triple(
          preferences.bottomLeftControls,
          preferences.topRightControls,
          preferences.bottomRightControls,
        )
      }
    }

    // State for buttons used in *other* regions (these are disabled)
    val disabledButtons by remember {
      mutableStateOf(
        (otherPref1.get().split(',') + otherPref2.get().split(','))
          .filter(String::isNotBlank)
          .mapNotNull { try { PlayerButton.valueOf(it) } catch (e: Exception) { null } }
          .toSet(),
      )
    }

    // State for the *current* selection
    var selectedButtons by remember {
      mutableStateOf(
        prefToEdit.get().split(',')
          .filter(String::isNotBlank)
          .mapNotNull { try { PlayerButton.valueOf(it) } catch (e: Exception) { null } },
      )
    }

    // Automatically save when the user leaves the screen
    DisposableEffect(Unit) {
      onDispose {
        prefToEdit.set(selectedButtons.joinToString(","))
      }
    }

    // --- Dynamic Title based on region ---
    val title = remember(region) {
      when (region) {
        ControlRegion.TOP_RIGHT -> "Edit Top Right Controls" // TODO: strings
        ControlRegion.BOTTOM_RIGHT -> "Edit Bottom Right Controls" // TODO: strings
        ControlRegion.BOTTOM_LEFT -> "Edit Bottom Left Controls" // TODO: strings
      }
    }

    // --- Live Preview Logic REMOVED ---

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
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
        ) {
          // --- Live Preview REMOVED ---

          // --- 1. Selected Controls --- (Was 2)
          PreferenceCategory(title = { Text("Selected") })
          FlowRow(
            modifier = Modifier
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
                badgeColor = MaterialTheme.colorScheme.error,
              )
            }
          }

          // --- 2. Available Controls --- (Was 3)
          PreferenceCategory(title = { Text("Available") })
          FlowRow(
            modifier = Modifier
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
                badgeColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
 * Now with no text, a larger icon, and a badge overlay.
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
    modifier = Modifier
      .size(width = 72.dp, height = 72.dp) // Total size including padding
      .padding(4.dp), // Padding to make room for the badge
  ) {
    Card(
      modifier = Modifier.fillMaxSize(), // Fills the box
      shape = MaterialTheme.shapes.medium,
      elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (enabled) {
          MaterialTheme.colorScheme.surfaceVariant
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        contentColor = if (enabled) {
          MaterialTheme.colorScheme.onSurfaceVariant
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
      ),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .clickable(enabled = enabled, onClick = onClick)
          .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = button.icon,
          contentDescription = label,
          modifier = Modifier.size(32.dp), // Increased icon size
        )
        // Removed the Text label
      }
    }

    // Badge Icon Overlay
    Icon(
      imageVector = badgeIcon,
      contentDescription = null, // Decorative
      tint = badgeColor,
      modifier = Modifier
        .size(20.dp)
        .align(Alignment.BottomEnd)
        .background(MaterialTheme.colorScheme.surface, CircleShape), // White circle background
    )
  }
}
