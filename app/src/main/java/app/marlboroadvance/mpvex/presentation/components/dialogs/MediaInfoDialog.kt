package app.marlboroadvance.mpvex.presentation.components.dialogs

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.marlboroadvance.mpvex.utils.media.AudioStreamInfo
import app.marlboroadvance.mpvex.utils.media.GeneralInfo
import app.marlboroadvance.mpvex.utils.media.MediaInfoData
import app.marlboroadvance.mpvex.utils.media.TextStreamInfo
import app.marlboroadvance.mpvex.utils.media.VideoStreamInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  fileName: String,
  mediaInfo: MediaInfoData?,
  isLoading: Boolean,
  error: String?,
) {
  if (!isOpen) return

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier =
        Modifier
          .fillMaxWidth(0.95f)
          .padding(16.dp),
      colors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "Media Info",
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(
              Icons.Filled.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File name
        Text(
          fileName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
          isLoading -> {
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              CircularProgressIndicator()
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                "Analyzing media...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          error != null -> {
            Text(
              "Error: $error",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }

          mediaInfo != null -> {
            MediaInfoContent(mediaInfo = mediaInfo)
          }
        }

        // Footer
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close")
          }
        }
      }
    }
  }
}

@Composable
private fun MediaInfoContent(mediaInfo: MediaInfoData) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(400.dp)
        .verticalScroll(rememberScrollState()),
  ) {
    // General Information
    InfoSection(title = "General") {
      GeneralInfoContent(mediaInfo.general)
    }

    // Video Streams
    mediaInfo.videoStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      InfoSection(title = "Video Stream ${index + 1}") {
        VideoStreamContent(stream)
      }
    }

    // Audio Streams
    mediaInfo.audioStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      InfoSection(title = "Audio Stream ${index + 1}") {
        AudioStreamContent(stream)
      }
    }

    // Text/Subtitle Streams
    mediaInfo.textStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      InfoSection(title = "Subtitle Stream ${index + 1}") {
        TextStreamContent(stream)
      }
    }
  }
}

@Composable
private fun InfoSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    content()
  }
}

@Composable
private fun GeneralInfoContent(general: GeneralInfo) {
  Column {
    InfoRow("Format", general.format)
    if (general.formatProfile.isNotEmpty()) {
      InfoRow("Format Profile", general.formatProfile)
    }
    if (general.codecId.isNotEmpty()) {
      InfoRow("Codec ID", general.codecId)
    }
    InfoRow("File Size", general.fileSize)
    InfoRow("Duration", general.duration)
    if (general.overallBitRate.isNotEmpty()) {
      InfoRow("Overall Bit Rate", general.overallBitRate)
    }
    if (general.writingApplication.isNotEmpty()) {
      InfoRow("Writing Application", general.writingApplication)
    }
  }
}

@Composable
private fun VideoStreamContent(stream: VideoStreamInfo) {
  Column {
    InfoRow("Format", stream.format)
    if (stream.formatProfile.isNotEmpty()) {
      InfoRow("Format Profile", stream.formatProfile)
    }
    if (stream.codecId.isNotEmpty()) {
      InfoRow("Codec ID", stream.codecId)
    }
    InfoRow("Resolution", "${stream.width} × ${stream.height}")
    if (stream.displayAspectRatio.isNotEmpty()) {
      InfoRow("Aspect Ratio", stream.displayAspectRatio)
    }
    InfoRow("Frame Rate", "${stream.frameRate} fps")
    if (stream.bitRate.isNotEmpty()) {
      InfoRow("Bit Rate", stream.bitRate)
    }
    if (stream.colorSpace.isNotEmpty()) {
      InfoRow("Color Space", stream.colorSpace)
    }
    if (stream.chromaSubsampling.isNotEmpty()) {
      InfoRow("Chroma Subsampling", stream.chromaSubsampling)
    }
    if (stream.bitDepth.isNotEmpty()) {
      InfoRow("Bit Depth", "${stream.bitDepth} bits")
    }
    if (stream.scanType.isNotEmpty()) {
      InfoRow("Scan Type", stream.scanType)
    }
  }
}

@Composable
private fun AudioStreamContent(stream: AudioStreamInfo) {
  Column {
    InfoRow("Format", stream.format)
    if (stream.formatProfile.isNotEmpty()) {
      InfoRow("Format Profile", stream.formatProfile)
    }
    if (stream.codecId.isNotEmpty()) {
      InfoRow("Codec ID", stream.codecId)
    }
    if (stream.title.isNotEmpty()) {
      InfoRow("Title", stream.title)
    }
    if (stream.language.isNotEmpty()) {
      InfoRow("Language", stream.language)
    }
    InfoRow("Channels", stream.channels)
    if (stream.channelLayout.isNotEmpty()) {
      InfoRow("Channel Layout", stream.channelLayout)
    }
    InfoRow("Sampling Rate", stream.samplingRate)
    if (stream.bitRate.isNotEmpty()) {
      InfoRow("Bit Rate", stream.bitRate)
    }
    if (stream.bitDepth.isNotEmpty()) {
      InfoRow("Bit Depth", "${stream.bitDepth} bits")
    }
  }
}

@Composable
private fun TextStreamContent(stream: TextStreamInfo) {
  Column {
    InfoRow("Format", stream.format)
    if (stream.codecId.isNotEmpty()) {
      InfoRow("Codec ID", stream.codecId)
    }
    if (stream.title.isNotEmpty()) {
      InfoRow("Title", stream.title)
    }
    if (stream.language.isNotEmpty()) {
      InfoRow("Language", stream.language)
    }
  }
}

@Composable
private fun InfoRow(
  label: String,
  value: String,
) {
  if (value.isEmpty()) return

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
  ) {
    Text(
      "$label:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(140.dp),
    )
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
