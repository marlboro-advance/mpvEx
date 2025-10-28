package app.marlboroadvance.mpvex.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.repository.PlaybackStateRepositoryImpl
import app.marlboroadvance.mpvex.database.repository.PrivateVideoRepository
import app.marlboroadvance.mpvex.database.repository.RecentlyPlayedRepositoryImpl
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import app.marlboroadvance.mpvex.domain.subtitle.repository.ExternalSubtitleRepository
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.utils.storage.StorageMonitor
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

// -----------------------------------------
// Safe Migration: Version 1 → 2
// Adds the new 'video_index' table if missing
// -----------------------------------------
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Check if the table already exists to avoid duplicate creation
        val cursor = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='video_index';"
        )

        val tableExists = cursor.moveToFirst()
        cursor.close()

        if (!tableExists) {
            // Create the new table safely
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_index` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `path` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `size` INTEGER NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `dateModified` INTEGER NOT NULL,
                    `dateAdded` INTEGER NOT NULL,
                    `lastModified` INTEGER NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `bucketId` TEXT NOT NULL,
                    `bucketDisplayName` TEXT NOT NULL,
                    `lastIndexed` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Safely create indexes (only if they don't exist)
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_index_path` ON `video_index` (`path`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_video_index_bucketId` ON `video_index` (`bucketId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_video_index_lastModified` ON `video_index` (`lastModified`)"
            )
        }
    }
}

// -----------------------------------------
// Koin Database Module
// -----------------------------------------
val DatabaseModule = module {

    // JSON Serializer (for preferences & repositories)
    single<Json> {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    // Database with migration
    single<MpvExDatabase> {
        Room
            .databaseBuilder(androidContext(), MpvExDatabase::class.java, "mpvex.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    // File system video repository
    single<FileSystemVideoRepository> { FileSystemVideoRepository(get(), get<AdvancedPreferences>()) }

    // Playback state repository
    singleOf(::PlaybackStateRepositoryImpl).bind(PlaybackStateRepository::class)

    // Recently played repository
    single<RecentlyPlayedRepository> {
        RecentlyPlayedRepositoryImpl(get<MpvExDatabase>().recentlyPlayedDao())
    }

    // External subtitle repository
    single<ExternalSubtitleRepository> {
        ExternalSubtitleRepository(
            context = androidContext(),
            dao = get<MpvExDatabase>().externalSubtitleDao(),
        )
    }

    // Private video repository
    single {
        PrivateVideoRepository(
            dao = get<MpvExDatabase>().privateVideoDao(),
            context = androidContext(),
        )
    }

    // Thumbnail repository
    single { ThumbnailRepository(androidContext()) }

    // Storage monitor (auto-starts to track storage mounts)
    single(createdAtStart = true) { StorageMonitor(androidContext(), get()) }
}
