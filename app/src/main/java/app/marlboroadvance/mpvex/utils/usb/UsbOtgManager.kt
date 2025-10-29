package app.marlboroadvance.mpvex.utils.usb

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object UsbOtgManager {
  private const val TAG = "UsbOtgManager"

  private val _usbOtgDevices = MutableStateFlow<List<UsbOtgDevice>>(emptyList())
  val usbOtgDevices: StateFlow<List<UsbOtgDevice>> = _usbOtgDevices.asStateFlow()

  /**
   * Scans for connected USB OTG devices
   */
  fun scanForUsbDevices(context: Context) {
    try {
      Log.d(TAG, "Scanning for USB OTG devices...")
      val devices = mutableListOf<UsbOtgDevice>()

      val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

      // Get all storage volumes
      val volumes = storageManager.storageVolumes

      for (volume in volumes) {
        Log.d(
          TAG,
          "Checking volume: ${volume.getDescription(context)}, " +
            "removable: ${volume.isRemovable}, " +
            "primary: ${volume.isPrimary}, " +
            "state: ${volume.state}",
        )

        // Check if it's a removable, non-primary storage (USB OTG)
        if (volume.isRemovable && !volume.isPrimary &&
          volume.state == Environment.MEDIA_MOUNTED
        ) {
          // Try to get the path
          val path = getVolumePath(volume)
          if (path != null && File(path).exists()) {
            val device =
              UsbOtgDevice(
                name = volume.getDescription(context),
                path = path,
                uuid = volume.uuid ?: path.hashCode().toString(),
              )
            devices.add(device)
            Log.d(TAG, "Found USB OTG device: ${device.name} at ${device.path}")
          }
        }
      }

      // Also check common mount points as fallback
      val commonMountPoints =
        listOf(
          "/storage/usbotg",
          "/mnt/usb",
          "/mnt/usbotg",
          "/mnt/usb_storage",
          "/mnt/media_rw",
        )

      for (mountPoint in commonMountPoints) {
        val dir = File(mountPoint)
        if (dir.exists() && dir.isDirectory) {
          dir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory && subDir.canRead()) {
              val existing = devices.find { it.path == subDir.absolutePath }
              if (existing == null) {
                val device =
                  UsbOtgDevice(
                    name = "USB: ${subDir.name}",
                    path = subDir.absolutePath,
                    uuid = subDir.absolutePath.hashCode().toString(),
                  )
                devices.add(device)
                Log.d(TAG, "Found USB OTG device via mount point: ${device.name} at ${device.path}")
              }
            }
          }
        }
      }

      _usbOtgDevices.value = devices
      Log.d(TAG, "USB scan complete. Found ${devices.size} device(s)")
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning for USB devices", e)
      _usbOtgDevices.value = emptyList()
    }
  }

  /**
   * Gets the path of a storage volume using reflection if necessary
   */
  private fun getVolumePath(volume: StorageVolume): String? =
    try {
      // Try reflection to get path (works on most Android versions)
      val method = volume.javaClass.getMethod("getPath")
      method.invoke(volume) as? String
    } catch (e: Exception) {
      Log.w(TAG, "Could not get volume path via reflection", e)

      // Fallback: try to construct path from UUID
      volume.uuid?.let { uuid ->
        val possiblePaths =
          listOf(
            "/storage/$uuid",
            "/mnt/media_rw/$uuid",
          )
        possiblePaths.find { File(it).exists() }
      }
    }

  /**
   * Gets video folders from a USB OTG device
   */
  fun getUsbVideoFolders(device: UsbOtgDevice): List<UsbVideoFolder> {
    val folders = mutableListOf<UsbVideoFolder>()

    try {
      val rootDir = File(device.path)
      if (!rootDir.exists() || !rootDir.canRead()) {
        Log.w(TAG, "Cannot access USB device at ${device.path}")
        return emptyList()
      }

      scanDirectoryForVideos(rootDir, device, folders)
      Log.d(TAG, "Found ${folders.size} video folders on USB device ${device.name}")
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning USB device for videos", e)
    }

    return folders
  }

  /**
   * Recursively scans a directory for video files
   */
  private fun scanDirectoryForVideos(
    directory: File,
    device: UsbOtgDevice,
    folders: MutableList<UsbVideoFolder>,
    maxDepth: Int = 5,
    currentDepth: Int = 0,
  ) {
    if (currentDepth >= maxDepth) return

    try {
      val files = directory.listFiles() ?: return
      val videoFiles = files.filter { it.isFile && isVideoFile(it) }

      if (videoFiles.isNotEmpty()) {
        val totalSize = videoFiles.sumOf { it.length() }
        val lastModified = videoFiles.maxOfOrNull { it.lastModified() } ?: 0L

        val folder =
          UsbVideoFolder(
            usbDevice = device,
            path = directory.absolutePath,
            name = directory.name,
            videoCount = videoFiles.size,
            totalSize = totalSize,
            lastModified = lastModified,
          )
        folders.add(folder)
        Log.d(TAG, "Found video folder: ${folder.name} with ${folder.videoCount} videos")
      }

      // Recursively scan subdirectories
      files
        .filter { it.isDirectory && it.canRead() }
        .forEach { subDir ->
          scanDirectoryForVideos(subDir, device, folders, maxDepth, currentDepth + 1)
        }
    } catch (e: SecurityException) {
      Log.w(TAG, "Security exception scanning directory: ${directory.path}", e)
    } catch (e: Exception) {
      Log.w(TAG, "Error scanning directory: ${directory.path}", e)
    }
  }

  /**
   * Checks if a file is a video file based on extension
   */
  private fun isVideoFile(file: File): Boolean {
    val videoExtensions =
      setOf(
        "mp4",
        "mkv",
        "avi",
        "mov",
        "wmv",
        "flv",
        "webm",
        "m4v",
        "3gp",
        "3g2",
        "mpg",
        "mpeg",
        "m2v",
        "m4v",
        "ogv",
        "ts",
        "mts",
        "m2ts",
        "vob",
        "divx",
        "xvid",
      )
    return videoExtensions.contains(file.extension.lowercase())
  }
}

/**
 * Represents a connected USB OTG device
 */
data class UsbOtgDevice(
  val name: String,
  val path: String,
  val uuid: String,
)

/**
 * Represents a folder containing videos on a USB OTG device
 */
data class UsbVideoFolder(
  val usbDevice: UsbOtgDevice,
  val path: String,
  val name: String,
  val videoCount: Int,
  val totalSize: Long,
  val lastModified: Long,
)
