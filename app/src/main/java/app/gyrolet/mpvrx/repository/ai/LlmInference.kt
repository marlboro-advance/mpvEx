package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import com.llamatik.library.platform.LlamaBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface LlmInference {
  suspend fun loadModel(modelPath: String, nCtx: Int = 2048): Result<Unit>
  suspend fun generate(
    prompt: String,
    maxTokens: Int = 200,
    temperature: Float = 0.3f,
  ): Result<String>
  suspend fun applyChatTemplate(system: String, user: String): Result<String>
  fun isLoaded(): Boolean
  /** Always true with llamatik — pre-built ABIs are bundled in the AAR. */
  fun isAvailable(): Boolean
  fun unload()
}

/**
 * LlmInference backed by [LlamaBridge] from the llamatik library (com.llamatik:library).
 *
 * Replaces the previous custom JNI/CMake approach:
 * - No native build toolchain required
 * - Pre-built arm64-v8a + x86_64 .so files bundled in the AAR
 * - llama.cpp b9102 under the hood
 * - Supports chat templates, concurrent sessions, JSON mode, streaming
 */
class LlamaCppInference : LlmInference {

  companion object {
    private const val TAG = "LlamaCppInference"
  }

  private var modelLoaded = false
  private var currentNCtx = 2048

  /**
   * Serialises model loading to prevent two coroutines from concurrently
   * calling [LlamaBridge.initGenerateModel] / [LlamaBridge.shutdown] on the
   * shared native context.
   */
  private val modelMutex = Mutex()

  /** llamatik ships pre-built native libs — always available on supported ABIs. */
  override fun isAvailable(): Boolean = true

  private fun recommendedThreads(): Int {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    return (cores - 1).coerceIn(1, 4)
  }

  override suspend fun loadModel(modelPath: String, nCtx: Int): Result<Unit> =
    modelMutex.withLock {
      withContext(Dispatchers.IO) {
        runCatching {
          val modelFile = File(modelPath)
          if (!modelFile.exists()) {
            throw IllegalStateException("Model file not found: $modelPath")
          }

          // Unload any previously loaded model first
          unload()

          // Configure inference params before loading (context length requires reload)
          LlamaBridge.updateGenerateParams(
            temperature = 0.3f,
            maxTokens = 512,
            topP = 0.95f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = nCtx,
            numThreads = recommendedThreads(),
            useMmap = true,
            flashAttention = false,
            batchSize = 512,
          )

          val success = LlamaBridge.initGenerateModel(modelPath)
          if (!success) {
            throw IllegalStateException("LlamaBridge failed to initialise model: ${modelFile.name}")
          }

          modelLoaded = true
          currentNCtx = nCtx
          Log.i(TAG, "Model loaded: ${modelFile.name}  (ctx=$nCtx)")
          Unit
        }
      }
    }

  override suspend fun generate(
    prompt: String,
    maxTokens: Int,
    temperature: Float,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      if (!modelLoaded) throw IllegalStateException("No model loaded")

      // Update sampling params before each call in case caller varies them
      LlamaBridge.updateGenerateParams(
        temperature = temperature,
        maxTokens = maxTokens,
        topP = 0.95f,
        topK = 40,
        repeatPenalty = 1.1f,
        contextLength = currentNCtx,
        numThreads = recommendedThreads(),
        useMmap = true,
        flashAttention = false,
        batchSize = 512,
      )

      LlamaBridge.generate(prompt).trim()
    }
  }

  override suspend fun applyChatTemplate(system: String, user: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!modelLoaded) throw IllegalStateException("No model loaded")

        val messages = listOf("system" to system, "user" to user)
        LlamaBridge.applyChatTemplate(
          messages = messages,
          addAssistantPrefix = true,
        ) ?: throw IllegalStateException(
          "Model has no embedded chat template. Using raw prompt fallback.",
        )
      }
    }

  override fun isLoaded(): Boolean = modelLoaded

  override fun unload() {
    if (modelLoaded) {
      runCatching { LlamaBridge.shutdown() }
        .onFailure { Log.w(TAG, "Error during shutdown", it) }
      modelLoaded = false
      Log.i(TAG, "Model unloaded")
    }
  }
}
