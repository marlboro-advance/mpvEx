package app.marlboroadvance.mpvex.presentation.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.utils.media.MediaUtils

@Composable
fun PlayLinkDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onPlayLink: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  var linkInputUrl by remember { mutableStateOf("") }
  var isLinkInputUrlValid by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = {
      onDismiss()
      linkInputUrl = ""
      isLinkInputUrlValid = true
    },
    title = { Text("Play Link") },
    text = {
      Column {
        OutlinedTextField(
          value = linkInputUrl,
          onValueChange = {
            linkInputUrl = it
            isLinkInputUrlValid = MediaUtils.isURLValid(it)
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Enter URL") },
          singleLine = true,
          isError = !isLinkInputUrlValid,
          trailingIcon = {
            if (linkInputUrl.isNotBlank()) {
              if (isLinkInputUrlValid) {
                Icon(
                  Icons.Filled.CheckCircle,
                  contentDescription = "Valid URL",
                  tint = MaterialTheme.colorScheme.primary,
                )
              } else {
                Icon(
                  Icons.Filled.Info,
                  contentDescription = "Invalid URL",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          },
        )
        if (!isLinkInputUrlValid) {
          Text(
            "Invalid Protocol",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onPlayLink(linkInputUrl)
          onDismiss()
          linkInputUrl = ""
          isLinkInputUrlValid = true
        },
        enabled = linkInputUrl.isNotBlank() && isLinkInputUrlValid,
      ) {
        Text("Play")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          onDismiss()
          linkInputUrl = ""
          isLinkInputUrlValid = true
        },
      ) {
        Text("Cancel")
      }
    },
    modifier = modifier,
  )
}
