package app.marlboroadvance.mpvex.utils.permission

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Utility object for managing storage permissions across different Android versions.
 */
object PermissionUtils {
  /**
   * Returns the appropriate storage permission string based on Android version.
   * - Android 13+ (API 33+): READ_MEDIA_VIDEO
   * - Android 12 and below: READ_EXTERNAL_STORAGE
   */
  fun getStoragePermission(): String =
    if (Build.VERSION.SDK_INT >= 33) {
      android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
      android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

  /**
   * Composable that provides a permission state for storage access.
   * Automatically selects the correct permission based on Android version.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun rememberStoragePermissionState(): PermissionState = rememberPermissionState(getStoragePermission())

  /**
   * Composable that handles storage permission with an automatic refresh callback
   * when permission is granted.
   *
   * @param onPermissionGranted Callback invoked when permission is granted
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun handleStoragePermission(onPermissionGranted: () -> Unit): PermissionState {
    val permissionState = rememberStoragePermissionState()

    LaunchedEffect(permissionState.status) {
      if (permissionState.status == PermissionStatus.Granted) {
        onPermissionGranted()
      }
    }

    return permissionState
  }
}
