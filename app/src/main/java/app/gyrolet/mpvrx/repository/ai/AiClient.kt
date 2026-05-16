package app.gyrolet.mpvrx.repository.ai

data class AiGenerationOptions(
  val maxTokens: Int = 200,
  val temperature: Double = 0.3,
)

interface AiClient {
  suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>>
  suspend fun verifyKey(apiKey: String): Result<String>
  suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions = AiGenerationOptions(),
  ): Result<String>
}
