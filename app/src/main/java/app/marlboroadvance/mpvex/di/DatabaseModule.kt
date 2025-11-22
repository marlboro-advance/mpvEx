package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `video_metadata_cache` (
        `path` TEXT NOT NULL,
        `size` INTEGER NOT NULL,
        `dateModified` INTEGER NOT NULL,
        `duration` INTEGER NOT NULL,
        `width` INTEGER NOT NULL,
        `height` INTEGER NOT NULL,
        `fps` REAL NOT NULL,
        `lastScanned` INTEGER NOT NULL,
        PRIMARY KEY(`path`)
      )
    """.trimIndent(),
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `network_connections` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `name` TEXT NOT NULL,
        `protocol` TEXT NOT NULL,
        `host` TEXT NOT NULL,
        `port` INTEGER NOT NULL,
        `username` TEXT NOT NULL,
        `password` TEXT NOT NULL,
        `path` TEXT NOT NULL,
        `isAnonymous` INTEGER NOT NULL,
        `lastConnected` INTEGER NOT NULL,
        `autoConnect` INTEGER NOT NULL
      )
    """.trimIndent(),
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `PlaylistEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `name` TEXT NOT NULL,
        `createdAt` INTEGER NOT NULL,
        `updatedAt` INTEGER NOT NULL
      )
    """.trimIndent(),
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `PlaylistItemEntity` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `playlistId` INTEGER NOT NULL,
        `filePath` TEXT NOT NULL,
        `fileName` TEXT NOT NULL,
        `position` INTEGER NOT NULL,
        `addedAt` INTEGER NOT NULL,
        `lastPlayedAt` INTEGER NOT NULL,
        `playCount` INTEGER NOT NULL,
        `lastPosition` INTEGER NOT NULL,
        FOREIGN KEY(`playlistId`) REFERENCES `PlaylistEntity`(`id`) ON DELETE CASCADE
      )
    """.trimIndent(),
    )

    db.execSQL("CREATE INDEX IF NOT EXISTS `index_PlaylistItemEntity_playlistId` ON `PlaylistItemEntity` (`playlistId`)")

    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `videoTitle` TEXT")
    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `RecentlyPlayedEntity` ADD COLUMN `playlistId` INTEGER")
  }
}

val DatabaseModule =
  module {
    single<Json> {
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    }

    single<MpvExDatabase> {
      val context = androidContext()
      Room
        .databaseBuilder(context, MpvExDatabase::class.java, "mpvex.db")
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

    single<RecentlyPlayedRepository> {
      RecentlyPlayedRepositoryImpl(get<MpvExDatabase>().recentlyPlayedDao())
    }

    single<ExternalSubtitleRepository> {
      ExternalSubtitleRepository(
        context = androidContext(),
        dao = get<MpvExDatabase>().externalSubtitleDao(),
      )
    }

    single { ThumbnailRepository(androidContext()) }

    single {
      app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository(
        context = androidContext(),
        dao = get<MpvExDatabase>().videoMetadataDao(),
      )
    }

    single {
      app.marlboroadvance.mpvex.repository.VideoRepository(
        metadataCache = get(),
      )
    }

    single {
      get<MpvExDatabase>().networkConnectionDao()
    }

    single {
      app.marlboroadvance.mpvex.repository.NetworkRepository(
        dao = get(),
      )
    }

    single {
      PlaylistRepository(
        playlistDao = get<MpvExDatabase>().playlistDao(),
      )
    }
  }
