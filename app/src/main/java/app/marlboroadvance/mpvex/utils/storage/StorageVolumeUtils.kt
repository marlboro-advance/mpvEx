package app.marlboroadvance.mpvex.utils.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import java.io.File

/**
 * Storage Volume Utilities
 * Handles detection and path resolution for primary and external (SD, OTG) volumes.
 */
object StorageVolumeUtils {
  private const val TAG = "StorageVolumeUtils"

  /**
   * Gets all mounted storage volumes
   */
  fun getAllStorageVolumes(context: Context): List<StorageVolume> =
    try {
      val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
      storageManager.storageVolumes.filter { volume ->
        volume.state == Environment.MEDIA_MOUNTED ||
          (getVolumePath(volume)?.let { path -> File(path).exists() } == true)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting storage volumes", e)
      emptyList()
    }

  /**
   * Gets non-primary (external) storage volumes (SD cards, USB OTG)
   */
  fun getExternalStorageVolumes(context: Context): List<StorageVolume> =
    getAllStorageVolumes(context).filter { !it.isPrimary }

  /**
   * Gets the physical path of a storage volume
   */
  fun getVolumePath(volume: StorageVolume): String? {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val directory = volume.directory
        if (directory != null) {
          return directory.absolutePath
        }
      }

      val method = volume.javaClass.getMethod("getPath")
      val path = method.invoke(volume) as? String
      if (path != null) {
        return path
      }

      volume.uuid?.let { uuid ->
        val possiblePaths = listOf(
          "/storage/$uuid",
          "/mnt/media_rw/$uuid",
        )
        for (possiblePath in possiblePaths) {
          if (File(possiblePath).exists()) {
            return possiblePath
          }
        }
      }

      return null
    } catch (e: Exception) {
      Log.w(TAG, "Could not get volume path", e)
      return null
    }
  }
}
