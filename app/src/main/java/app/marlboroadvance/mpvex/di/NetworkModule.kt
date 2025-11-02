package app.marlboroadvance.mpvex.di

import app.marlboroadvance.mpvex.domain.subtitle.SubdlApiService
import app.marlboroadvance.mpvex.domain.subtitle.repository.SubdlRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

val networkModule =
  module {
    // Moshi for JSON parsing
    single {
      Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    }

    // OkHttp Client
    single {
      val loggingInterceptor =
        HttpLoggingInterceptor().apply {
          level = HttpLoggingInterceptor.Level.BODY
        }

      OkHttpClient
        .Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    }

    // Retrofit instance for Subdl API
    single {
      Retrofit
        .Builder()
        .baseUrl("https://api.subdl.com/")
        .client(get())
        .addConverterFactory(MoshiConverterFactory.create(get()))
        .build()
    }

    // Subdl API Service
    single {
      get<Retrofit>().create(SubdlApiService::class.java)
    }

    // Subdl Repository
    single {
      SubdlRepository(
        context = androidContext(),
        apiService = get(),
        okHttpClient = get(),
        externalSubtitleRepository = get(),
      )
    }
  }
