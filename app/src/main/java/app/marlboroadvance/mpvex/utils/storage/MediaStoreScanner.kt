package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Simple MediaStore-based scanner for videos
 * Much faster and simpler than recursive file scanning
 */
object MediaStoreScanner {
    private const val TAG = "MediaStoreScanner"

    // Helper data class
    private data class FolderInfo(
        val path: String,
        val name: String,
        var videoCount: Int,
        var totalSize: Long,
        var totalDuration: Long,
        var lastModified: Long
    )

    /**
     * Scans all video folders using MediaStore
     * This is much faster than recursive file scanning
     */
    suspend fun getAllVideoFolders(
        context: Context,
        showHiddenFiles: Boolean = false
    ): List<VideoFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, FolderInfo>()
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val selection = if (!showHiddenFiles) {
            "${MediaStore.Video.Media.DATA} NOT LIKE '%/.%'"
        } else null
        
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    val info = folders.getOrPut(folderPath) {
                        FolderInfo(
                            path = folderPath,
                            name = File(folderPath).name,
                            videoCount = 0,
                            totalSize = 0L,
                            totalDuration = 0L,
                            lastModified = 0L
                        )
                    }
                    
                    info.videoCount++
                    info.totalSize += size
                    info.totalDuration += duration
                    if (dateModified > info.lastModified) {
                        info.lastModified = dateModified
                    }
                }
            }
            
            Log.d(TAG, "Found ${folders.size} video folders via MediaStore")
            
            folders.values.map { info ->
                VideoFolder(
                    bucketId = info.path,
                    name = info.name,
                    path = info.path,
                    videoCount = info.videoCount,
                    totalSize = info.totalSize,
                    totalDuration = info.totalDuration,
                    lastModified = info.lastModified
                )
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore", e)
            emptyList()
        }
    }

    /**
     * Gets all videos in a specific folder using MediaStore
     * Note: FPS and subtitle info are not available from MediaStore (set to 0/false)
     * Use MetadataRetrieval.enrichVideosIfNeeded() to extract detailed metadata when chips are enabled
     */
    suspend fun getVideosInFolder(
        context: Context,
        folderPath: String,
        showHiddenFiles: Boolean = false
    ): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        val selection = buildString {
            append("${MediaStore.Video.Media.DATA} LIKE ?")
            if (!showHiddenFiles) {
                append(" AND ${MediaStore.Video.Media.DATA} NOT LIKE '%/.%'")
            }
        }
        
        val selectionArgs = arrayOf("$folderPath/%")
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    // Only include videos directly in this folder (not subfolders)
                    if (file.parent != folderPath) continue
                    if (!file.exists()) continue
                    
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val title = file.nameWithoutExtension
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videos.add(
                        Video(
                            id = id,
                            title = title,
                            displayName = displayName,
                            path = path,
                            uri = uri,
                            duration = duration,
                            durationFormatted = formatDuration(duration),
                            size = size,
                            sizeFormatted = formatFileSize(size),
                            dateModified = dateModified,
                            dateAdded = dateAdded,
                            mimeType = mimeType,
                            bucketId = folderPath,
                            bucketDisplayName = File(folderPath).name,
                            width = width,
                            height = height,
                            fps = 0f,
                            resolution = formatResolution(width, height),
                            hasEmbeddedSubtitles = false,
                            subtitleCodec = ""
                        )
                    )
                }
            }
            
            Log.d(TAG, "Found ${videos.size} videos in $folderPath via MediaStore")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying videos from MediaStore", e)
        }
        
        videos
    }

    /**
     * Gets all videos from MediaStore
     */
    suspend fun getAllVideos(
        context: Context,
        showHiddenFiles: Boolean = false
    ): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        val selection = if (!showHiddenFiles) {
            "${MediaStore.Video.Media.DATA} NOT LIKE '%/.%'"
        } else null
        
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val file = File(path)
                    
                    if (!file.exists()) continue
                    
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val title = file.nameWithoutExtension
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    
                    val folderPath = file.parent ?: ""
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videos.add(
                        Video(
                            id = id,
                            title = title,
                            displayName = displayName,
                            path = path,
                            uri = uri,
                            duration = duration,
                            durationFormatted = formatDuration(duration),
                            size = size,
                            sizeFormatted = formatFileSize(size),
                            dateModified = dateModified,
                            dateAdded = dateAdded,
                            mimeType = mimeType,
                            bucketId = folderPath,
                            bucketDisplayName = File(folderPath).name,
                            width = width,
                            height = height,
                            fps = 0f,
                            resolution = formatResolution(width, height),
                            hasEmbeddedSubtitles = false,
                            subtitleCodec = ""
                        )
                    )
                }
            }
            
            Log.d(TAG, "Found ${videos.size} total videos via MediaStore")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying all videos from MediaStore", e)
        }
        
        videos
    }

    // Formatting utilities
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0s"

        val seconds = durationMs / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
            else -> "${secs}s"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
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
