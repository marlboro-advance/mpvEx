package app.marlboroadvance.mpvex.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lazy metadata retrieval system
 * Only extracts detailed metadata (duration, framerate, resolution) when chips are enabled
 * 
 * This improves app startup performance by deferring expensive metadata extraction
 * until the user actually needs to see the information.
 */
object MetadataRetrieval {
    private const val TAG = "MetadataRetrieval"

    /**
     * Checks if any metadata-dependent chips are enabled
     * If all chips are disabled, we can skip metadata extraction entirely
     */
    fun isMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        // Video card chips
        val needsVideoMetadata = browserPreferences.showResolutionChip.get() ||
                browserPreferences.showFramerateInResolution.get() ||
                browserPreferences.showSubtitleIndicator.get()

        // Folder card chips
        val needsFolderMetadata = browserPreferences.showTotalDurationChip.get()

        return needsVideoMetadata || needsFolderMetadata
    }

    /**
     * Checks if video-specific metadata is needed
     */
    fun isVideoMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        return browserPreferences.showResolutionChip.get() ||
                browserPreferences.showFramerateInResolution.get() ||
                browserPreferences.showSubtitleIndicator.get()
    }

    /**
     * Checks if folder-specific metadata is needed
     */
    fun isFolderMetadataNeeded(browserPreferences: BrowserPreferences): Boolean {
        return browserPreferences.showTotalDurationChip.get()
    }

    /**
     * Enriches a video with metadata only if needed
     * Returns the video with metadata populated, or the original if metadata is disabled
     */
    suspend fun enrichVideoIfNeeded(
        context: Context,
        video: Video,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): Video = withContext(Dispatchers.IO) {
        // If metadata chips are disabled, return video as-is
        if (!isVideoMetadataNeeded(browserPreferences)) {
            return@withContext video
        }

        // If video already has metadata (including FPS and subtitle info), return as-is
        if (video.width > 0 && video.height > 0 && video.duration > 0 && video.fps > 0f && video.subtitleCodec.isNotEmpty()) {
            return@withContext video
        }

        // Extract metadata
        try {
            val file = File(video.path)
            if (!file.exists()) {
                return@withContext video
            }

            val metadata = metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)
            if (metadata != null) {
                video.copy(
                    duration = metadata.durationMs,
                    durationFormatted = formatDuration(metadata.durationMs),
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    resolution = formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec
                )
            } else {
                video
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching video metadata: ${video.displayName}", e)
            video
        }
    }

    /**
     * Enriches a list of videos with metadata only if needed
     * Processes in batches for better performance
     */
    suspend fun enrichVideosIfNeeded(
        context: Context,
        videos: List<Video>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): List<Video> = withContext(Dispatchers.IO) {
        // If metadata chips are disabled, return videos as-is
        if (!isVideoMetadataNeeded(browserPreferences)) {
            return@withContext videos
        }

        // Filter videos that need metadata extraction
        // MediaStore provides width, height, duration but NOT FPS or subtitle info
        // So we need to extract metadata if FPS or subtitle info is missing
        val videosNeedingMetadata = videos.filter { video ->
            video.width == 0 || video.height == 0 || video.duration == 0L || 
            video.fps == 0f || video.subtitleCodec.isEmpty()
        }

        if (videosNeedingMetadata.isEmpty()) {
            return@withContext videos
        }

        Log.d(TAG, "Enriching ${videosNeedingMetadata.size} videos with metadata")

        // Prepare batch extraction
        val fileTriples = videosNeedingMetadata.mapNotNull { video ->
            val file = File(video.path)
            if (file.exists()) {
                Triple(file, video.uri, video.displayName)
            } else {
                null
            }
        }

        // Batch extract metadata
        val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)

        // Update videos with metadata
        videos.map { video ->
            val metadata = metadataMap[video.path]
            if (metadata != null) {
                video.copy(
                    duration = metadata.durationMs,
                    durationFormatted = formatDuration(metadata.durationMs),
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    resolution = formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec
                )
            } else {
                video
            }
        }
    }

    /**
     * Enriches videos progressively with metadata
     * Emits videos as they are processed, allowing UI to update incrementally
     */
    fun enrichVideosProgressively(
        context: Context,
        videos: List<Video>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): Flow<Video> = flow {
        // If metadata chips are disabled, emit all videos as-is
        if (!isVideoMetadataNeeded(browserPreferences)) {
            videos.forEach { emit(it) }
            return@flow
        }

        // Process each video
        for (video in videos) {
            // If video already has metadata (including FPS and subtitle info), emit as-is
            if (video.width > 0 && video.height > 0 && video.duration > 0 && 
                video.fps > 0f && video.subtitleCodec.isNotEmpty()) {
                emit(video)
                continue
            }

            // Extract metadata
            try {
                val file = File(video.path)
                if (!file.exists()) {
                    emit(video)
                    continue
                }

                val metadata = metadataCache.getOrExtractMetadata(file, video.uri, video.displayName)
                if (metadata != null) {
                    emit(
                        video.copy(
                            duration = metadata.durationMs,
                            durationFormatted = formatDuration(metadata.durationMs),
                            width = metadata.width,
                            height = metadata.height,
                            fps = metadata.fps,
                            resolution = formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                            hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                            subtitleCodec = metadata.subtitleCodec
                        )
                    )
                } else {
                    emit(video)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enriching video metadata: ${video.displayName}", e)
                emit(video)
            }
        }
    }

    /**
     * Enriches a folder with metadata only if needed
     * Calculates total duration by extracting metadata from all videos in the folder
     */
    suspend fun enrichFolderIfNeeded(
        context: Context,
        folder: VideoFolder,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository
    ): VideoFolder = withContext(Dispatchers.IO) {
        // If folder duration chip is disabled, return folder as-is
        if (!isFolderMetadataNeeded(browserPreferences)) {
            return@withContext folder
        }

        // If folder already has duration, return as-is
        if (folder.totalDuration > 0) {
            return@withContext folder
        }

        // Extract metadata for all videos in folder
        try {
            val directory = File(folder.path)
            if (!directory.exists() || !directory.isDirectory) {
                return@withContext folder
            }

            val videoFiles = directory.listFiles()?.filter { file ->
                file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS
            } ?: emptyList()

            if (videoFiles.isEmpty()) {
                return@withContext folder
            }

            // Batch extract metadata
            val fileTriples = videoFiles.map { file ->
                Triple(file, Uri.fromFile(file), file.name)
            }

            val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)

            // Calculate total duration
            val totalDuration = metadataMap.values.sumOf { it.durationMs }

            folder.copy(totalDuration = totalDuration)
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching folder metadata: ${folder.name}", e)
            folder
        }
    }

    /**
     * Enriches a list of folders with metadata only if needed
     * Processes in batches for better performance
     */
    suspend fun enrichFoldersIfNeeded(
        context: Context,
        folders: List<VideoFolder>,
        browserPreferences: BrowserPreferences,
        metadataCache: VideoMetadataCacheRepository,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<VideoFolder> = withContext(Dispatchers.IO) {
        // If folder duration chip is disabled, return folders as-is
        if (!isFolderMetadataNeeded(browserPreferences)) {
            return@withContext folders
        }

        // Filter folders that need metadata extraction
        val foldersNeedingMetadata = folders.filter { it.totalDuration == 0L }

        if (foldersNeedingMetadata.isEmpty()) {
            return@withContext folders
        }

        Log.d(TAG, "Enriching ${foldersNeedingMetadata.size} folders with metadata")

        var processed = 0
        val total = foldersNeedingMetadata.size

        // Process each folder
        val enrichedMap = foldersNeedingMetadata.associate { folder ->
            val enriched = enrichFolderIfNeeded(context, folder, browserPreferences, metadataCache)
            processed++
            onProgress?.invoke(processed, total)
            folder.path to enriched
        }

        // Return updated list
        folders.map { folder ->
            enrichedMap[folder.path] ?: folder
        }
    }

    // Helper: Video file extensions
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "3gp", "3g2", "mpg", "mpeg", "m2v", "ogv", "ts", "mts",
        "m2ts", "vob", "divx", "xvid", "f4v", "rm", "rmvb", "asf"
    )

    // Formatting utilities
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0s"

        val seconds = durationMs / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> "${secs}s"
        }
    }

    private fun formatResolutionWithFps(width: Int, height: Int, fps: Float): String {
        val baseResolution = formatResolution(width, height)
        if (baseResolution == "--" || fps <= 0f) return baseResolution

        val fpsFormatted = fps.toInt().toString()
        return "$baseResolution@$fpsFormatted"
    }

    private fun formatResolution(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "--"

        return when {
            width >= 7680 || height >= 4320 -> "4320p"
            width >= 3840 || height >= 2160 -> "2160p"
            width >= 2560 || height >= 1440 -> "1440p"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            width >= 854 || height >= 480 -> "480p"
            width >= 640 || height >= 360 -> "360p"
            width >= 426 || height >= 240 -> "240p"
            else -> "${height}p"
        }
    }
}
