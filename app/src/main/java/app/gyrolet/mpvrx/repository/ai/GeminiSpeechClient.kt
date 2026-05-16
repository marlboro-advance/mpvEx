package app.gyrolet.mpvrx.repository.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiSpeechClient(
  private val client: OkHttpClient,
  private val json: Json,
) : SpeechToTextClient {
  companion object {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private const val DEFAULT_MODEL = "gemini-2.5-flash"
    private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
  }

  private val apiClient: OkHttpClient = client.newBuilder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .build()

  override suspend fun transcribe(
    apiKey: String,
    audioFile: File,
    language: String?,
  ): Result<SpeechTranscript> = withContext(Dispatchers.IO) {
    runCatching {
      if (audioFile.length() > MAX_INLINE_AUDIO_BYTES) {
        throw Exception("Gemini inline audio is limited here. Use Groq or offline Whisper for this long video.")
      }

      val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
      val prompt = buildString {
        append("Transcribe this audio for subtitles. Return only spoken text, no markdown.")
        if (!language.isNullOrBlank()) append(" Spoken language: $language.")
      }

      val requestBody = json.encodeToString(
        GeminiAudioRequest.serializer(),
        GeminiAudioRequest(
          contents = listOf(
            GeminiAudioContent(
              parts = listOf(
                GeminiAudioPart(text = prompt),
                GeminiAudioPart(
                  inlineData = GeminiInlineData(
                    mimeType = "audio/mp4",
                    data = audioBase64,
                  ),
                ),
              ),
            ),
          ),
        ),
      )

      val request = Request.Builder()
        .url("$BASE_URL/models/$DEFAULT_MODEL:generateContent?key=$apiKey")
        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()
      if (!response.isSuccessful) {
        throw Exception("Gemini transcription failed: HTTP ${response.code} ${body.take(240)}")
      }

      val parsed = json.decodeFromString(GeminiAudioResponse.serializer(), body)
      val text = parsed.candidates
        .firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull { !it.text.isNullOrBlank() }
        ?.text
        ?.trim()
        .orEmpty()

      if (text.isBlank()) throw Exception("Gemini returned an empty transcript.")
      SpeechTranscript(text = text)
    }
  }
}

@Serializable
private data class GeminiAudioRequest(
  val contents: List<GeminiAudioContent>,
)

@Serializable
private data class GeminiAudioContent(
  val parts: List<GeminiAudioPart>,
)

@Serializable
private data class GeminiAudioPart(
  val text: String? = null,
  @SerialName("inline_data")
  val inlineData: GeminiInlineData? = null,
)

@Serializable
private data class GeminiInlineData(
  @SerialName("mime_type")
  val mimeType: String,
  val data: String,
)

@Serializable
private data class GeminiAudioResponse(
  val candidates: List<GeminiAudioCandidate> = emptyList(),
)

@Serializable
private data class GeminiAudioCandidate(
  val content: GeminiAudioContent? = null,
)
