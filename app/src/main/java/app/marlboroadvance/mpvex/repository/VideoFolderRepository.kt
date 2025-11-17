package app.marlboroadvance.mpvex.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.utils.storage.StorageScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object VideoFolderRepository {
  private const val TAG = "VideoFolderRepository"

  // Cache the external storage path to avoid repeated calls
  private val externalStoragePath: String by lazy { Environment.getExternalStorageDirectory().path }

  suspend fun getVideoFolders(context: Context): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      val folders = mutableMapOf<String, VideoFolderInfo>()

      // First, scan via MediaStore (fast, indexed)
      scanAllStorageVolumes(context, folders)

      // Then, scan file system directly for external volumes (catches unindexed files)
      scanFileSystemDirectly(context, folders)

      convertToVideoFolderList(folders)
    }

  private fun scanFileSystemDirectly(
    context: Context,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    try {
      // Scan ALL storage volumes (not just external), including USB OTG
      val allVolumes = StorageScanUtils.getAllStorageVolumes(context)
      Log.d(TAG, "Scanning ${allVolumes.size} volumes via filesystem")

      for (volume in allVolumes) {
        val volumePath = StorageScanUtils.getVolumePath(volume)
        if (volumePath != null) {
          Log.d(TAG, "Filesystem scanning volume: ${volume.getDescription(context)} at $volumePath")

          val volumeDir = File(volumePath)
          if (!volumeDir.exists() || !volumeDir.canRead()) {
            Log.w(TAG, "Volume not accessible: $volumePath")
            continue
          }

          StorageScanUtils.scanDirectoryForVideos(
            volumeDir,
            { folder, videoFiles ->
              val folderPath = normalizePath(folder.absolutePath)
              val bucketId = folderPath
              val folderName = folder.name
              val totalSize = videoFiles.sumOf { it.length() }
              val lastModified = videoFiles.maxOfOrNull { it.lastModified() } ?: 0L

              Log.d(TAG, "Found ${videoFiles.size} videos in $folderPath")

              // Update existing folder info or create new
              val existing = folders[bucketId]
              if (existing != null) {
                // Merge with existing entry from MediaStore
                val allProcessedVideos = existing.processedVideos.toMutableSet()
                videoFiles.forEach { allProcessedVideos.add(it.absolutePath) }

                folders[bucketId] = existing.copy(
                  videoCount = allProcessedVideos.size,
                  totalSize = existing.totalSize + totalSize,
                  processedVideos = allProcessedVideos,
                )
              } else {
                // Create new entry
                folders[bucketId] =
                  VideoFolderInfo(
                    bucketId = bucketId,
                    name = folderName,
                    path = folderPath,
                    videoCount = videoFiles.size,
                    totalSize = totalSize,
                    totalDuration = 0L,
                    lastModified = lastModified / 1000,
                    processedVideos = videoFiles.map { it.absolutePath }.toMutableSet(),
                  )
              }
            },
          )
        } else {
          Log.w(TAG, "Could not get path for volume: ${volume.getDescription(context)}")
        }
      }

      Log.d(TAG, "Filesystem scan complete. Found ${folders.size} total folders")
    } catch (e: Exception) {
      Log.e(TAG, "Error in direct filesystem scan", e)
    }
  }

  private fun scanAllStorageVolumes(
    context: Context,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    try {
      val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
      val volumes = storageManager.storageVolumes

      for (volume in volumes) {
        if (volume.state == Environment.MEDIA_MOUNTED) {
          scanVolumeForVideos(context, volume, folders)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning storage volumes", e)
      // Fallback to default external storage scan
      scanDefaultExternalStorage(context, folders)
    }
  }

  private fun scanVolumeForVideos(
    context: Context,
    volume: StorageVolume,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    try {
      // Build URI for this specific volume
      val contentUri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          if (volume.isPrimary) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
          } else {
            // For non-primary volumes (SD card, USB), use volume-specific URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              val volumeName = volume.mediaStoreVolumeName
              Log.d(TAG, "MediaStore volume name: $volumeName")
              MediaStore.Video.Media.getContentUri(volumeName)
            } else {
              // Fallback for Android 10-11
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
          }
        } else {
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

      val projection = getProjection()
      val cursor: Cursor? =
        context.contentResolver.query(
          contentUri,
          projection,
          null,
          null,
          "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )

      cursor?.use { c ->
        processFolderCursor(c, folders)
      }
    } catch (e: IllegalArgumentException) {
      // Volume not accessible via MediaStore (e.g., USB OTG not indexed)
      // This is fine - filesystem scan will catch these volumes
      Log.d(TAG, "Volume not indexed in MediaStore: ${volume.getDescription(context)}")
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning volume: ${volume.getDescription(context)}", e)
    }
  }

  private fun scanDefaultExternalStorage(
    context: Context,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    try {
      val projection = getProjection()
      val cursor: Cursor? =
        context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          null,
          null,
          "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )

      cursor?.use { c -> processFolderCursor(c, folders) }
    } catch (e: Exception) {
      Log.e(TAG, "Error in fallback scan", e)
    }
  }

  @SuppressLint("SdCardPath")
  private fun normalizePath(path: String): String {
    if (path.isBlank()) return path

    val preprocessedPath =
      when {
        path.startsWith("/sdcard/") || path == "/sdcard" -> path.replaceFirst("/sdcard", externalStoragePath)
        path.startsWith("/mnt/sdcard/") || path == "/mnt/sdcard" ->
          path.replaceFirst(
            "/mnt/sdcard",
            externalStoragePath,
          )

        else -> path
      }

    return try {
      val canonicalPath = File(preprocessedPath).canonicalPath
      if (canonicalPath.length > 1 && canonicalPath.endsWith("/")) canonicalPath.dropLast(1) else canonicalPath
    } catch (e: SecurityException) {
      Log.w(TAG, "Security exception normalizing path: $path", e)
      preprocessedPath.trimEnd('/')
    } catch (e: Exception) {
      Log.w(TAG, "Error normalizing path: $path", e)
      preprocessedPath.trimEnd('/')
    }
  }

  private fun getProjection(): Array<String> =
    arrayOf(
      MediaStore.Video.Media.BUCKET_ID,
      MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DATE_MODIFIED,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.DURATION,
    )

  private fun processFolderCursor(
    cursor: Cursor,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
    val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

    while (cursor.moveToNext()) {
      val bucketId = cursor.getString(bucketIdColumn)
      val bucketName = cursor.getString(bucketNameColumn)
      val filePath = cursor.getString(dataColumn)
      val dateModified = cursor.getLong(dateModifiedColumn)
      val size = cursor.getLong(sizeColumn)
      val duration = cursor.getLong(durationColumn)

      processVideoFile(filePath, bucketId, bucketName, dateModified, size, duration, folders)
    }
  }

  private fun processVideoFile(
    filePath: String?,
    bucketId: String?,
    bucketName: String?,
    dateModified: Long,
    size: Long,
    duration: Long,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    if (filePath == null) return

    val normalizedPath = normalizePath(File(filePath).parent ?: return)
    val (finalBucketId, finalBucketName) = getFinalBucketInfo(bucketId, bucketName, normalizedPath)

    updateFolderInfo(finalBucketId, finalBucketName, normalizedPath, dateModified, size, duration, filePath, folders)
  }

  private fun getFinalBucketInfo(
    bucketId: String?,
    bucketName: String?,
    folderPath: String,
  ): Pair<String, String> {
    // Always use path as bucket ID for consistency
    // This ensures no duplicates and works for all storage types
    val finalBucketId = folderPath

    val finalBucketName =
      when {
        !bucketName.isNullOrBlank() -> bucketName
        folderPath == externalStoragePath -> "Internal Storage"
        folderPath.startsWith(externalStoragePath) -> File(folderPath).name
        else -> File(folderPath).name.takeIf { it.isNotBlank() } ?: "Unknown Folder"
      }

    return Pair(finalBucketId, finalBucketName)
  }

  @Suppress("LongParameterList")
  private fun updateFolderInfo(
    bucketId: String,
    bucketName: String,
    folderPath: String,
    dateModified: Long,
    size: Long,
    duration: Long,
    filePath: String,
    folders: MutableMap<String, VideoFolderInfo>,
  ) {
    val normalizedFilePath = normalizePath(filePath)

    val folderInfo =
      folders[bucketId] ?: VideoFolderInfo(
        bucketId = bucketId,
        name = bucketName,
        path = folderPath,
        videoCount = 0,
        totalSize = 0L,
        totalDuration = 0L,
        lastModified = 0L,
        processedVideos = mutableSetOf(),
      )

    if (folderInfo.processedVideos.add(normalizedFilePath)) {
      folders[bucketId] =
        folderInfo.copy(
          videoCount = folderInfo.videoCount + 1,
          totalSize = folderInfo.totalSize + size,
          totalDuration = folderInfo.totalDuration + duration,
          lastModified = maxOf(folderInfo.lastModified, dateModified),
          processedVideos = folderInfo.processedVideos,
        )
    }
  }

  private fun convertToVideoFolderList(folders: Map<String, VideoFolderInfo>): List<VideoFolder> =
    folders.values
      .map { info ->
        VideoFolder(
          bucketId = info.bucketId,
          name = info.name,
          path = info.path,
          videoCount = info.videoCount,
          totalSize = info.totalSize,
          totalDuration = info.totalDuration,
          lastModified = info.lastModified,
        )
      }.sortedBy { it.name.lowercase(Locale.getDefault()) }

  private data class VideoFolderInfo(
    val bucketId: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSize: Long,
    val totalDuration: Long,
    val lastModified: Long,
    val processedVideos: MutableSet<String> = mutableSetOf(),
  )
}
