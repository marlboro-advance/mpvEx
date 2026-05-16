package app.gyrolet.mpvrx.di

import app.gyrolet.mpvrx.domain.anime4k.Anime4KManager
import app.gyrolet.mpvrx.domain.hdr.HdrToysManager
import app.gyrolet.mpvrx.domain.thumbnail.CoilVideoThumbnailDecoder
import app.gyrolet.mpvrx.domain.thumbnail.toThumbnailStrategy
import app.gyrolet.mpvrx.network.AndroidCookieJar
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.repository.IntroDbRepository
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleFileStore
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleOrchestrator
import app.gyrolet.mpvrx.repository.subtitlehub.MpvRxSubtitleHubRepository
import app.gyrolet.mpvrx.repository.wyzie.WyzieSearchRepository
import app.gyrolet.mpvrx.repository.ai.AiClient
import app.gyrolet.mpvrx.repository.ai.AiService
import app.gyrolet.mpvrx.repository.ai.GeminiClient
import app.gyrolet.mpvrx.repository.ai.GeminiSpeechClient
import app.gyrolet.mpvrx.repository.ai.AnthropicClient
import app.gyrolet.mpvrx.repository.ai.GroqClient
import app.gyrolet.mpvrx.repository.ai.GroqSpeechClient
import app.gyrolet.mpvrx.repository.ai.LlamaCppInference
import app.gyrolet.mpvrx.repository.ai.LocalAiClient
import app.gyrolet.mpvrx.repository.ai.ModelDownloadManager
import app.gyrolet.mpvrx.repository.ai.OpenAiClient
import app.gyrolet.mpvrx.repository.ai.OpenRouterClient
import app.gyrolet.mpvrx.repository.ai.RealtimeSubtitleService
import app.gyrolet.mpvrx.repository.ai.SubtitleGenerationService
import app.gyrolet.mpvrx.repository.ai.TogetherClient
import app.gyrolet.mpvrx.preferences.AiPreferences
import kotlinx.serialization.json.Json
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.FileSystem
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(AndroidCookieJar())
            .build()
    }
    single<ImageLoader> {
        val context = androidContext()
        val browserPreferences = get<BrowserPreferences>()

        val imageHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val host = original.url.host
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .header("Referer", "https://$host")
                    .build()
                chain.proceed(request)
            })
            .build()

        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
                add(
                    CoilVideoThumbnailDecoder.Factory(
                        thumbnailStrategy = {
                            browserPreferences.thumbnailMode.get().toThumbnailStrategy(
                                browserPreferences.thumbnailFramePosition.get()
                            )
                        }
                    )
                )
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(context.filesDir.resolve("thumbnails"))
                    .maxSizePercent(1.0)
                    .build()
            )
            .crossfade(true)
            .build()
    }
    single { Anime4KManager(androidContext()) }
    single { HdrToysManager(androidContext()) }
    single { OnlineSubtitleFileStore(androidContext(), get()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get(), get()) }
    single { MpvRxSubtitleHubRepository(androidContext(), get(), get(), get(), get()) }
    single { OnlineSubtitleOrchestrator(get<WyzieSearchRepository>(), get<MpvRxSubtitleHubRepository>()) }
    single { IntroDbRepository(get(), get()) }
    single { GeminiClient(get(), get()) }
    single { GroqClient(get(), get()) }
    single { OpenAiClient(get(), get()) }
    single { AnthropicClient(get(), get()) }
    single { OpenRouterClient(get(), get()) }
    single { TogetherClient(get(), get()) }
    single { GeminiSpeechClient(get(), get()) }
    single { GroqSpeechClient(get(), get()) }
    single<app.gyrolet.mpvrx.repository.ai.LlmInference> { app.gyrolet.mpvrx.repository.ai.LlamaCppInference() }
    single<AiClient>(named("gemini")) { GeminiClient(get(), get()) }
    single<AiClient>(named("groq")) { GroqClient(get(), get()) }
    single<AiClient>(named("openai")) { OpenAiClient(get(), get()) }
    single<AiClient>(named("anthropic")) { AnthropicClient(get(), get()) }
    single<AiClient>(named("openrouter")) { OpenRouterClient(get(), get()) }
    single<AiClient>(named("together")) { TogetherClient(get(), get()) }
    single { LocalAiClient(get()) }
    single { ModelDownloadManager(get()) }
    single { SubtitleGenerationService(androidContext(), get(), get(), get(), get(), get()) }
    single { RealtimeSubtitleService(androidContext(), get(), get(), get(), get(), get()) }
    single {
        AiService(
            androidContext(),
            get<AiPreferences>(),
            get<AiClient>(named("gemini")),
            get<AiClient>(named("groq")),
            get<AiClient>(named("openai")),
            get<AiClient>(named("anthropic")),
            get<AiClient>(named("openrouter")),
            get<AiClient>(named("together")),
            get<LocalAiClient>(),
            get<ModelDownloadManager>(),
            get<Json>()
        )
    }
}
