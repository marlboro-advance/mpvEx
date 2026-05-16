package app.gyrolet.mpvrx.repository.ai

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
private data class OrModel(
  val id: String,
  val name: String? = null,
)

@Serializable
private data class OrModelListResponse(
  val data: List<OrModel> = emptyList(),
)

@Serializable
private data class OrMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class OrChoice(
  val message: OrMessage? = null,
)

@Serializable
private data class OrResponse(
  val choices: List<OrChoice>? = null,
)

@Serializable
private data class OrErrorBody(val error: OrErrorDetail? = null)

@Serializable
private data class OrErrorDetail(val message: String? = null)

@Serializable
private data class OrChatRequest(
  val model: String,
  val messages: List<OrMessage>,
  val temperature: Double = 0.3,
  @SerialName("max_tokens") val maxTokens: Int = 200,
)

class OpenRouterClient(
  private val client: OkHttpClient,
  private val json: Json,
) : AiClient {
  companion object {
    private const val TAG = "OpenRouterClient"
    private const val BASE_URL = "https://openrouter.ai/api/v1"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    val FREE_MODEL_PREFIXES = listOf(
      "openrouter/auto", "cognitivecomputations/", "google/gemma",
      "microsoft/phi", "mistralai/mistral", "meta-llama/llama",
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
        .url("$BASE_URL/models")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("OpenRouter API error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<OrModelListResponse>(body)
      parsed.data.map { model ->
        val isFree = FREE_MODEL_PREFIXES.any { model.id.startsWith(it, ignoreCase = true) }
        AiModelInfo(
          id = model.id,
          displayName = model.name ?: model.id,
          isFree = isFree,
        )
      }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/auth/key")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      if (!response.isSuccessful) throw Exception("Invalid API key: ${response.code}")
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
        OrChatRequest.serializer(),
        OrChatRequest(
          model = model,
          messages = listOf(
            OrMessage(role = "system", content = instruction),
            OrMessage(role = "user", content = userInput),
          ),
          temperature = options.temperature,
          maxTokens = options.maxTokens,
        ),
      )

      val request = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .header("HTTP-Referer", "https://mpvrx.app")
        .header("X-Title", "mpvRx")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("OpenRouter generate error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<OrResponse>(body)
      parsed.choices?.firstOrNull()?.message?.content?.trim()
        ?: throw Exception("No response from OpenRouter")
    }
  }

  private fun parseError(body: String): String = try {
    val error = json.decodeFromString<OrErrorBody>(body)
    error.error?.message ?: body
  } catch (_: Exception) {
    body.take(200)
  }
}
