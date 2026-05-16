package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.repository.ai.AiModelInfo

@Composable
fun ModelSearchDialog(
  models: List<AiModelInfo>,
  selectedModelId: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }

  val sortedFiltered = remember(models, searchQuery) {
    models
      .filter { searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true) }
      .sortedWith(compareByDescending<AiModelInfo> { it.isFree }.thenBy { it.displayName })
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      TextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search models...") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
      )
    },
    text = {
      if (sortedFiltered.isEmpty()) {
        Text(
          text = if (searchQuery.isNotBlank()) "No models match \"$searchQuery\"" else "No models available",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      } else {
        Column(modifier = Modifier.heightIn(max = 480.dp)) {
          Text(
            text = "${sortedFiltered.size} model${if (sortedFiltered.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            items(sortedFiltered, key = { it.id }) { model ->
              ModelSearchItem(
                model = model,
                isSelected = model.id == selectedModelId,
                onClick = {
                  onSelect(model.id)
                  onDismiss()
                },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun ModelSearchItem(
  model: AiModelInfo,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
      RadioButton(
        selected = isSelected,
        onClick = null,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = model.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = model.id,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (model.isFree) {
        Spacer(modifier = Modifier.width(6.dp))
        FreeTag()
      }
    }
  }
}

@Composable
fun FreeTag() {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.primary,
  ) {
    Text(
      text = "FREE",
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      color = MaterialTheme.colorScheme.onPrimary,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}
