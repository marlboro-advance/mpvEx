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
import androidx.media.session.MediaButtonReceiver
import app.marlboroadvance.mpvex.R
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background playback service for mpv with MediaSession integration.
 */
class MediaPlaybackService :
  MediaBrowserServiceCompat(),
  MPVLib.EventObserver,
  KoinComponent {
  companion object {
    private const val TAG = "MediaPlaybackService"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL_ID = "mpvex_playback_channel"

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

      (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)
    }
  }

  private val binder = MediaPlaybackBinder()
  private lateinit var mediaSession: MediaSessionCompat
  private val playerPreferences: PlayerPreferences by inject()

  private var mediaTitle = ""
  private var mediaArtist = ""
  private var paused = false

  inner class MediaPlaybackBinder : Binder() {
    fun getService() = this@MediaPlaybackService
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Service created")

    setupMediaSession()
    MPVLib.addObserver(this)

    // Observe properties
    MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
    MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    MPVLib.observeProperty("metadata/artist", MPVLib.MpvFormat.MPV_FORMAT_STRING)
  }

  override fun onBind(intent: Intent): IBinder = binder

  @SuppressLint("ForegroundServiceType")
  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    Log.d(TAG, "Service starting")

    // Handle media button events
    intent?.let {
      MediaButtonReceiver.handleIntent(mediaSession, it)
    }

    // Read current state from MPV
    mediaTitle = MPVLib.getPropertyString("media-title") ?: ""
    mediaArtist = MPVLib.getPropertyString("metadata/artist") ?: ""
    paused = MPVLib.getPropertyBoolean("pause") == true

    updateMediaSession()

    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      } else {
        0
      }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

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
    updateMediaSession()
  }

  private fun setupMediaSession() {
    mediaSession =
      MediaSessionCompat(this, TAG).apply {
        setCallback(
          object : MediaSessionCompat.Callback() {
            override fun onPlay() {
              Log.d(TAG, "onPlay called")
              MPVLib.setPropertyBoolean("pause", false)
            }

            override fun onPause() {
              Log.d(TAG, "onPause called")
              MPVLib.setPropertyBoolean("pause", true)
            }

            override fun onStop() {
              Log.d(TAG, "onStop called")
              stopSelf()
            }

            override fun onSkipToNext() {
              Log.d(TAG, "onSkipToNext called")
              // Use precise seeking for videos shorter than 2 minutes (120 seconds) or if preference is enabled
              val duration = MPVLib.getPropertyInt("duration") ?: 0
              val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || duration < 120
              val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
              MPVLib.command("seek", "10", seekMode)
            }

            override fun onSkipToPrevious() {
              Log.d(TAG, "onSkipToPrevious called")
              // Use precise seeking for videos shorter than 2 minutes (120 seconds) or if preference is enabled
              val duration = MPVLib.getPropertyInt("duration") ?: 0
              val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || duration < 120
              val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
              MPVLib.command("seek", "-10", seekMode)
            }

            override fun onSeekTo(pos: Long) {
              Log.d(TAG, "onSeekTo called: $pos")
              MPVLib.setPropertyDouble("time-pos", pos / 1000.0)
            }
          },
        )

        // Set flags to handle media buttons and transport controls
        setFlags(
          MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )

        isActive = true
      }
    sessionToken = mediaSession.sessionToken
  }

  private fun updateMediaSession() {
    try {
      // Update metadata
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

      // Update playback state
      val position = MPVLib.getPropertyDouble("time-pos")?.times(1000)?.toLong() ?: 0L
      val state = if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING

      mediaSession.setPlaybackState(
        PlaybackStateCompat
          .Builder()
          .setActions(
            PlaybackStateCompat.ACTION_PLAY or
              PlaybackStateCompat.ACTION_PAUSE or
              PlaybackStateCompat.ACTION_PLAY_PAUSE or
              PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
              PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
              PlaybackStateCompat.ACTION_STOP or
              PlaybackStateCompat.ACTION_SEEK_TO,
          ).setState(state, position, 1.0f)
          .build(),
      )

      // Update notification
      updateNotification()
    } catch (e: Exception) {
      Log.e(TAG, "Error updating MediaSession", e)
    }
  }

  private fun updateNotification() {
    try {
      val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.notify(NOTIFICATION_ID, buildNotification())
    } catch (e: Exception) {
      Log.e(TAG, "Error updating notification", e)
    }
  }

  private fun buildNotification(): Notification {
    val openAppIntent =
      Intent(this, PlayerActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openAppIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    // Create notification actions
    val previousAction =
      NotificationCompat.Action(
        android.R.drawable.ic_media_previous,
        "Previous",
        MediaButtonReceiver.buildMediaButtonPendingIntent(
          this,
          PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
        ),
      )

    val playPauseAction =
      NotificationCompat.Action(
        if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
        if (paused) "Play" else "Pause",
        MediaButtonReceiver.buildMediaButtonPendingIntent(
          this,
          PlaybackStateCompat.ACTION_PLAY_PAUSE,
        ),
      )

    val nextAction =
      NotificationCompat.Action(
        android.R.drawable.ic_media_next,
        "Next",
        MediaButtonReceiver.buildMediaButtonPendingIntent(
          this,
          PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
        ),
      )

    return NotificationCompat
      .Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(mediaTitle)
      .setContentText(mediaArtist.ifBlank { getString(R.string.notification_playing) })
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setLargeIcon(thumbnail)
      .setContentIntent(pendingIntent)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(!paused)
      .addAction(previousAction)
      .addAction(playPauseAction)
      .addAction(nextAction)
      .setStyle(
        androidx.media.app.NotificationCompat
          .MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2),
      ).setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  // ==================== MPV Event Observers ====================

  override fun eventProperty(property: String) {}

  override fun eventProperty(
    property: String,
    value: Long,
  ) {}

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    if (property == "pause") {
      paused = value
      updateMediaSession()
    }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    when (property) {
      "media-title" -> mediaTitle = value
      "metadata/artist" -> mediaArtist = value
    }
    updateMediaSession()
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {}

  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {}

  override fun event(eventId: Int, data: MPVNode) {
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) stopSelf()
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
