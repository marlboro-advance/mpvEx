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
import app.marlboroadvance.mpvex.ui.preferences.components.PlayerButtonChip
import app.marlboroadvance.mpvex.ui.preferences.components.PlayerLayoutPreview
import app.marlboroadvance.mpvex.ui.preferences.components.ControlRegionReference
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
            val fromIndex = from.index - 2
            val toIndex = to.index - 2
            
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
            // --- 0. Mock Device Preview ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                   // We need to parse all buttons to show the full context
                   // We have 'prefs' which contains the preferences in a specific order based on region
                   // We also have 'selectedButtons' which is the LIVE state of the current region
                   
                   val isLandscape = region != ControlRegion.PORTRAIT_BOTTOM
                   
                   // Helper to parse comma-separated string to List<PlayerButton>
                   fun parse(p: Preference<String>): List<PlayerButton> {
                       return p.get().split(',')
                           .filter { it.isNotBlank() }
                           .mapNotNull { try { PlayerButton.valueOf(it) } catch(e: Exception) { null } }
                   }

                   // Determine which list corresponds to which region based on the 'prefs' mapping in line 79+
                   // This is a bit tricky because 'prefs' order changes. 
                   // Simpler approach: Just re-fetch the specific preferences from 'preferences' object since we have it injected.
                   // Actually 'preferences' variable (line 74) has them all!
                   
                   val tr = if (region == ControlRegion.TOP_RIGHT) selectedButtons else parse(preferences.topRightControls)
                   val br = if (region == ControlRegion.BOTTOM_RIGHT) selectedButtons else parse(preferences.bottomRightControls)
                   val bl = if (region == ControlRegion.BOTTOM_LEFT) selectedButtons else parse(preferences.bottomLeftControls)
                   val pb = if (region == ControlRegion.PORTRAIT_BOTTOM) selectedButtons else parse(preferences.portraitBottomControls)
                   
                   val highlight = when(region) {
                       ControlRegion.TOP_RIGHT -> ControlRegionReference.TOP_RIGHT
                       ControlRegion.BOTTOM_RIGHT -> ControlRegionReference.BOTTOM_RIGHT
                       ControlRegion.BOTTOM_LEFT -> ControlRegionReference.BOTTOM_LEFT
                       ControlRegion.PORTRAIT_BOTTOM -> ControlRegionReference.PORTRAIT_BOTTOM
                   }

                   PlayerLayoutPreview(
                       topRightButtons = tr,
                       bottomRightButtons = br,
                       bottomLeftButtons = bl,
                       portraitBottomButtons = pb,
                       isLandscape = isLandscape,
                       highlightRegion = highlight,
                       modifier = Modifier.fillMaxWidth(if(isLandscape) 0.95f else 0.7f)
                   )
                }
            }

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


