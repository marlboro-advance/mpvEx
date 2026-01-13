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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
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
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

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
            )
        }
      }

    val prefToEdit: Preference<String> = prefs[0]

    // State for buttons used in *other* regions
    val disabledButtons by remember {
      mutableStateOf(
        if (region == ControlRegion.PORTRAIT_BOTTOM) {
          emptySet()
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

    DisposableEffect(Unit) {
      onDispose {
        prefToEdit.set(selectedButtons.joinToString(","))
      }
    }

    val title =
      remember(region) {
        when (region) {
          ControlRegion.TOP_RIGHT -> "Edit Top Right"
          ControlRegion.BOTTOM_RIGHT -> "Edit Bottom Right"
          ControlRegion.BOTTOM_LEFT -> "Edit Bottom Left"
          ControlRegion.PORTRAIT_BOTTOM -> "Edit Portrait Bottom"
        }
      }

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
      ConfirmDialog(
        title = "Reset to default?",
        subtitle = "This will reset the controls in this region to their default configuration.",
        onConfirm = {
          prefToEdit.delete()
          selectedButtons = prefToEdit
            .get()
            .split(',')
            .filter(String::isNotBlank)
            .mapNotNull {
              try {
                PlayerButton.valueOf(it)
              } catch (_: Exception) {
                null
              }
            }
          showResetDialog = false
        },
        onCancel = {
          showResetDialog = false
        },
      )
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
          actions = {
            IconButton(onClick = { showResetDialog = true }) {
              Icon(Icons.Outlined.Restore, contentDescription = "Reset to default")
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val gridState = rememberLazyGridState()
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
            // Adjust indices because of header (index 0)
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            
            if (fromIndex in selectedButtons.indices && toIndex in selectedButtons.indices) {
                selectedButtons = selectedButtons.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 72.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- 1. Header ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                PreferenceCategory(title = { Text("Selected (Long press to reorder)") })
            }

            // --- 2. Selected Controls (Reorderable) ---
            items(
                count = selectedButtons.size,
                key = { selectedButtons[it] },
                span = { index ->
                    val button = selectedButtons[index]
                    if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                        GridItemSpan(maxLineSpan) 
                    } else {
                        GridItemSpan(1)
                    }
                }
            ) { index ->
                val button = selectedButtons[index]
                ReorderableItem(reorderableState, key = button) {
                   // Wrap in Box to control alignment/filling within the grid cell
                   Box(
                       modifier = Modifier
                           .draggableHandle()
                           .then(
                               if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                                   Modifier.wrapContentWidth(Alignment.Start)
                               } else {
                                   Modifier
                               }
                           )
                   ) {
                        PlayerButtonChip(
                            button = button,
                            enabled = true,
                            onClick = { selectedButtons = selectedButtons - button },
                            badgeIcon = Icons.Default.RemoveCircle,
                            badgeColor = Color(0xFFEF5350),
                        )
                   }
                }
            }

            if (selectedButtons.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                   Text(
                        text = "Click buttons from the 'Available' list below to add them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                   )
                }
            }

            // --- 3. Available Header ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                PreferenceCategory(title = { Text("Available") })
            }

            // --- 4. Available Controls (FlowRow for original look) ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                 FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp), // Adjust padding to match grid content padding visual
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val availableButtons = allPlayerButtons.filter { it !in selectedButtons }
                    availableButtons.forEach { button ->
                        val isEnabled = button !in disabledButtons
                        PlayerButtonChip(
                            button = button,
                            enabled = isEnabled,
                            onClick = { selectedButtons = selectedButtons + button },
                            badgeIcon = Icons.Default.AddCircle,
                            badgeColor = if (isEnabled) MaterialTheme.colorScheme.primary 
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
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
