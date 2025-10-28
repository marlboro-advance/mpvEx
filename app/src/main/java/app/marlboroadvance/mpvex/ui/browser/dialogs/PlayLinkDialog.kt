package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.utils.media.MediaUtils

/**
 * Dialog for playing video from URL/link
 *
 * @param isOpen Whether the dialog is open
 * @param onDismiss Callback when dialog is dismissed
 * @param onPlayLink Callback when user confirms to play the link
 */
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

  // Reset state when dialog opens
  LaunchedEffect(isOpen) {
    if (isOpen) {
      linkInputUrl = ""
      isLinkInputUrlValid = true
    }
  }

  val handleDismiss = {
    onDismiss()
  }

  val handleConfirm = {
    onPlayLink(linkInputUrl)
    onDismiss()
  }

  AlertDialog(
    onDismissRequest = handleDismiss,
    title = { Text(stringResource(R.string.play_link)) },
    text = {
      Column {
        OutlinedTextField(
          value = linkInputUrl,
          onValueChange = { newValue ->
            linkInputUrl = newValue
            isLinkInputUrlValid = newValue.isBlank() || MediaUtils.isURLValid(newValue)
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.enter_url)) },
          singleLine = true,
          isError = linkInputUrl.isNotBlank() && !isLinkInputUrlValid,
          trailingIcon = {
            if (linkInputUrl.isNotBlank()) {
              ValidationIcon(isValid = isLinkInputUrlValid)
            }
          },
        )

        if (linkInputUrl.isNotBlank() && !isLinkInputUrlValid) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            stringResource(R.string.invalid_url_protocol),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = handleConfirm,
        enabled = linkInputUrl.isNotBlank() && isLinkInputUrlValid,
      ) {
        Text(stringResource(R.string.play))
      }
    },
    dismissButton = {
      TextButton(onClick = handleDismiss) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
    modifier = modifier,
  )
}

@Composable
private fun ValidationIcon(isValid: Boolean) {
  if (isValid) {
    Icon(
      Icons.Filled.CheckCircle,
      contentDescription = stringResource(R.string.valid_url),
      tint = MaterialTheme.colorScheme.primary,
    )
  } else {
    Icon(
      Icons.Filled.Info,
      contentDescription = stringResource(R.string.invalid_url),
      tint = MaterialTheme.colorScheme.error,
    )
  }
}
