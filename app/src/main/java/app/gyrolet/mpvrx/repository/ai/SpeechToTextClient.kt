package app.gyrolet.mpvrx.repository.ai

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class SpeechSegment(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

@Serializable
data class SpeechTranscript(
  val text: String,
  val segments: List<SpeechSegment> = emptyList(),
)

interface SpeechToTextClient {
  suspend fun transcribe(
    apiKey: String,
    audioFile: File,
    language: String?,
  ): Result<SpeechTranscript>
}
