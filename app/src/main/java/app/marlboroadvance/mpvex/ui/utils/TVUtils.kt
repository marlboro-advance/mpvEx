package app.marlboroadvance.mpvex.ui.utils

import android.content.Context

/**
 * Utility object for Android TV specific functionality and configurations.
 * Centralizes all TV-related logic for better maintainability.
 */
object TVUtils {

  /**
   * Checks if the app is running on an Android TV device.
   * @param context Application context
   * @return true if running on Android TV, false otherwise
   */
  fun isAndroidTV(context: Context): Boolean {
    return DeviceUtils.isAndroidTV(context)
  }

  /**
   * TV-specific default configurations
   */
  object Defaults {
    /** Default dark mode setting for TV (always dark for better viewing experience) */
    const val DARK_MODE_ENABLED = true

    /** Default Material You setting for TV (disabled for consistency) */
    const val MATERIAL_YOU_ENABLED = false
  }

  /**
   * TV-specific file system paths for video scanning
   */
  object FileSystemPaths {
    /** Common video directories on Android TV */
    val COMMON_VIDEO_DIRECTORIES = listOf(
      "/storage/emulated/0/Movies",
      "/storage/emulated/0/Download",
      "/storage/emulated/0/DCIM",
      "/storage/emulated/0/Video",
      "/storage/emulated/0/Videos",
    )

    /** Root directory of external storage */
    const val EXTERNAL_STORAGE_ROOT = "/storage/emulated/0"

    /** Media mount directory for USB/SD cards */
    const val MEDIA_RW_DIR = "/mnt/media_rw"

    /** Common USB mount locations */
    val COMMON_USB_MOUNT_PATHS = listOf(
      "/mnt/media_rw",
      "/storage",
      "/mnt/usb",
      "/mnt/sdcard",
      "/usbotg",
      "/mnt/usbdisk",
      "/mnt/usb_storage",
      "/storage/usbotg",
      "/storage/usb1",
      "/storage/usb2",
    )

    /** File systems typically used by USB/SD cards */
    val USB_FILE_SYSTEMS = setOf(
      "vfat",
      "exfat",
      "ntfs",
      "fuse",
      "ext4",
      "ext3",
      "ext2",
    )

    /** Maximum depth for recursive directory scanning */
    const val MAX_SCAN_DEPTH = 3
  }

  /**
   * TV-specific UI configurations
   */
  object UI {
    /** Whether to show pull-to-refresh on TV (disabled for D-pad navigation) */
    const val ENABLE_PULL_TO_REFRESH = false

    /** Whether to show back button in player controls on TV */
    const val SHOW_BACK_BUTTON = false

    /** Whether to show PIP button on TV */
    const val SHOW_PIP_BUTTON = false

    /** Message to show when no videos found on TV */
    const val NO_VIDEOS_MESSAGE = "No videos found. Please connect a USB drive or SD card."

    /** Message to show when no videos found on non-TV devices */
    const val NO_VIDEOS_MESSAGE_DEFAULT = "No videos found"
  }

  /**
   * Checks if a mount point looks like an external USB/SD card mount
   */
  fun isExternalMount(device: String, mountPoint: String, fsType: String): Boolean {
    return device.startsWith("/dev/block/") &&
      FileSystemPaths.USB_FILE_SYSTEMS.contains(fsType) &&
      (mountPoint.startsWith("/mnt/media_rw/") ||
        mountPoint.startsWith("/storage/") && !mountPoint.contains("emulated") ||
        mountPoint.startsWith("/mnt/usb") ||
        mountPoint.startsWith("/usbotg"))
  }
}
