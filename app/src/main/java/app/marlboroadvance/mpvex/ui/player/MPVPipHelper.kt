package app.marlboroadvance.mpvex.ui.player

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import app.marlboroadvance.mpvex.R
import `is`.xyz.mpv.MPVLib

// Picture-in-Picture broadcast/action constants centralized here
const val PIP_INTENTS_FILTER = "pip_action"
const val PIP_INTENT_ACTION = "pip_action_code"
const val PIP_PLAY = 1
const val PIP_PAUSE = 2
const val PIP_REWIND = 3
const val PIP_FORWARD = 4

@Suppress("DEPRECATION")
class MPVPipHelper(
    private val activity: AppCompatActivity,
    private val mpvView: MPVView,
    private var autoPipEnabled: Boolean = true,
    private val onPipModeChanged: ((Boolean) -> Unit)? = null,
) {
    var isPipActive: Boolean = false
        private set

    private var pipBroadcastReceiver: BroadcastReceiver? = null

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    val isPipSupported: Boolean by lazy {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    val isPipEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appOps = activity.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps?.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                        Process.myUid(),
                        activity.packageName,
                    ) == AppOpsManager.MODE_ALLOWED
                } else {
                    @Suppress("DEPRECATION")
                    appOps?.checkOpNoThrow(
                        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                        Process.myUid(),
                        activity.packageName,
                    ) == AppOpsManager.MODE_ALLOWED
                }
            } else {
                false
            }
        }

    private fun isPlaying(): Boolean =
        try {
            MPVLib.getPropertyBoolean("pause") == false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking playback state", e)
            false
        }

    private fun updateVideoDimensions() {
        try {
            videoWidth = MPVLib.getPropertyInt("video-out-params/dw") ?: 0
            videoHeight = MPVLib.getPropertyInt("video-out-params/dh") ?: 0

            // Fallback to different properties if needed
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth = MPVLib.getPropertyInt("dwidth") ?: 0
                videoHeight = MPVLib.getPropertyInt("dheight") ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video dimensions", e)
            videoWidth = 0
            videoHeight = 0
        }
    }

    @SuppressLint("NewApi")
    fun onUserLeaveHint(isControlsLocked: Boolean = false) {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..<Build.VERSION_CODES.S &&
            isPipSupported &&
            autoPipEnabled &&
            isPlaying() &&
            !isControlsLocked
        ) {
            try {
                activity.enterPictureInPictureMode(updatePictureInPictureParams())
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error entering PIP mode", e)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.O)
    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        isPipActive = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            pipBroadcastReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (intent == null || intent.action != PIP_INTENTS_FILTER) return

                        when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                            PIP_PLAY -> {
                                try {
                                    MPVLib.setPropertyBoolean("pause", false)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error playing", e)
                                }
                            }

                            PIP_PAUSE -> {
                                try {
                                    MPVLib.setPropertyBoolean("pause", true)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error pausing", e)
                                }
                            }

                            PIP_REWIND -> {
                                try {
                                    MPVLib.command("seek", "-10", "relative+exact")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error rewinding", e)
                                }
                            }

                            PIP_FORWARD -> {
                                try {
                                    MPVLib.command("seek", "10", "relative+exact")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error forwarding", e)
                                }
                            }
                        }

                        if (isInPictureInPictureMode && !activity.isFinishing && !activity.isDestroyed) {
                            updatePictureInPictureParams()
                        }
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(
                    pipBroadcastReceiver,
                    IntentFilter(PIP_INTENTS_FILTER),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                activity.registerReceiver(pipBroadcastReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }

            onPipModeChanged?.invoke(true)
        } else {
            pipBroadcastReceiver?.let {
                try {
                    activity.unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver", e)
                }
                pipBroadcastReceiver = null
            }

            onPipModeChanged?.invoke(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePictureInPictureParams(enableAutoEnter: Boolean = isPlaying()): PictureInPictureParams {
        val viewWidth = mpvView.width
        val viewHeight = mpvView.height

        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.w(TAG, "Invalid view dimensions: $viewWidth x $viewHeight")
            return PictureInPictureParams.Builder().build()
        }

        updateVideoDimensions()

        val displayAspectRatio = Rational(viewWidth, viewHeight)

        return PictureInPictureParams
            .Builder()
            .apply {
                val aspectRatio = calculateVideoAspectRatio()
                if (aspectRatio != null) {
                    val sourceRectHint = calculateSourceRectHint(displayAspectRatio, aspectRatio)
                    setAspectRatio(aspectRatio)
                    setSourceRectHint(sourceRectHint)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(autoPipEnabled && enableAutoEnter)
                    setAutoEnterEnabled(autoPipEnabled && enableAutoEnter)
                }

                setActions(createPipActions())
            }.build()
            .also { params ->
                try {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.setPictureInPictureParams(params)
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed to set picture-in-picture params", e)
                }
            }
    }

    private fun calculateVideoAspectRatio(): Rational? {
        if (videoWidth == 0 || videoHeight == 0) return null

        return Rational(videoWidth, videoHeight).takeIf {
            it.toFloat() in 0.5f..2.39f
        }
    }

    private fun calculateSourceRectHint(
        displayAspectRatio: Rational,
        aspectRatio: Rational,
    ): Rect {
        val viewWidth = mpvView.width.toFloat()
        val viewHeight = mpvView.height.toFloat()

        return if (displayAspectRatio < aspectRatio) {
            val space = ((viewHeight - (viewWidth / aspectRatio.toFloat())) / 2).toInt()
            Rect(
                0,
                space,
                viewWidth.toInt(),
                (viewWidth / aspectRatio.toFloat()).toInt() + space,
            )
        } else {
            val space = ((viewWidth - (viewHeight * aspectRatio.toFloat())) / 2).toInt()
            Rect(
                space,
                0,
                (viewHeight * aspectRatio.toFloat()).toInt() + space,
                viewHeight.toInt(),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipActions(): List<RemoteAction> {
        val isCurrentlyPlaying = isPlaying()

        // Order is left-to-right in the PiP UI. Place play/pause in the middle.
        return listOf(
            createPipRemoteAction(
                context = activity,
                title = "rewind",
                icon = android.R.drawable.ic_media_rew,
                actionCode = PIP_REWIND,
            ),
            if (isCurrentlyPlaying) {
                createPipRemoteAction(
                    context = activity,
                    title = "pause",
                    icon = R.drawable.baseline_pause_24,
                    actionCode = PIP_PAUSE,
                )
            } else {
                createPipRemoteAction(
                    context = activity,
                    title = "play",
                    icon = R.drawable.baseline_play_arrow_24,
                    actionCode = PIP_PLAY,
                )
            },
            createPipRemoteAction(
                context = activity,
                title = "forward",
                icon = android.R.drawable.ic_media_ff,
                actionCode = PIP_FORWARD,
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPipMode() {
        if (!isPipSupported) {
            Toast
                .makeText(
                    activity,
                    "Picture-in-Picture is not supported on this device",
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }

        if (!isPipEnabled) {
            Toast
                .makeText(
                    activity,
                    "Please enable Picture-in-Picture in Settings",
                    Toast.LENGTH_SHORT,
                ).show()
            try {
                Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").apply {
                    data = "package:${activity.packageName}".toUri()
                    activity.startActivity(this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening PIP settings", e)
            }
            return
        }

        try {
            activity.enterPictureInPictureMode(updatePictureInPictureParams())
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error entering PIP mode", e)
        }
    }

    fun onStop() {
        // Cleanup if needed
    }

    companion object {
        private const val TAG = "MPVPipHelper"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun createPipRemoteAction(
    context: Context,
    title: String,
    @DrawableRes icon: Int,
    actionCode: Int,
): RemoteAction =
    RemoteAction(
        Icon.createWithResource(context, icon),
        title,
        title,
        PendingIntent.getBroadcast(
            context,
            actionCode,
            Intent(PIP_INTENTS_FILTER).apply {
                putExtra(PIP_INTENT_ACTION, actionCode)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_IMMUTABLE,
        ),
    )
