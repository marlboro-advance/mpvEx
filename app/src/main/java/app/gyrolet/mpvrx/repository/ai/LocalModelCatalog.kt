package app.gyrolet.mpvrx.repository.ai

import kotlinx.serialization.Serializable

enum class LocalModelTier(
  val label: String,
  val sortWeight: Int,
) {
  FASTEST("Fastest output", 0),
  BALANCED("Best balanced", 1),
  MULTILINGUAL("Best translation", 2),
  UTILITY("Utility only", 3),
}

enum class LocalModelLanguageTier(
  val label: String,
) {
  BROAD("Broad multilingual"),
  TARGETED("Targeted languages"),
  LIMITED("Limited multilingual"),
  UTILITY("Not for translation"),
}

@Serializable
data class LocalModelInfo(
  val id: String,
  val displayName: String,
  val hfRepo: String,
  val hfFile: String,
  val quantSizeMb: Int,
  val minRamGb: Int,
  val contextLength: Int,
  val description: String,
  val chatTemplate: String = "qwen",
  val supportsThinking: Boolean = false,
  val tier: LocalModelTier = LocalModelTier.FASTEST,
  val languageTier: LocalModelLanguageTier = LocalModelLanguageTier.BROAD,
  val speedRank: Int = 50,
  val translationRank: Int = 50,
  val sourceUrl: String = "",
  val bestLanguages: List<String> = emptyList(),
  val weakLanguages: List<String> = emptyList(),
) {
  val sizeLabel: String
    get() = if (quantSizeMb >= 1024) {
      "%.1f GB".format(quantSizeMb / 1024f)
    } else {
      "$quantSizeMb MB"
    }

  fun isGoodForLanguage(language: String): Boolean {
    val normalized = language.lowercase()
    return bestLanguages.isEmpty() || bestLanguages.any { normalized.contains(it.lowercase()) }
  }
}

object LocalModelCatalog {
  private val europeanLanguages = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese", "Dutch",
    "Polish", "Romanian", "Swedish", "Norwegian", "Danish", "Finnish", "Greek",
    "Czech", "Slovak", "Slovenian", "Croatian", "Bulgarian", "Ukrainian", "Russian",
  )

  private val southAsianLanguages = listOf(
    "Hindi", "Urdu", "Bengali", "Gujarati", "Marathi", "Punjabi", "Tamil",
    "Telugu", "Malayalam", "Kannada",
  )

  private val asianLanguages = listOf(
    "Chinese", "Japanese", "Korean", "Thai", "Vietnamese", "Indonesian", "Malay",
  )

  val models: List<LocalModelInfo> = listOf(
    LocalModelInfo(
      id = "qwen3-0.6b-q4",
      displayName = "Qwen3 0.6B Q4",
      hfRepo = "unsloth/Qwen3-0.6B-GGUF",
      hfFile = "Qwen3-0.6B-Q4_K_M.gguf",
      quantSizeMb = 378,
      minRamGb = 2,
      contextLength = 32768,
      description = "Smallest useful default. Loads fast, answers fast, good first pick for subtitle translation on most phones.",
      tier = LocalModelTier.FASTEST,
      languageTier = LocalModelLanguageTier.BROAD,
      speedRank = 98,
      translationRank = 72,
      sourceUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF",
      bestLanguages = europeanLanguages + southAsianLanguages + asianLanguages,
    ),
    LocalModelInfo(
      id = "gemma-3-1b-q4",
      displayName = "Gemma 3 1B Q4",
      hfRepo = "ggml-org/gemma-3-1b-it-GGUF",
      hfFile = "gemma-3-1b-it-Q4_K_M.gguf",
      quantSizeMb = 770,
      minRamGb = 3,
      contextLength = 32768,
      description = "Fast 1B model with stronger wording than tiny models. Good when Qwen feels too literal.",
      chatTemplate = "gemma",
      tier = LocalModelTier.FASTEST,
      languageTier = LocalModelLanguageTier.BROAD,
      speedRank = 92,
      translationRank = 76,
      sourceUrl = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF",
      bestLanguages = europeanLanguages + southAsianLanguages + asianLanguages,
    ),
    LocalModelInfo(
      id = "llama-3.2-1b-q4",
      displayName = "Llama 3.2 1B Q4",
      hfRepo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
      hfFile = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
      quantSizeMb = 770,
      minRamGb = 2,
      contextLength = 32768,
      description = "Very responsive for English and common languages. Better for rename/cleanup than broad translation.",
      chatTemplate = "llama",
      tier = LocalModelTier.FASTEST,
      languageTier = LocalModelLanguageTier.LIMITED,
      speedRank = 94,
      translationRank = 58,
      sourceUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
      bestLanguages = listOf("English", "German", "French", "Italian", "Portuguese", "Hindi", "Spanish", "Thai"),
      weakLanguages = listOf("Arabic", "Bengali", "Tamil", "Telugu", "Urdu"),
    ),
    LocalModelInfo(
      id = "qwen3-1.7b-q4",
      displayName = "Qwen3 1.7B Q4",
      hfRepo = "bartowski/Qwen_Qwen3-1.7B-GGUF",
      hfFile = "Qwen_Qwen3-1.7B-Q4_K_M.gguf",
      quantSizeMb = 1060,
      minRamGb = 4,
      contextLength = 32768,
      description = "Best speed-to-quality upgrade from Qwen 0.6B. Recommended when the phone has enough RAM.",
      tier = LocalModelTier.BALANCED,
      languageTier = LocalModelLanguageTier.BROAD,
      speedRank = 84,
      translationRank = 84,
      sourceUrl = "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF",
      bestLanguages = europeanLanguages + southAsianLanguages + asianLanguages,
    ),
    LocalModelInfo(
      id = "tiny-aya-global-q4",
      displayName = "Tiny Aya Global Q4",
      hfRepo = "CohereLabs/tiny-aya-global-GGUF",
      hfFile = "tiny-aya-global-q4_k_m.gguf",
      quantSizeMb = 2140,
      minRamGb = 5,
      contextLength = 8192,
      description = "Best pick for broad and lower-resource multilingual subtitles. Heavier, but worth it when translation quality matters.",
      chatTemplate = "gemma",
      tier = LocalModelTier.MULTILINGUAL,
      languageTier = LocalModelLanguageTier.BROAD,
      speedRank = 66,
      translationRank = 96,
      sourceUrl = "https://huggingface.co/CohereLabs/tiny-aya-global-GGUF",
      bestLanguages = europeanLanguages + southAsianLanguages + asianLanguages + listOf(
        "Arabic", "Persian", "Hebrew", "Swahili", "Amharic", "Yoruba", "Zulu",
      ),
    ),
    LocalModelInfo(
      id = "gemma-3-4b-q4",
      displayName = "Gemma 3 4B Q4",
      hfRepo = "bartowski/google_gemma-3-4b-it-GGUF",
      hfFile = "google_gemma-3-4b-it-Q4_K_M.gguf",
      quantSizeMb = 2600,
      minRamGb = 6,
      contextLength = 32768,
      description = "Higher quality balanced model for stronger phones. Use when output quality beats raw speed.",
      chatTemplate = "gemma",
      tier = LocalModelTier.BALANCED,
      languageTier = LocalModelLanguageTier.BROAD,
      speedRank = 58,
      translationRank = 88,
      sourceUrl = "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF",
      bestLanguages = europeanLanguages + southAsianLanguages + asianLanguages,
    ),
    LocalModelInfo(
      id = "phi-4-mini-q4",
      displayName = "Phi-4 Mini Q4",
      hfRepo = "bartowski/microsoft_Phi-4-mini-instruct-GGUF",
      hfFile = "microsoft_Phi-4-mini-instruct-Q4_K_M.gguf",
      quantSizeMb = 2380,
      minRamGb = 5,
      contextLength = 32768,
      description = "Utility model for rename, formatting, and reasoning-style cleanup. Not the default for subtitles.",
      chatTemplate = "phi",
      tier = LocalModelTier.UTILITY,
      languageTier = LocalModelLanguageTier.UTILITY,
      speedRank = 62,
      translationRank = 45,
      sourceUrl = "https://huggingface.co/bartowski/microsoft_Phi-4-mini-instruct-GGUF",
    ),
  )

  fun getById(id: String): LocalModelInfo? = models.find { it.id == id }

  fun speedFirst(ramMb: Int): List<LocalModelInfo> =
    models.sortedWith(
      compareByDescending<LocalModelInfo> { it.minRamGb * 1024 <= ramMb }
        .thenBy { it.tier.sortWeight }
        .thenByDescending { it.speedRank }
        .thenByDescending { it.translationRank },
    )

  fun recommendedForRam(ramMb: Int): List<LocalModelInfo> =
    speedFirst(ramMb).filter { it.minRamGb * 1024 <= ramMb }

  fun bestForTranslation(targetLanguage: String, ramMb: Int): LocalModelInfo? {
    val installedOrder = speedFirst(ramMb)
    val lowerLanguage = targetLanguage.lowercase()
    return installedOrder
      .filter { it.tier != LocalModelTier.UTILITY }
      .sortedWith(
        compareByDescending<LocalModelInfo> { it.bestLanguages.any { lang -> lowerLanguage.contains(lang.lowercase()) } }
          .thenByDescending { it.translationRank }
          .thenByDescending { it.speedRank },
      )
      .firstOrNull()
  }

  fun languageHint(model: LocalModelInfo, targetLanguage: String): String {
    if (targetLanguage.isBlank()) return model.languageTier.label
    val lowerLanguage = targetLanguage.lowercase()
    if (model.weakLanguages.any { lowerLanguage.contains(it.lowercase()) }) {
      return "Limited for $targetLanguage"
    }
    if (model.isGoodForLanguage(targetLanguage)) {
      return "Good for $targetLanguage"
    }
    return model.languageTier.label
  }

  fun formatPrompt(modelId: String, system: String, user: String): String {
    val info = getById(modelId)
    return when (info?.chatTemplate) {
      "gemma" -> "<bos><start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"
      "llama" -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
      "phi" -> "<|system|>\n$system<|end|>\n<|user|>\n$user<|end|>\n<|assistant|>\n"
      "smollm" -> "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
      else -> "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
    }
  }
}
