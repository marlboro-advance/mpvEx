package app.marlboroadvance.mpvex.presentation.components.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
) {
  if (!isOpen) return

  val (ascLabel, descLabel) = getLabelForType(sortType, sortOrderAsc)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        SortTypeSelector(
          sortType = sortType,
          onSortTypeChange = onSortTypeChange,
          types = types,
          icons = icons,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        SortOrderSelector(
          sortOrderAsc = sortOrderAsc,
          onSortOrderChange = onSortOrderChange,
          ascLabel = ascLabel,
          descLabel = descLabel,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {},
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(28.dp),
    modifier = modifier,
  )
}

// -----------------------------------------------------------------------------
// Consolidated internal composables for sort UI (Material You styling)
// -----------------------------------------------------------------------------

@Composable
private fun SortTypeSelector(
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    types.forEachIndexed { index, type ->
      val selected = sortType == type
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = { onSortTypeChange(type) },
          modifier =
            Modifier
              .size(56.dp)
              .background(
                color =
                  if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainer
                  },
                shape = RoundedCornerShape(28.dp),
              ),
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = type,
          style = MaterialTheme.typography.bodySmall,
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
    modifier =
      modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
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
