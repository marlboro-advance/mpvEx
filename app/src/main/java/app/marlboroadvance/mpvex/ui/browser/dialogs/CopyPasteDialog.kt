package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.utils.media.CopyPasteOps

@Composable
fun FileOperationProgressDialog(
  isOpen: Boolean,
  operationType: CopyPasteOps.OperationType,
  progress: CopyPasteOps.FileOperationProgress,
  onCancel: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  val operationName =
    when (operationType) {
      is CopyPasteOps.OperationType.Copy -> "Copying"
      is CopyPasteOps.OperationType.Move -> "Moving"
    }

  val isOperationComplete = progress.isComplete || progress.isCancelled || progress.error != null

  AlertDialog(
    onDismissRequest = {
      if (isOperationComplete) {
        onDismiss()
      }
    },
    title = {
      Text(
        text = "$operationName files",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Status Messages
        when {
          progress.error != null -> {
            StatusCard(
              message = progress.error,
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
          progress.isComplete -> {
            StatusCard(
              message = "Operation completed successfully!",
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          progress.isCancelled -> {
            StatusCard(
              message = "Operation cancelled",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }

        // Progress Section (only show during operation)
        if (!isOperationComplete) {
          Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Current File Info
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "File ${progress.currentFileIndex} of ${progress.totalFiles}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = progress.currentFile,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            // Current File Progress
            ProgressSection(
              label = "Current file",
              progress = progress.currentFileProgress,
            )

            // Overall Progress
            ProgressSection(
              label = "Overall progress",
              progress = progress.overallProgress,
            )

            // Size Information
            Text(
              text = "${CopyPasteOps.formatBytes(
                progress.bytesProcessed,
              )} of ${CopyPasteOps.formatBytes(progress.totalBytes)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        // Summary (when complete)
        if (isOperationComplete) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryRow(
              label = "Files processed",
              value = "${progress.currentFileIndex} / ${progress.totalFiles}",
            )
            SummaryRow(
              label = "Total size",
              value = CopyPasteOps.formatBytes(progress.totalBytes),
            )
          }
        }
      }
    },
    confirmButton = {
      if (isOperationComplete) {
        Button(
          onClick = onDismiss,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
          Text("Done")
        }
      } else {
        TextButton(
          onClick = onCancel,
        ) {
          Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = "Cancel",
            modifier = Modifier.padding(end = 4.dp),
          )
          Text("Cancel")
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    modifier = modifier,
  )
}

@Composable
private fun StatusCard(
  message: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor = containerColor,
      ),
    shape = MaterialTheme.shapes.medium,
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      color = contentColor,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(16.dp),
    )
  }
}

@Composable
private fun ProgressSection(
  label: String,
  progress: Float,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "${(progress * 100).toInt()}%",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    LinearProgressIndicator(
      progress = { progress },
      modifier =
        Modifier
          .fillMaxWidth()
          .height(8.dp),
      trackColor = MaterialTheme.colorScheme.surfaceVariant,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
private fun SummaryRow(
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
