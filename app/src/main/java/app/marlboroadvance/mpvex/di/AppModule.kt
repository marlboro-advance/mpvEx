package app.marlboroadvance.mpvex.di

import kotlinx.serialization.json.Json
import org.koin.dsl.module

// generic dependencies for the app's needs
val AppModule =
  module {
    single {
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    }
  }
