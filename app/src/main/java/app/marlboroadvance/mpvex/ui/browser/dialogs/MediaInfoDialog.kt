package app.marlboroadvance.mpvex.ui.browser.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps.AudioStreamInfo
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps.GeneralInfo
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps.TextStreamInfo
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps.VideoStreamInfo
import kotlinx.coroutines.launch
import java.io.File

/**
 * Dialog for displaying detailed media information
 *
 * @param isOpen Whether the dialog is open
 * @param onDismiss Callback when dialog is dismissed
 * @param fileName Name of the media file
 * @param mediaInfo Media information data to display
 * @param isLoading Whether media info is being loaded
 * @param error Error message if loading failed
 * @param videoForShare Pass a Video object to enable the Share button
 * The dialog also provides a simple Share action that generates a text report using
 * MediaInfoOps and shares it via ShareOps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaInfoDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  fileName: String,
  mediaInfo: MediaInfoOps.MediaInfoData?,
  isLoading: Boolean,
  error: String?,
  videoForShare: Video? = null,
) {
  if (!isOpen) return

  val context = LocalContext.current
  val scope = rememberCoroutineScope()

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
      elevation =
        CardDefaults.cardElevation(
          defaultElevation = 6.dp,
        ),
      shape = MaterialTheme.shapes.extraLarge,
    ) {
      Column(
        modifier = Modifier.padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        // Header
        DialogHeader(fileName = fileName)

        // Content
        DialogContent(
          isLoading = isLoading,
          error = error,
          mediaInfo = mediaInfo,
        )

        // Footer
        DialogFooter(
          showShareButton = mediaInfo != null && !isLoading && videoForShare != null,
          showCopyButton = mediaInfo != null && !isLoading && videoForShare != null,
          onShare = {
            val video = videoForShare ?: return@DialogFooter
            scope.launch {
              // Generate text output
              val result = MediaInfoOps.generateTextOutput(context, video.uri, video.displayName)
              result
                .onSuccess { textContent ->
                  val fileName = "mediainfo_${video.displayName.substringBeforeLast('.')}.txt"
                  val file = File(context.cacheDir, fileName)
                  runCatching { file.writeText(textContent) }
                    .onSuccess {
                      val fileUri =
                        FileProvider.getUriForFile(
                          context,
                          "${context.packageName}.provider",
                          file,
                        )
                      val intent =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                          putExtra(
                            android.content.Intent.EXTRA_SUBJECT,
                            "Media Info - ${video.displayName}",
                          )
                          addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                      context.startActivity(
                        android.content.Intent.createChooser(
                          intent,
                          "Media Info - ${video.displayName}",
                        ),
                      )
                    }.onFailure { e ->
                      Toast
                        .makeText(
                          context,
                          e.message ?: "Failed to share",
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                }.onFailure { e ->
                  Toast.makeText(context, e.message ?: "Failed to share", Toast.LENGTH_SHORT).show()
                }
            }
          },
          onCopy = {
            val video = videoForShare ?: return@DialogFooter
            scope.launch {
              val result = MediaInfoOps.generateTextOutput(context, video.uri, video.displayName)
              result
                .onSuccess { textContent ->
                  val clipboard =
                    context.getSystemService(
                      android.content.Context.CLIPBOARD_SERVICE,
                    ) as android.content.ClipboardManager
                  val clip =
                    android.content.ClipData.newPlainText(
                      "Media Info - ${video.displayName}",
                      textContent,
                    )
                  clipboard.setPrimaryClip(clip)
                  Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                  Toast.makeText(context, e.message ?: "Failed to copy", Toast.LENGTH_SHORT).show()
                }
            }
          },
          onDismiss = onDismiss,
        )
      }
    }
  }
}

@Composable
private fun DialogHeader(fileName: String) {
  Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = "Media Information",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Text(
      text = fileName,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun DialogContent(
  isLoading: Boolean,
  error: String?,
  mediaInfo: MediaInfoOps.MediaInfoData?,
) {
  when {
    isLoading -> LoadingState()
    error != null -> ErrorState(error)
    mediaInfo != null -> MediaInfoContent(mediaInfo)
  }
}

@Composable
private fun LoadingState() {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    CircularProgressIndicator(
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 4.dp,
    )
    Text(
      text = "Analyzing media...",
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ErrorState(error: String) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
      ),
    shape = MaterialTheme.shapes.extraLarge,
  ) {
    Text(
      text = "Error: $error",
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.padding(20.dp),
    )
  }
}

@Composable
private fun DialogFooter(
  showShareButton: Boolean,
  showCopyButton: Boolean,
  onShare: () -> Unit,
  onCopy: () -> Unit,
  onDismiss: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (showShareButton) {
        FilledTonalIconButton(
          onClick = onShare,
          colors =
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share",
          )
        }
      }
      if (showCopyButton) {
        FilledTonalIconButton(
          onClick = onCopy,
          colors =
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
          shape = MaterialTheme.shapes.extraLarge,
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy",
          )
        }
      }
    }

    Button(
      onClick = onDismiss,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
        ),
      shape = MaterialTheme.shapes.extraLarge,
    ) {
      Text(
        text = "Close",
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun MediaInfoContent(mediaInfo: MediaInfoOps.MediaInfoData) {
  SelectionContainer {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState())
          .padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      StreamInfoSection(
        title = "General",
        content = { GeneralInfoContent(mediaInfo.general) },
      )

      mediaInfo.videoStreams.forEachIndexed { index, stream ->
        StreamInfoSection(
          title = "Video Stream ${index + 1}",
          content = { VideoStreamContent(stream) },
        )
      }

      mediaInfo.audioStreams.forEachIndexed { index, stream ->
        StreamInfoSection(
          title = "Audio Stream ${index + 1}",
          content = { AudioStreamContent(stream) },
        )
      }

      mediaInfo.textStreams.forEachIndexed { index, stream ->
        StreamInfoSection(
          title = "Subtitle Stream ${index + 1}",
          content = { TextStreamContent(stream) },
        )
      }
    }
  }
}

@Composable
private fun StreamInfoSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    HorizontalDivider(
      thickness = 2.dp,
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    content()
  }
}

@Composable
private fun GeneralInfoContent(general: GeneralInfo) {
  InfoSection {
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
  InfoSection {
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
  InfoSection {
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
  InfoSection {
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
private fun InfoSection(content: @Composable () -> Unit) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    content()
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
        .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = "$label:",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(160.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style =
        MaterialTheme.typography.bodyMedium.copy(
          fontFamily = FontFamily.Monospace,
        ),
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
