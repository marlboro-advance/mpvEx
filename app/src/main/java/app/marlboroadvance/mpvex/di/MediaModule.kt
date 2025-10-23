package app.marlboroadvance.mpvex.di

import app.marlboroadvance.mpvex.data.media.repository.VideoFolderRepository
import app.marlboroadvance.mpvex.data.media.repository.VideoRepository
import org.koin.dsl.module

val MediaModule =
  module {
    single { VideoFolderRepository }
    single { VideoRepository }
  }
