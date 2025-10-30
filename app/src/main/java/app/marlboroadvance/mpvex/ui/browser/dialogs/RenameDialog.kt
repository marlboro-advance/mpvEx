package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
  val isError = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }

  // Auto-focus text field
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  fun validateAndConfirm() {
    when {
      baseName.value.isBlank() -> {
        isError.value = true
        errorMessage.value = "Name cannot be empty"
      }

      baseName.value.contains("/") || baseName.value.contains("\\") -> {
        isError.value = true
        errorMessage.value = "Name cannot contain / or \\"
      }

      else -> {
        onConfirm(baseName.value + (extension ?: ""))
        onDismiss()
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Rename $itemType",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // New name input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = baseName.value,
            onValueChange = {
              baseName.value = it
              isError.value = false
              errorMessage.value = ""
            },
            modifier =
              Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text("New name") },
            singleLine = true,
            isError = isError.value,
            supportingText =
              if (isError.value) {
                { Text(errorMessage.value) }
              } else {
                null
              },
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
              ),
            keyboardOptions =
              KeyboardOptions(
                imeAction = ImeAction.Done,
              ),
            keyboardActions =
              KeyboardActions(
                onDone = { validateAndConfirm() },
              ),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { validateAndConfirm() },
        enabled = baseName.value.isNotBlank(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
          ),
      ) {
        Text(
          text = "Rename",
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
  )
}
