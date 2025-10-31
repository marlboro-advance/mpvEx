package app.marlboroadvance.mpvex.ui.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import app.marlboroadvance.mpvex.R
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

/**
 * Background playback service that keeps mpv alive when the activity is in background.
 * Uses MediaSession for notification - MediaSession handles all the button callbacks.
 */
@Suppress("TooManyFunctions")
class MediaPlaybackService :
  MediaBrowserServiceCompat(),
  MPVLib.EventObserver {
  companion object {
    private const val TAG = "MediaPlaybackService"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL_ID = "mpvex_playback_channel"

    // Shared thumbnail for MediaSession
    var thumbnail: Bitmap? = null

    fun createNotificationChannel(context: Context) {
      val channel =
        NotificationChannel(
          NOTIFICATION_CHANNEL_ID,
          context.getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = context.getString(R.string.notification_channel_description)
          setShowBadge(false)
          enableLights(false)
          enableVibration(false)
        }

      val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private val binder = MediaPlaybackBinder()

  private var mediaTitle = ""
  private var mediaArtist = ""
  private var paused: Boolean = false

  private lateinit var mediaSession: MediaSessionCompat

  init {
    MPVLib.addObserver(this)
  }

  inner class MediaPlaybackBinder : Binder() {
    fun getService(): MediaPlaybackService = this@MediaPlaybackService
  }

  @SuppressLint("ForegroundServiceType")
  override fun onCreate() {
    super.onCreate()

    Log.d(TAG, "Service created")

    setupMediaSession()

    MPVLib.addObserver(this)
    mapOf(
      "pause" to MPVLib.MpvFormat.MPV_FORMAT_FLAG,
      "media-title" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
      "metadata/artist" to MPVLib.MpvFormat.MPV_FORMAT_STRING,
    ).onEach {
      MPVLib.observeProperty(it.key, it.value)
    }
  }

  override fun onBind(intent: Intent): IBinder = binder

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    Log.d(TAG, "Service starting")

    // Read current state from MPV
    mediaTitle = MPVLib.getPropertyString("media-title") ?: ""
    mediaArtist = MPVLib.getPropertyString("metadata/artist") ?: ""
    paused = MPVLib.getPropertyBoolean("pause") == true

    // Update MediaSession which will create the notification
    updateMediaSessionMetadata()
    updatePlaybackState()

    // Start as foreground - MediaSession notification will be used
    val notification = createMediaStyleNotification()
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      } else {
        0
      }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

    return START_NOT_STICKY
  }

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: android.os.Bundle?,
  ) = BrowserRoot("root_id", null)

  override fun onLoadChildren(
    parentId: String,
    result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
  ) {
    result.sendResult(mutableListOf())
  }

  fun setMediaInfo(
    title: String,
    artist: String,
    thumbnail: Bitmap? = null,
  ) {
    MediaPlaybackService.thumbnail = thumbnail
    mediaTitle = title
    mediaArtist = artist

    updateMediaSessionMetadata()
    updatePlaybackState()
  }

  private fun createMediaStyleNotification(): Notification {
    val openAppIntent =
      Intent(this, PlayerActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pendingOpenAppIntent =
      PendingIntent.getActivity(
        this,
        0,
        openAppIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return NotificationCompat
      .Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(mediaTitle)
      .setContentText(mediaArtist.ifBlank { getString(R.string.notification_playing) })
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setLargeIcon(thumbnail)
      .setContentIntent(pendingOpenAppIntent)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(!paused)
      .setStyle(
        androidx.media.app.NotificationCompat
          .MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2),
      ).setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun setupMediaSession() {
    mediaSession =
      MediaSessionCompat(this, TAG).apply {
        setCallback(
          object : MediaSessionCompat.Callback() {
            override fun onPlay() {
              MPVLib.setPropertyBoolean("pause", false)
            }

            override fun onPause() {
              MPVLib.setPropertyBoolean("pause", true)
            }

            override fun onStop() {
              stopSelf()
            }

            override fun onSkipToNext() {
              // Seek forward
              val seekAmount = 10
              MPVLib.command("seek", seekAmount.toString(), "relative")
            }

            override fun onSkipToPrevious() {
              // Seek backward
              val seekAmount = -10
              MPVLib.command("seek", seekAmount.toString(), "relative")
            }

            override fun onSeekTo(pos: Long) {
              MPVLib.setPropertyDouble("time-pos", pos / 1000.0)
            }
          },
        )

        setFlags(
          MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        setSessionToken(sessionToken)
        isActive = true
      }
  }

  private fun getAvailableActions(): Long =
    PlaybackStateCompat.ACTION_PLAY or
      PlaybackStateCompat.ACTION_PAUSE or
      PlaybackStateCompat.ACTION_PLAY_PAUSE or
      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
      PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
      PlaybackStateCompat.ACTION_STOP or
      PlaybackStateCompat.ACTION_SEEK_TO

  private fun updateMediaSessionMetadata() {
    try {
      val duration = MPVLib.getPropertyDouble("duration")?.times(1000)?.toLong() ?: 0L

      val metadataBuilder =
        MediaMetadataCompat
          .Builder()
          .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaTitle)
          .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaArtist)
          .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mediaTitle)
          .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

      thumbnail?.let {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
      }

      mediaSession.setMetadata(metadataBuilder.build())
    } catch (e: Exception) {
      Log.e(TAG, "Error updating metadata", e)
    }
  }

  private fun updatePlaybackState() {
    try {
      val position = MPVLib.getPropertyDouble("time-pos")?.times(1000)?.toLong() ?: 0L

      val stateBuilder =
        PlaybackStateCompat
          .Builder()
          .setActions(getAvailableActions())
          .setState(
            if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
            position,
            1.0f,
          )

      mediaSession.setPlaybackState(stateBuilder.build())
    } catch (e: Exception) {
      Log.e(TAG, "Error updating playback state", e)
    }
  }

  // ==================== MPV Event Observers ====================

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(property: String) {
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    when (property) {
      "pause" -> {
        paused = value
        updatePlaybackState()
      }
    }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    when (property) {
      "media-title" -> {
        mediaTitle = value
        updateMediaSessionMetadata()
      }
      "metadata/artist" -> {
        mediaArtist = value
        updateMediaSessionMetadata()
      }
    }
  }

  @Suppress("EmptyFunctionBlock")
  override fun eventProperty(
    property: String,
    value: Double,
  ) {
  }

  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
  }

  override fun event(eventId: Int) {
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) {
      stopSelf()
    }
  }

  override fun onDestroy() {
    try {
      Log.d(TAG, "Service destroyed")

      MPVLib.removeObserver(this)
      mediaSession.isActive = false
      mediaSession.release()

      super.onDestroy()
    } catch (e: Exception) {
      Log.e(TAG, "Error in onDestroy", e)
    }
  }
}
