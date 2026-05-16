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
private data class TogModel(
  val id: String,
  val display_name: String? = null,
  val displayName: String? = null,
)

@Serializable
private data class TogModelListResponse(
  val data: List<TogModel>? = null,
)

@Serializable
private data class TogMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class TogChoice(
  val message: TogMessage? = null,
)

@Serializable
private data class TogResponse(
  val choices: List<TogChoice>? = null,
)

@Serializable
private data class TogErrorBody(val error: TogErrorDetail? = null)

@Serializable
private data class TogErrorDetail(val message: String? = null)

@Serializable
private data class TogChatRequest(
  val model: String,
  val messages: List<TogMessage>,
  val temperature: Double = 0.3,
  @SerialName("max_tokens") val maxTokens: Int = 200,
)

class TogetherClient(
  private val client: OkHttpClient,
  private val json: Json,
) : AiClient {
  companion object {
    private const val TAG = "TogetherClient"
    private const val BASE_URL = "https://api.together.xyz/v1"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    val FREE_MODEL_PREFIXES = listOf(
      "meta-llama/", "mistralai/", "google/", "microsoft/",
      "Qwen/", "deepseek-ai/", "allenai/", "upstage/",
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

      if (!response.isSuccessful) throw Exception("Together API error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<TogModelListResponse>(body)
      val models = parsed.data ?: emptyList()
      models.map { model ->
        val isFree = FREE_MODEL_PREFIXES.any { model.id.startsWith(it, ignoreCase = true) }
        AiModelInfo(
          id = model.id,
          displayName = model.displayName ?: model.display_name ?: model.id,
          isFree = isFree,
        )
      }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/models")
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
        TogChatRequest.serializer(),
        TogChatRequest(
          model = model,
          messages = listOf(
            TogMessage(role = "system", content = instruction),
            TogMessage(role = "user", content = userInput),
          ),
          temperature = options.temperature,
          maxTokens = options.maxTokens,
        ),
      )

      val request = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("Together generate error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<TogResponse>(body)
      parsed.choices?.firstOrNull()?.message?.content?.trim()
        ?: throw Exception("No response from Together")
    }
  }

  private fun parseError(body: String): String = try {
    val error = json.decodeFromString<TogErrorBody>(body)
    error.error?.message ?: body
  } catch (_: Exception) {
    body.take(200)
  }
}
