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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SortTypeSelector(
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier
      .fillMaxWidth()
      .padding(vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    types.forEachIndexed { index, type ->
      val selected = sortType == type
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        IconButton(
          onClick = { onSortTypeChange(type) },
          modifier = Modifier
            .size(56.dp)
            .background(
              color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceContainer
              },
              shape = RoundedCornerShape(28.dp),
            ),
        ) {
          Icon(
            imageVector = icons[index],
            contentDescription = type,
            tint = if (selected) {
              MaterialTheme.colorScheme.primaryContainer
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
          color = if (selected) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    }
  }
}
