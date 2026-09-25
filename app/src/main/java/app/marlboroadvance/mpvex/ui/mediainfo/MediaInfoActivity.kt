package app.marlboroadvance.mpvex.ui.mediainfo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.MpvexTheme
import app.marlboroadvance.mpvex.utils.media.HttpUtils
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

class MediaInfoActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()
  private val TAG = "MediaInfoActivity"
  private val currentIntent = mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    currentIntent.value = intent

    setContent {
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)

      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      MpvexTheme {
        Surface {
          MediaInfoScreen(
            onBack = { finish() },
            isDarkMode = isDarkMode,
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    currentIntent.value = intent
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun MediaInfoScreen(
    onBack: () -> Unit,
    isDarkMode: Boolean,
  ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var fullMediaInfoText by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("Media File") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var mediaInfo by remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }

    // Get Material Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    val activeIntent = currentIntent.value

    LaunchedEffect(activeIntent) {
      isLoading = true
      error = null
      mediaInfo = null
      textContent = null
      fullMediaInfoText = null

      val uri = extractUriFromIntent(activeIntent)

      if (uri == null) {
        error = "No media file or link provided"
        isLoading = false
        return@LaunchedEffect
      }

      fileUri = uri
      val resolvedName = resolveFileName(context, uri)
      fileName = resolvedName

      val headers = extractHeadersFromIntent(activeIntent, uri)

      // Load media info
      scope.launch {
        try {
          val result = MediaInfoOps.getMediaInfo(context, uri, fileName, headers)
          result.onSuccess { mediaInfoResult ->
            if (mediaInfoResult.general.format.isEmpty() &&
                mediaInfoResult.videoStreams.isEmpty() &&
                mediaInfoResult.audioStreams.isEmpty() &&
                mediaInfoResult.rawReport.isEmpty()
            ) {
              error = "Unable to read media information. The link may be unreachable or unsupported."
            } else {
              mediaInfo = mediaInfoResult

              val text = mediaInfoResult.rawReport.ifEmpty {
                MediaInfoOps.generateTextOutput(context, uri, fileName, headers).getOrNull() ?: ""
              }
              textContent = text
              fullMediaInfoText = text
            }
            isLoading = false
          }.onFailure { e ->
            error = e.message ?: "Failed to load media information"
            isLoading = false
          }
        } catch (e: Exception) {
          error = e.message ?: "Unknown error"
          isLoading = false
        }
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = "Media Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
            }
          },
          actions = {
            if (!isLoading && error == null && textContent != null) {
              Row(modifier = Modifier.padding(end = 12.dp)) {
                FilledTonalIconButton(
                  onClick = {
                    scope.launch {
                      copyToClipboard(textContent!!, fileName)
                    }
                  },
                  colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                  )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                  onClick = {
                    scope.launch {
                      shareMediaInfo(textContent!!, fileName, fileUri)
                    }
                  },
                  colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share",
                  )
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
        )
      },
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
      ) {
        when {
          isLoading -> LoadingContent()
          error != null -> ErrorContent(error!!)
          mediaInfo != null -> MediaInfoContent(mediaInfo!!, fileName, fullMediaInfoText)
        }
      }
    }
  }

  @Composable
  private fun LoadingContent() {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 4.dp,
          modifier = Modifier.size(48.dp),
        )
        Text(
          text = "Analyzing media file...",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  @Composable
  private fun ErrorContent(errorMessage: String) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(
          text = "Error: $errorMessage",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(24.dp),
        )
      }
    }
  }

  @Composable
  private fun MediaInfoContent(mediaInfo: MediaInfoOps.MediaInfoData, fileName: String, fullMediaInfoText: String?) {
    if (fullMediaInfoText == null) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Text("Loading detailed information...")
      }
      return
    }

    // Parse the text output into sections
    val sections = parseMediaInfoText(fullMediaInfoText)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      sections.forEach { section ->
        MediaInfoSection(section)
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Footer
      Text(
        text = "Generated by mpvEx using MediaInfoLib",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  private fun parseMediaInfoText(text: String): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()
    val lines = text.lines()

    var currentSectionName: String? = null
    val currentProperties = mutableListOf<Pair<String, String>>()

    for (line in lines) {
      when {
        // Skip separator lines and empty lines
        line.trim().startsWith("=") || line.trim().isEmpty() -> continue

        // Skip header/footer
        line.contains("MEDIA INFO -") || line.contains("Generated by mpvex") -> continue

        // New section (no colon, not indented, has content)
        !line.startsWith(" ") && !line.contains(":") && line.trim().isNotEmpty() -> {
          // Save previous section
          if (currentSectionName != null && currentProperties.isNotEmpty()) {
            sections.add(InfoSection(currentSectionName, currentProperties.toList()))
            currentProperties.clear()
          }
          currentSectionName = line.trim()
        }

        // Property line (contains colon)
        line.contains(":") -> {
          val parts = line.split(":", limit = 2)
          if (parts.size == 2) {
            val key = parts[0].trim()
            val value = parts[1].trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
              currentProperties.add(key to value)
            }
          }
        }
      }
    }

    // Add last section
    if (currentSectionName != null && currentProperties.isNotEmpty()) {
      sections.add(InfoSection(currentSectionName, currentProperties.toList()))
    }

    return sections
  }

  @Composable
  private fun MediaInfoSection(section: InfoSection) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp,
        pressedElevation = 4.dp,
        hoveredElevation = 4.dp,
      ),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
      ) {
        // Section title
        Text(
          text = section.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(bottom = 12.dp),
        )

        // Properties
        androidx.compose.foundation.text.selection.SelectionContainer {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            section.properties.forEach { (key, value) ->
              PropertyRow(key, value)
            }
          }
        }
      }
    }
  }

  @Composable
  private fun PropertyRow(label: String, value: String) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .weight(1f)
          .padding(end = 16.dp),
      )

      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1.5f),
      )
    }
  }

  private data class InfoSection(
    val name: String,
    val properties: List<Pair<String, String>>,
  )

  private suspend fun copyToClipboard(content: String, fileName: String) {
    withContext(Dispatchers.Main) {
      val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText("Media Info - $fileName", content)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(this@MediaInfoActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
  }

  private suspend fun shareMediaInfo(content: String, fileName: String, mediaUri: Uri?) {
    withContext(Dispatchers.IO) {
      try {
        val textFileName = "mediainfo_${fileName.substringBeforeLast('.')}.txt"
        val file = File(cacheDir, textFileName)
        file.writeText(content)

        withContext(Dispatchers.Main) {
          val fileUri = FileProvider.getUriForFile(
            this@MediaInfoActivity,
            "${packageName}.provider",
            file,
          )

          val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Media Info - $fileName")
            putExtra(Intent.EXTRA_TEXT, "Media information for: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }

          startActivity(Intent.createChooser(shareIntent, "Share Media Info"))
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(
            this@MediaInfoActivity,
            "Failed to share: ${e.message}",
            Toast.LENGTH_LONG,
          ).show()
        }
      }
    }
  }

  private fun extractUriFromIntent(intent: Intent?): Uri? {
    if (intent == null) return null

    // 1. Direct intent data (ACTION_VIEW, or explicit data URI)
    intent.data?.let { return it }

    // 2. Extra stream (ACTION_SEND with file/media URI)
    if (intent.hasExtra(Intent.EXTRA_STREAM)) {
      val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
      }
      if (streamUri != null) return streamUri
    }

    // 3. String extra "uri" (internal mpvEx intents)
    intent.getStringExtra("uri")?.let { uriStr ->
      val parsed = Uri.parse(uriStr)
      if (parsed.scheme != null) return parsed
    }

    // 4. EXTRA_TEXT (shared links from browsers, YouTube, Twitter/X, social apps, chat, etc.)
    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
      ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    if (!sharedText.isNullOrBlank()) {
      val trimmed = sharedText.trim()
      // Extract URL from shared text (e.g. "Watch: https://example.com/stream.mp4")
      val urlRegex = Regex("""https?://[^\s<>"]+""")
      val match = urlRegex.find(trimmed)
      if (match != null) {
        return Uri.parse(match.value)
      }
      val directUri = Uri.parse(trimmed)
      if (directUri.scheme != null) {
        return directUri
      }
    }

    // 5. ClipData URI
    intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { return it }

    return null
  }

  private fun extractHeadersFromIntent(intent: Intent?, uri: Uri?): Map<String, String> {
    val headerMap = mutableMapOf<String, String>()
    if (uri != null && HttpUtils.isNetworkStream(uri)) {
      HttpUtils.extractRefererDomain(uri)?.let { referer ->
        headerMap["Referer"] = referer
      }
    }
    intent?.getStringArrayExtra("headers")?.let { headers ->
      for (i in 0 until headers.size - 1 step 2) {
        headerMap[headers[i]] = headers[i + 1]
      }
    }
    return headerMap
  }

  private fun resolveFileName(context: Context, uri: Uri): String {
    val scheme = uri.scheme?.lowercase()
    if (scheme == "http" || scheme == "https") {
      val path = uri.path.orEmpty()
      val lastSegment = path.substringAfterLast('/')
      val decoded = Uri.decode(lastSegment.substringBefore('?').substringBefore('#'))
      if (decoded.isNotBlank()) {
        return decoded
      }
      return uri.host ?: "Online Stream"
    }

    if (scheme == "content") {
      try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrBlank()) return name
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error querying content resolver for file name", e)
      }
      val segment = uri.lastPathSegment
      if (!segment.isNullOrBlank()) return segment
    }

    if (scheme == "file") {
      val path = uri.path
      if (!path.isNullOrBlank()) {
        return File(path).name
      }
    }

    return uri.lastPathSegment ?: uri.toString()
  }
}
