package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

class LocalAiClient(
  private val inference: LlmInference,
) : AiClient {

  companion object {
    private const val TAG = "LocalAiClient"
  }

  private val generationMutex = Mutex()
  private var loadedModelPath: String? = null
  private var loadedModelId: String? = null

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> {
    return Result.success(
      LocalModelCatalog.models.map { model ->
        AiModelInfo(
          id = model.id,
          displayName = "${model.displayName} (${model.tier.label}, ${model.sizeLabel})",
        )
      },
    )
  }

  override suspend fun verifyKey(apiKey: String): Result<String> {
    if (!inference.isAvailable()) {
      return Result.failure(
        IllegalStateException(
          "Local AI native library is not available on this device. " +
            "Please use Gemini or Groq instead.",
        ),
      )
    }
    return Result.success("Offline local model - no API key needed")
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<String> = withContext(Dispatchers.IO) {
    generationMutex.withLock {
      runCatching {
        // Guard #1: native library must be present
        if (!inference.isAvailable()) {
          throw IllegalStateException(
            "Local AI is not supported on this device. Please use Gemini or Groq.",
          )
        }

        // Guard #2: model must be loaded (lazy-load on first use)
        val modelPath = apiKey
        if (modelPath.isBlank()) {
          throw IllegalStateException(
            "No local model downloaded. Go to AI Settings -> Offline Model -> Download.",
          )
        }
        if (!inference.isLoaded() || loadedModelPath != modelPath || loadedModelId != model) {
          val info = LocalModelCatalog.getById(model)
          val nCtx = info?.contextLength ?: 2048
          inference.loadModel(modelPath, nCtx).getOrThrow()
          loadedModelPath = modelPath
          loadedModelId = model
        }

        val prompt = inference.applyChatTemplate(instruction, userInput)
          .getOrElse { LocalModelCatalog.formatPrompt(model, instruction, userInput) }

        inference.generate(
          prompt = prompt,
          maxTokens = options.maxTokens,
          temperature = options.temperature.toFloat(),
        ).getOrThrow()
      }
    }
  }

  suspend fun benchmark(
    modelPath: String,
    modelId: String,
  ): Result<LocalModelBenchmark> = withContext(Dispatchers.IO) {
    generationMutex.withLock {
      runCatching {
        val info = LocalModelCatalog.getById(modelId)
          ?: throw IllegalArgumentException("Unknown model: $modelId")
        val loadStarted = System.currentTimeMillis()
        if (!inference.isLoaded() || loadedModelPath != modelPath || loadedModelId != modelId) {
          inference.loadModel(modelPath, info.contextLength).getOrThrow()
          loadedModelPath = modelPath
          loadedModelId = modelId
        }
        val loadMs = System.currentTimeMillis() - loadStarted
        val prompt = LocalModelCatalog.formatPrompt(
          modelId,
          "Translate the sentence naturally. Return only the translation.",
          "Translate to Hindi: This movie is getting interesting now.",
        )
        val generationStarted = System.currentTimeMillis()
        val output = inference.generate(
          prompt = prompt,
          maxTokens = 48,
          temperature = 0.2f,
        ).getOrThrow()
        val generationMs = max(1L, System.currentTimeMillis() - generationStarted)
        val tokenEstimate = output.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        LocalModelBenchmark(
          modelId = modelId,
          loadMs = loadMs,
          tokensPerSecond = tokenEstimate * 1000f / generationMs,
          memoryEstimateMb = (info.quantSizeMb * 1.35f).toInt(),
          benchmarkedAtMs = System.currentTimeMillis(),
        )
      }
    }
  }

  fun isModelLoaded(): Boolean = inference.isLoaded()

  fun unloadModel() {
    if (inference.isLoaded()) {
      inference.unload()
      loadedModelPath = null
      loadedModelId = null
      Log.i(TAG, "Model unloaded")
    }
  }
}
