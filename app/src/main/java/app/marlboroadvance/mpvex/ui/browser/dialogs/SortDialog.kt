package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  title: String,
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  getLabelForType: (String, Boolean) -> Pair<String, String>,
  modifier: Modifier = Modifier,
  visibilityToggles: List<VisibilityToggle> = emptyList(),
  viewModeSelector: ViewModeSelector? = null,
  layoutModeSelector:  ViewModeSelector? = null,
  folderGridColumnSelector: GridColumnSelector? = null,
  videoGridColumnSelector: GridColumnSelector? = null,
  showSortOptions: Boolean = true,
  enableViewModeOptions: Boolean = true,
  enableLayoutModeOptions: Boolean = true,
) {
  if (!isOpen) return

  val (ascLabel, descLabel) = getLabelForType(sortType, sortOrderAsc)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (showSortOptions) {
          SortTypeSelector(
            sortType = sortType,
            onSortTypeChange = onSortTypeChange,
            types = types,
            icons = icons,
            modifier = Modifier.fillMaxWidth(),
          )

          SortOrderSelector(
            sortOrderAsc = sortOrderAsc,
            onSortOrderChange = onSortOrderChange,
            ascLabel = ascLabel,
            descLabel = descLabel,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        if (viewModeSelector != null || layoutModeSelector != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
          ) {
            if (viewModeSelector != null) {
              ViewModeSelectorComponent(
                viewModeSelector = viewModeSelector,
                enabled = enableViewModeOptions,
                modifier = Modifier.weight(1f),
              )
            }

            if (layoutModeSelector != null) {
              ViewModeSelectorComponent(
                viewModeSelector = layoutModeSelector,
                enabled = enableLayoutModeOptions,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        GridColumnsSection(
          folderGridColumnSelector = folderGridColumnSelector,
          videoGridColumnSelector = videoGridColumnSelector,
        )

        if (visibilityToggles.isNotEmpty()) {
          VisibilityTogglesSection(
            toggles = visibilityToggles,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {},
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier,
  )
}

data class VisibilityToggle(
  val label: String,
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)

data class ViewModeSelector(
  val label: String,
  val firstOptionLabel: String,
  val secondOptionLabel: String,
  val firstOptionIcon: ImageVector,
  val secondOptionIcon: ImageVector,
  val isFirstOptionSelected: Boolean,
  val onViewModeChange: (Boolean) -> Unit,
)

data class GridColumnSelector(
  val label: String,
  val currentValue: Int,
  val onValueChange: (Int) -> Unit,
  val valueRange: ClosedFloatingPointRange<Float> = 1f..4f,
  val steps: Int = 2,
)

// -----------------------------------------------------------------------------
// Consolidated internal composable for sort UI (Material You styling)
// -----------------------------------------------------------------------------

@Composable
private fun SortTypeSelector(
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = "Sort by",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      types.forEachIndexed { index, type ->
        val selected = sortType == type
        val shape = RoundedCornerShape(16.dp)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier
                .size(56.dp)
                .clip(shape)
                .background(
                  color =
                    if (selected) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                )
                .clickable(
                  onClick = { onSortTypeChange(type) },
                  interactionSource = remember { MutableInteractionSource() },
                  indication = ripple(bounded = true),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icons[index],
              contentDescription = type,
              tint =
                if (selected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.size(24.dp),
            )
          }

          Text(
            text = type,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color =
              if (selected) {
                MaterialTheme.colorScheme.onSurface
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    }
  }
}

@Composable
private fun SortOrderSelector(
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  ascLabel: String,
  descLabel: String,
  modifier: Modifier = Modifier,
) {
  val options = listOf(ascLabel, descLabel)
  val selectedIndex = if (sortOrderAsc) 0 else 1

  SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth(),
  ) {
    options.forEachIndexed { index, label ->
      SegmentedButton(
        shape =
          SegmentedButtonDefaults.itemShape(
            index = index,
            count = options.size,
          ),
        onClick = { onSortOrderChange(index == 0) },
        selected = index == selectedIndex,
        icon = {
          Icon(
            if (index == 0) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        },
      ) {
        Text(label)
      }
    }
  }
}

@Composable
private fun ViewModeSelectorComponent(
  viewModeSelector: ViewModeSelector,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val options = listOf(viewModeSelector.firstOptionLabel, viewModeSelector.secondOptionLabel)
  val icons = listOf(viewModeSelector.firstOptionIcon, viewModeSelector.secondOptionIcon)
  val selectedIndex = if (viewModeSelector.isFirstOptionSelected) 0 else 1

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = viewModeSelector.label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      },
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      options.forEachIndexed { index, label ->
        val selected = index == selectedIndex
        val shape = RoundedCornerShape(12.dp)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .clip(shape)
            .clickable(enabled = enabled) { 
              if (enabled) {
                viewModeSelector.onViewModeChange(index == 0)
              }
            }
            .padding(8.dp),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(shape)
              .background(
                color = if (selected && enabled) {
                  MaterialTheme.colorScheme.primaryContainer
                } else if (enabled) {
                  MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f)
                },
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icons[index],
              contentDescription = label,
              tint = if (selected && enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
              },
              modifier = Modifier.size(20.dp),
            )
          }

          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected && enabled) FontWeight.Medium else FontWeight.Normal,
            color = if (selected && enabled) {
              MaterialTheme.colorScheme.primary
            } else if (enabled) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilityTogglesSection(
  toggles: List<VisibilityToggle>,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Header row with Fields text and dropdown button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Fields",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      IconButton(
        onClick = { expanded = !expanded },
      ) {
        Icon(
          imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ArrowDropDown,
          contentDescription = if (expanded) "Collapse" else "Expand",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Expandable filter chips section
    if (expanded) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        toggles.forEach { toggle ->
          FilterChip(
            selected = toggle.checked,
            onClick = { toggle.onCheckedChange(!toggle.checked) },
            label = {
              Text(
                text = toggle.label,
                style = MaterialTheme.typography.labelLarge,
              )
            },
            leadingIcon =
              if (toggle.checked) {
                {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                  )
                }
              } else {
                null
              },
          )
        }
      }
    }
  }
}

@Composable
private fun GridColumnSelectorComponent(
  gridColumnSelector: GridColumnSelector,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = gridColumnSelector.label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Slider(
      value = gridColumnSelector.currentValue.toFloat(),
      onValueChange = { gridColumnSelector.onValueChange(it.toInt()) },
      valueRange = gridColumnSelector.valueRange,
      steps = gridColumnSelector.steps,
      modifier = Modifier.fillMaxWidth(),
    )

    Text(
      text = "${gridColumnSelector.currentValue} columns",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
  }
}

@Composable
private fun GridColumnsSection(
  folderGridColumnSelector: GridColumnSelector?,
  videoGridColumnSelector: GridColumnSelector?,
  modifier: Modifier = Modifier,
) {
  if (folderGridColumnSelector == null && videoGridColumnSelector == null) return

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = "Grid Columns",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (folderGridColumnSelector != null) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Folder Grid",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = folderGridColumnSelector.currentValue.toFloat(),
            onValueChange = { folderGridColumnSelector.onValueChange(it.toInt()) },
            valueRange = folderGridColumnSelector.valueRange,
            steps = folderGridColumnSelector.steps,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = "${folderGridColumnSelector.currentValue} columns",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }

      if (videoGridColumnSelector != null) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Video Grid",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Slider(
            value = videoGridColumnSelector.currentValue.toFloat(),
            onValueChange = { videoGridColumnSelector.onValueChange(it.toInt()) },
            valueRange = videoGridColumnSelector.valueRange,
            steps = videoGridColumnSelector.steps,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = "${videoGridColumnSelector.currentValue} columns",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }
    }
  }
}
