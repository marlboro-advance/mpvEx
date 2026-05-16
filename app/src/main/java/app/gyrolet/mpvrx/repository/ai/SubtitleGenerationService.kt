package app.gyrolet.mpvrx.repository.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

data class SubtitleGenerationProgress(
  val progress: Float,
  val stage: String,
)

@kotlinx.serialization.Serializable
private data class SttResponse(
  val text: String? = null,
  val segments: List<SttSegment> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class SttSegment(
  val start: Double = 0.0,
  val end: Double = 0.0,
  val text: String? = null,
)

data class GeneratedSubtitle(
  val content: String,
  val extension: String,
)

class SubtitleGenerationService(
  private val context: Context,
  private val preferences: AiPreferences,
  private val groqSpeechClient: GroqSpeechClient,
  private val geminiSpeechClient: GeminiSpeechClient,
  private val okHttpClient: OkHttpClient,
  private val json: Json,
) {
  companion object {
    private const val CHUNK_DURATION_MS = 15_000L
    private const val CHUNK_OVERLAP_MS = 5_000L
    private const val CHUNK_STEP_MS = CHUNK_DURATION_MS - CHUNK_OVERLAP_MS
  }

  suspend fun generateSubtitles(
    videoUri: Uri,
    language: String?,
    outputFormat: String,
    onProgress: (SubtitleGenerationProgress) -> Unit = {},
  ): Result<GeneratedSubtitle> = withContext(Dispatchers.IO) {
    runCatching {
      val normalizedFormat = outputFormat.lowercase(Locale.ROOT).let {
        if (it == "vtt") "vtt" else "srt"
      }

      val videoDurationMs = getVideoDurationMs(videoUri)
      val totalChunks = ((videoDurationMs + CHUNK_STEP_MS - 1) / CHUNK_STEP_MS).toInt()
      val allSegments = mutableListOf<SpeechSegment>()
      var previousEndMs = 0L

      for (chunkIndex in 0 until totalChunks) {
        if (!isActive) break

        val chunkStartMs = chunkIndex * CHUNK_STEP_MS
        val chunkEndMs = (chunkStartMs + CHUNK_DURATION_MS).coerceAtMost(videoDurationMs)

        val extractProgress = 0.1f + (chunkIndex.toFloat() / totalChunks) * 0.35f
        onProgress(
          SubtitleGenerationProgress(
            extractProgress,
            "Extracting chunk ${chunkIndex + 1}/$totalChunks",
          ),
        )

        val audioChunk = extractAudioChunk(videoUri, chunkStartMs, chunkEndMs)
        if (audioChunk == null) continue

        try {
          val transcribeProgress = 0.45f + (chunkIndex.toFloat() / totalChunks) * 0.45f
          onProgress(
            SubtitleGenerationProgress(
              transcribeProgress,
              "Transcribing chunk ${chunkIndex + 1}/$totalChunks",
            ),
          )

          val transcript = transcribe(audioChunk, language).getOrNull()
          if (transcript == null || transcript.segments.isEmpty()) continue

          val newSegments = transcript.segments.filter { segment ->
            segment.endMs > previousEndMs
          }.map { segment ->
            SpeechSegment(
              startMs = chunkStartMs + segment.startMs,
              endMs = chunkStartMs + segment.endMs,
              text = segment.text,
            )
          }

          if (newSegments.isNotEmpty()) {
            allSegments.addAll(newSegments)
            allSegments.sortBy { it.startMs }
            deduplicateSegments(allSegments)
            previousEndMs = allSegments.lastOrNull()?.endMs ?: 0L
          }
        } finally {
          audioChunk.delete()
        }
      }

      onProgress(SubtitleGenerationProgress(0.9f, "Writing subtitle"))

      val segments = allSegments.ifEmpty {
        createHeuristicSegments("")
      }
      val content = if (normalizedFormat == "vtt") {
        toVtt(segments)
      } else {
        toSrt(segments)
      }

      onProgress(SubtitleGenerationProgress(1f, "Subtitle ready"))
      GeneratedSubtitle(content = content, extension = normalizedFormat)
    }
  }

  private fun getVideoDurationMs(videoUri: Uri): Long {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(context, videoUri, null)
      val durationUs = extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION)
      return durationUs / 1000
    } finally {
      extractor.release()
    }
  }

  private suspend fun extractAudioChunk(
    videoUri: Uri,
    startMs: Long,
    endMs: Long,
  ): File? = withContext(Dispatchers.IO) {
    runCatching {
      val extractor = MediaExtractor()
      var outputFile: File? = null
      try {
        extractor.setDataSource(context, videoUri, null)

        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
          if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            break
          }
        }
        if (audioTrackIndex < 0) return@runCatching null

        val audioFormat = extractor.getTrackFormat(audioTrackIndex)
        val audioMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
        val isWebm = audioMime == "audio/opus" || audioMime == "audio/vorbis"

        outputFile = File.createTempFile(
          "sub_chunk_",
          if (isWebm) ".webm" else ".m4a",
          context.cacheDir,
        )
        if (!isActive) { outputFile!!.delete(); return@runCatching null }

        extractor.selectTrack(audioTrackIndex)
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val muxer = MediaMuxer(
          outputFile!!.absolutePath,
          if (isWebm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
          else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        try {
          val muxerTrackIndex = muxer.addTrack(audioFormat)
          muxer.start()

          val buffer = ByteBuffer.allocate(512 * 1024)
          val bufferInfo = MediaCodec.BufferInfo()

          while (isActive) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs > endMs * 1000) break

            bufferInfo.set(0, sampleSize, sampleTimeUs, extractor.sampleFlags)
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            extractor.advance()
          }

          muxer.stop()
        } finally {
          muxer.release()
        }
      } finally {
        extractor.release()
      }

      if (!isActive) { outputFile!!.delete(); return@runCatching null }
      outputFile
    }.getOrNull()
  }

  private suspend fun transcribe(
    audioFile: File,
    language: String?,
  ): Result<SpeechTranscript> {
    val provider = preferences.sttProvider.get()
    return when (provider) {
      AiProvider.GROQ -> {
        val key = preferences.groqApiKey.get()
        if (key.isBlank()) Result.failure(Exception("Groq API key not configured."))
        else groqSpeechClient.transcribe(key, audioFile, language)
      }
      AiProvider.GEMINI -> {
        val key = preferences.geminiApiKey.get()
        if (key.isBlank()) Result.failure(Exception("Gemini API key not configured."))
        else geminiSpeechClient.transcribe(key, audioFile, language)
      }
      AiProvider.OPENAI -> {
        val key = preferences.openaiApiKey.get()
        if (key.isBlank()) Result.failure(Exception("OpenAI API key not configured."))
        else transcribeOpenAiCompatible("https://api.openai.com/v1/audio/transcriptions", key, "whisper-1", audioFile, language)
      }
      AiProvider.OPENROUTER -> {
        val key = preferences.openrouterApiKey.get()
        if (key.isBlank()) Result.failure(Exception("OpenRouter API key not configured."))
        else transcribeOpenAiCompatible("https://openrouter.ai/api/v1/audio/transcriptions", key, "openai/whisper-1", audioFile, language)
      }
      else -> Result.failure(Exception("Only online providers (Groq, Gemini, OpenAI, OpenRouter) are supported."))
    }
  }

  private suspend fun transcribeOpenAiCompatible(
    baseUrl: String,
    apiKey: String,
    model: String,
    audioFile: File,
    language: String?,
  ): Result<SpeechTranscript> = withContext(Dispatchers.IO) {
    runCatching {
      val apiClient = okHttpClient.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

      val bodyBuilder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", model)
        .addFormDataPart("response_format", "verbose_json")
        .addFormDataPart("temperature", "0")
        .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(audioMediaType(audioFile)))

      if (!language.isNullOrBlank()) bodyBuilder.addFormDataPart("language", language)

      val request = Request.Builder()
        .url(baseUrl)
        .header("Authorization", "Bearer $apiKey")
        .post(bodyBuilder.build())
        .build()

      val response = apiClient.newCall(request).execute()
      val responseBody = response.body.string()
      if (!response.isSuccessful) {
        throw Exception("STT failed: HTTP ${response.code} ${responseBody.take(240)}")
      }

      val parsed = json.decodeFromString(SttResponse.serializer(), responseBody)
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

  private fun createHeuristicSegments(text: String): List<SpeechSegment> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return words.chunked(9).mapIndexed { index, chunk ->
      val start = index * 3500L
      SpeechSegment(
        startMs = start,
        endMs = start + 3200L,
        text = chunk.joinToString(" "),
      )
    }
  }

  private fun deduplicateSegments(segments: MutableList<SpeechSegment>) {
    var i = 0
    while (i < segments.size - 1) {
      val current = segments[i]
      val next = segments[i + 1]
      if (current.endMs >= next.startMs && current.text == next.text) {
        segments.removeAt(i + 1)
      } else {
        i++
      }
    }
  }

  private fun toSrt(segments: List<SpeechSegment>): String =
    segments.mapIndexed { index, segment ->
      "${index + 1}\n${formatSrtTime(segment.startMs)} --> ${formatSrtTime(segment.endMs)}\n${segment.text.trim()}"
    }.joinToString("\n\n")

  private fun toVtt(segments: List<SpeechSegment>): String =
    "WEBVTT\n\n" + segments.joinToString("\n\n") { segment ->
      "${formatVttTime(segment.startMs)} --> ${formatVttTime(segment.endMs)}\n${segment.text.trim()}"
    }

  private fun formatSrtTime(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    val seconds = (ms % 60_000) / 1000
    val millis = ms % 1000
    return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
  }

  private fun formatVttTime(ms: Long): String = formatSrtTime(ms).replace(',', '.')
}

private fun audioMediaType(file: File): okhttp3.MediaType {
  val ext = file.name.substringAfterLast('.', "").lowercase()
  return when (ext) {
    "webm" -> "audio/webm".toMediaType()
    else -> "audio/mp4".toMediaType()
  }
}
