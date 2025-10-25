package app.marlboroadvance.mpvex.utils.permission

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.ContentResolver
import android.content.ContentValues
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized, scoped-storage friendly storage permission utilities.
 *
 * This object consolidates:
 * - Runtime permission helpers (READ_EXTERNAL_STORAGE / READ_MEDIA_VIDEO)
 * - ActivityResult launchers for storage operations (delete/rename via MediaStore)
 * - Scoped delete/rename handlers that never require MANAGE_EXTERNAL_STORAGE
 *
 * Notes
 * - On Android Q+ all destructive operations (delete, write/rename)
 *   must go through MediaStore's recoverable actions. The helpers here
 *   surface the system confirmation sheets using ActivityResult APIs.
 * - We deliberately avoid MANAGE_EXTERNAL_STORAGE (All Files Access)
 *   for Play Store compliance.
 */
object PermissionUtils {
  // --------------------------------------------------------------------------
  // Runtime permission helpers
  // --------------------------------------------------------------------------

  /**
   * Returns the appropriate runtime permission required to read media on this device.
   * - Android 13+ (API 33+): [android.Manifest.permission.READ_MEDIA_VIDEO]
   * - Android 12- (API 32 and below): [android.Manifest.permission.READ_EXTERNAL_STORAGE]
   */
  fun getStoragePermission(): String =
    if (Build.VERSION.SDK_INT >= 33) {
      android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
      android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

  /**
   * Creates a permission state for the storage permission appropriate to this device.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun rememberStoragePermissionState(): PermissionState = rememberPermissionState(getStoragePermission())

  /**
   * Observes the storage permission state and invokes [onPermissionGranted]
   * when it is granted. Returns the underlying permission state so callers can
   * trigger a request if needed.
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

  /**
   * Strongly-typed container for storage operation ActivityResult launchers.
   */
  data class StorageOperationLaunchers(
    val deleteLauncher: (IntentSenderRequest) -> Unit,
    val writeLauncher: (IntentSenderRequest) -> Unit,
  )

  /**
   * Prepares ActivityResult launchers used for storage operations.
   *
   * - Delete: receives system confirmation sheet result (Android Q+)
   * - Write (rename): receives system confirmation sheet result (Android Q+)
   */
  @Composable
  fun rememberStorageOperationLaunchers(
    onDeleteSuccess: () -> Unit,
    onWriteSuccess: () -> Unit,
  ): StorageOperationLaunchers {
    // Delete
    val deleteLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
      ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) onDeleteSuccess()
      }

    // Write (rename)
    val writeLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
      ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) onWriteSuccess()
      }

    return remember(deleteLauncher, writeLauncher) {
      StorageOperationLaunchers(
        deleteLauncher = { req -> deleteLauncher.launch(req) },
        writeLauncher = { req -> writeLauncher.launch(req) },
      )
    }
  }

  // --------------------------------------------------------------------------
  // Scoped storage operations (migrated from ScopedStorageOps)
  // --------------------------------------------------------------------------
  object StorageOps {
    private const val TAG = "StorageOps"

    data class DeleteResult(
      val deletedCount: Int,
      val failedCount: Int,
      val failedItems: List<Video> = emptyList(),
    )

    /** Build a delete request (Android R+) so system can show confirmation sheet */
    fun createDeleteRequest(
      contentResolver: ContentResolver,
      uris: List<android.net.Uri>,
    ): IntentSender? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
        MediaStore.createDeleteRequest(contentResolver, uris).intentSender
      } else {
        null
      }

    /** Build a write request (Android R+) for edits such as rename */
    fun createWriteIntentSender(
      contentResolver: ContentResolver,
      uris: List<android.net.Uri>,
    ): IntentSender? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
        MediaStore.createWriteRequest(contentResolver, uris).intentSender
      } else {
        null
      }

    /** Delete videos using ContentResolver (scoped) with best-effort file fallback */
    suspend fun deleteVideos(
      context: Context,
      videos: List<Video>,
    ): Pair<Int, Int> {
      var deleted = 0
      var failed = 0
      val resolver = context.contentResolver
      for (video in videos) {
        try {
          val rows = resolver.delete(video.uri, null, null)
          if (rows > 0) {
            deleted++
            kotlin.runCatching {
              // Keep recently played clean when an item is deleted
              withContext(Dispatchers.IO) { RecentlyPlayedOps.onVideoDeleted(video.path) }
            }
          } else failed++
        } catch (t: Throwable) {
          Log.e(TAG, "Failed to delete ${video.displayName}", t)
          try {
            val file = java.io.File(video.path)
            if (file.exists() && file.delete()) {
              deleted++
              kotlin.runCatching {
                withContext(Dispatchers.IO) { RecentlyPlayedOps.onVideoDeleted(video.path) }
              }
            } else failed++
          } catch (_: Throwable) {
            failed++
          }
        }
      }
      return Pair(deleted, failed)
    }

    suspend fun deleteVideosDetailed(
      context: Context,
      videos: List<Video>,
    ): DeleteResult {
      val failed = mutableListOf<Video>()
      val (ok, bad) = deleteVideos(context, videos)
      if (bad > 0) {
        videos.forEach { v ->
          val exists = kotlin.runCatching { java.io.File(v.path).exists() }.getOrDefault(false)
          if (exists) failed += v
        }
      }
      return DeleteResult(ok, bad, failed)
    }

    suspend fun deleteVideosInBuckets(
      context: Context,
      bucketIds: Set<String>,
      videoLoader: suspend (Context, String) -> List<Video>,
    ): DeleteResult {
      val toDelete = mutableListOf<Video>()
      for (id in bucketIds) {
        runCatching { toDelete += videoLoader(context, id) }
      }
      return deleteVideosDetailed(context, toDelete)
    }

    /** Rename via MediaStore update; fallback to java.io.File.renameTo */
    suspend fun renameVideo(
      context: Context,
      video: Video,
      newDisplayName: String,
    ): Result<Unit> {
      return try {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName) }
        val rows = context.contentResolver.update(video.uri, values, null, null)
        if (rows > 0) {
          val oldFile = java.io.File(video.path)
          val newPath = java.io.File(oldFile.parentFile, newDisplayName).absolutePath
          RecentlyPlayedOps.onVideoRenamed(video.path, newPath)
          Result.success(Unit)
        } else {
          val oldFile = java.io.File(video.path)
          val target = java.io.File(oldFile.parentFile, newDisplayName)
          if (oldFile.exists() && oldFile.renameTo(target)) {
            RecentlyPlayedOps.onVideoRenamed(oldFile.absolutePath, target.absolutePath)
            Result.success(Unit)
          } else Result.failure(IllegalStateException("Rename failed"))
        }
      } catch (t: Throwable) {
        Result.failure(t)
      }
    }
  }

  // --------------------------------------------------------------------------
  // Scoped permission handler (delete/rename flows)
  // --------------------------------------------------------------------------
  /**
   * Scoped-storage aware handler for delete/rename flows.
   * Ensures operations remain Play-compliant by using MediaStore APIs on Q+.
   */
  class StoragePermissionHandler(
    private val context: Context,
    private val launchers: StorageOperationLaunchers,
  ) {
    /**
     * Attempts to delete the supplied [videos]. On Android Q+ this will surface a
     * system confirmation sheet (RecoverableAction) if required. Below Q it will
     * perform the operation directly.
     *
     * @return true if handled directly in-app (no UI surfaced), false if a sheet was launched.
     */
    suspend fun handleDelete(
      videos: List<Video>,
      onDirectDelete: suspend () -> Unit,
    ): Boolean = withContext(Dispatchers.Main) {
      when {
        // Android Q+ - use MediaStore delete request
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
          val deleteRequest = StorageOps.createDeleteRequest(
            context.contentResolver,
            videos.map { it.uri },
          )
          if (deleteRequest != null) {
            launchers.deleteLauncher(IntentSenderRequest.Builder(deleteRequest).build())
            false
          } else {
            onDirectDelete(); true
          }
        }

        else -> {
          onDirectDelete(); true
        }
      }
    }

    /**
     * Attempts to rename a single [video] to [newName]. On Android Q+ this will surface a
     * write request sheet when necessary, or handle a RecoverableSecurityException thrown
     * by a direct attempt.
     *
     * @return true if handled directly in-app (no UI surfaced), false if a sheet was launched.
     */
    suspend fun handleRename(
      video: Video,
      newName: String,
      onDirectRename: suspend () -> Result<Unit>,
      onRecoverableSecurity: (RecoverableSecurityException) -> Unit = {},
    ): Boolean = withContext(Dispatchers.Main) {
      // Always try direct rename first. If a RecoverableSecurityException occurs, we
      // will surface the system sheet inside handleDirectRename and return false.
      handleDirectRename(onDirectRename, onRecoverableSecurity)
    }

    /**
     * Tries the provided [onDirectRename] and maps RecoverableSecurityException into a
     * user-visible system sheet. Returns true if no sheet needed.
     */
    private suspend fun handleDirectRename(
      onDirectRename: suspend () -> Result<Unit>,
      onRecoverableSecurity: (RecoverableSecurityException) -> Unit,
    ): Boolean {
      val result = onDirectRename()
      return if (result.isFailure) {
        val exc = result.exceptionOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && exc is RecoverableSecurityException) {
          onRecoverableSecurity(exc)
          val sender = exc.userAction.actionIntent.intentSender
          launchers.writeLauncher(IntentSenderRequest.Builder(sender).build())
          false
        } else {
          true
        }
      } else {
        true
      }
    }
  }

  /**
   * Remembers a [StoragePermissionHandler] bound to the provided [launchers].
   */
  @Composable
  fun rememberStoragePermissionHandler(launchers: StorageOperationLaunchers): StoragePermissionHandler {
    val context = LocalContext.current
    return remember(context, launchers) { StoragePermissionHandler(context, launchers) }
  }
}
