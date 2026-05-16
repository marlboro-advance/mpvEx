package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class DownloadProgress(
  val bytesDownloaded: Long = 0,
  val totalBytes: Long = -1,
  val isComplete: Boolean = false,
  val error: String? = null,
) {
  val percentage: Float get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes) else 0f
}

class ModelDownloadManager(
  private val client: OkHttpClient,
) {
  companion object {
    private const val TAG = "ModelDownloadManager"
    private const val TIMEOUT_SECONDS = 300L
  }

  private val downloadClient: OkHttpClient =
    client.newBuilder()
      .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  private val _progress = MutableStateFlow<DownloadProgress>(DownloadProgress())
  val progress: Flow<DownloadProgress> = _progress

  suspend fun downloadModel(
    model: LocalModelInfo,
    targetDir: File,
    hfToken: String? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      if (!targetDir.exists()) targetDir.mkdirs()

      val hfUrl = "https://huggingface.co/${model.hfRepo}/resolve/main/${model.hfFile}"
      val targetFile = File(targetDir, model.hfFile)
      val tmpFile = File(targetDir, model.hfFile + ".part")

      if (targetFile.exists() && targetFile.length() > model.quantSizeMb * 1_000_000L * 0.9) {
        Log.i(TAG, "Model already downloaded: ${targetFile.absolutePath}")
        _progress.value = DownloadProgress(
          bytesDownloaded = targetFile.length(),
          totalBytes = targetFile.length(),
          isComplete = true,
        )
        return@runCatching targetFile
      }

      var attempt = 0
      val maxAttempts = 2
      var lastException: Exception? = null

      while (attempt < maxAttempts) {
        attempt++
        try {
          Log.i(TAG, "Downloading from: $hfUrl (attempt $attempt)")
          _progress.value = DownloadProgress()

          val resumeBytes = tmpFile.takeIf { it.exists() }?.length()?.takeIf { it > 0L } ?: 0L
          val request = Request.Builder()
            .url(hfUrl)
            .header("User-Agent", "MpvRx/1.0")
            .apply {
              if (resumeBytes > 0L) {
                header("Range", "bytes=$resumeBytes-")
              }
              if (!hfToken.isNullOrBlank()) {
                header("Authorization", "Bearer $hfToken")
              }
            }
            .build()

          val response = downloadClient.newCall(request).execute()
          if (!response.isSuccessful) {
            val msg = if (response.code == 401) {
              "Download failed: HTTP 401 (Unauthorized). This model may require a Hugging Face token. Go to Settings → AI Integration → Offline Model and enter your Hugging Face token (get one from huggingface.co/settings/tokens)"
            } else {
              "Download failed: HTTP ${response.code}"
            }
            throw Exception(msg)
          }

          val body = response.body
          val responseLength = body.contentLength()
          val isResuming = resumeBytes > 0L && response.code == 206
          val initialBytes = if (isResuming) resumeBytes else 0L
          val totalBytes = when {
            isResuming && responseLength > 0 -> initialBytes + responseLength
            responseLength > 0 -> responseLength
            else -> -1L
          }
          if (resumeBytes > 0L && !isResuming) {
            tmpFile.delete()
          }

          body.byteStream().use { input ->
            FileOutputStream(tmpFile, isResuming).use { output ->
              val buffer = ByteArray(64 * 1024)
              var bytesRead: Long = initialBytes
              var read: Int
              var lastProgressAt = 0L

              while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesRead += read
                val now = System.currentTimeMillis()
                if (now - lastProgressAt >= 250L || bytesRead == totalBytes) {
                  _progress.value = DownloadProgress(
                    bytesDownloaded = bytesRead,
                    totalBytes = totalBytes,
                  )
                  lastProgressAt = now
                }
              }
              output.flush()
            }
          }

          // Validate size if quantSizeMb is available
          val downloadedLen = tmpFile.length()
          if (model.quantSizeMb > 0) {
            val expected = model.quantSizeMb * 1_000_000L
            val okLower = (expected * 0.85).toLong()
            val okUpper = (expected * 1.15).toLong()
            if (downloadedLen < okLower) {
              tmpFile.delete()
              throw Exception("Downloaded file too small: $downloadedLen bytes (expected ~${expected} bytes)")
            }
            if (downloadedLen > okUpper) {
              // allow slightly larger files but log
              Log.w(TAG, "Downloaded file larger than expected: $downloadedLen bytes (expected ~$expected)")
            }
          }

          // Rename temp file to final
          if (tmpFile.renameTo(targetFile)) {
            _progress.value = DownloadProgress(
              bytesDownloaded = targetFile.length(),
              totalBytes = targetFile.length(),
              isComplete = true,
            )
            Log.i(TAG, "Download complete: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return@runCatching targetFile
          } else {
            tmpFile.delete()
            throw Exception("Failed to move downloaded file into place")
          }
        } catch (e: Exception) {
          lastException = e
          Log.w(TAG, "Download attempt $attempt failed", e)
          _progress.value = DownloadProgress(error = e.message)
          // small backoff
          try { Thread.sleep(500) } catch (_: InterruptedException) {}
        }
      }

      throw lastException ?: Exception("Unknown download error")
    }.onFailure { e ->
      _progress.value = DownloadProgress(error = e.message)
      Log.e(TAG, "Download failed", e)
    }
  }

  fun resetProgress() {
    _progress.value = DownloadProgress()
  }

  fun getDownloadedModels(targetDir: File): List<File> {
    if (!targetDir.exists()) return emptyList()
    return targetDir.listFiles { f -> f.extension == "gguf" }?.toList() ?: emptyList()
  }

  fun deleteModel(file: File): Boolean = file.delete()

  fun getModelFile(model: LocalModelInfo, targetDir: File): File =
    File(targetDir, model.hfFile)
}
