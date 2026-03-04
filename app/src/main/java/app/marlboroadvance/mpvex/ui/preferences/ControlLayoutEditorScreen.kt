package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.allPlayerButtons
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.preferences.components.PlayerButtonChip
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
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
            val fromKey = from.key as? PlayerButton
            val toKey = to.key as? PlayerButton
            
            val fromIndex = selectedButtons.indexOf(fromKey)
            val toIndex = selectedButtons.indexOf(toKey)
            
            // Only update if indices are valid to prevent unnecessary state changes
            if (fromIndex in selectedButtons.indices && toIndex in selectedButtons.indices && fromIndex != toIndex) {
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
            // --- 1. Header & Active Selected Zone ---
            item(span = { GridItemSpan(maxLineSpan) }) {
              androidx.compose.material3.Text(
                      text = "Long press to reorder items. Tap the '-' icon to remove them.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                  )
            }

            // --- 2. Selected Controls (Reorderable) ---
            if (selectedButtons.isEmpty()) {
                 item(span = { GridItemSpan(maxLineSpan) }) {
                     androidx.compose.material3.Surface(
                         modifier = Modifier
                             .fillMaxWidth()
                             .height(120.dp),
                         shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                         color = MaterialTheme.colorScheme.surfaceContainerLowest,
                         border = BorderStroke(
                             1.dp,
                             MaterialTheme.colorScheme.outlineVariant
                         ),
                     ) {
                         androidx.compose.foundation.layout.Column(
                             modifier = Modifier.fillMaxSize(),
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.Center
                         ) {
                             Icon(
                                 imageVector = Icons.Default.AddCircle,
                                 contentDescription = null,
                                 modifier = Modifier
                                     .size(32.dp)
                                     .padding(bottom = 8.dp),
                                 tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                             )
                             androidx.compose.material3.Text(
                                  text = "Drop zone is empty",
                                  style = MaterialTheme.typography.bodyMedium,
                                  fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                             )
                             androidx.compose.material3.Text(
                                  text = "Tap buttons from the 'Available Palette' below",
                                  style = MaterialTheme.typography.labelSmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                             )
                         }
                     }
                 }
            } else {
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
                    ReorderableItem(reorderableState, key = button) { isDragging ->
                       val elevation by animateFloatAsState(
                           targetValue = if (isDragging) 8f else 0f,
                           label = "drag_elevation"
                       )
                       
                       // Wrap in Box to control alignment/filling within the grid cell
                       androidx.compose.material3.Surface(
                           modifier = Modifier
                               .draggableHandle()
                               .then(
                                   if (button == PlayerButton.CURRENT_CHAPTER || button == PlayerButton.VIDEO_TITLE) {
                                       Modifier.wrapContentWidth(Alignment.Start)
                                   } else {
                                       Modifier
                                   }
                               ),
                           shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), // Match chip border radius
                           shadowElevation = elevation.dp,
                           color = Color.Transparent
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
            }

            // --- 3. SPaceing ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(40.dp)) 
            }

            // --- 4. Available Controls  ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                 androidx.compose.material3.Card(
                     modifier = Modifier.fillMaxWidth(),
                     shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                     colors = androidx.compose.material3.CardDefaults.cardColors(
                         containerColor = MaterialTheme.colorScheme.surfaceContainer
                     ),
                 ) {
                     FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        
                        if (availableButtons.isEmpty()) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Text(
                                    text = "All available buttons are in use.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                 }
            }
            
            // --- 5. Icons Legend ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                IconsLegend()
                Spacer(Modifier.height(16.dp))
            }
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IconsLegend() {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Icons Legend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "What is each icon for?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // FlowRow grid of icons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // We use allPlayerButtons to avoid showing NONE and defaults like Back/Title which are static
                allPlayerButtons.forEach { button ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        val modifier = if (button == PlayerButton.VERTICAL_FLIP) {
                            Modifier.rotate(90f)
                        } else {
                            Modifier
                        }

                        Icon(
                            imageVector = button.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .then(modifier)
                        )
                        
                        Text(
                            text = app.marlboroadvance.mpvex.preferences.getPlayerButtonLabel(button),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}


