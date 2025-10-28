package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository

@Composable
fun IndexingProgressDialog(
  progress: FileSystemVideoRepository.IndexingProgress,
  modifier: Modifier = Modifier,
) {
  if (!progress.isIndexing) return

  AlertDialog(
    onDismissRequest = { /* Cannot dismiss during indexing */ },
    icon = {
      Icon(
        imageVector = Icons.Default.Movie,
        contentDescription = "Indexing Videos",
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text =
          when (progress.phase) {
            FileSystemVideoRepository.IndexingPhase.SCANNING_FILES -> "Scanning for videos..."
            FileSystemVideoRepository.IndexingPhase.EXTRACTING_METADATA -> "Indexing videos..."
            FileSystemVideoRepository.IndexingPhase.SAVING_TO_DB -> "Saving to database..."
            FileSystemVideoRepository.IndexingPhase.COMPLETE -> "Complete!"
            else -> "Loading..."
          },
        style = MaterialTheme.typography.headlineSmall,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Phase description
        Text(
          text =
            when (progress.phase) {
              FileSystemVideoRepository.IndexingPhase.SCANNING_FILES ->
                "Discovering video files on your device..."

              FileSystemVideoRepository.IndexingPhase.EXTRACTING_METADATA ->
                "This is a one-time process. Extracting video information..."

              FileSystemVideoRepository.IndexingPhase.SAVING_TO_DB ->
                "Finalizing database..."

              FileSystemVideoRepository.IndexingPhase.COMPLETE ->
                "Index created successfully!"

              else -> ""
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Progress indicator
        when (progress.phase) {
          FileSystemVideoRepository.IndexingPhase.SCANNING_FILES,
          FileSystemVideoRepository.IndexingPhase.SAVING_TO_DB,
          -> {
            // Indeterminate progress for scanning and saving
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          FileSystemVideoRepository.IndexingPhase.EXTRACTING_METADATA -> {
            // Determinate progress for metadata extraction
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              LinearProgressIndicator(
                progress = {
                  if (progress.totalFiles > 0) {
                    progress.processedFiles.toFloat() / progress.totalFiles
                  } else {
                    0f
                  }
                },
                modifier = Modifier.fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "${progress.processedFiles} / ${progress.totalFiles}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (progress.currentFile.isNotEmpty()) {
                  Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                      Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                  )
                }
              }
            }
          }

          else -> {}
        }

        // Helpful tip
        if (progress.phase == FileSystemVideoRepository.IndexingPhase.EXTRACTING_METADATA) {
          Text(
            text = "💡 Tip: This only happens once. Next time will be instant!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
      }
    },
    confirmButton = {},
    modifier = modifier,
  )
}
