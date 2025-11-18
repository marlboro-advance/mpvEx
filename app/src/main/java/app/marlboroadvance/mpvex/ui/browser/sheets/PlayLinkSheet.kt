package app.marlboroadvance.mpvex.ui.browser.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayLinkSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onPlayLink: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  var linkInputUrl by remember { mutableStateOf("") }
  var isLinkInputUrlValid by remember { mutableStateOf(true) }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(true) {
    if (isOpen) {
      linkInputUrl = ""
      isLinkInputUrlValid = true
    }
  }

  val handleDismiss = {
    onDismiss()
  }

  val handleConfirm = {
    val url = linkInputUrl.trim()
    if (url.isNotBlank() && MediaUtils.isURLValid(url)) {
      // Optimistically record in history so it shows up immediately
      coroutineScope.launch {
        val uri = url.toUri()
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { url } ?: url
        RecentlyPlayedOps.addRecentlyPlayed(
          filePath = url,
          fileName = name,
          launchSource = "play_link",
        )
      }
      onPlayLink(url)
      onDismiss()
    }
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

  ModalBottomSheet(
    onDismissRequest = handleDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Title
      Text(
        text = "Play Link",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      // URL Input
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = linkInputUrl,
          onValueChange = { newValue ->
            linkInputUrl = newValue
            isLinkInputUrlValid = newValue.isBlank() || MediaUtils.isURLValid(newValue)
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Enter URL") },
          placeholder = { Text("https://example.com/video.mp4") },
          singleLine = true,
          isError = linkInputUrl.isNotBlank() && !isLinkInputUrlValid,
          trailingIcon = {
            if (linkInputUrl.isNotBlank()) {
              ValidationIcon(isValid = isLinkInputUrlValid)
            }
          },
        )

        if (linkInputUrl.isNotBlank() && !isLinkInputUrlValid) {
          Text(
            text = "Invalid URL protocol",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      // Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = handleDismiss) {
          Text(
            text = "Cancel",
            fontWeight = FontWeight.Medium,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = handleConfirm,
          enabled = linkInputUrl.isNotBlank() && isLinkInputUrlValid,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
          Text(
            text = "Play",
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun ValidationIcon(isValid: Boolean) {
  if (isValid) {
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
