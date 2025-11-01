package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteConfirmationDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  itemType: String,
  itemCount: Int,
) {
  if (!isOpen) return

  val itemText = if (itemCount == 1) itemType else "${itemType}s"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Delete $itemCount $itemText?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Text(
            text = "This action cannot be undone. The selected item${if (itemCount == 1) "" else "s"} will be permanently deleted.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(
          text = "Delete",
          fontWeight = FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Cancel", fontWeight = FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}
