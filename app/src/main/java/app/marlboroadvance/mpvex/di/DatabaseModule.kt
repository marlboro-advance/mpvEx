package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val DatabaseModule = module {
  single<MpvExDatabase> {
    Room
      .databaseBuilder(androidContext(), MpvExDatabase::class.java, "mpvex.db")
      .fallbackToDestructiveMigrationOnDowngrade(false)
      .build()
  }

  singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

  single<RecentlyPlayedRepository> {
    RecentlyPlayedRepositoryImpl(get<MpvExDatabase>().recentlyPlayedDao())
  }
}
