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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
  onDownload: () -> Unit = {},
) {
  if (!isOpen) return

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Start,
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

        Spacer(modifier = Modifier.height(8.dp))
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
              modifier = Modifier.fillMaxWidth().padding(32.dp),
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
          mediaInfo != null -> MediaInfoContent(mediaInfo)
        }

        // Footer
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          if (mediaInfo != null && !isLoading) {
            TextButton(onClick = onDownload) {
              Icon(
                Icons.Filled.Download,
                contentDescription = "Download",
                modifier = Modifier.padding(end = 4.dp),
              )
              Text("Save")
            }
          } else {
            Spacer(modifier = Modifier.width(1.dp))
          }
          TextButton(onClick = onDismiss) { Text("Close") }
        }
      }
    }
  }
}

@Composable
private fun MediaInfoContent(mediaInfo: MediaInfoData) {
  Column(
    modifier = Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()).padding(4.dp),
  ) {
    SectionHeader("General")
    GeneralInfoContent(mediaInfo.general)

    mediaInfo.videoStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      SectionHeader("Video Stream ${index + 1}")
      VideoStreamContent(stream)
    }

    mediaInfo.audioStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      SectionHeader("Audio Stream ${index + 1}")
      AudioStreamContent(stream)
    }

    mediaInfo.textStreams.forEachIndexed { index, stream ->
      Spacer(modifier = Modifier.height(16.dp))
      SectionHeader("Subtitle Stream ${index + 1}")
      TextStreamContent(stream)
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Column {
    Text(
      title.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(vertical = 8.dp),
    )
    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun GeneralInfoContent(general: GeneralInfo) {
  Column {
    InfoRow("Complete name", general.completeName)
    InfoRow("Format", general.format)
    InfoRow("Format version", general.formatVersion)
    InfoRow("File size", general.fileSize)
    InfoRow("Duration", general.duration)
    InfoRow("Overall bit rate", general.overallBitRate)
    InfoRow("Frame rate", general.frameRate)
    InfoRow("Title", general.title)
    InfoRow("Encoded date", general.encodedDate)
    InfoRow("Writing application", general.writingApplication)
    InfoRow("Writing library", general.writingLibrary)
  }
}

@Composable
private fun VideoStreamContent(stream: VideoStreamInfo) {
  Column {
    InfoRow("ID", stream.id)
    InfoRow("Format", stream.format)
    InfoRow("Format/Info", stream.formatInfo)
    InfoRow("Format profile", stream.formatProfile)
    InfoRow("Codec ID", stream.codecId)
    InfoRow("Duration", stream.duration)
    InfoRow("Bit rate", stream.bitRate)
    InfoRow("Width", stream.width)
    InfoRow("Height", stream.height)
    InfoRow("Display aspect ratio", stream.displayAspectRatio)
    InfoRow("Frame rate mode", stream.frameRateMode)
    InfoRow("Frame rate", stream.frameRate)
    InfoRow("Color space", stream.colorSpace)
    InfoRow("Chroma subsampling", stream.chromaSubsampling)
    InfoRow("Bit depth", stream.bitDepth)
    InfoRow("Bits/(Pixel*Frame)", stream.bitsPixelFrame)
    InfoRow("Stream size", stream.streamSize)
    InfoRow("Writing library", stream.encodingLibrary)
    InfoRow("Default", stream.defaultStream)
    InfoRow("Forced", stream.forcedStream)

    if (stream.hdrFormat.isNotEmpty()) {
      InfoRow("HDR Format", stream.hdrFormat)
      InfoRow("Max CLL", stream.maxCLL)
      InfoRow("Max FALL", stream.maxFALL)
    }
  }
}

@Composable
private fun AudioStreamContent(stream: AudioStreamInfo) {
  Column {
    InfoRow("ID", stream.id)
    InfoRow("Format", stream.format)
    InfoRow("Format/Info", stream.formatInfo)
    InfoRow("Codec ID", stream.codecId)
    InfoRow("Duration", stream.duration)
    InfoRow("Bit rate", stream.bitRate)
    InfoRow("Channel(s)", stream.channels)
    InfoRow("Channel layout", stream.channelLayout)
    InfoRow("Sampling rate", stream.samplingRate)
    InfoRow("Frame rate", stream.frameRate)
    InfoRow("Compression mode", stream.compressionMode)
    InfoRow("Delay relative to video", stream.delay)
    InfoRow("Stream size", stream.streamSize)
    InfoRow("Title", stream.title)
    InfoRow("Language", stream.language)
    InfoRow("Default", stream.defaultStream)
    InfoRow("Forced", stream.forcedStream)
  }
}

@Composable
private fun TextStreamContent(stream: TextStreamInfo) {
  Column {
    InfoRow("ID", stream.id)
    InfoRow("Format", stream.format)
    InfoRow("Muxing mode", stream.muxingMode)
    InfoRow("Codec ID", stream.codecId)
    InfoRow("Codec ID/Info", stream.codecIdInfo)
    InfoRow("Duration", stream.duration)
    InfoRow("Bit rate", stream.bitRate)
    InfoRow("Frame rate", stream.frameRate)
    InfoRow("Count of elements", stream.countOfElements)
    InfoRow("Stream size", stream.streamSize)
    InfoRow("Title", stream.title)
    InfoRow("Language", stream.language)
    InfoRow("Default", stream.defaultStream)
    InfoRow("Forced", stream.forcedStream)
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  if (value.isEmpty()) return

  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(
      "$label:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(160.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
