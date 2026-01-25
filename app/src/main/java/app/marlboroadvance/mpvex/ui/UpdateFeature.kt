package app.marlboroadvance.mpvex.ui
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// --- Data Models ---

@Serializable
data class Release(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("assets") val assets: List<Asset>
)

@Serializable
data class Asset(
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)

// --- Domain Manager ---

class UpdateManager(
    private val context: Context
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(forceShow: Boolean = false): Release? {
        // Return null immediately if update feature is disabled (F-Droid flavor)
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }
        
        val release = getLatestRelease("https://api.github.com/repos/marlboro-advance/mpvEx/releases/latest")
        val currentVersion = BuildConfig.VERSION_NAME.replace("-dev", "")
        val remoteVersion = release.tagName.removePrefix("v")
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)

        // If this version was ignored, don't show it unless forced (manual check)
        if (!forceShow && ignoredVersion == remoteVersion) {
            return null
        }

        return if (isNewerVersion(remoteVersion, currentVersion)) {
            release
        } else {
            null
        }
    }

    fun ignoreVersion(version: String) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        val prefs = context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ignored_version", version)
            .apply()
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body?.string() ?: throw IOException("Empty body")
        json.decodeFromString<Release>(responseBody)
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun downloadUpdate(release: Release): Flow<Float> {
        // Return completed flow immediately if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return flowOf(100f)
        }
        
        val asset = selectBestApkAsset(release.assets)
            ?: throw Exception("No compatible APK asset found")
        
        val destination = File(context.externalCacheDir, asset.name)
        return downloadApk(asset.downloadUrl, destination)
    }

    private fun selectBestApkAsset(assets: List<Asset>): Asset? {
        val deviceArch = getDeviceArchitecture()
        
        // First, try to find architecture-specific APK
        val archSpecificApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk") && asset.name.contains(deviceArch, ignoreCase = true)
        }
        
        if (archSpecificApk != null) {
            return archSpecificApk
        }
        
        // Fallback to universal APK
        val universalApk = assets.firstOrNull { asset ->
            asset.name.endsWith(".apk") && asset.name.contains("universal", ignoreCase = true)
        }
        
        if (universalApk != null) {
            return universalApk
        }
        
        // Last resort: any APK
        return assets.firstOrNull { it.name.endsWith(".apk") }
    }

    private fun getDeviceArchitecture(): String {
        // Get the primary ABI (Application Binary Interface)
        val primaryAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS[0]
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
        
        // Map Android ABI names to your APK naming convention
        return when (primaryAbi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> "universal" // Fallback for unknown architectures
        }
    }

    private fun downloadApk(url: String, destination: File): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val body = response.body ?: throw IOException("Empty body")
        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(destination)

        try {
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead.toFloat() / contentLength.toFloat()) * 100
                } else {
                    -1f 
                }
                emit(progress)
            }
            outputStream.flush()
            emit(100f) 
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }.flowOn(Dispatchers.IO)
    
    fun getApkFile(release: Release): File? {
        // Return null if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return null
        }
        
        val asset = selectBestApkAsset(release.assets) ?: return null
        val file = File(context.externalCacheDir, asset.name)
        return if (file.exists()) file else null
    }

    fun clearCache() {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
         context.externalCacheDir?.listFiles()?.forEach { 
             if (it.name.endsWith(".apk")) it.delete()
         }
    }
}

// --- ViewModel ---

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float>(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val prefs = application.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE)
    private val _isAutoUpdateEnabled = MutableStateFlow(
        if (BuildConfig.ENABLE_UPDATE_FEATURE) prefs.getBoolean("auto_update", false) else false
    )
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    fun toggleAutoUpdate(enabled: Boolean) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        prefs.edit().putBoolean("auto_update", enabled).apply()
        _isAutoUpdateEnabled.value = enabled
        if (enabled) {
            checkForUpdate(manual = false)
        }
    }

    init {
        // Only initialize auto-update if feature is enabled
        if (BuildConfig.ENABLE_UPDATE_FEATURE && isAutoUpdateEnabled.value) {
            checkForUpdate(manual = false)
        }
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Available(val release: Release) : UpdateState()
        object NoUpdate : UpdateState()
        object Error : UpdateState()
        data class ReadyToInstall(val release: Release) : UpdateState()
    }

    fun dismissNoUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun checkForUpdate(manual: Boolean = false) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val release = updateManager.checkForUpdate(forceShow = manual)
                if (release != null) {
                    val existingFile = updateManager.getApkFile(release)
                     if (existingFile != null) {
                         _updateState.value = UpdateState.ReadyToInstall(release)
                     } else {
                         _updateState.value = UpdateState.Available(release)
                     }
                } else {
                    if (manual) _updateState.value = UpdateState.NoUpdate
                    else _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual) _updateState.value = UpdateState.Error
                else _updateState.value = UpdateState.Idle
            }
        }
    }

    fun downloadUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                updateManager.downloadUpdate(release).collect { progress ->
                    _downloadProgress.value = progress
                }
                _isDownloading.value = false
                 _updateState.value = UpdateState.ReadyToInstall(release)
            } catch (e: Exception) {
                e.printStackTrace()
                _isDownloading.value = false
                _updateState.value = UpdateState.Error 
            }
        }
    }

    fun installUpdate(release: Release) {
        // No-op if update feature is disabled
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
            return
        }
        
        val file = updateManager.getApkFile(release) ?: return
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    fun dismiss() {
        // Clean up downloaded APK when user dismisses the dialog
        updateManager.clearCache()
        _updateState.value = UpdateState.Idle
    }

    fun ignoreVersion(version: String) {
        updateManager.ignoreVersion(version)
        _updateState.value = UpdateState.Idle
    }
}

// --- UI Components ---

@Composable
fun UpdateDialog(
    release: Release,
    isDownloading: Boolean,
    progress: Float,
    actionLabel: String,
    currentVersion: String,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onIgnore: () -> Unit
) {
    val downloadSize = release.assets.find { it.name.endsWith(".apk") }?.size ?: 0L
    val formattedDate = formatDate(release.publishedAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (actionLabel == "Install") Icons.Filled.SystemUpdate else Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (actionLabel == "Install") "Ready to Install" else "Update Available",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.tagName.removePrefix("v"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (actionLabel != "Install") {
                    // Show version info for update available state
                    InfoRow(label = "Current Version", value = currentVersion)
                    InfoRow(label = "Latest Version", value = release.tagName.removePrefix("v"))
                    InfoRow(label = "Release Date", value = formattedDate)
                    InfoRow(label = "Size", value = formatFileSize(downloadSize))
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Downloading...", style = MaterialTheme.typography.bodySmall)
                        Text(text = "${progress.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (progress >= 0) progress / 100f else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onAction) {
                    Text(if (actionLabel == "Install") "Install" else "Download")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    if (actionLabel != "Install") {
                        TextButton(onClick = onIgnore) {
                            Text("Ignore")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "Unknown size"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(dateString: String): String {
    return try {
        // GitHub API returns ISO 8601 format: "2024-01-15T10:30:00Z"
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString) ?: return dateString

        val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        dateString
    }
}
