package app.marlboroadvance.mpvex.ui.browser.usbvideolist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.ui.utils.debouncedCombinedClickable
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class UsbVideoListScreen(
  private val folderPath: String,
  private val folderName: String,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current

    val isLoading = remember { mutableStateOf(true) }
    val videoFiles = remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(folderPath) {
      isLoading.value = true
      videoFiles.value =
        withContext(Dispatchers.IO) {
          getVideoFilesFromFolder(folderPath)
        }
      isLoading.value = false
    }

    BackHandler {
      backstack.removeLastOrNull()
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = folderName,
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = videoFiles.value.size,
          onBackClick = { backstack.removeLastOrNull() },
          onCancelSelection = {},
          onSortClick = null,
          onSettingsClick = null,
          onDeleteClick = null,
          onRenameClick = null,
          isSingleSelection = false,
          onInfoClick = null,
          onShareClick = null,
          onSelectAll = null,
          onInvertSelection = null,
          onDeselectAll = null,
        )
      },
    ) { padding ->
      when {
        isLoading.value -> {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        videoFiles.value.isEmpty() -> {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "No videos found in this folder",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyLarge,
            )
          }
        }

        else -> {
          LazyColumn(
            modifier =
              Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(8.dp),
          ) {
            items(videoFiles.value, key = { it.absolutePath }) { file ->
              UsbVideoCard(
                file = file,
                onClick = {
                  MediaUtils.playFile(file.absolutePath, context, "usb_video")
                },
              )
            }
          }
        }
      }
    }
  }

  private fun getVideoFilesFromFolder(folderPath: String): List<File> {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.canRead()) {
      return emptyList()
    }

    val videoExtensions =
      setOf(
        "mp4",
        "mkv",
        "avi",
        "mov",
        "wmv",
        "flv",
        "webm",
        "m4v",
        "3gp",
        "3g2",
        "mpg",
        "mpeg",
        "m2v",
        "ogv",
        "ts",
        "mts",
        "m2ts",
        "vob",
        "divx",
        "xvid",
      )

    return folder
      .listFiles()
      ?.filter { it.isFile && videoExtensions.contains(it.extension.lowercase()) }
      ?.sortedBy { it.name.lowercase() }
      ?: emptyList()
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UsbVideoCard(
  file: File,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .debouncedCombinedClickable(onClick = onClick),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.VideoFile,
        contentDescription = "Video file",
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.primary,
      )

      Spacer(modifier = Modifier.width(16.dp))

      Column(
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = file.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = "${formatFileSize(file.length())} • ${formatDate(file.lastModified())}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String =
  when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
  }

private fun formatDate(timestamp: Long): String {
  val date = Date(timestamp)
  val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
  return format.format(date)
}
