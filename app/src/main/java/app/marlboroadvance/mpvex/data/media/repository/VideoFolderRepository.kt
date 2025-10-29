package app.marlboroadvance.mpvex.data.media.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object VideoFolderRepository {
  private const val Tag = "VideoFolderRepository"

  // Cache the external storage path to avoid repeated calls
  private val externalStoragePath: String by lazy { Environment.getExternalStorageDirectory().path }

  suspend fun getVideoFolders(context: Context): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      Log.d(Tag, "Starting video folder scan")
      val folders = mutableMapOf<String, VideoFolderInfo>()

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

      Log.d(Tag, "Finished video folder scan")
      val result = convertToVideoFolderList(folders)
      Log.d(Tag, "Found ${result.size} folders")
      result
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
      Log.w(Tag, "Security exception normalizing path: $path", e)
      preprocessedPath.trimEnd('/')
    } catch (e: Exception) {
      Log.w(Tag, "Error normalizing path: $path", e)
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

    Log.d(Tag, "Found video: $filePath, bucketId: $finalBucketId, bucketName: $finalBucketName")

    updateFolderInfo(finalBucketId, finalBucketName, normalizedPath, dateModified, size, duration, filePath, folders)
  }

  private fun getFinalBucketInfo(
    bucketId: String?,
    bucketName: String?,
    folderPath: String,
  ): Pair<String, String> {
    val finalBucketId = bucketId?.takeIf { it.isNotBlank() } ?: folderPath.hashCode().toString()
    val finalBucketName =
      when {
        !bucketName.isNullOrBlank() -> bucketName
        folderPath.contains(externalStoragePath) && folderPath == externalStoragePath -> "Internal Storage"
        folderPath.contains(externalStoragePath) -> File(folderPath).name
        else -> "Unknown Folder"
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
      Log.d(Tag, "Counted video: $normalizedFilePath in folder $bucketName (total: ${folderInfo.videoCount + 1})")
    } else {
      Log.d(Tag, "Skipping duplicate video: $normalizedFilePath in folder $bucketName")
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
