package app.gyrolet.mpvrx.preferences

import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.preferences.preference.getEnum

enum class AiProvider(val displayName: String) {
  GEMINI("Gemini"),
  GROQ("Groq"),
  OPENAI("OpenAI"),
  ANTHROPIC("Anthropic"),
  OPENROUTER("OpenRouter"),
  TOGETHER("Together"),
  LOCAL("Offline Model"),
}

class AiPreferences(
  preferenceStore: PreferenceStore,
) {
  val enabled = preferenceStore.getBoolean("ai_enabled", false)

  val provider = preferenceStore.getEnum("ai_provider", AiProvider.GEMINI)

  val geminiApiKey = preferenceStore.getString("ai_gemini_api_key", "")
  val groqApiKey = preferenceStore.getString("ai_groq_api_key", "")
  val openaiApiKey = preferenceStore.getString("ai_openai_api_key", "")
  val anthropicApiKey = preferenceStore.getString("ai_anthropic_api_key", "")
  val openrouterApiKey = preferenceStore.getString("ai_openrouter_api_key", "")
  val togetherApiKey = preferenceStore.getString("ai_together_api_key", "")

  val selectedModel = preferenceStore.getString("ai_selected_model", "")

  val availableModels = preferenceStore.getString("ai_available_models", "[]")

  val localModelId = preferenceStore.getString("ai_local_model_id", "")
  val localModelPath = preferenceStore.getString("ai_local_model_path", "")
  val localModelDownloaded = preferenceStore.getBoolean("ai_local_model_downloaded", false)
  val localModelDownloadProgress = preferenceStore.getFloat("ai_local_model_download_progress", 0f)
  val localModelBenchmarks = preferenceStore.getString("ai_local_model_benchmarks", "[]")
  val huggingfaceToken = preferenceStore.getString("ai_huggingface_token", "")

  val subtitleGenerationOutputFormat = preferenceStore.getString("ai_subtitle_generation_output_format", "srt")

  // Speech-to-text (real-time subs + batch generation)
  val sttProvider = preferenceStore.getEnum("ai_stt_provider", AiProvider.GROQ)
  val sttModel = preferenceStore.getString("ai_stt_model", "")
  val sttAvailableModels = preferenceStore.getString("ai_stt_available_models", "[]")
  val sttLanguage = preferenceStore.getString("ai_stt_language", "")

  // Auto-translate target languages (comma-separated codes: "en,es,fr")
  val autoTranslateLanguages = preferenceStore.getString("ai_auto_translate_languages", "")

  val customPromptEnabled = preferenceStore.getBoolean("ai_custom_prompt_enabled", false)
  val customPrompt = preferenceStore.getString("ai_custom_prompt", "")
  val customRenamePrompt = preferenceStore.getString("ai_custom_rename_prompt", "")
  val customSubtitleTranslationPrompt = preferenceStore.getString("ai_custom_subtitle_translation_prompt", "")
  val customSubtitleFormatPrompt = preferenceStore.getString("ai_custom_subtitle_format_prompt", "")

  val renameWithAi = preferenceStore.getBoolean("ai_rename_enabled", true)
  val subtitleFormatWithAi = preferenceStore.getBoolean("ai_subtitle_format_enabled", true)
  val subtitleTranslationEnabled = preferenceStore.getBoolean("ai_subtitle_translation_enabled", false)
  val subtitleTranslationFirstTime = preferenceStore.getBoolean("ai_subtitle_translation_first_time", true)

  val showThinking = preferenceStore.getBoolean("ai_show_thinking", true)

  val lastVerified = preferenceStore.getLong("ai_last_verified", 0L)
}
