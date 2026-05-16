package app.gyrolet.mpvrx.repository.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class GeminiModel(
  val name: String,
  @SerialName("display_name")
  val displayName: String? = null,
)

@Serializable
private data class GeminiModelListResponse(
  val models: List<GeminiModel> = emptyList(),
)

@Serializable
private data class GeminiContent(
  val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
  val text: String = "",
)

@Serializable
private data class GeminiCandidate(
  val content: GeminiContent? = null,
)

@Serializable
private data class GeminiResponse(
  val candidates: List<GeminiCandidate>? = null,
)

class GeminiClient(
  private val client: OkHttpClient,
  private val json: Json,
) : AiClient {
  companion object {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private const val MODELS_URL = "$BASE_URL/models"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    val FREE_MODEL_PREFIXES = listOf(
      "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash",
      "gemini-1.5-pro", "learnlm", "gemma",
    )
  }

  private val apiClient: OkHttpClient =
    client.newBuilder()
      .connectTimeout(60, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$MODELS_URL?key=$apiKey&pageSize=100")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) {
        val errorMsg = parseError(body)
        throw Exception("Gemini model fetch error ${response.code}: $errorMsg")
      }

      val parsed = json.decodeFromString<GeminiModelListResponse>(body)
      parsed.models
        .filter { it.name.contains("gemini") || it.name.contains("gemma") || it.name.contains("learnlm") }
        .map {
          val id = it.name.removePrefix("models/")
          val isFree = FREE_MODEL_PREFIXES.any { prefix -> id.startsWith(prefix, ignoreCase = true) }
          AiModelInfo(
            id = id,
            displayName = it.displayName ?: id,
            isFree = isFree,
          )
        }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$MODELS_URL?key=$apiKey&pageSize=1")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      if (!response.isSuccessful) {
        val body = response.body.string()
        throw Exception("Invalid API key: ${response.code} $body")
      }
      "API key verified successfully"
    }
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val requestBody = json.encodeToString(
        GeminiGenerateRequest.serializer(),
        GeminiGenerateRequest(
          systemInstruction = GeminiContent(
            parts = listOf(GeminiPart(text = instruction)),
          ),
          contents = listOf(
            GeminiContent(
              parts = listOf(GeminiPart(text = userInput)),
            ),
          ),
          generationConfig = GeminiGenerationConfig(
            temperature = options.temperature,
            maxOutputTokens = options.maxTokens,
          ),
        ),
      )

      val request = Request.Builder()
        .url("$BASE_URL/models/$model:generateContent?key=$apiKey")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) {
        val errorMsg = parseError(body)
        throw Exception("Gemini generate error ${response.code}: $errorMsg")
      }

      val parsed = json.decodeFromString<GeminiResponse>(body)
      val text = parsed.candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull()
        ?.text
        ?.trim()

      text ?: throw Exception("No response from Gemini")
    }
  }

  private fun parseError(body: String): String = try {
    val error = json.decodeFromString<JsonErrorResponse>(body)
    error.error?.message ?: body
  } catch (_: Exception) {
    body.take(200)
  }
}

@Serializable
private data class GeminiGenerateRequest(
  @SerialName("system_instruction")
  val systemInstruction: GeminiContent? = null,
  val contents: List<GeminiContent>,
  @SerialName("generation_config")
  val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiGenerationConfig(
  val temperature: Double = 0.3,
  @SerialName("max_output_tokens")
  val maxOutputTokens: Int = 200,
)

@Serializable
private data class JsonError(
  val message: String? = null,
)

@Serializable
private data class JsonErrorResponse(
  val error: JsonError? = null,
)
