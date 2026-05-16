package app.gyrolet.mpvrx.repository.ai

import android.content.Context
import android.util.Log
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class AiService(
  private val context: Context,
  private val preferences: AiPreferences,
  private val geminiClient: AiClient,
  private val groqClient: AiClient,
  private val openAiClient: AiClient,
  private val anthropicClient: AiClient,
  private val openRouterClient: AiClient,
  private val togetherClient: AiClient,
  private val localAiClient: LocalAiClient,
  private val modelDownloadManager: ModelDownloadManager,
  private val json: Json,
) {
  companion object {
    private const val TAG = "AiService"
  }

  private val clients: Map<AiProvider, AiClient> = mapOf(
    AiProvider.GEMINI to geminiClient,
    AiProvider.GROQ to groqClient,
    AiProvider.OPENAI to openAiClient,
    AiProvider.ANTHROPIC to anthropicClient,
    AiProvider.OPENROUTER to openRouterClient,
    AiProvider.TOGETHER to togetherClient,
    AiProvider.LOCAL to localAiClient,
  )

  @Serializable
  private data class SubtitleTranslationCache(
    val key: String,
    val translatedChunks: List<String?>,
    val updatedAtMs: Long,
  )

  data class SubtitleTranslationProgress(
    val progress: Float,
    val completedChunks: Int,
    val totalChunks: Int,
    val isResuming: Boolean = false,
  )

  suspend fun fetchModels(): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    fetchModelsForProvider(provider)
  }

  suspend fun fetchModelsForProvider(provider: AiProvider): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    val apiKey = getApiKey(provider)

    if (provider == AiProvider.LOCAL) {
      return@withContext localAiClient.fetchModels("")
    }

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    val client = clients[provider] ?: return@withContext Result.failure(Exception("Unknown provider: $provider"))
    client.fetchModels(apiKey)
  }

  suspend fun verifyKey(): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()

    if (provider == AiProvider.LOCAL) {
      return@withContext Result.success("Local model ready")
    }

    val apiKey = getApiKey(provider)
    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    val client = clients[provider] ?: return@withContext Result.failure(Exception("Unknown provider: $provider"))
    client.verifyKey(apiKey)
  }

  suspend fun generateWithAi(
    userInput: String,
    task: AiTask,
    extraInstruction: String? = null,
  ): Result<String> = withContext(Dispatchers.IO) {
    // Subtitle translation uses only online providers
    val effectiveProvider = if (task == AiTask.TRANSLATE && preferences.provider.get() == AiProvider.LOCAL) {
      preferences.sttProvider.get()
    } else {
      preferences.provider.get()
    }
    val model = preferences.selectedModel.get()
    val apiKey = getApiKey(effectiveProvider)

    if (userInput.isBlank()) {
      return@withContext Result.failure(Exception("Empty input provided to AI"))
    }
    val customPromptEnabled = preferences.customPromptEnabled.get()
    val customPrompt = preferences.customPrompt.get()
    val customRenamePrompt = preferences.customRenamePrompt.get()
    val customSubtitleTranslationPrompt = preferences.customSubtitleTranslationPrompt.get()
    val customSubtitleFormatPrompt = preferences.customSubtitleFormatPrompt.get()

    if (effectiveProvider != AiProvider.LOCAL && apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $effectiveProvider"))
    }
    if (effectiveProvider == AiProvider.LOCAL) {
      if (preferences.localModelId.get().isBlank()) {
        return@withContext Result.failure(Exception("No local model selected. Go to AI Settings to select a model."))
      }
    } else if (model.isBlank()) {
      return@withContext Result.failure(Exception("No AI model selected"))
    }

    var instruction = AiPrompts.resolveInstruction(
      task,
      customPromptEnabled,
      customPrompt,
      customRenamePrompt,
      customSubtitleTranslationPrompt,
      customSubtitleFormatPrompt,
    )
    if (extraInstruction != null) {
      instruction = "$instruction\n\n$extraInstruction"
    }

    val client = clients[effectiveProvider] ?: return@withContext Result.failure(Exception("Unknown provider: $effectiveProvider"))
    val options = generationOptionsFor(task)

    if (effectiveProvider == AiProvider.LOCAL) {
      val modelId = preferences.localModelId.get()
      val localInfo = LocalModelCatalog.getById(modelId)
      if (localInfo == null) {
        return@withContext Result.failure(Exception("Local model not selected. Go to AI Settings to download a model."))
      }
      val modelDir = getLocalModelDir()
      val modelFile = modelDownloadManager.getModelFile(localInfo, modelDir)
      if (!modelFile.exists()) {
        return@withContext Result.failure(Exception("Model file not found. Please download the model first."))
      }
      return@withContext localAiClient.generateContent(modelFile.absolutePath, modelId, instruction, userInput, options)
    }

    client.generateContent(apiKey, model, instruction, userInput, options)
  }

  private fun generationOptionsFor(task: AiTask): AiGenerationOptions = when (task) {
    AiTask.RENAME -> AiGenerationOptions(maxTokens = 128, temperature = 0.2)
    AiTask.SUBTITLE_FORMAT -> AiGenerationOptions(maxTokens = 256, temperature = 0.1)
    AiTask.TRANSLATE -> AiGenerationOptions(maxTokens = 2048, temperature = 0.2)
  }

  private fun deviceRamMb(): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return (memoryInfo.totalMem / (1024 * 1024)).toInt()
  }

  private fun selectBestDownloadedLocalModelForTranslation(extraInstruction: String) {
    val targetLanguage = Regex("TARGET LANGUAGE:\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
      .find(extraInstruction)
      ?.groupValues
      ?.getOrNull(1)
      ?.trim()
      .orEmpty()
    if (targetLanguage.isBlank()) return

    val ramMb = deviceRamMb()
    val downloaded = LocalModelCatalog.speedFirst(ramMb).filter { isLocalModelDownloaded(it.id) }
    if (downloaded.isEmpty()) return

    val current = LocalModelCatalog.getById(preferences.localModelId.get())
    val lowerLanguage = targetLanguage.lowercase(Locale.ROOT)
    val currentIsGood = current != null &&
      current.tier != LocalModelTier.UTILITY &&
      current.weakLanguages.none { lowerLanguage.contains(it.lowercase(Locale.ROOT)) } &&
      isLocalModelDownloaded(current.id)
    if (currentIsGood) return

    val best = downloaded
      .filter { it.tier != LocalModelTier.UTILITY }
      .sortedWith(
        compareByDescending<LocalModelInfo> { it.bestLanguages.any { lang -> lowerLanguage.contains(lang.lowercase(Locale.ROOT)) } }
          .thenByDescending { it.translationRank }
          .thenByDescending { it.speedRank },
      )
      .firstOrNull()
    if (best != null && best.id != preferences.localModelId.get()) {
      preferences.localModelId.set(best.id)
      preferences.localModelPath.set(modelDownloadManager.getModelFile(best, getLocalModelDir()).absolutePath)
      preferences.localModelDownloaded.set(true)
      Log.i(TAG, "Selected ${best.displayName} for $targetLanguage subtitle translation")
    }
  }

  suspend fun renameWithAi(
    currentName: String,
    extension: String?,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(currentName, AiTask.RENAME)
    result.map { aiName ->
      // Strip reasoning blocks that might corrupt filenames
      val withoutThinking = aiName.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
      val clean = withoutThinking.trim().removeSurrounding("\"").removeSurrounding("'")
      if (extension != null && !clean.endsWith(extension)) {
        "$clean$extension"
      } else clean
    }
  }

  suspend fun formatTitleForSubtitleSearch(
    fileTitle: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(fileTitle, AiTask.SUBTITLE_FORMAT)
    result.map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
  }

  suspend fun verifyModel(): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()

    if (provider == AiProvider.LOCAL) {
      val modelId = preferences.localModelId.get()
      if (modelId.isBlank()) return@withContext Result.failure(Exception("No local model selected"))
      if (!isLocalModelDownloaded(modelId)) return@withContext Result.failure(Exception("Local model not downloaded"))
      return@withContext Result.success("Local model is ready")
    }

    val model = preferences.selectedModel.get()
    if (model.isBlank()) return@withContext Result.failure(Exception("No model selected"))

    val apiKey = getApiKey(provider)
    if (apiKey.isBlank()) return@withContext Result.failure(Exception("API key not configured"))

    val stored = preferences.availableModels.get()
    val knownModels = if (stored.isNotBlank()) {
      runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AiModelInfo.serializer()), stored)
      }.getOrDefault(emptyList())
    } else emptyList()
    val modelInfo = knownModels.firstOrNull { it.id == model }

    val sb = StringBuilder()
    if (modelInfo != null) {
      sb.appendLine("Available")
      sb.appendLine(if (modelInfo.isFree) "Free model" else "Paid model")
    }
    val client = clients[provider] ?: return@withContext Result.failure(Exception("Unknown provider"))
    val testResult = client.generateContent(apiKey, model, "", "OK", AiGenerationOptions(maxTokens = 2, temperature = 0.0))
    if (testResult.isSuccess) {
      sb.append("API access working")
    } else {
      val msg = testResult.exceptionOrNull()?.message ?: "unknown error"
      when {
        msg.contains("quota", ignoreCase = true) || msg.contains("rate limit", ignoreCase = true) || msg.contains("insufficient_quota", ignoreCase = true) ->
          sb.append("Quota exceeded / rate limited")
        msg.contains("not found", ignoreCase = true) || msg.contains("not available", ignoreCase = true) || msg.contains("model_not_found", ignoreCase = true) ->
          sb.append("Model not available")
        msg.contains("billing", ignoreCase = true) || msg.contains("payment", ignoreCase = true) || msg.contains("credit", ignoreCase = true) ||
          msg.contains("insufficient", ignoreCase = true) ->
          sb.append("Paid model \u2014 billing required")
        else ->
          sb.append("Access error: ${testResult.exceptionOrNull()?.message?.take(100)}")
      }
    }
    Result.success(sb.toString())
  }

  suspend fun isConfigured(): Boolean {
    val provider = preferences.provider.get()
    return when (provider) {
      AiProvider.LOCAL -> {
        preferences.enabled.get() && preferences.localModelId.get().isNotBlank() && preferences.localModelDownloaded.get()
      }
      else -> {
        val apiKey = getApiKey(provider)
        preferences.enabled.get() && apiKey.isNotBlank() && preferences.selectedModel.get().isNotBlank()
      }
    }
  }

  fun getOnlineProviders(): List<AiProvider> = AiProvider.values().filter { it != AiProvider.LOCAL }

  fun getApiKey(provider: AiProvider): String = when (provider) {
    AiProvider.GEMINI -> preferences.geminiApiKey.get()
    AiProvider.GROQ -> preferences.groqApiKey.get()
    AiProvider.OPENAI -> preferences.openaiApiKey.get()
    AiProvider.ANTHROPIC -> preferences.anthropicApiKey.get()
    AiProvider.OPENROUTER -> preferences.openrouterApiKey.get()
    AiProvider.TOGETHER -> preferences.togetherApiKey.get()
    AiProvider.LOCAL -> ""
  }

  fun getLocalModelDir(): File =
    File(context.filesDir, "local_ai_models").also { it.mkdirs() }

  suspend fun downloadLocalModel(modelId: String): Result<File> {
    val model = LocalModelCatalog.getById(modelId)
      ?: return Result.failure(Exception("Unknown model: $modelId"))

    val modelDir = getLocalModelDir()
    val hfToken = preferences.huggingfaceToken.get()
    return modelDownloadManager.downloadModel(model, modelDir, hfToken).onSuccess { file ->
      try {
        // Persist the absolute path and mark downloaded in preferences for later debugging/use
        preferences.localModelPath.set(file.absolutePath)
        preferences.localModelDownloaded.set(true)
      } catch (_: Exception) {
        // ignore preference write failures but continue returning the file
      }
    }
  }

  fun isLocalModelDownloaded(modelId: String): Boolean {
    val model = LocalModelCatalog.getById(modelId) ?: return false
    return modelDownloadManager.getModelFile(model, getLocalModelDir()).exists()
  }

  fun deleteLocalModel(modelId: String): Boolean {
    val model = LocalModelCatalog.getById(modelId) ?: return false
    val file = modelDownloadManager.getModelFile(model, getLocalModelDir())
    if (file.exists()) {
      val deleted = file.delete()
      if (deleted && preferences.localModelPath.get() == file.absolutePath) {
        preferences.localModelDownloaded.set(false)
        preferences.localModelPath.set("")
      }
      return deleted
    }
    return false
  }

  fun getDownloadProgress() = modelDownloadManager.progress

  fun getLocalModelBenchmarks(): List<LocalModelBenchmark> = runCatching {
    json.decodeFromString(
      ListSerializer(LocalModelBenchmark.serializer()),
      preferences.localModelBenchmarks.get(),
    )
  }.getOrDefault(emptyList())

  suspend fun benchmarkLocalModel(modelId: String): Result<LocalModelBenchmark> = withContext(Dispatchers.IO) {
    val model = LocalModelCatalog.getById(modelId)
      ?: return@withContext Result.failure(Exception("Unknown model: $modelId"))
    val modelFile = modelDownloadManager.getModelFile(model, getLocalModelDir())
    if (!modelFile.exists()) {
      return@withContext Result.failure(Exception("Download ${model.displayName} before benchmarking."))
    }

    localAiClient.benchmark(modelFile.absolutePath, model.id).onSuccess { benchmark ->
      val existing = getLocalModelBenchmarks().filterNot { it.modelId == model.id }
      preferences.localModelBenchmarks.set(
        json.encodeToString(
          ListSerializer(LocalModelBenchmark.serializer()),
          existing + benchmark,
        ),
      )
    }
  }

  suspend fun translateSubtitle(
    content: String,
    targetLanguage: String,
    subtitleFormat: String? = null,
    onProgress: (SubtitleTranslationProgress) -> Unit = {},
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val fmt = subtitleFormat?.lowercase(Locale.ROOT)
      val normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n")

      // ASS/SSA: header must be preserved verbatim; only Dialogue: lines translated
      if (fmt == "ass" || fmt == "ssa") {
        return@withContext translateAssContent(normalizedContent, targetLanguage, onProgress)
      }

      val chunks = when (fmt) {
        "srt", "vtt", "sbv", "srv1", "srv2", "srv3" ->
          normalizedContent.split(Regex("\n{2,}")).map(String::trim).filter { it.isNotBlank() }
        "ttml", "dfxp", "itt", "imsc" ->
          Regex("<p\\b[^>]*>.*?</p>", RegexOption.DOT_MATCHES_ALL)
            .findAll(normalizedContent).map { it.value }.toList()
        "lrc", "krc" ->
          normalizedContent.lines().filter { it.isNotBlank() }
        else ->
          normalizedContent.lines().filter { it.isNotBlank() }
      }

      if (chunks.isEmpty()) return@withContext Result.success(content)

      val chunkSize = 15
      val totalChunks = (chunks.size + chunkSize - 1) / chunkSize
      val cacheKey = translationCacheKey(normalizedContent, targetLanguage, fmt)
      val cacheFile = translationCacheFile(cacheKey)
      val cachedChunks = loadTranslationCache(cacheKey, totalChunks)
      val translatedChunks = MutableList<String?>(totalChunks) { index -> cachedChunks.getOrNull(index) }
      val cachedCount = translatedChunks.count { it != null }
      if (cachedCount > 0) {
        onProgress(
          SubtitleTranslationProgress(
            progress = cachedCount.toFloat() / totalChunks,
            completedChunks = cachedCount,
            totalChunks = totalChunks,
            isResuming = true,
          ),
        )
      }

      for (i in 0 until totalChunks) {
        if (translatedChunks[i] != null) {
          continue
        }
        val start = i * chunkSize
        val end = minOf(start + chunkSize, chunks.size)
        val chunk = chunks.subList(start, end).joinToString("\n\n")

        val extra = buildString {
          append("TARGET LANGUAGE: $targetLanguage\n")
          append("OUTPUT FORMAT: keep the exact subtitle format and structure of the original file.")
          subtitleFormat?.let { append("\nSOURCE FORMAT: .$it") }
        }

        val result = generateWithAi(chunk, AiTask.TRANSLATE, extra)
        result.onSuccess { 
            val clean = it.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
            translatedChunks[i] = clean
            saveTranslationCache(cacheFile, cacheKey, translatedChunks)
        }
          .onFailure { return@withContext Result.failure(it) }

        onProgress(
          SubtitleTranslationProgress(
            progress = translatedChunks.count { it != null }.toFloat() / totalChunks,
            completedChunks = translatedChunks.count { it != null },
            totalChunks = totalChunks,
            isResuming = cachedCount > 0,
          ),
        )
      }

      cacheFile.delete()
      Result.success(translatedChunks.filterNotNull().joinToString("\n\n"))
    } catch (e: Exception) {
      Log.e(TAG, "Subtitle translation failed", e)
      Result.failure(e)
    }
  }

  /**
   * ASS/SSA translation strategy:
   * - All header/style sections are kept verbatim (never sent to AI).
   * - Only the free-text field of each `Dialogue:` line is translated.
   * - A batch delimiter allows one AI call per 30 dialogue lines instead of N calls.
   */
  private suspend fun translateAssContent(
    content: String,
    targetLanguage: String,
    onProgress: (SubtitleTranslationProgress) -> Unit,
  ): Result<String> {
    data class DLine(val lineIdx: Int, val prefix: String, val text: String)

    val lines = content.lines()
    val dialogueLines = mutableListOf<DLine>()

    lines.forEachIndexed { idx, line ->
      if (line.startsWith("Dialogue:", ignoreCase = true)) {
        // ASS has 10 comma-separated fields; text starts after the 9th comma
        var pos = -1
        var count = 0
        while (count < 9) {
          pos = line.indexOf(',', pos + 1)
          if (pos == -1) break
          count++
        }
        if (pos != -1 && pos + 1 < line.length) {
          dialogueLines.add(DLine(idx, line.substring(0, pos + 1), line.substring(pos + 1)))
        }
      }
    }

    if (dialogueLines.isEmpty()) return Result.success(content)

    val delimiter = "\u2016" // double vertical line, unlikely to appear in subtitles
    val chunks = dialogueLines.chunked(15)
    val translatedTextByIdx = mutableMapOf<Int, String>()
    val cacheKey = translationCacheKey(content, targetLanguage, "ass")
    val cacheFile = translationCacheFile(cacheKey)
    val cachedChunks = loadTranslationCache(cacheKey, chunks.size)
    val translatedChunkTexts = MutableList<String?>(chunks.size) { index -> cachedChunks.getOrNull(index) }
    val cachedCount = translatedChunkTexts.count { it != null }

    chunks.forEachIndexed { chunkIdx, chunk ->
      translatedChunkTexts[chunkIdx]?.let { cached ->
        cached.split(delimiter).forEachIndexed { partIdx, text ->
          if (partIdx < chunk.size) translatedTextByIdx[chunk[partIdx].lineIdx] = text.trim()
        }
        onProgress(
          SubtitleTranslationProgress(
            progress = translatedChunkTexts.count { it != null }.toFloat() / chunks.size,
            completedChunks = translatedChunkTexts.count { it != null },
            totalChunks = chunks.size,
            isResuming = cachedCount > 0,
          ),
        )
        return@forEachIndexed
      }

      val batchInput = chunk.joinToString(delimiter) { it.text }
      val extra = "TARGET LANGUAGE: $targetLanguage\n" +
        "OUTPUT FORMAT: Return ONLY the translated segments in the same order separated by '$delimiter'. " +
        "Preserve ALL ASS override tags like {\\an8}, {\\pos()}, {\\i1}, {\\b1} exactly. " +
        "Do NOT add or remove any '$delimiter' separators."

      val result = generateWithAi(batchInput, AiTask.TRANSLATE, extra)
      result.onSuccess { translated ->
        translatedChunkTexts[chunkIdx] = translated
        saveTranslationCache(cacheFile, cacheKey, translatedChunkTexts)
        val parts = translated.split(delimiter)
        parts.forEachIndexed { partIdx, text ->
          if (partIdx < chunk.size) translatedTextByIdx[chunk[partIdx].lineIdx] = text.trim()
        }
      }.onFailure { return Result.failure(it) }

      onProgress(
        SubtitleTranslationProgress(
          progress = translatedChunkTexts.count { it != null }.toFloat() / chunks.size,
          completedChunks = translatedChunkTexts.count { it != null },
          totalChunks = chunks.size,
          isResuming = cachedCount > 0,
        ),
      )
    }

    val resultLines = lines.mapIndexed { idx, line ->
      val dl = dialogueLines.find { it.lineIdx == idx }
      if (dl != null) "${dl.prefix}${translatedTextByIdx[idx] ?: dl.text}" else line
    }
    cacheFile.delete()
    return Result.success(resultLines.joinToString("\n"))
  }

  private fun translationCacheKey(
    content: String,
    targetLanguage: String,
    format: String?,
  ): String {
    val effectiveProvider = if (preferences.provider.get() == AiProvider.LOCAL) {
      preferences.sttProvider.get().name
    } else {
      preferences.provider.get().name
    }
    val model = preferences.selectedModel.get()
    val input = listOf(content, targetLanguage, format.orEmpty(), effectiveProvider, model).joinToString("\u001f")
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun translationCacheFile(key: String): File =
    File(context.filesDir, "ai_translation_cache").also { it.mkdirs() }.resolve("$key.json")

  private fun loadTranslationCache(key: String, totalChunks: Int): List<String?> {
    val file = translationCacheFile(key)
    if (!file.exists()) return emptyList()
    return runCatching {
      val cache = json.decodeFromString(SubtitleTranslationCache.serializer(), file.readText())
      if (cache.key == key && cache.translatedChunks.size == totalChunks) {
        cache.translatedChunks
      } else {
        emptyList()
      }
    }.getOrDefault(emptyList())
  }

  private fun saveTranslationCache(
    file: File,
    key: String,
    chunks: List<String?>,
  ) {
    runCatching {
      file.writeText(
        json.encodeToString(
          SubtitleTranslationCache.serializer(),
          SubtitleTranslationCache(
            key = key,
            translatedChunks = chunks,
            updatedAtMs = System.currentTimeMillis(),
          ),
        ),
      )
    }.onFailure {
      Log.w(TAG, "Could not save translation cache", it)
    }
  }
}

