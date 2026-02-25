package app.marlboroadvance.mpvex.di

import app.marlboroadvance.mpvex.domain.anime4k.Anime4KManager
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSearchRepository
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.util.concurrent.TimeUnit

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    single { Anime4KManager(androidContext()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get()) }
}
