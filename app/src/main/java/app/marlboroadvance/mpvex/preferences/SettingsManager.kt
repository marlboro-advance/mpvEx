package app.marlboroadvance.mpvex.preferences

import android.content.Context
import android.net.Uri
import android.util.Xml
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.entities.PlaylistItemEntity
import app.marlboroadvance.mpvex.database.entities.RecentlyPlayedEntity
import app.marlboroadvance.mpvex.database.entities.VideoMetadataEntity
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkProtocol
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages export and import of ALL user settings AND data to/from XML files.
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * COMPLETE EXPORT INCLUDES:
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │ 1. SHARED PREFERENCES (100+ settings from ALL preference screens)              │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 *
 * APPEARANCE PREFERENCES (~10+ settings):
 * - dark_mode, material_you, amoled_mode, unlimited_name_lines
 * - hide_player_buttons_background, show_hidden_files, show_unplayed_old_video_label
 * - unplayed_old_video_days, top_left_controls, top_right_controls
 * - bottom_right_controls, bottom_left_controls, portrait_bottom_controls
 *
 * PLAYER PREFERENCES (~30+ settings):
 * - player_orientation, invert_duration, hold_for_multiple_speed
 * - horizontal_seek_gesture, show_seekbar_when_seeking, show_double_tap_ovals
 * - show_seek_time_while_seeking, use_precise_seeking, gestures_brightness
 * - volume_brightness, pinch_to_zoom_gesture, video_aspect
 * - custom_aspect_ratios, current_aspect_ratio, default_speed
 * - default_speed_presets, display_volume_as_percentage, display_volume_on_right
 * - show_loading_circle, save_position, close_after_eof
 * - remember_brightness, default_brightness, allow_gestures_in_panels
 * - show_system_status_bar, reduce_motion, player_time_to_disappear
 * - default_video_zoom, include_subtitles_in_snapshot, playlist_mode, use_wavy_seekbar
 *
 * GESTURE, DECODER, SUBTITLES, AUDIO, ADVANCED, BROWSER, FOLDERS (~60+ more settings)
 *
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │ 2. DATABASE DATA (ALL user-created and user-configured data)                   │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 *
 * ★ NETWORK CONNECTIONS (NetworkConnection):
 *   - SMB/FTP/WebDAV server connections
 *   - Host, port, credentials, paths, auto-connect settings
 *   - Connection history
 *
 * ★ PLAYLISTS (PlaylistEntity + PlaylistItemEntity):
 *   - ALL user-created playlists
 *   - Playlist items with file paths, positions, play counts
 *   - Last played timestamps and positions
 *
 * ★ PLAYBACK STATES (PlaybackStateEntity):
 *   - Video playback positions for resume functionality
 *   - Per-video playback speed, zoom, subtitle/audio track selections
 *   - Subtitle and audio delays
 *
 * ★ RECENTLY PLAYED (RecentlyPlayedEntity):
 *   - Recently played video history
 *   - Video metadata (duration, resolution, file size)
 *   - Launch sources and timestamps
 *
 * ★ VIDEO METADATA CACHE (VideoMetadataEntity):
 *   - Cached video information (duration, resolution, framerate)
 *   - Prevents re-scanning on every app launch
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * TOTAL: 100+ settings + ALL database records (potentially thousands of items)!
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * The export uses:
 * - SharedPreferences.getAll() to ensure NO settings are missed
 * - Database DAOs to export ALL tables completely
 */
class SettingsManager(
  private val context: Context,
  private val preferenceStore: PreferenceStore,
  private val database: MpvExDatabase,
) {
  companion object {
    private const val TAG_ROOT = "mpvExSettings"
    private const val TAG_PREFERENCES = "preferences"
    private const val TAG_PREFERENCE = "preference"
    private const val TAG_DATABASE = "database"
    private const val TAG_NETWORK_CONNECTIONS = "networkConnections"
    private const val TAG_NETWORK_CONNECTION = "networkConnection"
    private const val TAG_PLAYLISTS = "playlists"
    private const val TAG_PLAYLIST = "playlist"
    private const val TAG_PLAYLIST_ITEM = "playlistItem"
    private const val TAG_PLAYBACK_STATES = "playbackStates"
    private const val TAG_PLAYBACK_STATE = "playbackState"
    private const val TAG_RECENTLY_PLAYED = "recentlyPlayed"
    private const val TAG_RECENTLY_PLAYED_ITEM = "recentlyPlayedItem"
    private const val TAG_VIDEO_METADATA = "videoMetadata"
    private const val TAG_VIDEO_METADATA_ITEM = "videoMetadataItem"
    
    private const val ATTR_KEY = "key"
    private const val ATTR_TYPE = "type"
    private const val ATTR_VALUE = "value"
    private const val ATTR_EXPORT_DATE = "exportDate"
    private const val ATTR_VERSION = "version"

    private const val TYPE_STRING = "string"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_STRING_SET = "stringSet"
    private const val STRING_SET_SEPARATOR = "|||"

    private const val CURRENT_VERSION = "2.0"
  }

  /**
   * Export all settings to an XML file
   */
  suspend fun exportSettings(outputUri: Uri): Result<ExportStats> =
    withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
          val stats = writeSettingsToXml(outputStream)
          Result.success(stats)
        } ?: Result.failure(Exception("Failed to open output stream"))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Import settings from an XML file
   */
  suspend fun importSettings(inputUri: Uri): Result<ImportStats> =
    withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
          val stats = readSettingsFromXml(inputStream)
          Result.success(stats)
        } ?: Result.failure(Exception("Failed to open input stream"))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Write all preferences and database data to XML format
   */
  private suspend fun writeSettingsToXml(outputStream: OutputStream): ExportStats {
    val serializer: XmlSerializer = Xml.newSerializer()
    serializer.setOutput(outputStream, "UTF-8")
    serializer.startDocument("UTF-8", true)
    serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

    // Start root element
    serializer.startTag(null, TAG_ROOT)
    serializer.attribute(null, ATTR_VERSION, CURRENT_VERSION)
    serializer.attribute(
      null,
      ATTR_EXPORT_DATE,
      SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    )

    var exportedCount = 0
    val exportedKeys = mutableListOf<String>()

    // Export SharedPreferences
    serializer.startTag(null, TAG_PREFERENCES)
    val allPreferences = preferenceStore.getAll()
    for ((key, value) in allPreferences) {
      if (value != null) {
        writePreference(serializer, key, value)
        exportedCount++
        exportedKeys.add("pref:$key")
      }
    }
    serializer.endTag(null, TAG_PREFERENCES)

    // Export Database Data
    serializer.startTag(null, TAG_DATABASE)

    // Export Network Connections
    val networkConnections = database.networkConnectionDao().getAllConnectionsList()
    serializer.startTag(null, TAG_NETWORK_CONNECTIONS)
    networkConnections.forEach { connection ->
      writeNetworkConnection(serializer, connection)
      exportedCount++
      exportedKeys.add("network:${connection.name}")
    }
    serializer.endTag(null, TAG_NETWORK_CONNECTIONS)

    // Export Playlists
    val playlists = database.playlistDao().getAllPlaylists()
    serializer.startTag(null, TAG_PLAYLISTS)
    for (playlist in playlists) {
      val items = database.playlistDao().getPlaylistItems(playlist.id)
      writePlaylist(serializer, playlist, items)
      exportedCount++
      exportedKeys.add("playlist:${playlist.name}")
    }
    serializer.endTag(null, TAG_PLAYLISTS)

    // Export Playback States
    val playbackStates = database.videoDataDao().getAllPlaybackStates()
    serializer.startTag(null, TAG_PLAYBACK_STATES)
    playbackStates.forEach { state ->
      writePlaybackState(serializer, state)
      exportedCount++
      exportedKeys.add("playback:${state.mediaTitle}")
    }
    serializer.endTag(null, TAG_PLAYBACK_STATES)

    // Export Recently Played
    val recentlyPlayed = database.recentlyPlayedDao().getAllRecentlyPlayed()
    serializer.startTag(null, TAG_RECENTLY_PLAYED)
    recentlyPlayed.forEach { item ->
      writeRecentlyPlayed(serializer, item)
      exportedCount++
      exportedKeys.add("recent:${item.fileName}")
    }
    serializer.endTag(null, TAG_RECENTLY_PLAYED)

    // Export Video Metadata Cache
    val videoMetadata = database.videoMetadataDao().getAllMetadata()
    serializer.startTag(null, TAG_VIDEO_METADATA)
    videoMetadata.forEach { metadata ->
      writeVideoMetadata(serializer, metadata)
      exportedCount++
      exportedKeys.add("metadata:${metadata.path}")
    }
    serializer.endTag(null, TAG_VIDEO_METADATA)

    serializer.endTag(null, TAG_DATABASE)

    // End root element
    serializer.endTag(null, TAG_ROOT)
    serializer.endDocument()
    serializer.flush()

    return ExportStats(
      totalExported = exportedCount,
      exportedKeys = exportedKeys,
    )
  }

  /**
   * Write a single preference to XML
   */
  private fun writePreference(
    serializer: XmlSerializer,
    key: String,
    value: Any,
  ) {
    serializer.startTag(null, TAG_PREFERENCE)
    serializer.attribute(null, ATTR_KEY, key)

    when (value) {
      is String -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_STRING)
        serializer.attribute(null, ATTR_VALUE, value)
      }
      is Int -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_INT)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Long -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_LONG)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Float -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_FLOAT)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Boolean -> {
        serializer.attribute(null, ATTR_TYPE, TYPE_BOOLEAN)
        serializer.attribute(null, ATTR_VALUE, value.toString())
      }
      is Set<*> -> {
        @Suppress("UNCHECKED_CAST")
        val stringSet = value as? Set<String>
        if (stringSet != null) {
          serializer.attribute(null, ATTR_TYPE, TYPE_STRING_SET)
          // Encode string set by joining with separator
          serializer.attribute(null, ATTR_VALUE, stringSet.joinToString(STRING_SET_SEPARATOR))
        }
      }
    }

    serializer.endTag(null, TAG_PREFERENCE)
  }

  /**
   * Write a network connection to XML
   */
  private fun writeNetworkConnection(
    serializer: XmlSerializer,
    connection: NetworkConnection,
  ) {
    serializer.startTag(null, TAG_NETWORK_CONNECTION)
    serializer.attribute(null, "id", connection.id.toString())
    serializer.attribute(null, "name", connection.name)
    serializer.attribute(null, "protocol", connection.protocol.name)
    serializer.attribute(null, "host", connection.host)
    serializer.attribute(null, "port", connection.port.toString())
    serializer.attribute(null, "username", connection.username)
    serializer.attribute(null, "password", connection.password)
    serializer.attribute(null, "path", connection.path)
    serializer.attribute(null, "isAnonymous", connection.isAnonymous.toString())
    serializer.attribute(null, "lastConnected", connection.lastConnected.toString())
    serializer.attribute(null, "autoConnect", connection.autoConnect.toString())
    serializer.endTag(null, TAG_NETWORK_CONNECTION)
  }

  /**
   * Write a playlist with its items to XML
   */
  private fun writePlaylist(
    serializer: XmlSerializer,
    playlist: PlaylistEntity,
    items: List<PlaylistItemEntity>,
  ) {
    serializer.startTag(null, TAG_PLAYLIST)
    serializer.attribute(null, "id", playlist.id.toString())
    serializer.attribute(null, "name", playlist.name)
    serializer.attribute(null, "createdAt", playlist.createdAt.toString())
    serializer.attribute(null, "updatedAt", playlist.updatedAt.toString())

    // Write playlist items
    for (item in items) {
      serializer.startTag(null, TAG_PLAYLIST_ITEM)
      serializer.attribute(null, "id", item.id.toString())
      serializer.attribute(null, "playlistId", item.playlistId.toString())
      serializer.attribute(null, "filePath", item.filePath)
      serializer.attribute(null, "fileName", item.fileName)
      serializer.attribute(null, "position", item.position.toString())
      serializer.attribute(null, "addedAt", item.addedAt.toString())
      serializer.attribute(null, "lastPlayedAt", item.lastPlayedAt.toString())
      serializer.attribute(null, "playCount", item.playCount.toString())
      serializer.attribute(null, "lastPosition", item.lastPosition.toString())
      serializer.endTag(null, TAG_PLAYLIST_ITEM)
    }

    serializer.endTag(null, TAG_PLAYLIST)
  }

  /**
   * Write a playback state to XML
   */
  private fun writePlaybackState(
    serializer: XmlSerializer,
    state: PlaybackStateEntity,
  ) {
    serializer.startTag(null, TAG_PLAYBACK_STATE)
    serializer.attribute(null, "mediaTitle", state.mediaTitle)
    serializer.attribute(null, "lastPosition", state.lastPosition.toString())
    serializer.attribute(null, "playbackSpeed", state.playbackSpeed.toString())
    serializer.attribute(null, "videoZoom", state.videoZoom.toString())
    serializer.attribute(null, "sid", state.sid.toString())
    serializer.attribute(null, "subDelay", state.subDelay.toString())
    serializer.attribute(null, "subSpeed", state.subSpeed.toString())
    serializer.attribute(null, "aid", state.aid.toString())
    serializer.attribute(null, "audioDelay", state.audioDelay.toString())
    serializer.attribute(null, "timeRemaining", state.timeRemaining.toString())
    serializer.endTag(null, TAG_PLAYBACK_STATE)
  }

  /**
   * Write a recently played item to XML
   */
  private fun writeRecentlyPlayed(
    serializer: XmlSerializer,
    item: RecentlyPlayedEntity,
  ) {
    serializer.startTag(null, TAG_RECENTLY_PLAYED_ITEM)
    serializer.attribute(null, "id", item.id.toString())
    serializer.attribute(null, "filePath", item.filePath)
    serializer.attribute(null, "fileName", item.fileName)
    serializer.attribute(null, "videoTitle", item.videoTitle ?: "")
    serializer.attribute(null, "duration", item.duration.toString())
    serializer.attribute(null, "fileSize", item.fileSize.toString())
    serializer.attribute(null, "width", item.width.toString())
    serializer.attribute(null, "height", item.height.toString())
    serializer.attribute(null, "timestamp", item.timestamp.toString())
    serializer.attribute(null, "launchSource", item.launchSource ?: "")
    serializer.attribute(null, "playlistId", item.playlistId?.toString() ?: "")
    serializer.endTag(null, TAG_RECENTLY_PLAYED_ITEM)
  }

  /**
   * Write video metadata to XML
   */
  private fun writeVideoMetadata(
    serializer: XmlSerializer,
    metadata: VideoMetadataEntity,
  ) {
    serializer.startTag(null, TAG_VIDEO_METADATA_ITEM)
    serializer.attribute(null, "path", metadata.path)
    serializer.attribute(null, "size", metadata.size.toString())
    serializer.attribute(null, "dateModified", metadata.dateModified.toString())
    serializer.attribute(null, "duration", metadata.duration.toString())
    serializer.attribute(null, "width", metadata.width.toString())
    serializer.attribute(null, "height", metadata.height.toString())
    serializer.attribute(null, "fps", metadata.fps.toString())
    serializer.attribute(null, "lastScanned", metadata.lastScanned.toString())
    serializer.endTag(null, TAG_VIDEO_METADATA_ITEM)
  }

  /**
   * Read settings from XML and apply them
   */
  private suspend fun readSettingsFromXml(inputStream: InputStream): ImportStats {
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(inputStream, "UTF-8")

    val stats = ImportStats()
    var eventType = parser.eventType

    val networkConnections = mutableListOf<NetworkConnection>()
    val playlists = mutableListOf<Pair<PlaylistEntity, List<PlaylistItemEntity>>>()
    val playbackStates = mutableListOf<PlaybackStateEntity>()
    val recentlyPlayed = mutableListOf<RecentlyPlayedEntity>()
    val videoMetadata = mutableListOf<VideoMetadataEntity>()

    var currentPlaylist: PlaylistEntity? = null
    var currentPlaylistItems = mutableListOf<PlaylistItemEntity>()

    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            TAG_ROOT -> {
              val version = parser.getAttributeValue(null, ATTR_VERSION)
              stats.version = version ?: "unknown"
            }
            TAG_PREFERENCE -> {
              try {
                readPreference(parser)
                stats.imported++
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import preference: ${e.message}")
              }
            }
            TAG_NETWORK_CONNECTION -> {
              try {
                networkConnections.add(readNetworkConnection(parser))
                stats.imported++
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import network connection: ${e.message}")
              }
            }
            TAG_PLAYLIST -> {
              try {
                currentPlaylist = readPlaylist(parser)
                currentPlaylistItems.clear()
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import playlist: ${e.message}")
              }
            }
            TAG_PLAYLIST_ITEM -> {
              try {
                currentPlaylistItems.add(readPlaylistItem(parser))
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import playlist item: ${e.message}")
              }
            }
            TAG_PLAYBACK_STATE -> {
              try {
                playbackStates.add(readPlaybackState(parser))
                stats.imported++
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import playback state: ${e.message}")
              }
            }
            TAG_RECENTLY_PLAYED_ITEM -> {
              try {
                recentlyPlayed.add(readRecentlyPlayed(parser))
                stats.imported++
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import recently played: ${e.message}")
              }
            }
            TAG_VIDEO_METADATA_ITEM -> {
              try {
                videoMetadata.add(readVideoMetadata(parser))
                stats.imported++
              } catch (e: Exception) {
                stats.failed++
                stats.errors.add("Failed to import video metadata: ${e.message}")
              }
            }
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == TAG_PLAYLIST && currentPlaylist != null) {
            playlists.add(Pair(currentPlaylist!!, currentPlaylistItems.toList()))
            stats.imported++
            currentPlaylist = null
          }
        }
      }
      eventType = parser.next()
    }

    // Insert all database data
    try {
      if (networkConnections.isNotEmpty()) {
        database.networkConnectionDao().insertAll(networkConnections)
      }
      if (playlists.isNotEmpty()) {
        playlists.forEach { (playlist, items) ->
          val playlistId = database.playlistDao().insertPlaylist(playlist).toInt()
          val updatedItems = items.map { it.copy(playlistId = playlistId) }
          database.playlistDao().insertPlaylistItems(updatedItems)
        }
      }
      if (playbackStates.isNotEmpty()) {
        database.videoDataDao().upsertAll(playbackStates)
      }
      if (recentlyPlayed.isNotEmpty()) {
        database.recentlyPlayedDao().insertAll(recentlyPlayed)
      }
      if (videoMetadata.isNotEmpty()) {
        database.videoMetadataDao().insertMetadataBatch(videoMetadata)
      }
    } catch (e: Exception) {
      stats.failed++
      stats.errors.add("Failed to insert database data: ${e.message}")
    }

    return stats
  }

  /**
   * Read and apply a single preference from XML
   */
  private fun readPreference(parser: XmlPullParser) {
    val key = parser.getAttributeValue(null, ATTR_KEY) ?: return
    val type = parser.getAttributeValue(null, ATTR_TYPE) ?: return
    val valueStr = parser.getAttributeValue(null, ATTR_VALUE) ?: return

    // Get the SharedPreferences editor from the Android context
    val sharedPrefs =
      androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    val editor = sharedPrefs.edit()

    when (type) {
      TYPE_STRING -> editor.putString(key, valueStr)
      TYPE_INT -> editor.putInt(key, valueStr.toIntOrNull() ?: 0)
      TYPE_LONG -> editor.putLong(key, valueStr.toLongOrNull() ?: 0L)
      TYPE_FLOAT -> editor.putFloat(key, valueStr.toFloatOrNull() ?: 0f)
      TYPE_BOOLEAN -> editor.putBoolean(key, valueStr.toBoolean())
      TYPE_STRING_SET -> {
        val stringSet =
          if (valueStr.isEmpty()) {
            emptySet()
          } else {
            valueStr.split(STRING_SET_SEPARATOR).toSet()
          }
        editor.putStringSet(key, stringSet)
      }
    }

    editor.apply()
  }

  /**
   * Read a network connection from XML
   */
  private fun readNetworkConnection(parser: XmlPullParser): NetworkConnection {
    return NetworkConnection(
      id = 0, // Will be auto-generated
      name = parser.getAttributeValue(null, "name") ?: "",
      protocol =
        NetworkProtocol.valueOf(
          parser.getAttributeValue(null, "protocol") ?: "SMB",
        ),
      host = parser.getAttributeValue(null, "host") ?: "",
      port = parser.getAttributeValue(null, "port")?.toIntOrNull() ?: 445,
      username = parser.getAttributeValue(null, "username") ?: "",
      password = parser.getAttributeValue(null, "password") ?: "",
      path = parser.getAttributeValue(null, "path") ?: "/",
      isAnonymous = parser.getAttributeValue(null, "isAnonymous")?.toBoolean() ?: false,
      lastConnected = parser.getAttributeValue(null, "lastConnected")?.toLongOrNull() ?: 0L,
      autoConnect = parser.getAttributeValue(null, "autoConnect")?.toBoolean() ?: false,
    )
  }

  /**
   * Read a playlist from XML
   */
  private fun readPlaylist(parser: XmlPullParser): PlaylistEntity {
    return PlaylistEntity(
      id = 0, // Will be auto-generated
      name = parser.getAttributeValue(null, "name") ?: "",
      createdAt = parser.getAttributeValue(null, "createdAt")?.toLongOrNull() ?: System.currentTimeMillis(),
      updatedAt = parser.getAttributeValue(null, "updatedAt")?.toLongOrNull() ?: System.currentTimeMillis(),
    )
  }

  /**
   * Read a playlist item from XML
   */
  private fun readPlaylistItem(parser: XmlPullParser): PlaylistItemEntity {
    return PlaylistItemEntity(
      id = 0, // Will be auto-generated
      playlistId = 0, // Will be set when inserting
      filePath = parser.getAttributeValue(null, "filePath") ?: "",
      fileName = parser.getAttributeValue(null, "fileName") ?: "",
      position = parser.getAttributeValue(null, "position")?.toIntOrNull() ?: 0,
      addedAt = parser.getAttributeValue(null, "addedAt")?.toLongOrNull() ?: System.currentTimeMillis(),
      lastPlayedAt = parser.getAttributeValue(null, "lastPlayedAt")?.toLongOrNull() ?: 0L,
      playCount = parser.getAttributeValue(null, "playCount")?.toIntOrNull() ?: 0,
      lastPosition = parser.getAttributeValue(null, "lastPosition")?.toLongOrNull() ?: 0L,
    )
  }

  /**
   * Read a playback state from XML
   */
  private fun readPlaybackState(parser: XmlPullParser): PlaybackStateEntity {
    return PlaybackStateEntity(
      mediaTitle = parser.getAttributeValue(null, "mediaTitle") ?: "",
      lastPosition = parser.getAttributeValue(null, "lastPosition")?.toIntOrNull() ?: 0,
      playbackSpeed = parser.getAttributeValue(null, "playbackSpeed")?.toDoubleOrNull() ?: 1.0,
      videoZoom = parser.getAttributeValue(null, "videoZoom")?.toFloatOrNull() ?: 0f,
      sid = parser.getAttributeValue(null, "sid")?.toIntOrNull() ?: 0,
      subDelay = parser.getAttributeValue(null, "subDelay")?.toIntOrNull() ?: 0,
      subSpeed = parser.getAttributeValue(null, "subSpeed")?.toDoubleOrNull() ?: 1.0,
      aid = parser.getAttributeValue(null, "aid")?.toIntOrNull() ?: 0,
      audioDelay = parser.getAttributeValue(null, "audioDelay")?.toIntOrNull() ?: 0,
      timeRemaining = parser.getAttributeValue(null, "timeRemaining")?.toIntOrNull() ?: 0,
    )
  }

  /**
   * Read a recently played item from XML
   */
  private fun readRecentlyPlayed(parser: XmlPullParser): RecentlyPlayedEntity {
    val playlistIdStr = parser.getAttributeValue(null, "playlistId")
    return RecentlyPlayedEntity(
      id = 0, // Will be auto-generated
      filePath = parser.getAttributeValue(null, "filePath") ?: "",
      fileName = parser.getAttributeValue(null, "fileName") ?: "",
      videoTitle = parser.getAttributeValue(null, "videoTitle")?.takeIf { it.isNotEmpty() },
      duration = parser.getAttributeValue(null, "duration")?.toLongOrNull() ?: 0L,
      fileSize = parser.getAttributeValue(null, "fileSize")?.toLongOrNull() ?: 0L,
      width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
      height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0,
      timestamp = parser.getAttributeValue(null, "timestamp")?.toLongOrNull() ?: System.currentTimeMillis(),
      launchSource = parser.getAttributeValue(null, "launchSource")?.takeIf { it.isNotEmpty() },
      playlistId =
        if (playlistIdStr.isNullOrEmpty()) {
          null
        } else {
          playlistIdStr.toIntOrNull()
        },
    )
  }

  /**
   * Read video metadata from XML
   */
  private fun readVideoMetadata(parser: XmlPullParser): VideoMetadataEntity {
    return VideoMetadataEntity(
      path = parser.getAttributeValue(null, "path") ?: "",
      size = parser.getAttributeValue(null, "size")?.toLongOrNull() ?: 0L,
      dateModified = parser.getAttributeValue(null, "dateModified")?.toLongOrNull() ?: 0L,
      duration = parser.getAttributeValue(null, "duration")?.toLongOrNull() ?: 0L,
      width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
      height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0,
      fps = parser.getAttributeValue(null, "fps")?.toFloatOrNull() ?: 0f,
      lastScanned = parser.getAttributeValue(null, "lastScanned")?.toLongOrNull() ?: System.currentTimeMillis(),
    )
  }

  /**
   * Get default filename for export
   */
  fun getDefaultExportFilename(): String {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "mpvEx_settings_${dateFormat.format(Date())}.xml"
  }

  data class ImportStats(
    var imported: Int = 0,
    var failed: Int = 0,
    var version: String = "unknown",
    val errors: MutableList<String> = mutableListOf(),
  )

  data class ExportStats(
    val totalExported: Int,
    val exportedKeys: List<String>,
  )
}
