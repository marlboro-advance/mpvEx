package app.marlboroadvance.mpvex.ui.player.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class AudioFocusManager(
  private val context: Context,
  private val audioManager: AudioManager,
  private val onPausePlayback: () -> Unit,
  private val onDuckPlayback: (() -> Unit)? = null,
  private val onResumePlayback: (() -> Unit)? = null,
) {
  companion object {
    private const val TAG = "AudioFocusManager"
  }

  private var audioFocusRequested = false
  private var noisyReceiverRegistered = false
  private var focusRequest: AudioFocusRequest? = null
  private val handler = Handler(Looper.getMainLooper())

  var wasPausedByFocusLoss: Boolean = false
    private set

  private val noisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.d(TAG, "Headphones disconnected: pausing playback")
        handler.post {
          try {
            onPausePlayback()
          } catch (e: Exception) {
            Log.e(TAG, "Error handling noisy receiver", e)
          }
        }
      }
    }
  }

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    Log.d(TAG, "audioFocusChangeListener: $focusChange")
    handler.post {
      try {
        when (focusChange) {
          AudioManager.AUDIOFOCUS_LOSS -> {
            Log.d(TAG, "AUDIOFOCUS_LOSS: Permanent loss, pausing and abandoning focus")
            wasPausedByFocusLoss = true
            onPausePlayback()
            // Abandon focus on permanent loss
            handler.postDelayed({ abandonAudioFocus() }, 100)
          }

          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Temporary loss, pausing but keeping focus")
            wasPausedByFocusLoss = true
            onPausePlayback()
          }

          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Can duck volume")
            // For video, we prefer to pause rather than duck
            if (onDuckPlayback != null) {
              onDuckPlayback.invoke()
            } else {
              wasPausedByFocusLoss = true
              onPausePlayback()
            }
          }

          AudioManager.AUDIOFOCUS_GAIN -> {
            Log.d(TAG, "AUDIOFOCUS_GAIN: Focus regained")
            if (wasPausedByFocusLoss && onResumePlayback != null) {
              // Optional: Auto-resume if configured
              // onResumePlayback.invoke()
            }
            wasPausedByFocusLoss = false
          }

          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
            Log.d(TAG, "AUDIOFOCUS_GAIN_TRANSIENT: Temporary focus gain")
            wasPausedByFocusLoss = false
          }

          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
            Log.d(TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK: Gain with possible ducking")
            wasPausedByFocusLoss = false
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error handling audio focus change", e)
      }
    }
  }

  @Synchronized
  fun requestAudioFocus(): Boolean {
    try {
      // If already have focus, return true
      if (audioFocusRequested) {
        Log.d(TAG, "Audio focus already requested")
        return true
      }

      val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

      val focusType = AudioManager.AUDIOFOCUS_GAIN

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Android O and above
        focusRequest = AudioFocusRequest.Builder(focusType)
          .setAudioAttributes(audioAttrs)
          .setAcceptsDelayedFocusGain(true) // Allow delayed focus gain
          .setOnAudioFocusChangeListener(audioFocusChangeListener, handler)
          .setWillPauseWhenDucked(false) // We handle ducking ourselves
          .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        audioFocusRequested = when (result) {
          AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
            Log.d(TAG, "Audio focus granted")
            true
          }

          AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
            Log.d(TAG, "Audio focus delayed")
            true // We'll get it later
          }

          AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
            Log.w(TAG, "Audio focus request failed")
            false
          }

          else -> false
        }
      } else {
        // Legacy API
        @Suppress("DEPRECATION")
        val result = audioManager.requestAudioFocus(
          audioFocusChangeListener,
          AudioManager.STREAM_MUSIC,
          focusType,
        )
        audioFocusRequested = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        if (audioFocusRequested) {
          Log.d(TAG, "Audio focus granted (legacy)")
        } else {
          Log.w(TAG, "Audio focus request failed (legacy)")
        }
      }
      return audioFocusRequested
    } catch (e: Exception) {
      Log.e(TAG, "Error requesting audio focus", e)
      return false
    }
  }

  @Synchronized
  fun abandonAudioFocus() {
    try {
      if (!audioFocusRequested) {
        Log.d(TAG, "No audio focus to abandon")
        return
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest?.let {
          val result = audioManager.abandonAudioFocusRequest(it)
          if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus abandoned successfully")
          } else {
            Log.w(TAG, "Failed to abandon audio focus")
          }
        }
      } else {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        Log.d(TAG, "Audio focus abandoned (legacy)")
      }

      audioFocusRequested = false
      wasPausedByFocusLoss = false
    } catch (e: Exception) {
      Log.e(TAG, "Error abandoning audio focus", e)
    }
  }

  fun registerNoisyReceiver() {
    try {
      if (!noisyReceiverRegistered) {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
          context.registerReceiver(noisyReceiver, filter)
        }
        noisyReceiverRegistered = true
        Log.d(TAG, "Noisy receiver registered")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error registering noisy receiver", e)
    }
  }

  fun unregisterNoisyReceiver() {
    try {
      if (noisyReceiverRegistered) {
        context.unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
        Log.d(TAG, "Noisy receiver unregistered")
      }
    } catch (e: IllegalArgumentException) {
      // Receiver not registered - this is fine
      noisyReceiverRegistered = false
    } catch (e: Exception) {
      Log.e(TAG, "Error unregistering noisy receiver", e)
      noisyReceiverRegistered = false
    }
  }

  fun cleanup() {
    try {
      abandonAudioFocus()
      unregisterNoisyReceiver()
      handler.removeCallbacksAndMessages(null)
    } catch (e: Exception) {
      Log.e(TAG, "Error during cleanup", e)
    }
  }
}
