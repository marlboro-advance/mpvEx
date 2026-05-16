package app.gyrolet.mpvrx.repository.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class GroqSpeechClient(
  private val client: OkHttpClient,
  private val json: Json,
) : SpeechToTextClient {
  companion object {
    private const val BASE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val MAX_FILE_BYTES = 22L * 1024 * 1024
    private val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()
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
      val fileSizeMb = audioFile.length() / (1024 * 1024)
      if (audioFile.length() > MAX_FILE_BYTES) {
        throw Exception("Audio file too large ($fileSizeMb MB) for Groq 25MB limit. Try a shorter video or a different STT provider.")
      }

      val bodyBuilder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", "whisper-large-v3-turbo")
        .addFormDataPart("response_format", "verbose_json")
        .addFormDataPart("temperature", "0")
        .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(AUDIO_MEDIA_TYPE))

      languageCode(language)?.let { bodyBuilder.addFormDataPart("language", it) }

      val request = Request.Builder()
        .url(BASE_URL)
        .header("Authorization", "Bearer $apiKey")
        .post(bodyBuilder.build())
        .build()

      val response = apiClient.newCall(request).execute()
      val responseBody = response.body.string()
      if (!response.isSuccessful) {
        throw Exception("Groq transcription failed: HTTP ${response.code} ${responseBody.take(240)}")
      }

      val parsed = json.decodeFromString(GroqTranscriptionResponse.serializer(), responseBody)
      SpeechTranscript(
        text = parsed.text.orEmpty().trim(),
        segments = parsed.segments.mapNotNull { segment ->
          val text = segment.text?.trim().orEmpty()
          if (text.isBlank()) return@mapNotNull null
          SpeechSegment(
            startMs = (segment.start * 1000).toLong(),
            endMs = (segment.end * 1000).toLong(),
            text = text,
          )
        },
      )
    }
  }

  private fun languageCode(language: String?): String? {
    if (language.isNullOrBlank()) return null
    return when (language.lowercase(Locale.ROOT)) {
      "english" -> "en"
      "hindi" -> "hi"
      "spanish" -> "es"
      "french" -> "fr"
      "german" -> "de"
      "japanese" -> "ja"
      "korean" -> "ko"
      "chinese", "chinese (simplified)", "chinese (traditional)" -> "zh"
      "arabic" -> "ar"
      "portuguese" -> "pt"
      "russian" -> "ru"
      "italian" -> "it"
      else -> null
    }
  }
}

@Serializable
private data class GroqTranscriptionResponse(
  val text: String? = null,
  val segments: List<GroqTranscriptionSegment> = emptyList(),
)

@Serializable
private data class GroqTranscriptionSegment(
  val start: Double = 0.0,
  val end: Double = 0.0,
  val text: String? = null,
)
