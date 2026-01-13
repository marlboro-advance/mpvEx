package app.marlboroadvance.mpvex.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val PIP_INTENTS_FILTER = "pip_action"
private const val PIP_INTENT_ACTION = "pip_action_code"
private const val PIP_PLAY = 1
private const val PIP_PAUSE = 2
private const val PIP_REWIND = 3
private const val PIP_FORWARD = 4

class MPVPipHelper(
  private val activity: AppCompatActivity,
  private val mpvView: MPVView,
) : KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private var pipReceiver: BroadcastReceiver? = null

  fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
    if (isInPipMode) {
      registerPipReceiver()
    } else {
      unregisterPipReceiver()
    }
  }

  @Suppress("UnspecifiedRegisterReceiverFlag")
  private fun registerPipReceiver() {
    pipReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          val seekMode = if (playerPreferences.usePreciseSeeking.get()) "relative+exact" else "relative+keyframes"
          when (intent?.getIntExtra(PIP_INTENT_ACTION, 0)) {
            PIP_PLAY -> MPVLib.setPropertyBoolean("pause", false)
            PIP_PAUSE -> MPVLib.setPropertyBoolean("pause", true)
            PIP_REWIND -> MPVLib.command("seek", "-10", seekMode)
            PIP_FORWARD -> MPVLib.command("seek", "10", seekMode)
          }
          updatePictureInPictureParams()
        }
      }

    val filter = IntentFilter(PIP_INTENTS_FILTER)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      activity.registerReceiver(pipReceiver, filter)
    }
  }

  private fun unregisterPipReceiver() {
    pipReceiver?.let {
      runCatching { activity.unregisterReceiver(it) }
      pipReceiver = null
    }
  }

  fun updatePictureInPictureParams() {
    if (activity.isFinishing || activity.isDestroyed) return

    val params = buildPipParams()
    runCatching { activity.setPictureInPictureParams(params) }
  }

  private fun buildPipParams(): PictureInPictureParams =
    PictureInPictureParams
      .Builder()
      .apply {
        getVideoAspectRatio()?.let { aspectRatio ->
          setAspectRatio(aspectRatio)
          setSourceRectHint(calculateSourceRect(aspectRatio))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          setAutoEnterEnabled(playerPreferences.autoPiPOnNavigation.get())
        }

        setActions(createPipActions())
      }.build()

  private fun getVideoAspectRatio(): Rational? {
    val width = MPVLib.getPropertyInt("video-out-params/dw") ?: 0
    val height = MPVLib.getPropertyInt("video-out-params/dh") ?: 0

    if (width == 0 || height == 0) return null

    return Rational(width, height).takeIf { it.toFloat() in 0.5f..2.39f }
  }

  private fun calculateSourceRect(aspectRatio: Rational): Rect {
    val viewWidth = mpvView.width.toFloat()
    val viewHeight = mpvView.height.toFloat()
    val videoAspect = aspectRatio.toFloat()
    val viewAspect = viewWidth / viewHeight

    return if (viewAspect < videoAspect) {
      // Letterboxed (black bars top/bottom)
      val height = viewWidth / videoAspect
      val top = ((viewHeight - height) / 2).toInt()
      Rect(0, top, viewWidth.toInt(), (height + top).toInt())
    } else {
      // Pillarboxed (black bars left/right)
      val width = viewHeight * videoAspect
      val left = ((viewWidth - width) / 2).toInt()
      Rect(left, 0, (width + left).toInt(), viewHeight.toInt())
    }
  }

  private fun createPipActions(): List<RemoteAction> {
    val isPlaying = MPVLib.getPropertyBoolean("pause") == false

    return listOf(
      createRemoteAction("rewind", android.R.drawable.ic_media_rew, PIP_REWIND),
      if (isPlaying) {
        createRemoteAction("pause", R.drawable.baseline_pause_24, PIP_PAUSE)
      } else {
        createRemoteAction("play", R.drawable.baseline_play_arrow_24, PIP_PLAY)
      },
      createRemoteAction("forward", android.R.drawable.ic_media_ff, PIP_FORWARD),
    )
  }

  private fun createRemoteAction(
    title: String,
    @DrawableRes icon: Int,
    actionCode: Int,
  ): RemoteAction {
    val intent =
      Intent(PIP_INTENTS_FILTER).apply {
        putExtra(PIP_INTENT_ACTION, actionCode)
        setPackage(activity.packageName)
      }

    val pendingIntent =
      PendingIntent.getBroadcast(
        activity,
        actionCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE,
      )

    return RemoteAction(
      Icon.createWithResource(activity, icon),
      title,
      title,
      pendingIntent,
    )
  }

  fun enterPipMode() {
    runCatching {
      activity.enterPictureInPictureMode(buildPipParams())
    }.onFailure {
      Log.e("MPVPipHelper", "Failed to enter PiP mode", it)
    }
  }

  fun onStop() {
    unregisterPipReceiver()
  }
}
