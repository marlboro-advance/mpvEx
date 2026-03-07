package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Folder View Scanner - Optimized for folder list view
 * 
 * Only shows folders with immediate video children (not recursive)
 * Fast scanning using MediaStore + filesystem fallback
 */
object FolderViewScanner {
    private const val TAG = "FolderViewScanner"
    
    // Smart cache with short TTL (10 seconds)
    private var cachedFolderList: List<VideoFolder>? = null
    private var cacheTimestamp: Long = 0
    private const val CACHE_TTL_MS = 10_000L // 10 seconds for faster refresh
    
    /**
     * Clear cache (call when media library changes)
     */
    fun clearCache() {
        cachedFolderList = null
        cacheTimestamp = 0
    }
    
    /**
     * Folder metadata
     */
    data class FolderData(
        val path: String,
        val name: String,
        val videoCount: Int,
        val totalSize: Long,
        val totalDuration: Long,
        val lastModified: Long,
        val hasSubfolders: Boolean = false
    )
    
    /**
     * Helper data class for video info during scanning
     */
    private data class VideoInfo(
        val size: Long,
        val duration: Long,
        val dateModified: Long
    )
    
    /**
     * Get all video folders for folder list view
     * Only shows folders with immediate video children (not recursive)
     */
    suspend fun getAllVideoFolders(context: Context): List<VideoFolder> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        // Return cached data if still valid
        cachedFolderList?.let { cached ->
            if (now - cacheTimestamp < CACHE_TTL_MS) {
                return@withContext cached
            }
        }
        
        // Build fresh data
        val allFolders = mutableMapOf<String, FolderData>()
        
        // Step 1: Scan MediaStore (fast, covers most cases)
        scanMediaStoreImmediateChildren(context, allFolders)
        
        // Step 2: Scan external volumes via filesystem (USB OTG, SD cards)
        scanExternalVolumes(context, allFolders)
        
        // Convert to VideoFolder list
        val result = allFolders.values.map { data ->
            VideoFolder(
                bucketId = data.path,
                name = data.name,
                path = data.path,
                videoCount = data.videoCount,
                totalSize = data.totalSize,
                totalDuration = data.totalDuration,
                lastModified = data.lastModified
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        
        // Update cache
        cachedFolderList = result
        cacheTimestamp = now
        
        result
    }
    
    /**
     * Scan MediaStore for all videos and build folder map (immediate children only)
     */
    private fun scanMediaStoreImmediateChildren(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                // Collect videos by folder
                val videosByFolder = mutableMapOf<String, MutableList<VideoInfo>>()
                
                while (cursor.moveToNext()) {
                    val videoPath = cursor.getString(dataColumn)
                    val file = File(videoPath)
                    
                    if (!file.exists()) continue
                    
                    val folderPath = file.parent ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    videosByFolder.getOrPut(folderPath) { mutableListOf() }.add(
                        VideoInfo(size, duration, dateModified)
                    )
                }
                
                // Build folder data - only count immediate children videos
                for ((folderPath, videos) in videosByFolder) {
                    var totalSize = 0L
                    var totalDuration = 0L
                    var lastModified = 0L
                    
                    for (video in videos) {
                        totalSize += video.size
                        totalDuration += video.duration
                        if (video.dateModified > lastModified) {
                            lastModified = video.dateModified
                        }
                    }
                    
                    // Check if this folder has subfolders with videos
                    val hasSubfolders = videosByFolder.keys.any { otherPath ->
                        otherPath != folderPath && 
                        otherPath.startsWith("$folderPath${File.separator}") &&
                        File(otherPath).parent == folderPath
                    }
                    
                    folders[folderPath] = FolderData(
                        path = folderPath,
                        name = File(folderPath).name,
                        videoCount = videos.size,
                        totalSize = totalSize,
                        totalDuration = totalDuration,
                        lastModified = lastModified,
                        hasSubfolders = hasSubfolders
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore scan error", e)
        }
    }
    
    /**
     * Scan external volumes (USB OTG, SD cards) via filesystem
     */
    private fun scanExternalVolumes(
        context: Context,
        folders: MutableMap<String, FolderData>
    ) {
        try {
            val externalVolumes = StorageVolumeUtils.getExternalStorageVolumes(context)
            
            if (externalVolumes.isEmpty()) {
                return
            }
            
            for (volume in externalVolumes) {
                val volumePath = StorageVolumeUtils.getVolumePath(volume)
                if (volumePath == null) {
                    continue
                }
                
                val volumeDir = File(volumePath)
                if (!volumeDir.exists() || !volumeDir.canRead()) {
                    continue
                }
                
                scanDirectoryRecursive(volumeDir, folders, maxDepth = 20)
            }
        } catch (e: Exception) {
            Log.e(TAG, "External volume scan error", e)
        }
    }
    
    /**
     * Recursively scan directory for videos
     */
    private fun scanDirectoryRecursive(
        directory: File,
        folders: MutableMap<String, FolderData>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return
        if (!directory.exists() || !directory.canRead() || !directory.isDirectory) return
        
        try {
            val files = directory.listFiles() ?: return
            
            val videoFiles = mutableListOf<File>()
            val subdirectories = mutableListOf<File>()
            
            for (file in files) {
                try {
                    when {
                        file.isDirectory -> {
                            if (!FileFilterUtils.shouldSkipFolder(file)) {
                                subdirectories.add(file)
                            }
                        }
                        file.isFile -> {
                            val extension = file.extension.lowercase(Locale.getDefault())
                            if (FileTypeUtils.VIDEO_EXTENSIONS.contains(extension)) {
                                videoFiles.add(file)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    continue
                }
            }
            
            // Add folder if it has videos
            if (videoFiles.isNotEmpty()) {
                val folderPath = directory.absolutePath
                
                // Skip if already from MediaStore
                if (!folders.containsKey(folderPath)) {
                    var totalSize = 0L
                    var lastModified = 0L
                    
                    for (video in videoFiles) {
                        totalSize += video.length()
                        val modified = video.lastModified()
                        if (modified > lastModified) {
                            lastModified = modified
                        }
                    }
                    
                    folders[folderPath] = FolderData(
                        path = folderPath,
                        name = directory.name,
                        videoCount = videoFiles.size,
                        totalSize = totalSize,
                        totalDuration = 0L, // Duration not available from filesystem
                        lastModified = lastModified / 1000,
                        hasSubfolders = subdirectories.isNotEmpty()
                    )
                }
            }
            
            // Recurse into subdirectories
            for (subdir in subdirectories) {
                scanDirectoryRecursive(subdir, folders, maxDepth, currentDepth + 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning: ${directory.absolutePath}", e)
        }
    }
}
