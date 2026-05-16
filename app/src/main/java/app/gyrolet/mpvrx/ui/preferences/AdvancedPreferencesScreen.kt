package app.gyrolet.mpvrx.ui.preferences

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastJoinToString
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.os.Build
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.database.MpvRxDatabase
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailRepository
import app.gyrolet.mpvrx.preferences.AdvancedPreferences
import app.gyrolet.mpvrx.preferences.FoldersPreferences
import app.gyrolet.mpvrx.preferences.SettingsManager
import app.gyrolet.mpvrx.preferences.SubtitlesPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.ConfirmDialog
import app.gyrolet.mpvrx.presentation.crash.CrashActivity
import app.gyrolet.mpvrx.ui.player.NotificationStyle
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import app.gyrolet.mpvrx.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TwoTargetIconButtonPreference
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
object AdvancedPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val settingsManager = koinInject<SettingsManager>()
    val foldersPreferences = koinInject<FoldersPreferences>()
    val subtitlesPreferences = koinInject<SubtitlesPreferences>()
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importStats by remember { mutableStateOf<SettingsManager.ImportStats?>(null) }
    var exportStats by remember { mutableStateOf<SettingsManager.ExportStats?>(null) }
    val playbackHistoryClearedMessage = stringResource(R.string.pref_advanced_cleared_playback_history)
    val fontsCacheClearedMessage = stringResource(R.string.pref_advanced_cleared_fonts_cache)

    // Export settings launcher
    val exportLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml"),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.exportSettings(it).fold(
              onSuccess = { stats ->
                exportStats = stats
                showExportDialog = true
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  "Export failed: ${error.message}",
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    // Import settings launcher
    val importLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.importSettings(it).fold(
              onSuccess = { stats ->
                importStats = stats
                showImportDialog = true
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  "Import failed: ${error.message}",
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    val baseStorageFolder by foldersPreferences.baseStorageFolder.collectAsState()

    val storageRootPicker =
      rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
      ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val uriString = uri.toString()
        foldersPreferences.baseStorageFolder.set(uriString)
        preferences.mpvConfStorageUri.set(uriString)
        subtitlesPreferences.subtitleSaveFolder.set(uriString)
        subtitlesPreferences.fontsFolder.set(uriString)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        listOf("fonts", "Subtitles", "scripts", "script-opts", "shaders").forEach { name ->
          if (root.findFile(name) == null) root.createDirectory(name)
        }
      }

    // Export results dialog
    if (showExportDialog && exportStats != null) {
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = { Text("Export Complete") },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          ) {
            Text(
              "Successfully exported ${exportStats?.totalExported} items!\n\n"
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { showExportDialog = false }) {
            Text("OK")
          }
        },
      )
    }

    // Import results dialog
    if (showImportDialog && importStats != null) {
      AlertDialog(
        onDismissRequest = { showImportDialog = false },
        title = { Text("Import Complete") },
        text = {
          Text(
            "Successfully imported: ${importStats?.imported}\n" +
              "Failed: ${importStats?.failed}\n" +
              "Version: ${importStats?.version}\n\n" +
              "Please restart the app for all changes to take effect.",
          )
        },
        confirmButton = {
          TextButton(onClick = { showImportDialog = false }) {
            Text("OK")
          }
        },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_advanced),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(
                Icons.Default.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          // Backup & Restore Section
          item {
            PreferenceSectionHeader(title = "Backup & Restore")
          }
          
          item {
            PreferenceCard {
              Preference(
                title = { Text(text = "Export Settings") },
                summary = { 
                  Text(
                    text = "Export settings to an XML file",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.FileUpload, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = {
                  exportLauncher.launch(settingsManager.getDefaultExportFilename())
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(text = "Import Settings") },
                summary = { 
                  Text(
                    text = "Import settings from an XML file",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.FileDownload, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = {
                  importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                },
              )
            }
          }
          
          // Storage Root Section
          item {
            PreferenceSectionHeader(title = "Storage Root")
          }
          
          item {
            PreferenceCard {
              Preference(
                title = { Text("Base Storage Folder") },
                summary = {
                  Text(
                    text = if (baseStorageFolder.isNotEmpty())
                      getSimplifiedStoragePath(baseStorageFolder)
                    else
                      "Tap to select — creates Subtitles/, Fonts/, scripts/ subdirs",
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { storageRootPicker.launch(null) },
              )
              
              if (baseStorageFolder.isNotEmpty()) {
                PreferenceDivider()
                Preference(
                  title = { Text("Clear Storage Root") },
                  icon = { Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                  onClick = {
                    foldersPreferences.baseStorageFolder.set("")
                    preferences.mpvConfStorageUri.set("")
                    subtitlesPreferences.subtitleSaveFolder.set("")
                    subtitlesPreferences.fontsFolder.set("")
                  },
                )
              }
            }
          }
          
          // MPV Configuration Section
          item {
            PreferenceSectionHeader(title = "MPV Configuration")
          }

          item {
            PreferenceCard {
              var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
              var inputConf by remember { mutableStateOf(preferences.inputConf.get()) }

              // Load config files when storage location changes
              LaunchedEffect(mpvConfStorageLocation) {
                if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                  val tempFile = kotlin.io.path.createTempFile()
                  runCatching {
                    val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
                    val mpvConfFile = tree?.findFile("mpv.conf")
                    if (mpvConfFile != null && mpvConfFile.exists()) {
                      context.contentResolver.openInputStream(mpvConfFile.uri)?.copyTo(tempFile.outputStream())
                      val content = tempFile.readLines().fastJoinToString("\n")
                      preferences.mpvConf.set(content)
                      File(context.filesDir, "mpv.conf").writeText(content)
                      withContext(Dispatchers.Main) { mpvConf = content }
                    }
                  }
                  tempFile.deleteIfExists()
                }
              }

              LaunchedEffect(mpvConfStorageLocation) {
                if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                  val tempFile = kotlin.io.path.createTempFile()
                  runCatching {
                    val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
                    val inputConfFile = tree?.findFile("input.conf")
                    if (inputConfFile != null && inputConfFile.exists()) {
                      context.contentResolver.openInputStream(inputConfFile.uri)?.copyTo(tempFile.outputStream())
                      val content = tempFile.readLines().fastJoinToString("\n")
                      preferences.inputConf.set(content)
                      File(context.filesDir, "input.conf").writeText(content)
                      withContext(Dispatchers.Main) { inputConf = content }
                    }
                  }
                  tempFile.deleteIfExists()
                }
              }

              Preference(
                title = { Text(stringResource(R.string.pref_advanced_mpv_conf)) },
                summary = {
                  val firstLine = mpvConf.lines().firstOrNull()
                  if (firstLine != null && firstLine.isNotBlank()) {
                    Text(firstLine, color = MaterialTheme.colorScheme.outline)
                  } else {
                    Text("Tap to edit configuration", color = MaterialTheme.colorScheme.outline)
                  }
                },
                onClick = {
                  backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.MPV_CONF))
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_advanced_input_conf)) },
                summary = {
                  val firstLine = inputConf.lines().firstOrNull()
                  if (firstLine != null && firstLine.isNotBlank()) {
                    Text(firstLine, color = MaterialTheme.colorScheme.outline)
                  } else {
                    Text("Tap to edit configuration", color = MaterialTheme.colorScheme.outline)
                  }
                },
                onClick = {
                  backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.INPUT_CONF))
                },
              )
            }
          }
          
          // Scripts Section
          item {
            PreferenceSectionHeader(title = "Scripts (Lua / JS)")
          }
          
          item {
            PreferenceCard {
              val selectedScripts by preferences.selectedLuaScripts.collectAsState()
              val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
              
              SwitchPreference(
                value = enableLuaScripts,
                onValueChange = preferences.enableLuaScripts::set,
                title = { Text("Enable scripts") },
                summary = { 
                  Text(
                    "Load scripts from your MPV configuration directory",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text("Manage scripts") },
                summary = {
                  when {
                    mpvConfStorageLocation.isBlank() || !enableLuaScripts -> Text(
                      "Set storage location and enable scripts first", 
                      color = MaterialTheme.colorScheme.outline
                    )
                    selectedScripts.isEmpty() -> Text(
                      "No scripts enabled", 
                      color = MaterialTheme.colorScheme.outline
                    )
                    selectedScripts.size == 1 -> Text(
                      "1 script enabled",
                      color = MaterialTheme.colorScheme.outline
                    )
                    else -> Text(
                      "${selectedScripts.size} scripts enabled",
                      color = MaterialTheme.colorScheme.outline
                    )
                  }
                },
                onClick = {
                  backStack.add(LuaScriptsScreen)
                },
                enabled = mpvConfStorageLocation.isNotBlank() && enableLuaScripts,
              )

              PreferenceDivider()

              Preference(
                title = { Text("Custom buttons") },
                summary = {
                  Text(
                    "Create and manage script-powered player buttons",
                    color = MaterialTheme.colorScheme.outline
                  )
                },
                onClick = {
                  backStack.add(app.gyrolet.mpvrx.ui.preferences.CustomButtonScreen)
                },
              )
            }
          }
          
          // Data & Cache Section
          item {
            PreferenceSectionHeader(title = "Data & Cache")
          }

          item {
            PreferenceCard {
              var isConfirmDialogShown by remember { mutableStateOf(false) }
              val mpvrxDatabase = koinInject<MpvRxDatabase>()
              val enableRecentlyPlayed by preferences.enableRecentlyPlayed.collectAsState()

              SwitchPreference(
                value = enableRecentlyPlayed,
                onValueChange = preferences.enableRecentlyPlayed::set,
                title = { Text(stringResource(R.string.pref_advanced_enable_recently_played_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_advanced_enable_recently_played_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_advanced_clear_playback_history)) },
                onClick = { isConfirmDialogShown = true },
              )

              if (isConfirmDialogShown) {
                ConfirmDialog(
                  stringResource(R.string.pref_advanced_clear_playback_history_confirm_title),
                  stringResource(R.string.pref_advanced_clear_playback_history_confirm_subtitle),
                  onConfirm = {
                    scope.launch(Dispatchers.IO) {
                      runCatching {
                        mpvrxDatabase.videoDataDao().clearAllPlaybackStates()
                        RecentlyPlayedOps.clearAll()
                      }.onSuccess {
                        withContext(Dispatchers.Main) {
                          isConfirmDialogShown = false
                          Toast
                            .makeText(
                              context,
                              playbackHistoryClearedMessage,
                              Toast.LENGTH_SHORT,
                            ).show()
                        }
                      }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                          isConfirmDialogShown = false
                          Toast
                            .makeText(
                              context,
                              "Failed to clear: ${error.message}",
                              Toast.LENGTH_LONG,
                            ).show()
                        }
                      }
                    }
                  },
                  onCancel = { isConfirmDialogShown = false },
                )
              }

              PreferenceDivider()

              var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
              var isClearThumbsConfirmShown by remember { mutableStateOf(false) }
              val thumbnailRepository = koinInject<ThumbnailRepository>()
              
              Preference(
                title = { Text(text = "Clear config cache") },
                summary = { 
                  Text(
                    text = "Clear the cached mpv.conf settings",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val mpvConfFile = File(context.filesDir, "mpv.conf")
                    mpvConfFile.delete()
                    // Clear preferences too
                    preferences.mpvConf.delete()
                    withContext(Dispatchers.Main) {
                      mpvConf = ""
                      Toast
                        .makeText(
                          context,
                          "Config cache cleared",
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                  }
                },
              )
              
              PreferenceDivider()

              Preference(
                title = { Text(text = "Clear thumbnail cache") },
                summary = {
                  Text(
                    text = "Delete all cached video thumbnails (will regenerate as you browse folders)",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { isClearThumbsConfirmShown = true },
              )

              if (isClearThumbsConfirmShown) {
                ConfirmDialog(
                  title = "Clear thumbnail cache?",
                  subtitle = "This will delete cached thumbnails from storage and memory.",
                  onConfirm = {
                    scope.launch(Dispatchers.IO) {
                      runCatching {
                        thumbnailRepository.clearThumbnailCache()
                      }.onSuccess {
                        withContext(Dispatchers.Main) {
                          isClearThumbsConfirmShown = false
                          Toast.makeText(context, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
                        }
                      }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                          isClearThumbsConfirmShown = false
                          Toast.makeText(context, "Failed to clear: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                      }
                    }
                  },
                  onCancel = { isClearThumbsConfirmShown = false },
                )
              }
              
              PreferenceDivider()
              
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_advanced_clear_fonts_cache)) },
                summary = { 
                  Text(
                    text = "Remove all cached subtitle fonts",
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val fontsDir = File(context.filesDir.path + "/fonts")
                    if (fontsDir.exists()) {
                      fontsDir.listFiles()?.forEach { file ->
                        // Delete all font files
                        if (file.isFile &&
                          file.name
                            .lowercase()
                            .matches(".*\\.[ot]tf$".toRegex())
                        ) {
                          file.delete()
                        }
                      }
                    }
                    withContext(Dispatchers.Main) {
                      Toast
                        .makeText(
                          context,
                          fontsCacheClearedMessage,
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                  }
                },
              )
            }
          }
          
          item {
            PreferenceSectionHeader(title = "Notification")
          }

          item {
            PreferenceCard {
              val notificationStyle by preferences.notificationStyle.collectAsState()
              val supportedNotificationStyles =
                remember {
                  NotificationStyle.entries.filter { it.isSupportedOn(Build.VERSION.SDK_INT) }
                }
              val selectedNotificationStyle =
                notificationStyle.takeIf { it.isSupportedOn(Build.VERSION.SDK_INT) }
                  ?: NotificationStyle.Media

              ListPreference(
                value = selectedNotificationStyle,
                onValueChange = preferences.notificationStyle::set,
                values = supportedNotificationStyles,
                valueToText = { AnnotatedString(it.displayName) },
                title = { Text(text = stringResource(R.string.pref_advanced_notification_style)) },
                summary = {
                  Text(
                    text = selectedNotificationStyle.displayName,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          // Logging Section
          item {
            PreferenceSectionHeader(title = "Logging")
          }
          
          item {
            PreferenceCard {
              val activity = LocalActivity.current!!
              val clipboardManager = context.getSystemService(ClipboardManager::class.java)
              val verboseLogging by preferences.verboseLogging.collectAsState()
              
              SwitchPreference(
                value = verboseLogging,
                onValueChange = preferences.verboseLogging::set,
                title = { Text(stringResource(R.string.pref_advanced_verbose_logging_title)) },
                summary = { 
                  Text(
                    stringResource(R.string.pref_advanced_verbose_logging_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(stringResource(R.string.pref_advanced_dump_logs_title)) },
                summary = { 
                  Text(
                    stringResource(R.string.pref_advanced_dump_logs_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val deviceInfo = CrashActivity.collectDeviceInfo()
                    val logcat = CrashActivity.collectLogcat()
    
                    clipboardManager?.setPrimaryClip(
                      ClipData.newPlainText(
                        "mpvrx_logs",
                        CrashActivity.concatLogs(deviceInfo, null, logcat),
                      ),
                    )
                    CrashActivity.shareLogs(deviceInfo, null, logcat, activity)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

fun getSimplifiedPathFromUri(uri: String): String =
  Environment.getExternalStorageDirectory().canonicalPath + "/" + Uri.decode(uri).substringAfterLast(":")
