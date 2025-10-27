package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun RenameDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  currentName: String,
  itemType: String,
  extension: String? = null,
) {
  if (!isOpen) return

  val baseName = remember(currentName) { mutableStateOf(currentName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename $itemType") },
    text = {
      Column {
        OutlinedTextField(
          value = baseName.value,
          onValueChange = { baseName.value = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Name") },
          singleLine = true,
        )
        if (extension != null) {
          Text(extension)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(baseName.value + (extension ?: ""))
          onDismiss()
        },
      ) { Text("Rename") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
