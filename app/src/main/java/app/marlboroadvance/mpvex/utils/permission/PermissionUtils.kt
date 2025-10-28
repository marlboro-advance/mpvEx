package app.marlboroadvance.mpvex.utils.permission

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.marlboroadvance.mpvex.data.media.repository.FileSystemVideoRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.media.PlaybackStateOps
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simplified storage permission utilities with MANAGE_EXTERNAL_STORAGE support.
 *
 * With MANAGE_EXTERNAL_STORAGE permission, all file operations (delete, rename, read)
 * work directly without MediaStore confirmation sheets.
 */
object PermissionUtils {
  /**
   * Returns READ_EXTERNAL_STORAGE permission for all Android versions.
   * On Android 11+, MANAGE_EXTERNAL_STORAGE provides full file access.
   */
  fun getStoragePermission(): String = android.Manifest.permission.READ_EXTERNAL_STORAGE

  /**
   * Creates a permission state for storage access.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun rememberStoragePermissionState(): PermissionState = rememberPermissionState(getStoragePermission())

  /**
   * Handles storage permission and invokes [onPermissionGranted] when granted.
   * On Android 11+, also checks MANAGE_EXTERNAL_STORAGE permission.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun handleStoragePermission(onPermissionGranted: () -> Unit): PermissionState {
    val permissionState = rememberStoragePermissionState()
    val context = LocalContext.current
    var lifecycleTrigger by remember { mutableIntStateOf(0) }

    // Re-check permission when app resumes from Settings
    DisposableEffect(Unit) {
      val lifecycleOwner = context as? LifecycleOwner
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            lifecycleTrigger++
          }
        }
      lifecycleOwner?.lifecycle?.addObserver(observer)
      onDispose {
        lifecycleOwner?.lifecycle?.removeObserver(observer)
      }
    }

    // Wrap permission state to consider MANAGE_EXTERNAL_STORAGE on Android 11+
    val effectivePermissionState =
      remember(permissionState.status, lifecycleTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
          android.os.Environment.isExternalStorageManager()
        ) {
          object : PermissionState {
            override val permission = permissionState.permission
            override val status = PermissionStatus.Granted

            override fun launchPermissionRequest() = permissionState.launchPermissionRequest()
          }
        } else {
          permissionState
        }
      }

    LaunchedEffect(effectivePermissionState.status) {
      if (effectivePermissionState.status == PermissionStatus.Granted) {
        onPermissionGranted()
      }
    }

    return effectivePermissionState
  }

  // --------------------------------------------------------------------------
  // Direct file operations (works with MANAGE_EXTERNAL_STORAGE)
  // --------------------------------------------------------------------------

  object StorageOps {
    private const val TAG = "StorageOps"

    /**
     * Delete videos using direct file operations.
     * Requires MANAGE_EXTERNAL_STORAGE on Android 11+.
     */
    suspend fun deleteVideos(
      context: Context,
      videos: List<Video>,
      videoRepository: FileSystemVideoRepository,
    ): Pair<Int, Int> =
      withContext(Dispatchers.IO) {
        var deleted = 0
        var failed = 0
        val deletedPaths = mutableListOf<String>()

        for (video in videos) {
          try {
            val file = File(video.path)
            if (file.exists() && file.delete()) {
              deleted++
              deletedPaths.add(video.path)
              RecentlyPlayedOps.onVideoDeleted(video.path)
              PlaybackStateOps.onVideoDeleted(video.path)
              Log.d(TAG, "✓ Deleted: ${video.displayName}")
            } else {
              failed++
              Log.w(TAG, "✗ Failed to delete: ${video.displayName}")
            }
          } catch (e: Exception) {
            failed++
            Log.e(TAG, "✗ Error deleting ${video.displayName}", e)
          }
        }

        // Update database cache and notify
        if (deletedPaths.isNotEmpty()) {
          videoRepository.removeCacheForFiles(context, deletedPaths)
        }

        Pair(deleted, failed)
      }

    /**
     * Rename video using direct file operations.
     * Requires MANAGE_EXTERNAL_STORAGE on Android 11+.
     */
    suspend fun renameVideo(
      context: Context,
      video: Video,
      newDisplayName: String,
      videoRepository: FileSystemVideoRepository,
    ): Result<Unit> =
      withContext(Dispatchers.IO) {
        try {
          val oldFile = File(video.path)
          val newFile = File(oldFile.parentFile, newDisplayName)

          if (oldFile.exists() && oldFile.renameTo(newFile)) {
            // Update history
            RecentlyPlayedOps.onVideoRenamed(oldFile.absolutePath, newFile.absolutePath)
            PlaybackStateOps.onVideoRenamed(oldFile.absolutePath, newFile.absolutePath)

            // Update database cache
            videoRepository.removeCacheForFiles(context, listOf(oldFile.absolutePath))
            videoRepository.updateCacheForFiles(context, listOf(newFile.absolutePath))
            Log.d(TAG, "✓ Renamed: ${video.displayName} -> $newDisplayName")
            Result.success(Unit)
          } else {
            Log.w(TAG, "✗ Rename failed: ${video.displayName}")
            Result.failure(IllegalStateException("Rename operation failed"))
          }
        } catch (e: Exception) {
          Log.e(TAG, "✗ Error renaming ${video.displayName}", e)
          Result.failure(e)
        }
      }
  }
}
