package app.marlboroadvance.mpvex.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.util.fastJoinToString
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
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
    val scope = rememberCoroutineScope()
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(stringResource(R.string.pref_advanced))
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val locationPicker =
          rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.mpvConfStorageUri.set(uri.toString())
          }
        val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
        Column(
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          TwoTargetIconButtonPreference(
            title = { Text(stringResource(R.string.pref_advanced_mpv_conf_storage_location)) },
            summary = {
              if (mpvConfStorageLocation.isNotBlank()) {
                Text(getSimplifiedPathFromUri(mpvConfStorageLocation))
              }
            },
            onClick = { locationPicker.launch(null) },
            iconButtonIcon = { Icon(Icons.Default.Clear, null) },
            onIconButtonClick = { preferences.mpvConfStorageUri.delete() },
            iconButtonEnabled = mpvConfStorageLocation.isNotBlank(),
          )
          var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
          LaunchedEffect(mpvConfStorageLocation) {
            if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
            withContext(Dispatchers.IO) {
              val tempFile = kotlin.io.path.createTempFile()
              runCatching {
                val tree =
                  DocumentFile.fromTreeUri(
                    context,
                    mpvConfStorageLocation.toUri(),
                  )
                val mpvConfFile = tree?.findFile("mpv.conf")
                if (mpvConfFile != null && mpvConfFile.exists()) {
                  context.contentResolver
                    .openInputStream(
                      mpvConfFile.uri,
                    )?.copyTo(tempFile.outputStream())
                  val content = tempFile.readLines().fastJoinToString("\n")
                  preferences.mpvConf.set(content)
                  File(context.filesDir, "mpv.conf").writeText(content)
                  withContext(Dispatchers.Main) {
                    mpvConf = content
                  }
                }
              }
              tempFile.deleteIfExists()
            }
          }
          TextFieldPreference(
            value = mpvConf,
            onValueChange = { mpvConf = it },
            title = { Text(stringResource(R.string.pref_advanced_mpv_conf)) },
            textField = { value, onValueChange, onOk ->
              OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                maxLines = Int.MAX_VALUE,
                keyboardActions = KeyboardActions(onDone = { onOk() }),
              )
            },
            textToValue = {
              preferences.mpvConf.set(it)
              File(context.filesDir, "mpv.conf").writeText(it)
              if (mpvConfStorageLocation.isNotBlank()) {
                val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())!!
                val uri =
                  if (tree.findFile("mpv.conf") == null) {
                    val conf = tree.createFile("text/plain", "mpv.conf")!!
                    conf.renameTo("mpv.conf")
                    conf.uri
                  } else {
                    tree.findFile("mpv.conf")!!.uri
                  }
                val out = context.contentResolver.openOutputStream(uri, "wt")
                out!!.write(it.toByteArray())
                out.flush()
                out.close()
              }
              it
            },
            summary = {
              val firstLine = mpvConf.lines().firstOrNull()
              if (firstLine != null && firstLine.isNotBlank()) {
                Text(firstLine)
              }
            },
          )
          var inputConf by remember { mutableStateOf(preferences.inputConf.get()) }
          LaunchedEffect(mpvConfStorageLocation) {
            if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
            withContext(Dispatchers.IO) {
              val tempFile = kotlin.io.path.createTempFile()
              runCatching {
                val tree =
                  DocumentFile.fromTreeUri(
                    context,
                    mpvConfStorageLocation.toUri(),
                  )
                val inputConfFile = tree?.findFile("input.conf")
                if (inputConfFile != null && inputConfFile.exists()) {
                  context.contentResolver
                    .openInputStream(
                      inputConfFile.uri,
                    )?.copyTo(tempFile.outputStream())
                  val content = tempFile.readLines().fastJoinToString("\n")
                  preferences.inputConf.set(content)
                  File(context.filesDir, "input.conf").writeText(content)
                  withContext(Dispatchers.Main) {
                    inputConf = content
                  }
                }
              }
              tempFile.deleteIfExists()
            }
          }
          TextFieldPreference(
            value = inputConf,
            onValueChange = { inputConf = it },
            title = { Text(stringResource(R.string.pref_advanced_input_conf)) },
            textField = { value, onValueChange, onOk ->
              OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                maxLines = Int.MAX_VALUE,
                keyboardActions = KeyboardActions(onDone = { onOk() }),
              )
            },
            textToValue = {
              preferences.inputConf.set(it)
              File(context.filesDir, "input.conf").writeText(it)
              if (mpvConfStorageLocation.isNotBlank()) {
                val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())!!
                val uri =
                  if (tree.findFile("input.conf") == null) {
                    val conf = tree.createFile("text/plain", "input.conf")!!
                    conf.renameTo("input.conf")
                    conf.uri
                  } else {
                    tree.findFile("input.conf")!!.uri
                  }
                val out = context.contentResolver.openOutputStream(uri, "wt")
                out!!.write(it.toByteArray())
                out.flush()
                out.close()
              }
              it
            },
            summary = {
              val firstLine = inputConf.lines().firstOrNull()
              if (firstLine != null && firstLine.isNotBlank()) {
                Text(firstLine)
              }
            },
          )
          
          // Lua Scripts Section
          val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
          SwitchPreference(
            value = enableLuaScripts,
            onValueChange = preferences.enableLuaScripts::set,
            title = { Text("Enable Lua Scripts") },
            summary = { Text("Load Lua scripts from configuration directory") },
          )
          
          var showScriptDialog by remember { mutableStateOf(false) }
          var availableScripts by remember { mutableStateOf<List<String>>(emptyList()) }
          val selectedScripts by preferences.selectedLuaScripts.collectAsState()
          
          Preference(
            title = { Text("Select Lua Scripts") },
            summary = {
              when {
                !enableLuaScripts -> Text("Enable Lua scripts first")
                mpvConfStorageLocation.isBlank() -> Text("Set MPV config storage location first")
                selectedScripts.isEmpty() -> Text("No scripts selected")
                else -> Text("${selectedScripts.size} script(s) selected: ${selectedScripts.joinToString(", ")}")
              }
            },
            onClick = {
              scope.launch(Dispatchers.IO) {
                val scripts = mutableListOf<String>()
                if (mpvConfStorageLocation.isNotBlank()) {
                  runCatching {
                    val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
                    if (tree != null && tree.exists()) {
                      tree.listFiles().forEach { file ->
                        if (file.isFile && file.name?.endsWith(".lua") == true) {
                          file.name?.let { scripts.add(it) }
                        }
                      }
                    }
                  }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                      Toast.makeText(
                        context,
                        "Error reading scripts directory: ${e.message}",
                        Toast.LENGTH_LONG
                      ).show()
                    }
                  }
                }
                withContext(Dispatchers.Main) {
                  availableScripts = scripts.sorted()
                  if (scripts.isEmpty()) {
                    Toast.makeText(
                      context,
                      "No .lua files found in the config directory",
                      Toast.LENGTH_SHORT
                    ).show()
                  }
                  showScriptDialog = true
                }
              }
            },
            enabled = enableLuaScripts && mpvConfStorageLocation.isNotBlank(),
          )
          
          if (showScriptDialog) {
            LuaScriptSelectionDialog(
              availableScripts = availableScripts,
              selectedScripts = selectedScripts,
              onScriptsSelected = { newSelection ->
                preferences.selectedLuaScripts.set(newSelection)
                showScriptDialog = false
              },
              onDismiss = { showScriptDialog = false },
            )
          }
          
          val activity = LocalActivity.current!!
          @Suppress("DEPRECATION")
          val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
          Preference(
            title = { Text(stringResource(R.string.pref_advanced_dump_logs_title)) },
            summary = { Text(stringResource(R.string.pref_advanced_dump_logs_summary)) },
            onClick = {
              scope.launch(Dispatchers.IO) {
                val deviceInfo = CrashActivity.collectDeviceInfo()
                val logcat = CrashActivity.collectLogcat()

                clipboard.setText(AnnotatedString(CrashActivity.concatLogs(deviceInfo, null, logcat)))
                CrashActivity.shareLogs(deviceInfo, null, logcat, activity)
              }
            },
          )
          val verboseLogging by preferences.verboseLogging.collectAsState()
          SwitchPreference(
            value = verboseLogging,
            onValueChange = preferences.verboseLogging::set,
            title = { Text(stringResource(R.string.pref_advanced_verbose_logging_title)) },
            summary = { Text(stringResource(R.string.pref_advanced_verbose_logging_summary)) },
          )
          val enableRecentlyPlayed by preferences.enableRecentlyPlayed.collectAsState()
          SwitchPreference(
            value = enableRecentlyPlayed,
            onValueChange = preferences.enableRecentlyPlayed::set,
            title = { Text(stringResource(R.string.pref_advanced_enable_recently_played_title)) },
            summary = { Text(stringResource(R.string.pref_advanced_enable_recently_played_summary)) },
          )
          // Removed: folder scan recursion depth (no longer used)
          var isConfirmDialogShown by remember { mutableStateOf(false) }
          val mpvexDatabase = koinInject<MpvExDatabase>()
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
                    mpvexDatabase.videoDataDao().clearAllPlaybackStates()
                    RecentlyPlayedOps.clearAll()
                  }.onSuccess {
                    withContext(Dispatchers.Main) {
                      isConfirmDialogShown = false
                      Toast
                        .makeText(
                          context,
                          context.getString(R.string.pref_advanced_cleared_playback_history),
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
          Preference(
            title = { Text(text = "Clear config cache") },
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
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_advanced_clear_fonts_cache)) },
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
                      context.getString(R.string.pref_advanced_cleared_fonts_cache),
                      Toast.LENGTH_SHORT,
                    ).show()
                }
              }
            },
          )
        }
      }
    }
  }
}

@Composable
fun LuaScriptSelectionDialog(
  availableScripts: List<String>,
  selectedScripts: Set<String>,
  onScriptsSelected: (Set<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var tempSelectedScripts by remember(selectedScripts) { 
    mutableStateOf(selectedScripts.toMutableSet()) 
  }
  
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Lua Scripts") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        if (availableScripts.isEmpty()) {
          Text("No Lua scripts found in the configuration directory.")
        } else {
          Text(
            text = "Select the Lua scripts to load with MPV:",
            modifier = Modifier.padding(bottom = 8.dp),
          )
          availableScripts.forEach { script ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = tempSelectedScripts.contains(script),
                onCheckedChange = { checked ->
                  tempSelectedScripts = if (checked) {
                    (tempSelectedScripts + script).toMutableSet()
                  } else {
                    (tempSelectedScripts - script).toMutableSet()
                  }
                },
              )
              Text(
                text = script,
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { 
          onScriptsSelected(tempSelectedScripts.toSet())
        }
      ) {
        Text("OK")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

fun getSimplifiedPathFromUri(uri: String): String =
  Environment.getExternalStorageDirectory().canonicalPath + "/" + Uri.decode(uri).substringAfterLast(":")
