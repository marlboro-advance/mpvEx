package app.sfsakhawat999.mpvrex.di

import app.sfsakhawat999.mpvrex.preferences.AdvancedPreferences
import app.sfsakhawat999.mpvrex.preferences.AppearancePreferences
import app.sfsakhawat999.mpvrex.preferences.AudioPreferences
import app.sfsakhawat999.mpvrex.preferences.BrowserPreferences
import app.sfsakhawat999.mpvrex.preferences.DecoderPreferences
import app.sfsakhawat999.mpvrex.preferences.GesturePreferences
import app.sfsakhawat999.mpvrex.preferences.PlayerPreferences
import app.sfsakhawat999.mpvrex.preferences.SubtitlesPreferences
import app.sfsakhawat999.mpvrex.preferences.preference.AndroidPreferenceStore
import app.sfsakhawat999.mpvrex.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    singleOf(::BrowserPreferences)
  }
