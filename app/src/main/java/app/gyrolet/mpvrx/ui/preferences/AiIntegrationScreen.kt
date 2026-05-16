package app.gyrolet.mpvrx.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import app.gyrolet.mpvrx.ui.icons.Icon
import me.zhanghai.compose.preference.TextFieldPreference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.AiPreferences
import app.gyrolet.mpvrx.preferences.AiProvider
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.repository.ai.AiModelInfo
import app.gyrolet.mpvrx.repository.ai.AiService
import app.gyrolet.mpvrx.repository.ai.LocalModelCatalog
import app.gyrolet.mpvrx.repository.ai.LocalModelInfo
import app.gyrolet.mpvrx.repository.ai.ModelDownloadManager
import app.gyrolet.mpvrx.repository.ai.DownloadProgress
import app.gyrolet.mpvrx.repository.ai.LocalModelBenchmark
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import app.gyrolet.mpvrx.ui.preferences.PreferenceCard
import app.gyrolet.mpvrx.ui.preferences.PreferenceDivider
import app.gyrolet.mpvrx.ui.preferences.PreferenceSectionHeader
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

private val allLanguages = mapOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
    "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "zh" to "Chinese",
    "ja" to "Japanese", "ko" to "Korean", "ar" to "Arabic", "hi" to "Hindi",
    "bn" to "Bengali", "vi" to "Vietnamese", "te" to "Telugu", "ta" to "Tamil",
    "ur" to "Urdu", "tr" to "Turkish", "pl" to "Polish", "uk" to "Ukrainian",
    "nl" to "Dutch", "el" to "Greek", "hu" to "Hungarian", "sv" to "Swedish",
    "cs" to "Czech", "ro" to "Romanian", "da" to "Danish", "fi" to "Finnish",
    "no" to "Norwegian", "he" to "Hebrew", "id" to "Indonesian", "th" to "Thai",
    "ms" to "Malay", "fa" to "Persian", "sk" to "Slovak", "bg" to "Bulgarian",
    "hr" to "Croatian", "sr" to "Serbian", "sl" to "Slovenian", "et" to "Estonian",
    "lv" to "Latvian", "lt" to "Lithuanian", "af" to "Afrikaans", "sw" to "Swahili",
)

@Serializable
object AiIntegrationScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<AiPreferences>()
    val aiService = koinInject<AiService>()
    val scope = rememberCoroutineScope()

    val enabled by preferences.enabled.collectAsState()
    val provider by preferences.provider.collectAsState()
    val geminiKey by preferences.geminiApiKey.collectAsState()
    val groqKey by preferences.groqApiKey.collectAsState()
    val openaiKey by preferences.openaiApiKey.collectAsState()
    val anthropicKey by preferences.anthropicApiKey.collectAsState()
    val openrouterKey by preferences.openrouterApiKey.collectAsState()
    val togetherKey by preferences.togetherApiKey.collectAsState()
    val selectedModel by preferences.selectedModel.collectAsState()
    val localModelId by preferences.localModelId.collectAsState()
    val localModelDownloaded by preferences.localModelDownloaded.collectAsState()
    val huggingfaceToken by preferences.huggingfaceToken.collectAsState()
    val customPromptEnabled by preferences.customPromptEnabled.collectAsState()
    val customPrompt by preferences.customPrompt.collectAsState()
    val customRenamePrompt by preferences.customRenamePrompt.collectAsState()
    val customSubtitleTranslationPrompt by preferences.customSubtitleTranslationPrompt.collectAsState()
    val customSubtitleFormatPrompt by preferences.customSubtitleFormatPrompt.collectAsState()
    val renameWithAi by preferences.renameWithAi.collectAsState()
    val subtitleFormatWithAi by preferences.subtitleFormatWithAi.collectAsState()
    val subtitleTranslationEnabled by preferences.subtitleTranslationEnabled.collectAsState()
    val subtitleTranslationFirstTime by preferences.subtitleTranslationFirstTime.collectAsState()
    val subtitleGenerationOutputFormat by preferences.subtitleGenerationOutputFormat.collectAsState()
    val autoTranslateLanguages by preferences.autoTranslateLanguages.collectAsState()

    var models by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<String?>(null) }
    var isVerifyingModel by remember { mutableStateOf(false) }
    var verifyModelResult by remember { mutableStateOf<String?>(null) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var showSubtitleTranslationWarning by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var localModelSort by remember { mutableStateOf("Recommended") }
    var benchmarks by remember { mutableStateOf(aiService.getLocalModelBenchmarks()) }
    var benchmarkingModelId by remember { mutableStateOf<String?>(null) }

    val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val ramMb = (memoryInfo.totalMem / (1024 * 1024)).toInt()
    val recommendedModelIds = remember(ramMb) { LocalModelCatalog.recommendedForRam(ramMb).map { it.id }.toSet() }

    val json = koinInject<Json>()

    fun loadModels() {
      scope.launch {
        isLoadingModels = true
        modelLoadError = null
        aiService.fetchModels()
          .onSuccess { fetchedModels ->
            models = fetchedModels
            preferences.availableModels.set(json.encodeToString(
              kotlinx.serialization.builtins.ListSerializer(AiModelInfo.serializer()),
              fetchedModels,
            ))
          }
          .onFailure { e ->
            modelLoadError = e.message
          }
        isLoadingModels = false
      }
    }

    LaunchedEffect(provider) {
      val stored = preferences.availableModels.get()
      if (stored.isNotBlank() && stored != "[]") {
        try {
          models = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AiModelInfo.serializer()),
            stored,
          )
        } catch (_: Exception) {}
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "AI Integration",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backstack.popSafely() }) {
              Icon(
                Icons.Default.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          item { PreferenceSectionHeader(title = "AI Features") }

          item {
            PreferenceCard {
              SwitchPreference(
                value = enabled,
                onValueChange = { preferences.enabled.set(it) },
                title = { Text("Enable AI Features") },
                summary = {
                  Text(
                    if (enabled) "AI features are active" else "AI features are disabled",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          if (enabled) {
            item { PreferenceSectionHeader(title = "Provider") }

            item {
              PreferenceCard {
                val providers = AiProvider.values().toList()
                ListPreference(
                  value = provider,
                  onValueChange = {
                    preferences.provider.set(it)
                    preferences.selectedModel.set("")
                  },
                  values = providers,
                  valueToText = { androidx.compose.ui.text.AnnotatedString(it.displayName) },
                  title = { Text("AI Provider") },
                  summary = {
                    Text(provider.displayName, color = MaterialTheme.colorScheme.outline)
                  },
                )
              }
            }

            if (provider == AiProvider.LOCAL) {
              item { PreferenceSectionHeader(title = "Hugging Face Setup") }
              
              item {
                PreferenceCard {
                  TextFieldPreference(
                    value = huggingfaceToken,
                    onValueChange = preferences.huggingfaceToken::set,
                    textToValue = { it.trim() },
                    title = { Text("Hugging Face Token") },
                    summary = {
                      if (huggingfaceToken.isBlank()) {
                        Text("Required for gated models. Get one from huggingface.co/settings/tokens", color = MaterialTheme.colorScheme.error)
                      } else {
                        Text("Token saved on device", color = MaterialTheme.colorScheme.outline)
                      }
                    },
                    textField = { value, onValueChange, _ ->
                      Column {
                        Text("Paste your Hugging Face token")
                        TextField(
                          value = value,
                          onValueChange = onValueChange,
                          modifier = Modifier.fillMaxWidth(),
                          placeholder = { Text("hf_...") },
                          singleLine = true,
                          visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                      }
                    },
                  )
                }
              }

              item { PreferenceSectionHeader(title = "Offline Models") }

              item {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "Speed-first model picker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "Models are ranked by how fast they feel after loading, RAM pressure, and subtitle translation quality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                  )
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                  ) {
                    listOf("Recommended", "Fastest", "Best translation", "Downloaded").forEach { mode ->
                      FilterChip(
                        selected = localModelSort == mode,
                        onClick = { localModelSort = mode },
                        label = { Text(mode, maxLines = 1) },
                      )
                    }
                  }
                }
              }

              val visibleLocalModels = when (localModelSort) {
                "Fastest" -> LocalModelCatalog.models.sortedByDescending { it.speedRank }
                "Best translation" -> LocalModelCatalog.models.sortedByDescending { it.translationRank }
                "Downloaded" -> LocalModelCatalog.models.sortedWith(
                  compareByDescending<LocalModelInfo> { aiService.isLocalModelDownloaded(it.id) }
                    .thenBy { it.tier.sortWeight }
                    .thenByDescending { it.speedRank },
                )
                else -> LocalModelCatalog.speedFirst(ramMb)
              }

              items(visibleLocalModels) { model ->
                  val isDownloaded = remember(model.id, isDownloading) {
                      aiService.isLocalModelDownloaded(model.id)
                  }
                  val isSelected = localModelId == model.id
                  val isThisDownloading = isDownloading && localModelId == model.id
                  val benchmark = benchmarks.firstOrNull { it.modelId == model.id }

                  OfflineModelCard(
                      model = model,
                      isDownloaded = isDownloaded,
                      isSelected = isSelected,
                      isDownloading = isThisDownloading,
                      isBenchmarking = benchmarkingModelId == model.id,
                      isRecommended = recommendedModelIds.contains(model.id),
                      benchmark = benchmark,
                      downloadProgress = if (isThisDownloading) downloadProgress else null,
                      onDownload = {
                          preferences.localModelId.set(model.id)
                          isDownloading = true
                          downloadProgress = null
                          scope.launch {
                              aiService.downloadLocalModel(model.id)
                                  .onSuccess {
                                      downloadProgress = DownloadProgress(isComplete = true)
                                      Toast.makeText(context, "Model downloaded successfully", Toast.LENGTH_SHORT).show()
                                      if (model.supportsThinking) {
                                          preferences.showThinking.set(true)
                                      }
                                  }
                                  .onFailure { e ->
                                      downloadProgress = DownloadProgress(error = e.message)
                                      Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                  }
                              isDownloading = false
                          }
                      },
                      onDelete = {
                          if (aiService.deleteLocalModel(model.id)) {
                              Toast.makeText(context, "Model deleted", Toast.LENGTH_SHORT).show()
                          }
                      },
                      onSelect = {
                          preferences.localModelId.set(model.id)
                          if (model.supportsThinking) {
                              preferences.showThinking.set(true)
                          }
                          Toast.makeText(context, "Using ${model.displayName}", Toast.LENGTH_SHORT).show()
                      },
                      onBenchmark = {
                          benchmarkingModelId = model.id
                          scope.launch {
                              aiService.benchmarkLocalModel(model.id)
                                  .onSuccess {
                                      benchmarks = aiService.getLocalModelBenchmarks()
                                      Toast.makeText(context, "Benchmark saved: ${it.speedLabel}", Toast.LENGTH_SHORT).show()
                                  }
                                  .onFailure { e ->
                                      Toast.makeText(context, "Benchmark failed: ${e.message}", Toast.LENGTH_LONG).show()
                                  }
                              benchmarkingModelId = null
                          }
                      }
                  )
              }
              
              item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            if (provider != AiProvider.LOCAL) {
              val apiKeyInfo = when (provider) {
                AiProvider.GEMINI -> ApiKeyInfo("Gemini API Key", "Get your key from aistudio.google.com", "AIza...", geminiKey, preferences.geminiApiKey::set)
                AiProvider.GROQ -> ApiKeyInfo("Groq API Key", "Get your key from console.groq.com", "gsk_...", groqKey, preferences.groqApiKey::set)
                AiProvider.OPENAI -> ApiKeyInfo("OpenAI API Key", "Get your key from platform.openai.com/api-keys", "sk-...", openaiKey, preferences.openaiApiKey::set)
                AiProvider.ANTHROPIC -> ApiKeyInfo("Anthropic API Key", "Get your key from console.anthropic.com", "sk-ant-...", anthropicKey, preferences.anthropicApiKey::set)
                AiProvider.OPENROUTER -> ApiKeyInfo("OpenRouter API Key", "Get your key from openrouter.ai/keys", "sk-or-...", openrouterKey, preferences.openrouterApiKey::set)
                AiProvider.TOGETHER -> ApiKeyInfo("Together API Key", "Get your key from api.together.xyz/settings/api-keys", "...", togetherKey, preferences.togetherApiKey::set)
                else -> null
              }

              if (apiKeyInfo != null) {
                item { PreferenceSectionHeader(title = "API Configuration") }

                item {
                  PreferenceCard {
                    TextFieldPreference(
                      value = apiKeyInfo.apiKey,
                      onValueChange = apiKeyInfo.onChange,
                      textToValue = { it.trim() },
                      title = { Text(apiKeyInfo.title) },
                      summary = {
                        if (apiKeyInfo.apiKey.isBlank()) {
                          Text(apiKeyInfo.hint, color = MaterialTheme.colorScheme.error)
                        } else {
                          Text("API key saved on device", color = MaterialTheme.colorScheme.outline)
                        }
                      },
                      textField = { value, onValueChange, _ ->
                        Column {
                          Text("Paste your ${apiKeyInfo.title}")
                          TextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(apiKeyInfo.placeholder) },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                          )
                        }
                      },
                    )

                    PreferenceDivider()

                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      Button(
                        onClick = { showApiKey = !showApiKey },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondaryContainer,
                          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                      ) {
                        Text(if (showApiKey) "Hide Key" else "Show Key")
                      }

                      Button(
                        onClick = {
                          scope.launch {
                            isVerifying = true
                            verifyResult = null
                            aiService.verifyKey()
                              .onSuccess {
                                verifyResult = it
                                preferences.lastVerified.set(System.currentTimeMillis())
                              }
                              .onFailure { e ->
                                verifyResult = "Verification failed: ${e.message}"
                              }
                            isVerifying = false
                          }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isVerifying && apiKeyInfo.apiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                      ) {
                        if (isVerifying) {
                          CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                          )
                        } else {
                          Text("Verify Key")
                        }
                      }
                    }

                    if (verifyResult != null) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        val isSuccess = verifyResult!!.contains("successfully") || verifyResult!!.contains("ready")
                        Icon(
                          imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning,
                          contentDescription = null,
                          tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                          modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                          text = verifyResult!!,
                          style = MaterialTheme.typography.bodySmall,
                          color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }

                item { PreferenceSectionHeader(title = "Model") }

                item {
                  PreferenceCard {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text = "Available Models",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                      )
                      Button(
                        onClick = { loadModels() },
                        enabled = !isLoadingModels && apiKeyInfo.apiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                      ) {
                        if (isLoadingModels) {
                          CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                          )
                        } else {
                          Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                          )
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Fetch Models")
                      }
                    }

                    if (modelLoadError != null) {
                      Text(
                        text = modelLoadError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                      )
                    }

                    if (models.isNotEmpty()) {
                      var showModelDialog by remember { mutableStateOf(false) }

                      val modelDisplayNames = models.associate { it.id to it.displayName }

                      Surface(
                        onClick = { showModelDialog = true },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 16.dp, vertical = 4.dp),
                      ) {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                          Column(modifier = Modifier.weight(1f)) {
                            Text(
                              text = "Model",
                              style = MaterialTheme.typography.labelLarge,
                              fontWeight = FontWeight.Bold,
                            )
                            Text(
                              text = if (selectedModel.isNotBlank()) {
                                modelDisplayNames[selectedModel] ?: selectedModel
                              } else "Tap to select a model",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.outline,
                            )
                          }
                          if (selectedModel.isNotBlank()) {
                            val isFree = models.firstOrNull { it.id == selectedModel }?.isFree == true
                            if (isFree) {
                              FreeTag()
                              Spacer(modifier = Modifier.width(8.dp))
                            }
                          }
                          Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                          )
                        }
                      }

                      if (showModelDialog) {
                        ModelSearchDialog(
                          models = models,
                          selectedModelId = selectedModel,
                          onSelect = { preferences.selectedModel.set(it) },
                          onDismiss = { showModelDialog = false },
                        )
                      }
                    } else {
                      Text(
                        text = "Tap 'Fetch Models' to load available models from ${provider.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                      )
                    }

                    PreferenceDivider()

                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = "Verify Model",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                      )

                      Button(
                        onClick = {
                          scope.launch {
                            isVerifyingModel = true
                            verifyModelResult = null
                            aiService.verifyModel()
                              .onSuccess { verifyModelResult = it }
                              .onFailure { e ->
                                verifyModelResult = "Error: ${e.message}"
                              }
                            isVerifyingModel = false
                          }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifyingModel && selectedModel.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                      ) {
                        if (isVerifyingModel) {
                          CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary,
                          )
                          Spacer(Modifier.width(8.dp))
                        }
                        Text("Check Model Access")
                      }

                      if (verifyModelResult != null) {
                        val lines = verifyModelResult!!.split("\n")
                        Surface(
                          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                          shape = MaterialTheme.shapes.medium,
                          modifier = Modifier.fillMaxWidth(),
                        ) {
                          Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                          ) {
                            lines.forEach { line ->
                              val isPositive = line.startsWith("Available") ||
                                line.startsWith("Free") ||
                                line.startsWith("API access working")
                              val isWarning = line.startsWith("Quota") ||
                                line.startsWith("Paid") ||
                                line.startsWith("⚠")
                              Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                              ) {
                                Icon(
                                  imageVector = when {
                                    isPositive -> Icons.Default.Check
                                    isWarning -> Icons.Default.Warning
                                    else -> Icons.Default.Close
                                  },
                                  contentDescription = null,
                                  modifier = Modifier.size(16.dp),
                                  tint = when {
                                    isPositive -> MaterialTheme.colorScheme.primary
                                    isWarning -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.error
                                  },
                                )
                                Text(
                                  text = line,
                                  style = MaterialTheme.typography.bodySmall,
                                )
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            item { PreferenceSectionHeader(title = "General Settings") }

            item {
              PreferenceCard {
                SwitchPreference(
                  value = preferences.showThinking.collectAsState().value,
                  onValueChange = { preferences.showThinking.set(it) },
                  title = { Text("Show AI Reasoning (Thinking)") },
                  summary = { Text("Enable this to see the model's internal thought process for supported models.") },
                  icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null) }
                )
              }
            }

            item { PreferenceSectionHeader(title = "Features") }

            item {
              PreferenceCard {
                SwitchPreference(
                  value = renameWithAi,
                  onValueChange = { preferences.renameWithAi.set(it) },
                  title = { Text("AI-Powered Rename") },
                  summary = {
                    Text(
                      "Use AI to generate clean filenames for bulk rename operations",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )

                PreferenceDivider()

                SwitchPreference(
                  value = subtitleFormatWithAi,
                  onValueChange = { preferences.subtitleFormatWithAi.set(it) },
                  title = { Text("AI Subtitle Search Formatting") },
                  summary = {
                    Text(
                      "Auto-format video titles for Wyzie/SubHub subtitle search",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
              }
            }

            if (provider != AiProvider.LOCAL) {
              item { PreferenceSectionHeader(title = "Speech-to-Text [Extreme Experimental]") }

              item {
                PreferenceCard {
                  val sttProviders = listOf(AiProvider.GROQ, AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.OPENROUTER)
                  val sttProvider by preferences.sttProvider.collectAsState()
                  val sttModel by preferences.sttModel.collectAsState()

                  ListPreference(
                    value = subtitleGenerationOutputFormat,
                    onValueChange = { preferences.subtitleGenerationOutputFormat.set(it) },
                    values = listOf("srt", "vtt"),
                    valueToText = { androidx.compose.ui.text.AnnotatedString(it.uppercase()) },
                    title = { Text("Default Output Format") },
                    summary = {
                      Text(
                        subtitleGenerationOutputFormat.uppercase(),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )

                  PreferenceDivider()

                  ListPreference(
                    value = sttProvider,
                    onValueChange = {
                      preferences.sttProvider.set(it)
                      preferences.sttModel.set("")
                    },
                    values = sttProviders,
                    valueToText = { androidx.compose.ui.text.AnnotatedString(it.displayName) },
                    title = { Text("Speech-to-Text Provider") },
                    summary = {
                      Text(
                        "Used for both real-time streaming and batch subtitle generation",
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )

                  PreferenceDivider()

                  SttModelSelector(
                    sttProvider = sttProvider,
                    sttModel = sttModel,
                    onSelectModel = { preferences.sttModel.set(it) },
                  )

                  PreferenceDivider()

                  val sttLanguage by preferences.sttLanguage.collectAsState()
                  ListPreference(
                    value = sttLanguage,
                    onValueChange = { preferences.sttLanguage.set(it) },
                    values = listOf("", "en", "es", "fr", "de", "hi", "ja", "zh", "ko", "pt", "ru", "ar", "it", "nl", "pl", "tr", "vi", "th"),
                    valueToText = { lang ->
                      androidx.compose.ui.text.AnnotatedString(
                        when (lang) {
                          "" -> "Auto-detect"
                          "en" -> "English"; "es" -> "Spanish"
                          "fr" -> "French"; "de" -> "German"; "hi" -> "Hindi"; "ja" -> "Japanese"
                          "zh" -> "Chinese"; "ko" -> "Korean"; "pt" -> "Portuguese"; "ru" -> "Russian"
                          "ar" -> "Arabic"; "it" -> "Italian"; "nl" -> "Dutch"; "pl" -> "Polish"
                          "tr" -> "Turkish"; "vi" -> "Vietnamese"; "th" -> "Thai"
                          else -> lang
                        }
                      )
                    },
                    title = { Text("Audio Language") },
                    summary = {
                      Text(
                        if (sttLanguage.isBlank()) "Auto-detect speech language" else sttLanguage.uppercase(),
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )
                }
              }
            }

            if (provider != AiProvider.LOCAL) {
              item { PreferenceSectionHeader(title = "Subtitle Translation") }

              item {
                PreferenceCard {
                  SwitchPreference(
                    value = subtitleTranslationEnabled,
                    onValueChange = { enabled ->
                      preferences.subtitleTranslationEnabled.set(enabled)
                      if (enabled && subtitleTranslationFirstTime) {
                        showSubtitleTranslationWarning = true
                      }
                    },
                    title = { Text("Enable Translation") },
                    summary = {
                      Text(
                        "Translate external subtitles using AI",
                        color = MaterialTheme.colorScheme.outline,
                      )
                    },
                  )

                  PreferenceDivider()

                  AutoTranslateLanguageConfig(
                    languages = autoTranslateLanguages,
                    onLanguagesChange = { preferences.autoTranslateLanguages.set(it) },
                  )
                }
              }
            }

            item { PreferenceSectionHeader(title = "Custom Prompt") }

            item {
              PreferenceCard {
                SwitchPreference(
                  value = customPromptEnabled,
                  onValueChange = { preferences.customPromptEnabled.set(it) },
                  title = { Text("Override Default Instructions") },
                  summary = {
                    Text(
                      if (customPromptEnabled) "Custom prompt will be used instead of built-in instructions"
                      else "Built-in AI instructions will be used",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )

                if (customPromptEnabled) {
                  PreferenceDivider()

                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Text(
                      text = "Custom Prompts",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                    )

                    Text(
                      text = "Leave a field blank to use the built-in instruction for that task. If you have an older global prompt saved, it will be used as a fallback.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                    )

                    TextField(
                      value = customRenamePrompt,
                      onValueChange = { preferences.customRenamePrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom rename prompt") },
                      placeholder = { Text("Instructions for AI file renaming...") },
                      maxLines = 6,
                    )

                    TextField(
                      value = customSubtitleTranslationPrompt,
                      onValueChange = { preferences.customSubtitleTranslationPrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom subtitle translation prompt") },
                      placeholder = { Text("Instructions for AI subtitle translation...") },
                      maxLines = 6,
                    )

                    TextField(
                      value = customSubtitleFormatPrompt,
                      onValueChange = { preferences.customSubtitleFormatPrompt.set(it) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                      label = { Text("Custom subtitle formatting prompt") },
                      placeholder = { Text("Instructions for formatting subtitle search queries...") },
                      maxLines = 6,
                    )

                    if (customPrompt.isNotBlank()) {
                      Text(
                        text = "Legacy global prompt saved. It will be used whenever a task-specific prompt is empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                      )
                    }
                  }
                }
              }
          }
        }
      }
    }

    if (showSubtitleTranslationWarning) {
      AlertDialog(
        onDismissRequest = {
          showSubtitleTranslationWarning = false
          preferences.subtitleTranslationFirstTime.set(false)
        },
        title = { Text("Subtitle Translation") },
        text = {
          Text(
            "Subtitle translation can be a bit messy. " +
            "For best results, use better models and don't rant that subs aren't working properly."
          )
        },
        confirmButton = {
          TextButton(onClick = {
            showSubtitleTranslationWarning = false
            preferences.subtitleTranslationFirstTime.set(false)
          }) {
            Text("Got it")
          }
        }
      )
    }
  }
}

@Composable
private fun OfflineModelCard(
    model: LocalModelInfo,
    isDownloaded: Boolean,
    isSelected: Boolean,
    isDownloading: Boolean,
    isBenchmarking: Boolean,
    isRecommended: Boolean = false,
    benchmark: LocalModelBenchmark?,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onBenchmark: () -> Unit,
) {
    val cardColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        ModelChip(model.tier.label)
                        ModelChip(model.sizeLabel)
                        ModelChip("${model.minRamGb}GB RAM+")
                    }
                }
                
                if (isDownloaded && !isDownloading) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete", 
                            tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                if (isRecommended) ModelChip("Recommended here")
                ModelChip("Speed ${model.speedRank}")
                ModelChip("Translation ${model.translationRank}")
                ModelChip(model.languageTier.label)
            }

            if (benchmark != null) {
                Text(
                    text = "${benchmark.speedLabel} - ${benchmark.loadLabel} - about ${benchmark.memoryEstimateMb} MB while loaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            
            if (isDownloading && downloadProgress != null) {
                val percentage = downloadProgress.percentage
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                Text(
                    text = "${(percentage * 100).toInt()}% downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isDownloading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isDownloaded && !isDownloading) {
                    OutlinedButton(
                        onClick = onBenchmark,
                        enabled = !isBenchmarking,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isBenchmarking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (benchmark == null) "Benchmark" else "Re-test")
                    }
                }
                if (!isDownloaded && !isDownloading) {
                    Button(
                        onClick = onDownload, 
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                } else if (isDownloaded && !isSelected && !isDownloading) {
                    Button(
                        onClick = onSelect, 
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Use Model")
                    }
                } else if (isSelected && isDownloaded && !isDownloading) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check, 
                                contentDescription = null, 
                                modifier = Modifier.size(16.dp), 
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Active", 
                                style = MaterialTheme.typography.labelLarge, 
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ApiKeyInfo(
  val title: String,
  val hint: String,
  val placeholder: String,
  val apiKey: String,
  val onChange: (String) -> Unit,
)

@Composable
private fun ModelChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SttModelSelector(
  sttProvider: AiProvider,
  sttModel: String,
  onSelectModel: (String) -> Unit,
) {
  val aiService = koinInject<AiService>()
  val context = LocalContext.current
  var showDialog by remember { mutableStateOf(false) }
  var sttModels by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
  var isLoadingStt by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  val modelKey = "${sttProvider.name}_stt"
  val cachedModels = remember(modelKey) { mutableStateOf<List<AiModelInfo>?>(null) }

    Surface(
    onClick = {
      val cached = cachedModels.value
      if (cached != null) {
        sttModels = cached
        showDialog = true
      } else {
        isLoadingStt = true
        scope.launch {
          aiService.fetchModelsForProvider(sttProvider)
            .onSuccess { allModels ->
              val sttOnly = allModels.filter { model ->
                model.id.contains("whisper", ignoreCase = true) ||
                  model.id.contains("gemini", ignoreCase = true) ||
                  model.id.contains("flash", ignoreCase = true)
              }.ifEmpty {
                allModels.take(5)
              }
              cachedModels.value = sttOnly
              sttModels = sttOnly
              showDialog = true
            }
            .onFailure { e ->
              Toast.makeText(context, "Failed to load models: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          isLoadingStt = false
        }
      }
    },
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Real-time Model",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = if (sttModel.isNotBlank()) sttModel
                 else if (isLoadingStt) "Loading..."
                 else "Tap to select STT model",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      if (isLoadingStt) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
      } else {
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
      }
    }
  }

  if (showDialog) {
    ModelSearchDialog(
      models = sttModels,
      selectedModelId = sttModel,
      onSelect = onSelectModel,
      onDismiss = { showDialog = false },
    )
  }
}

@Composable
private fun AutoTranslateLanguageConfig(
  languages: String,
  onLanguagesChange: (String) -> Unit,
) {
  val selectedCodes = remember(languages) { languages.split(",").filter { it.isNotBlank() }.toMutableSet() }
  var adding by remember { mutableStateOf(false) }
  var addingSearch by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "Auto-Translate Target Languages",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "When translating subtitles, if 1 language is configured it translates directly. If 2+ are configured, a picker appears.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )

    if (selectedCodes.isEmpty()) {
      Text(
        text = "No target languages configured",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
      )
    } else {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      ) {
        selectedCodes.forEach { code ->
          val langName = allLanguages[code] ?: code.uppercase()
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
              Text(
                text = langName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(4.dp))
              IconButton(
                onClick = {
                  selectedCodes.remove(code)
                  onLanguagesChange(selectedCodes.joinToString(","))
                },
                modifier = Modifier.size(20.dp),
              ) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Remove $langName",
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    if (adding) {
      TextField(
        value = addingSearch,
        onValueChange = { addingSearch = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search languages...") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
      )

      val filtered = allLanguages.filter { (_, name) ->
        addingSearch.isBlank() || name.contains(addingSearch, ignoreCase = true)
      }.toList().sortedBy { it.second }

      Column(
        modifier = Modifier
          .heightIn(max = 200.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        filtered.forEach { (code, name) ->
          Surface(
            onClick = {
              selectedCodes.add(code)
              onLanguagesChange(selectedCodes.joinToString(","))
              addingSearch = ""
            },
            shape = RoundedCornerShape(8.dp),
            color = if (selectedCodes.contains(code)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              val isSelected = selectedCodes.contains(code)
              Checkbox(
                checked = isSelected,
                onCheckedChange = {
                  if (isSelected) selectedCodes.remove(code)
                  else selectedCodes.add(code)
                  onLanguagesChange(selectedCodes.joinToString(","))
                },
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.weight(1f))
              Text(
                text = code.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
              )
            }
          }
        }
      }
    }

    Button(
      onClick = { adding = !adding },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = if (adding) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (adding) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
      ),
    ) {
      Icon(
        imageVector = if (adding) Icons.Default.Check else Icons.Default.Add,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(if (adding) "Done" else "Add Language")
    }
  }
}
}
