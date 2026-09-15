package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.media.MediaFormatUtils
import app.marlboroadvance.mpvex.utils.media.MediaInfoOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Video Scanning Utilities
 * Handles video file scanning and metadata extraction
 */
object VideoScanUtils {
    private const val TAG = "VideoScanUtils"
    
    /**
     * Video metadata extracted from files
     */
    data class VideoMetadata(
        val duration: Long,
        val mimeType: String,
        val width: Int = 0,
        val height: Int = 0,
    )
    
    /**
     * Get all videos in a specific folder
     * MediaStore first, filesystem fallback for external devices
     */
    suspend fun getVideosInFolder(
        context: Context,
        folderPath: String
    ): List<Video> = withContext(Dispatchers.IO) {
        val videosMap = mutableMapOf<String, Video>()
        
        // Try MediaStore first (fast)
        scanVideosFromMediaStore(context, folderPath, videosMap)
        
        // Fallback to filesystem if MediaStore returned nothing
        val folder = File(folderPath)
        if (folder.exists() && folder.canRead() && videosMap.isEmpty()) {
            scanVideosFromFileSystem(context, folder, videosMap)
        }
        
        videosMap.values.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }
    
    /**
     * Scan videos from MediaStore
     */
    private fun scanVideosFromMediaStore(
        context: Context,
        folderPath: String,
        videosMap: MutableMap<String, Video>
    ) {
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
        
        val normalizedFolder = folderPath.trimEnd('/')
        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$normalizedFolder/%")
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
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
                    
                    // Only direct children
                    if (file.parent?.trimEnd('/') != normalizedFolder) continue
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
                    
                    videosMap[path] = Video(
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
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore video scan error", e)
        }
    }
    
    /**
     * Scan videos from filesystem (fallback)
     */
    private suspend fun scanVideosFromFileSystem(
        context: Context,
        folder: File,
        videosMap: MutableMap<String, Video>
    ) {
        try {
            val files = folder.listFiles() ?: return
            
            for (file in files) {
                try {
                    if (!file.isFile) continue
                    
                    val extension = file.extension.lowercase(Locale.getDefault())
                    if (!FileTypeUtils.VIDEO_EXTENSIONS.contains(extension)) continue
                    
                    val path = file.absolutePath
                    if (videosMap.containsKey(path)) continue
                    
                    val uri = Uri.fromFile(file)
                    val displayName = file.name
                    val title = file.nameWithoutExtension
                    val size = file.length()
                    val dateModified = file.lastModified() / 1000
                    
                    // Extract metadata
                    val metadata = extractVideoMetadata(context, file)
                    
                    videosMap[path] = Video(
                        id = path.hashCode().toLong(),
                        title = title,
                        displayName = displayName,
                        path = path,
                        uri = uri,
                        duration = metadata.duration,
                        durationFormatted = formatDuration(metadata.duration),
                        size = size,
                        sizeFormatted = formatFileSize(size),
                        dateModified = dateModified,
                        dateAdded = dateModified,
                        mimeType = metadata.mimeType,
                        bucketId = folder.absolutePath,
                        bucketDisplayName = folder.name,
                        width = metadata.width,
                        height = metadata.height,
                        fps = 0f,
                        resolution = formatResolution(metadata.width, metadata.height),
                        hasEmbeddedSubtitles = false,
                        subtitleCodec = ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
                    continue
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Filesystem video scan error", e)
        }
    }
    
    /**
     * Extracts video metadata using MediaInfo library
     */
    suspend fun extractVideoMetadata(
        context: Context,
        file: File,
    ): VideoMetadata {
        var duration = 0L
        var mimeType = "video/*"
        var width = 0
        var height = 0
        
        try {
            val uri = Uri.fromFile(file)
            val result = MediaInfoOps.extractBasicMetadata(context, uri, file.name)
            
            result.onSuccess { metadata ->
                duration = metadata.durationMs
                width = metadata.width
                height = metadata.height
                mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
            }.onFailure { e ->
                Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
                mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract metadata for ${file.absolutePath}, using fallback", e)
            mimeType = FileTypeUtils.getMimeTypeFromExtension(file.extension.lowercase())
        }
        
        return VideoMetadata(duration, mimeType, width, height)
    }
    
    // Formatting utilities delegate to MediaFormatUtils
    private fun formatDuration(durationMs: Long): String = MediaFormatUtils.formatDuration(durationMs)
    private fun formatFileSize(bytes: Long): String = MediaFormatUtils.formatFileSize(bytes)
    private fun formatResolution(width: Int, height: Int): String = MediaFormatUtils.formatResolution(width, height)
}

