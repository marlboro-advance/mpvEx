package app.marlboroadvance.mpvex.di

import app.marlboroadvance.mpvex.repository.DlnaRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val DlnaModule = module {
    single { DlnaRepository(androidContext()) }
}
