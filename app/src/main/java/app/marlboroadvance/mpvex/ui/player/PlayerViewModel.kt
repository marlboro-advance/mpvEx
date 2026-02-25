package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSearchRepository
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSubtitle
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import app.marlboroadvance.mpvex.ui.preferences.CustomButton
import java.io.File
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


enum class RepeatMode {
  OFF,      // No repeat
  ONE,      // Repeat current file
  ALL       // Repeat all (playlist)
}

class PlayerViewModelProviderFactory(
  private val host: PlayerHost,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(
    modelClass: Class<T>,
    extras: CreationExtras,
  ): T {
    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PlayerViewModel(host) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private val host: PlayerHost,
) : ViewModel(),
  KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val json: Json by inject()
  private val playbackStateDao: app.marlboroadvance.mpvex.database.dao.PlaybackStateDao by inject()
  private val wyzieRepository: WyzieSearchRepository by inject()

  // Playlist items for the playlist sheet
  private val _playlistItems = kotlinx.coroutines.flow.MutableStateFlow<List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>>(emptyList())
  val playlistItems: kotlinx.coroutines.flow.StateFlow<List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>> = _playlistItems.asStateFlow()

  // Wyzie Search Results
  private val _wyzieSearchResults = MutableStateFlow<List<WyzieSubtitle>>(emptyList())
  val wyzieSearchResults: StateFlow<List<WyzieSubtitle>> = _wyzieSearchResults.asStateFlow()

  private val _isDownloadingSub = MutableStateFlow(false)
  val isDownloadingSub: StateFlow<Boolean> = _isDownloadingSub.asStateFlow()

  private val _isSearchingSub = MutableStateFlow(false)
  val isSearchingSub: StateFlow<Boolean> = _isSearchingSub.asStateFlow()

  private val _isOnlineSectionExpanded = MutableStateFlow(true)
  val isOnlineSectionExpanded: StateFlow<Boolean> = _isOnlineSectionExpanded.asStateFlow()

  // Media Search / Autocomplete
  private val _mediaSearchResults = MutableStateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult>>(emptyList())
  val mediaSearchResults: StateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult>> = _mediaSearchResults.asStateFlow()

  private val _isSearchingMedia = MutableStateFlow(false)
  val isSearchingMedia: StateFlow<Boolean> = _isSearchingMedia.asStateFlow()

  // TV Show Details
  private val _selectedTvShow = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails?>(null)
  val selectedTvShow: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails?> = _selectedTvShow.asStateFlow()

  private val _isFetchingTvDetails = MutableStateFlow(false)
  val isFetchingTvDetails: StateFlow<Boolean> = _isFetchingTvDetails.asStateFlow()

  // Season / Episode
  private val _selectedSeason = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason?>(null)
  val selectedSeason: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason?> = _selectedSeason.asStateFlow()

  private val _seasonEpisodes = MutableStateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>>(emptyList())
  val seasonEpisodes: StateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>> = _seasonEpisodes.asStateFlow()

  private val _isFetchingEpisodes = MutableStateFlow(false)
  val isFetchingEpisodes: StateFlow<Boolean> = _isFetchingEpisodes.asStateFlow()

  private val _selectedEpisode = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode?>(null)
  val selectedEpisode: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode?> = _selectedEpisode.asStateFlow()

  fun toggleOnlineSection() {
      _isOnlineSectionExpanded.value = !_isOnlineSectionExpanded.value
  }

  // Cache for video metadata to avoid re-extracting — LruCache handles bounds + thread-safety
  private val metadataCache = object : android.util.LruCache<String, Pair<String, String>>(100) {}

  private fun updateMetadataCache(key: String, value: Pair<String, String>) {
    metadataCache.put(key, value)
  }

  // MPV properties with efficient collection
  val paused by MPVLib.propBoolean["pause"].collectAsState(viewModelScope)
  val pos by MPVLib.propInt["time-pos"].collectAsState(viewModelScope)
  val duration by MPVLib.propInt["duration"].collectAsState(viewModelScope)

  // High-precision position and duration for smooth seekbar
  private val _precisePosition = MutableStateFlow(0f)
  val precisePosition = _precisePosition.asStateFlow()

  private val _preciseDuration = MutableStateFlow(0f)
  val preciseDuration = _preciseDuration.asStateFlow()

  // Audio state
  val currentVolume = MutableStateFlow(host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)

  init {
    // Poll precise position only when playing
    viewModelScope.launch {
      while (isActive) {
        val time = MPVLib.getPropertyDouble("time-pos")
        if (time != null) {
          _precisePosition.value = time.toFloat()
        }
        delay(16) // ~60fps updates
      }
    }

    // Update precise duration when the integer duration changes (avoid polling)
    viewModelScope.launch {
      MPVLib.propInt["duration"].collect { _ ->
        val dur = MPVLib.getPropertyDouble("duration")
        if (dur != null && dur > 0) {
            _preciseDuration.value = dur.toFloat()
        }
      }
    }
  }
  val maxVolume = host.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

  val subtitleTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isSubtitle }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val audioTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isAudio }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val chapters: StateFlow<List<dev.vivvvek.seeker.Segment>> =
    MPVLib.propNode["chapter-list"]
      .map { node ->
        node?.toObject<List<ChapterNode>>(json)?.map { it.toSegment() }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  // UI state
  private val _controlsShown = MutableStateFlow(false)
  val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()

  private val _seekBarShown = MutableStateFlow(false)
  val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()

  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val volumeSliderTimestamp = MutableStateFlow(0L)
  val brightnessSliderTimestamp = MutableStateFlow(0L)
  val currentBrightness =
    MutableStateFlow(
      runCatching {
        Settings.System
          .getFloat(host.hostContentResolver, Settings.System.SCREEN_BRIGHTNESS)
          .normalize(0f, 255f, 0f, 1f)
      }.getOrElse { 0f },
    )

  val sheetShown = MutableStateFlow(Sheets.None)
  val panelShown = MutableStateFlow(Panels.None)

  // Seek state
  private val _seekText = MutableStateFlow<String?>(null)
  val seekText: StateFlow<String?> = _seekText.asStateFlow()

  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount: StateFlow<Int> = _doubleTapSeekAmount.asStateFlow()

  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards: StateFlow<Boolean> = _isSeekingForwards.asStateFlow()

  // Frame navigation
  private val _currentFrame = MutableStateFlow(0)
  val currentFrame: StateFlow<Int> = _currentFrame.asStateFlow()

  private val _totalFrames = MutableStateFlow(0)
  val totalFrames: StateFlow<Int> = _totalFrames.asStateFlow()

  private val _isFrameNavigationExpanded = MutableStateFlow(false)
  val isFrameNavigationExpanded: StateFlow<Boolean> = _isFrameNavigationExpanded.asStateFlow()

  private val _isSnapshotLoading = MutableStateFlow(false)
  val isSnapshotLoading: StateFlow<Boolean> = _isSnapshotLoading.asStateFlow()

  // Video zoom
  private val _videoZoom = MutableStateFlow(0f)
  val videoZoom: StateFlow<Float> = _videoZoom.asStateFlow()

  // Timer
  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

  // Media title for subtitle association
  var currentMediaTitle: String = ""
  private var lastAutoSelectedMediaTitle: String? = null

  // External subtitle tracking
  private val _externalSubtitles = mutableListOf<String>()
  val externalSubtitles: List<String> get() = _externalSubtitles.toList()
  
  // Mapping from mpv internal path/URI to the original source URI (resolves deletion issues)
  private val mpvPathToUriMap = mutableMapOf<String, String>()

  // Repeat and Shuffle state
  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  // A-B Loop state
  private val _abLoopA = MutableStateFlow<Double?>(null)
  val abLoopA: StateFlow<Double?> = _abLoopA.asStateFlow()

  private val _abLoopB = MutableStateFlow<Double?>(null)
  val abLoopB: StateFlow<Double?> = _abLoopB.asStateFlow()

  private val _isABLoopExpanded = MutableStateFlow(false)
  val isABLoopExpanded: StateFlow<Boolean> = _isABLoopExpanded.asStateFlow()

  // Mirroring state
  private val _isMirrored = MutableStateFlow(false)
  val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

  // Vertical flip state
  private val _isVerticalFlipped = MutableStateFlow(false)
  val isVerticalFlipped: StateFlow<Boolean> = _isVerticalFlipped.asStateFlow()

  init {
    // Track selection is now handled by TrackSelector in PlayerActivity
    
    // Restore repeat mode and shuffle state from preferences
    _repeatMode.value = playerPreferences.repeatMode.get()
    _shuffleEnabled.value = playerPreferences.shuffleEnabled.get()

    // Observe volume boost cap changes to enforce limits dynamically (in PiP)
    viewModelScope.launch {
      audioPreferences.volumeBoostCap.changes().collect { cap ->
        val maxVol = 100 + cap
        MPVLib.setPropertyString("volume-max", maxVol.toString())
        
        // Clamp current volume if it exceeds the new limit
        val currentMpvVol = MPVLib.getPropertyInt("volume") ?: 100
        if (currentMpvVol > maxVol) {
          MPVLib.setPropertyInt("volume", maxVol)
        }
      }
    }

    // Monitor duration and AB loop changes to automatically enable precise seeking
    viewModelScope.launch {
      combine(
        MPVLib.propInt["duration"],
        abLoopA,
        abLoopB
      ) { duration, loopA, loopB ->
        Triple(duration, loopA, loopB)
      }.collect { (duration, loopA, loopB) ->
        val videoDuration = duration ?: 0
        // Use precise seeking for videos shorter than 2 minutes, or if AB loop is active, or if preference is enabled
        val isLoopActive = loopA != null || loopB != null
        val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || videoDuration < 120 || isLoopActive
        
        // Update hr-seek settings dynamically
        MPVLib.setPropertyString("hr-seek", if (shouldUsePreciseSeeking) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", if (shouldUsePreciseSeeking) "no" else "yes")
      }
    }
    
    
    // Refresh custom buttons when Lua scripts are enabled/disabled or configuration changes
    viewModelScope.launch {
      combine(
        advancedPreferences.enableLuaScripts.changes().drop(1),
        playerPreferences.customButtons.changes().drop(1)
      ) { _, _ -> }.collect {
        setupCustomButtons()
      }
    }

    setupCustomButtons()
  }

  // ==================== Custom Buttons ====================

  data class CustomButtonState(
    val id: String,
    val label: String,
    val isLeft: Boolean,
  )

  private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
  val customButtons: StateFlow<List<CustomButtonState>> = _customButtons.asStateFlow()

  private fun setupCustomButtons() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val buttons = mutableListOf<CustomButtonState>()
        if (!advancedPreferences.enableLuaScripts.get()) {
            _customButtons.value = buttons
            return@launch
        }

        val scriptContent = buildString {
          val jsonString = playerPreferences.customButtons.get()
          if (jsonString.isNotBlank()) {
            try {
               // Try new slot-based format first
               val slotsData = json.decodeFromString<app.marlboroadvance.mpvex.ui.preferences.CustomButtonSlots>(jsonString)
               slotsData.slots.forEachIndexed { index, btn ->
                 if (btn != null) {
                   val safeId = btn.id.replace("-", "_")
                   val isLeft = index < 4 // Slots 0-3 are left, 4-7 are right
                   processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, buttons)
                 }
               }
            } catch (e: Exception) {
               // Fallback to old format for backward compatibility
               try {
                 val customButtonsList = json.decodeFromString<List<app.marlboroadvance.mpvex.ui.preferences.CustomButton>>(jsonString)
                 customButtonsList.forEachIndexed { index, btn ->
                   val safeId = btn.id.replace("-", "_")
                   val isLeft = index < 4 // First 4 are left buttons, rest are right
                   processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, buttons)
                 }
               } catch (e2: Exception) {
                 e2.printStackTrace()
               }
            }
          }
        }

        _customButtons.value = buttons

        if (scriptContent.isNotEmpty()) {
          val scriptsDir = File(host.context.filesDir, "scripts")
          if (!scriptsDir.exists()) scriptsDir.mkdirs()
          
          val file = File(scriptsDir, "custombuttons.lua")
          file.writeText(scriptContent)
          MPVLib.command("load-script", file.absolutePath)
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error setting up custom buttons", e)
      }
    }
  }

  fun callCustomButton(id: String) {
    val safeId = id.replace("-", "_")
    MPVLib.command("script-message", "call_button_$safeId")
  }
  
  fun callCustomButtonLongPress(id: String) {
    val safeId = id.replace("-", "_")
    MPVLib.command("script-message", "call_button_long_$safeId")
  }

  private fun StringBuilder.processButton(
    originalId: String,
    safeId: String,
    label: String,
    command: String,
    longPressCommand: String,
    onStartup: String,
    isLeft: Boolean,
    uiList: MutableList<CustomButtonState>
  ) {
    if (label.isNotBlank()) {
      uiList.add(CustomButtonState(originalId, label, isLeft))
      
      // On Startup Code
      if (onStartup.isNotBlank()) {
          append(onStartup)
          append("\n")
      }

      // Click Handler
      if (command.isNotBlank()) {
        append(
          """
          function button_${safeId}()
              ${command}
          end
          mp.register_script_message('call_button_${safeId}', button_${safeId})
          """.trimIndent()
        )
        append("\n")
      }
      
      // Long Press Handler
      if (longPressCommand.isNotBlank()) {
        append(
          """
          function button_long_${safeId}()
              ${longPressCommand}
          end
          mp.register_script_message('call_button_long_${safeId}', button_long_${safeId})
          """.trimIndent()
        )
        append("\n")
      }
    }
  }

  // Cached values
  private val doubleTapToSeekDuration by lazy { gesturePreferences.doubleTapToSeekDuration.get() }
  private val inputMethodManager by lazy {
    host.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  }

  // Seek coalescing for smooth performance
  private var pendingSeekOffset: Int = 0
  private var seekCoalesceJob: Job? = null

  private companion object {
    const val TAG = "PlayerViewModel"
    const val SEEK_COALESCE_DELAY_MS = 60L
    val VALID_SUBTITLE_EXTENSIONS =
      setOf("srt", "ass", "ssa", "sub", "idx", "vtt", "sup", "txt", "pgs")
  }

  // ==================== Timer ====================

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return

    timerJob =
      viewModelScope.launch {
        for (time in seconds downTo 0) {
          _remainingTime.value = time
          delay(1000)
        }
        MPVLib.setPropertyBoolean("pause", true)
        showToast(host.context.getString(R.string.toast_sleep_timer_ended))
      }
  }

  // ==================== Decoder ====================

  // ==================== Audio/Subtitle Management ====================

  fun addAudio(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val path =
          uri.resolveUri(host.context)
            ?: return@launch withContext(Dispatchers.Main) {
              showToast("Failed to load audio file: Invalid URI")
            }

        MPVLib.command("audio-add", path, "cached")
        withContext(Dispatchers.Main) {
          showToast("Audio track added")
        }
      }.onFailure { e ->
        withContext(Dispatchers.Main) {
          showToast("Failed to load audio: ${e.message}")
        }
        android.util.Log.e("PlayerViewModel", "Error adding audio", e)
      }
    }
  }

  fun addSubtitle(uri: Uri, select: Boolean = true, silent: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      val uriString = uri.toString()
      if (_externalSubtitles.contains(uriString)) {
        android.util.Log.d("PlayerViewModel", "Subtitle already tracked, skipping: $uriString")
        return@launch
      }

      runCatching {
        val fileName = getFileNameFromUri(uri) ?: "subtitle.srt"

        if (!isValidSubtitleFile(fileName)) {
          return@launch withContext(Dispatchers.Main) {
            showToast("Invalid subtitle file format")
          }
        }

        // Take persistent URI permission for content:// URIs
        if (uri.scheme == "content") {
          try {
            host.context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          } catch (e: SecurityException) {
            // Permission already granted, not available, or not needed (e.g. from tree).
            android.util.Log.i("PlayerViewModel", "Persistent permission not taken for $uri (may already have it via tree)")
          }
        }

        val mpvPath = uri.resolveUri(host.context) ?: uri.toString()
        val mode = if (select) "select" else "auto"
        
        // Store mapping for reliable physical deletion later
        mpvPathToUriMap[mpvPath] = uri.toString()
        
        MPVLib.command("sub-add", mpvPath, mode)

        // Track external subtitle URI for persistence
        val uriString = uri.toString()
        if (!_externalSubtitles.contains(uriString)) {
          _externalSubtitles.add(uriString)
        }

        val displayName = fileName.take(30).let { if (fileName.length > 30) "$it..." else it }
        if (!silent) {
          withContext(Dispatchers.Main) {
            showToast("$displayName added")
          }
        }
      }.onFailure {
        if (!silent) {
          withContext(Dispatchers.Main) {
            showToast("Failed to load subtitle")
          }
        }
      }
    }
  }

  private fun scanLocalSubtitles(mediaTitle: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val saveFolderUri = subtitlesPreferences.subtitleSaveFolder.get()
      if (saveFolderUri.isBlank()) return@launch
      
      try {
        val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
        val parentDir = DocumentFile.fromTreeUri(host.context, Uri.parse(saveFolderUri)) ?: return@launch
        val movieDir = parentDir.findFile(sanitizedTitle) ?: return@launch
        
        if (movieDir.isDirectory) {
          movieDir.listFiles().forEach { file ->
            if (file.isFile && isValidSubtitleFile(file.name ?: "")) {
              withContext(Dispatchers.Main) {
                // Don't auto-select during scan, just make available
                addSubtitle(file.uri, select = false, silent = true)
              }
            }
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error scanning local subtitles: ${e.message}", e)
      }
    }
  }

  fun setMediaTitle(mediaTitle: String) {
    if (currentMediaTitle != mediaTitle) {
      currentMediaTitle = mediaTitle
      lastAutoSelectedMediaTitle = null
      // Clear external subtitles when media changes
      _externalSubtitles.clear()
      // Scan for previously downloaded/added subtitles
      scanLocalSubtitles(mediaTitle)
    }
  }


  fun removeSubtitle(id: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      // Find the subtitle track info before removing
      val tracks = subtitleTracks.value
      val trackToRemove = tracks.firstOrNull { it.id == id }
      
      // If it's external, physically delete the file if we can find its URI
      if (trackToRemove?.external == true && trackToRemove.externalFilename != null) {
        val mpvPath = trackToRemove.externalFilename
        val originalUriString = mpvPathToUriMap[mpvPath] ?: mpvPath
        val uri = Uri.parse(originalUriString)
        
        val deleted = wyzieRepository.deleteSubtitleFile(uri)
        
        if (deleted) {
          _externalSubtitles.remove(originalUriString)
          mpvPathToUriMap.remove(mpvPath)
          withContext(Dispatchers.Main) {
            showToast("Subtitle deleted")
          }
        }
      }
      
        MPVLib.command("sub-remove", id.toString())
    }
  }

  // --- Media Search and Series Management ---

  private var mediaSearchJob: Job? = null

  fun searchMedia(query: String) {
    mediaSearchJob?.cancel()
    if (query.isBlank()) {
      _mediaSearchResults.value = emptyList()
      return
    }

    mediaSearchJob = viewModelScope.launch {
      delay(300) // Debounce
      _isSearchingMedia.value = true
      wyzieRepository.searchMedia(query)
        .onSuccess { results ->
          _mediaSearchResults.value = results
        }
        .onFailure {
          // Silent failure for autocomplete, or optionally show toast(if someone is reading this if u need u can impelmen this in future )
        }
      _isSearchingMedia.value = false
    }
  }

  fun selectMedia(result: app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult) {
    _mediaSearchResults.value = emptyList() // Clear results after selection
    _wyzieSearchResults.value = emptyList() // Clear old subtitle results
    
    if (result.mediaType == "tv") {
      fetchTvShowDetails(result.id)
    } else {
      // For movies, just search subtitles directly with the TMDB ID
      searchSubtitles(result.title)
      // Ideally we should pass the TMDB ID to searchSubtitles too if the API supports it
    }
  }

  private fun fetchTvShowDetails(id: Int) {
    viewModelScope.launch {
      _isFetchingTvDetails.value = true
      wyzieRepository.getTvShowDetails(id)
        .onSuccess { details ->
          val validSeasons = details.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
          _selectedTvShow.value = details.copy(seasons = validSeasons)
          _selectedSeason.value = null
          _seasonEpisodes.value = emptyList()
        }
        .onFailure {
          showToast("Failed to load series details: ${it.message}")
        }
      _isFetchingTvDetails.value = false
    }
  }

  fun selectSeason(season: app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason) {
    val tvShowId = _selectedTvShow.value?.id ?: return
    _selectedSeason.value = season
    
    viewModelScope.launch {
      _isFetchingEpisodes.value = true
      wyzieRepository.getSeasonEpisodes(tvShowId, season.season_number)
        .onSuccess { episodes ->
          val validEpisodes = episodes.filter { it.episode_number > 0 }.sortedBy { it.episode_number }
          _seasonEpisodes.value = validEpisodes
          _selectedEpisode.value = null
        }
        .onFailure {
          showToast("Failed to load episodes: ${it.message}")
        }
      _isFetchingEpisodes.value = false
    }
  }

  fun selectEpisode(episode: app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode) {
    _selectedEpisode.value = episode
    val tvShowName = _selectedTvShow.value?.name ?: currentMediaTitle
    searchSubtitles(tvShowName, episode.season_number, episode.episode_number)
  }

  fun clearMediaSelection() {
    _selectedTvShow.value = null
    _selectedSeason.value = null
    _seasonEpisodes.value = emptyList()
    _selectedEpisode.value = null
    _mediaSearchResults.value = emptyList()
  }

  // --- Subtitle Search ---
  fun searchSubtitles(query: String, season: Int? = null, episode: Int? = null) {
     viewModelScope.launch {
         _isSearchingSub.value = true
         wyzieRepository.search(query, season, episode)
             .onSuccess { results ->
                 _wyzieSearchResults.value = results
             }
             .onFailure {
                 showToast("Search failed: ${it.message}")
             }
         _isSearchingSub.value = false
     }
  }

  fun downloadSubtitle(subtitle: WyzieSubtitle) {
      viewModelScope.launch {
          _isDownloadingSub.value = true
          wyzieRepository.download(subtitle, currentMediaTitle)
              .onSuccess { uri ->
                  addSubtitle(uri)
              }
              .onFailure {
                  showToast("Download failed: ${it.message}")
              }
          _isDownloadingSub.value = false
      }
  }


  fun toggleSubtitle(id: Int) {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0

    when {
      id == primarySid -> MPVLib.setPropertyString("sid", "no")
      id == secondarySid -> MPVLib.setPropertyString("secondary-sid", "no")
      primarySid <= 0 -> MPVLib.setPropertyInt("sid", id)
      secondarySid <= 0 -> MPVLib.setPropertyInt("secondary-sid", id)
      else -> MPVLib.setPropertyInt("sid", id)
    }
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    return (id == primarySid && primarySid > 0) || (id == secondarySid && secondarySid > 0)
  }

  private fun getFileNameFromUri(uri: Uri): String? =
    when (uri.scheme) {
      "content" ->
        host.context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }

      "file" -> uri.lastPathSegment
      else -> uri.lastPathSegment
    }

  private fun isValidSubtitleFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in VALID_SUBTITLE_EXTENSIONS

  // ==================== Playback Control ====================

  fun pauseUnpause() {
    viewModelScope.launch(Dispatchers.IO) {
      val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
      if (isPaused) {
        // We are about to unpause, so request focus
        withContext(Dispatchers.Main) { host.requestAudioFocus() }
        MPVLib.setPropertyBoolean("pause", false)
      } else {
        // We are about to pause
        MPVLib.setPropertyBoolean("pause", true)
        withContext(Dispatchers.Main) { host.abandonAudioFocus() }
      }
    }
  }

  fun pause() {
    viewModelScope.launch(Dispatchers.IO) {
      MPVLib.setPropertyBoolean("pause", true)
      withContext(Dispatchers.Main) { host.abandonAudioFocus() }
    }
  }

  fun unpause() {
    viewModelScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) { host.requestAudioFocus() }
      MPVLib.setPropertyBoolean("pause", false)
    }
  }

  // ==================== UI Control ====================

  fun showControls() {
    if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        host.windowInsetsController.isAppearanceLightStatusBars = false
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      // Defensive: InsetsController animation can crash under FD pressure
      // (e.g. during high-res HEVC playback on certain devices)
      Log.e(TAG, "Failed to show system bars", e)
    }
    _controlsShown.value = true
  }

  fun hideControls() {
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = false
  }

  fun autoHideControls() {
    try {
      if (playerPreferences.showSystemStatusBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      }
      if (playerPreferences.showSystemNavigationBar.get()) {
        host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = true
  }

  fun showSeekBar() {
    if (sheetShown.value == Sheets.None) {
      _seekBarShown.value = true
    }
  }

  fun hideSeekBar() {
    _seekBarShown.value = false
  }

  fun lockControls() {
    _areControlsLocked.value = true
  }

  fun unlockControls() {
    _areControlsLocked.value = false
  }

  // ==================== Seeking ====================

  fun seekBy(offset: Int) {
    coalesceSeek(offset)
  }

  fun seekTo(position: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      val maxDuration = MPVLib.getPropertyInt("duration") ?: 0
      var clampedPosition = position.coerceIn(0, maxDuration)

      // Clamp within AB loop if active
      val loopA = _abLoopA.value
      val loopB = _abLoopB.value
      if (loopA != null && loopB != null) {
        val min = minOf(loopA.toInt(), loopB.toInt())
        val max = maxOf(loopA.toInt(), loopB.toInt())
        clampedPosition = clampedPosition.coerceIn(min, max)
      }

      if (clampedPosition !in 0..maxDuration) return@launch

      // Cancel pending relative seek before absolute seek
      seekCoalesceJob?.cancel()
      pendingSeekOffset = 0
      
      // Use precise seeking for videos shorter than 2 minutes (120 seconds) or if preference is enabled
      val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || maxDuration < 120
      val seekMode = if (shouldUsePreciseSeeking) "absolute+exact" else "absolute+keyframes"
      MPVLib.command("seek", clampedPosition.toString(), seekMode)
    }
  }

  private fun coalesceSeek(offset: Int) {
    pendingSeekOffset += offset
    seekCoalesceJob?.cancel()
    seekCoalesceJob =
      viewModelScope.launch(Dispatchers.IO) {
        delay(SEEK_COALESCE_DELAY_MS)
        val toApply = pendingSeekOffset
        pendingSeekOffset = 0
        
        if (toApply != 0) {
          val duration = MPVLib.getPropertyInt("duration") ?: 0
          val currentPos = MPVLib.getPropertyInt("time-pos") ?: 0
          
          if (duration > 0 && currentPos + toApply >= duration) {
              // If seeking past the end, force seek to 100% absolute to ensure EOF is triggered
              MPVLib.command("seek", "100", "absolute-percent+exact")
          } else {
              // Use precise seeking for videos shorter than 2 minutes (120 seconds) or if preference is enabled
              val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || duration < 120
              val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
              MPVLib.command("seek", toApply.toString(), seekMode)
          }
        }
      }
  }

  fun leftSeek() {
    if ((pos ?: 0) > 0) {
      _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    }
    _isSeekingForwards.value = false
    seekBy(-doubleTapToSeekDuration)
  }

  fun rightSeek() {
    if ((pos ?: 0) < (duration ?: 0)) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    seekBy(doubleTapToSeekDuration)
  }

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.value = amount
  }

  fun updateSeekText(text: String?) {
    _seekText.value = text
  }

  fun updateIsSeekingForwards(isForwards: Boolean) {
    _isSeekingForwards.value = isForwards
  }

  private fun seekToWithText(
    seekValue: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    _isSeekingForwards.value = seekValue > currentPos
    _doubleTapSeekAmount.value = seekValue - currentPos
    _seekText.value = text
    seekTo(seekValue)
  }

  private fun seekByWithText(
    value: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    val maxDuration = duration ?: return

    _doubleTapSeekAmount.update {
      if ((value < 0 && it < 0) || currentPos + value > maxDuration) 0 else it + value
    }
    _seekText.value = text
    _isSeekingForwards.value = value > 0
    seekBy(value)
  }

  // ==================== Brightness & Volume ====================

  fun changeBrightnessTo(brightness: Float) {
    val coercedBrightness = brightness.coerceIn(0f, 1f)
    host.hostWindow.attributes =
      host.hostWindow.attributes.apply {
        screenBrightness = coercedBrightness
      }
    currentBrightness.value = coercedBrightness

    // Save brightness to preferences if enabled
    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.set(coercedBrightness)
    }
  }

  fun displayBrightnessSlider() {
    isBrightnessSliderShown.value = true
    brightnessSliderTimestamp.value = System.currentTimeMillis()
  }

  fun changeVolumeBy(change: Int) {
    val mpvVolume = MPVLib.getPropertyInt("volume")
    val absoluteMaxVolume = volumeBoostCap ?: (audioPreferences.volumeBoostCap.get() + 100)

    if (absoluteMaxVolume > 100 && currentVolume.value == maxVolume) {
      if (mpvVolume == 100 && change < 0) {
        changeVolumeTo(currentVolume.value + change)
      }
      val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
      if (finalMPVVolume in 100..absoluteMaxVolume) {
        return changeMPVVolumeTo(finalMPVVolume)
      }
    }
    changeVolumeTo(currentVolume.value + change)
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    currentVolume.value = newVolume
  }

  fun changeMPVVolumeTo(volume: Int) {
    MPVLib.setPropertyInt("volume", volume)
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.value = true
    volumeSliderTimestamp.value = System.currentTimeMillis()
  }

  // ==================== Video Aspect ====================

  fun changeVideoAspect(
    aspect: VideoAspect,
    showUpdate: Boolean = true,
  ) {
    when (aspect) {
      VideoAspect.Fit -> {
        // To FIT: Reset both properties to their defaults.
        MPVLib.setPropertyDouble("panscan", 0.0)
        MPVLib.setPropertyDouble("video-aspect-override", -1.0)
      }
      VideoAspect.Crop -> {
        // To CROP: Set panscan. MPV will auto-reset video-aspect-override.
        MPVLib.setPropertyDouble("panscan", 1.0)
      }
      VideoAspect.Stretch -> {
        // To STRETCH: Calculate ratio and set it. MPV will auto-reset panscan.
        @Suppress("DEPRECATION")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        host.hostWindowManager.defaultDisplay.getRealMetrics(dm)
        val ratio = dm.widthPixels / dm.heightPixels.toDouble()

        MPVLib.setPropertyDouble("video-aspect-override", ratio)
      }
    }

    // Save the preference
    playerPreferences.videoAspect.set(aspect)

    // Notify the UI
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  fun setCustomAspectRatio(ratio: Double) {
    MPVLib.setPropertyDouble("panscan", 0.0)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    playerPreferences.currentAspectRatio.set(ratio.toFloat())
    playerUpdate.value = PlayerUpdates.AspectRatio
  }

  fun restoreCustomAspectRatio() {
    val savedRatio = playerPreferences.currentAspectRatio.get()
    if (savedRatio > 0) {
      MPVLib.setPropertyDouble("panscan", 0.0)
      MPVLib.setPropertyDouble("video-aspect-override", savedRatio.toDouble())
    }
  }

  // ==================== Screen Rotation ====================

  fun cycleScreenRotations() {
    // Temporarily cycle orientation WITHOUT modifying preferences
    // Preferences remain the single source of truth and will be reapplied on next video
    host.hostRequestedOrientation =
      when (host.hostRequestedOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        -> {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        else -> {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
      }
  }

  // ==================== Lua Invocation Handling ====================

  fun handleLuaInvocation(
    property: String,
    value: String,
  ) {
    val data = value.removeSurrounding("\"").ifEmpty { return }

    when (property.substringAfterLast("/")) {
      "show_text" -> playerUpdate.value = PlayerUpdates.ShowText(data)
      "toggle_ui" -> handleToggleUI(data)
      "show_panel" -> handleShowPanel(data)
      "seek_to_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekToWithText(seekValue.toInt(), text)
      }
      "seek_by_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekByWithText(seekValue.toInt(), text)
      }
      "seek_by" -> seekByWithText(data.toInt(), null)
      "seek_to" -> seekToWithText(data.toInt(), null)
      "software_keyboard" -> handleSoftwareKeyboard(data)
    }

    MPVLib.setPropertyString(property, "")
  }

  private fun handleToggleUI(data: String) {
    when (data) {
      "show" -> showControls()
      "toggle" -> if (controlsShown.value) hideControls() else showControls()
      "hide" -> {
        sheetShown.value = Sheets.None
        panelShown.value = Panels.None
        hideControls()
      }
    }
  }

  private fun handleShowPanel(data: String) {
    when (data) {
      "frame_navigation" -> {
        sheetShown.value = Sheets.FrameNavigation
      }
      else -> {
        panelShown.value =
          when (data) {
            "subtitle_settings" -> Panels.SubtitleSettings
            "subtitle_delay" -> Panels.SubtitleDelay
            "audio_delay" -> Panels.AudioDelay
            "video_filters" -> Panels.VideoFilters
            else -> Panels.None
          }
      }
    }
  }

  private fun handleSoftwareKeyboard(data: String) {
    when (data) {
      "show" -> forceShowSoftwareKeyboard()
      "hide" -> forceHideSoftwareKeyboard()
      "toggle" ->
        if (!inputMethodManager.isActive) {
          forceShowSoftwareKeyboard()
        } else {
          forceHideSoftwareKeyboard()
        }
    }
  }

  @Suppress("DEPRECATION")
  private fun forceShowSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
  }

  @Suppress("DEPRECATION")
  private fun forceHideSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
  }

  // ==================== Gesture Handling ====================

  fun handleLeftDoubleTap() {
    when (gesturePreferences.leftSingleActionGesture.get()) {
      SingleActionGesture.Seek -> leftSeek()
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> viewModelScope.launch(Dispatchers.IO) {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
      }
      SingleActionGesture.None -> {}
    }
  }

  fun handleCenterDoubleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> viewModelScope.launch(Dispatchers.IO) {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      }
      SingleActionGesture.Seek, SingleActionGesture.None -> {}
    }
  }

  fun handleCenterSingleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> viewModelScope.launch(Dispatchers.IO) {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      }
      SingleActionGesture.Seek, SingleActionGesture.None -> {}
    }
  }

  fun handleRightDoubleTap() {
    when (gesturePreferences.rightSingleActionGesture.get()) {
      SingleActionGesture.Seek -> rightSeek()
      SingleActionGesture.PlayPause -> pauseUnpause()
      SingleActionGesture.Custom -> viewModelScope.launch(Dispatchers.IO) {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
      }
      SingleActionGesture.None -> {}
    }
  }

  // ==================== Video Zoom ====================

  fun setVideoZoom(zoom: Float) {
    _videoZoom.value = zoom
    MPVLib.setPropertyDouble("video-zoom", zoom.toDouble())
  }

  // Video pan (for pan & zoom feature)
  private val _videoPanX = MutableStateFlow(0f)
  val videoPanX: StateFlow<Float> = _videoPanX.asStateFlow()

  private val _videoPanY = MutableStateFlow(0f)
  val videoPanY: StateFlow<Float> = _videoPanY.asStateFlow()

  fun setVideoPan(x: Float, y: Float) {
    _videoPanX.value = x
    _videoPanY.value = y
    MPVLib.setPropertyDouble("video-pan-x", x.toDouble())
    MPVLib.setPropertyDouble("video-pan-y", y.toDouble())
  }

  fun resetVideoPan() {
    setVideoPan(0f, 0f)
  }

  fun resetVideoZoom() {
    setVideoZoom(0f)
  }

  // ==================== Frame Navigation ====================

  fun updateFrameInfo() {
    _currentFrame.value = MPVLib.getPropertyInt("estimated-frame-number") ?: 0

    val durationValue = MPVLib.getPropertyDouble("duration") ?: 0.0
    val fps =
      MPVLib.getPropertyDouble("container-fps")
        ?: MPVLib.getPropertyDouble("estimated-vf-fps")
        ?: 0.0

    _totalFrames.value =
      if (durationValue > 0 && fps > 0) {
        (durationValue * fps).toInt()
      } else {
        0
      }
  }

  fun toggleFrameNavigationExpanded() {
    val wasExpanded = _isFrameNavigationExpanded.value
    _isFrameNavigationExpanded.update { !it }
    // Update frame info and pause when expanding (going from false to true)
    if (!wasExpanded) {
      // Pause the video if it's playing
      if (paused != true) {
        pauseUnpause()
      }
      updateFrameInfo()
      showFrameInfoOverlay()
      resetFrameNavigationTimer()
    } else {
      // Cancel timer when manually collapsing
      frameNavigationCollapseJob?.cancel()
    }
  }

  private fun showFrameInfoOverlay() {
    playerUpdate.value = PlayerUpdates.FrameInfo(_currentFrame.value, _totalFrames.value)
  }

  fun frameStepForward() {
    viewModelScope.launch(Dispatchers.IO) {
      if (paused != true) {
        pauseUnpause()
        delay(50)
      }
      MPVLib.command("no-osd", "frame-step")
      delay(100)
      updateFrameInfo()
      withContext(Dispatchers.Main) {
        showFrameInfoOverlay()
        // Reset the inactivity timer
        resetFrameNavigationTimer()
      }
    }
  }

  fun frameStepBackward() {
    viewModelScope.launch(Dispatchers.IO) {
      if (paused != true) {
        pauseUnpause()
        delay(50)
      }
      MPVLib.command("no-osd", "frame-back-step")
      delay(100)
      updateFrameInfo()
      withContext(Dispatchers.Main) {
        showFrameInfoOverlay()
        // Reset the inactivity timer
        resetFrameNavigationTimer()
      }
    }
  }

  private var frameNavigationCollapseJob: Job? = null

  fun resetFrameNavigationTimer() {
    frameNavigationCollapseJob?.cancel()
    frameNavigationCollapseJob = viewModelScope.launch {
      delay(10000) // 10 seconds
      if (_isFrameNavigationExpanded.value) {
        _isFrameNavigationExpanded.value = false
      }
    }
  }

  fun takeSnapshot(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      _isSnapshotLoading.value = true
      try {
        val includeSubtitles = playerPreferences.includeSubtitlesInSnapshot.get()

        // Generate filename with timestamp
        val timestamp =
          java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val filename = "mpv_snapshot_$timestamp.png"

        // Create a temporary file first
        val tempFile = File(context.cacheDir, filename)

        // Take screenshot using MPV to temp file, with or without subtitles
        if (includeSubtitles) {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "subtitles")
        } else {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "video")
        }

        // Wait a bit for MPV to finish writing the file
        delay(200)

        // Check if file was created
        if (!tempFile.exists() || tempFile.length() == 0L) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to create screenshot", Toast.LENGTH_SHORT).show()
          }
          return@launch
        }

        // Use different methods based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          // Android 10+ - Use MediaStore with RELATIVE_PATH
          val contentValues =
            android.content.ContentValues().apply {
              put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
              put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
              put(
                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                "${android.os.Environment.DIRECTORY_PICTURES}/mpvSnaps",
              )
              put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }

          val resolver = context.contentResolver
          val imageUri =
            resolver.insert(
              android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              contentValues,
            )

          if (imageUri != null) {
            // Copy temp file to MediaStore
            resolver.openOutputStream(imageUri)?.use { outputStream ->
              tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
              }
            }

            // Mark as finished
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)

            // Delete temp file
            tempFile.delete()

            // Show success toast
            withContext(Dispatchers.Main) {
              Toast
                .makeText(
                  context,
                  context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                  Toast.LENGTH_SHORT,
                ).show()
            }
          } else {
            throw Exception("Failed to create MediaStore entry")
          }
        } else {
          // Android 9 and below - Use legacy external storage
          val picturesDir =
            android.os.Environment.getExternalStoragePublicDirectory(
              android.os.Environment.DIRECTORY_PICTURES,
            )
          val snapshotsDir = File(picturesDir, "mpvSnaps")

          // Create directory if it doesn't exist
          if (!snapshotsDir.exists()) {
            val created = snapshotsDir.mkdirs()
            if (!created && !snapshotsDir.exists()) {
              throw Exception("Failed to create mpvSnaps directory")
            }
          }

          val destFile = File(snapshotsDir, filename)
          tempFile.copyTo(destFile, overwrite = true)
          tempFile.delete()

          // Notify media scanner about the new file
          android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("image/png"),
            null,
          )

          withContext(Dispatchers.Main) {
            Toast
              .makeText(
                context,
                context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                Toast.LENGTH_SHORT,
              ).show()
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(context, "Failed to save snapshot: ${e.message}", Toast.LENGTH_LONG).show()
        }
      } finally {
        _isSnapshotLoading.value = false
      }
    }
  }

  // ==================== Playlist Management ====================

  fun hasPlaylistSupport(): Boolean {
    val playlistModeEnabled = playerPreferences.playlistMode.get()
    return playlistModeEnabled && ((host as? PlayerActivity)?.playlist?.isNotEmpty() ?: false)
  }

  fun getPlaylistInfo(): String? {
    val activity = host as? PlayerActivity ?: return null
    if (activity.playlist.isEmpty()) return null

    val totalCount = getPlaylistTotalCount()
    return "${activity.playlistIndex + 1}/$totalCount"
  }

  fun isPlaylistM3U(): Boolean {
    val activity = host as? PlayerActivity ?: return false
    return activity.isCurrentPlaylistM3U()
  }

  fun getPlaylistTotalCount(): Int {
    val activity = host as? PlayerActivity ?: return 0
    return activity.playlist.size
  }

  fun getPlaylistData(): List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>? {
    val activity = host as? PlayerActivity ?: return null
    if (activity.playlist.isEmpty()) return null

    // Get current video progress
    val currentPos = pos ?: 0
    val currentDuration = duration ?: 0
    val currentProgress = if (currentDuration > 0) {
      ((currentPos.toFloat() / currentDuration.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 0f

    return activity.playlist.mapIndexed { index, uri ->
      val title = activity.getPlaylistItemTitle(uri)
      // Path is not used for thumbnail loading - thumbnails are loaded directly from URI
      // Keep it for cache key compatibility
      val path = uri.toString()
      val isCurrentlyPlaying = index == activity.playlistIndex

      // Try to get from cache first (synchronized access)
      val cacheKey = uri.toString()
      val (durationStr, resolutionStr) = synchronized(metadataCache) { metadataCache[cacheKey] } ?: ("" to "")

      app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem(
        uri = uri,
        title = title,
        index = index,
        isPlaying = isCurrentlyPlaying,
        path = path,
        progressPercent = if (isCurrentlyPlaying) currentProgress else 0f,
        isWatched = isCurrentlyPlaying && currentProgress >= 95f,
        duration = durationStr,
        resolution = resolutionStr,
      )
    }
  }

  private fun getVideoMetadata(uri: Uri): Pair<String, String> {
    // Skip metadata extraction for network streams and M3U playlists
    if (uri.scheme?.startsWith("http") == true || uri.scheme == "rtmp" || uri.scheme == "ftp" || uri.scheme == "rtsp" || uri.scheme == "mms") {
      return "" to ""
    }

    // Skip M3U/M3U8 files
    val uriString = uri.toString().lowercase()
    if (uriString.contains(".m3u8") || uriString.contains(".m3u")) {
      return "" to ""
    }

    val retriever = android.media.MediaMetadataRetriever()
    return try {
      // For file:// URIs, use the path directly (faster)
      if (uri.scheme == "file") {
        retriever.setDataSource(uri.path)
      } else {
        // For content:// URIs, use context
        retriever.setDataSource(host.context, uri)
      }

      // Get duration
      val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
      val durationStr = if (durationMs != null) {
        val seconds = durationMs.toLong() / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
      } else ""

      // Get resolution
      val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      val resolutionStr = if (width != null && height != null) {
        "${width}x${height}"
      } else ""

      durationStr to resolutionStr
    } catch (e: Exception) {
      android.util.Log.e("PlayerViewModel", "Failed to get video metadata for $uri", e)
      "" to ""
    } finally {
      try {
        retriever.release()
      } catch (e: Exception) {
        // Ignore release errors
      }
    }
  }
  
  

  fun playPlaylistItem(index: Int) {
    val activity = host as? PlayerActivity ?: return
    activity.playPlaylistItem(index)
  }

  /**
   * Refreshes the playlist items to update the currently playing indicator.
   * Called when a new video starts playing to update the playlist UI.
   */
  fun refreshPlaylistItems() {
    viewModelScope.launch(Dispatchers.IO) {
      val updatedItems = getPlaylistData()
      if (updatedItems != null) {
        // Clear cache if playlist size changed
        if (_playlistItems.value.size != updatedItems.size) {
          metadataCache.evictAll()
        }

        _playlistItems.value = updatedItems

        // Load metadata asynchronously in the background
        loadPlaylistMetadataAsync(updatedItems)
      }
    }
  }

  /**
   * Loads metadata for all playlist items asynchronously in the background.
   * Updates the playlist items as metadata becomes available.
   * Uses batched updates to avoid O(n²) complexity with large playlists.
   * Skips metadata extraction for M3U playlists (network streams).
   */
  private fun loadPlaylistMetadataAsync(items: List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      // Skip metadata extraction for M3U playlists
      val activity = host as? PlayerActivity
      if (activity?.isCurrentPlaylistM3U() == true) {
        Log.d(TAG, "Skipping metadata extraction for M3U playlist")
        return@launch
      }

      // Limit concurrent metadata extraction to avoid overwhelming resources
      val batchSize = 5
      items.chunked(batchSize).forEach { batch ->
        val updates = mutableMapOf<String, Pair<String, String>>()

        // Extract metadata for the batch
        batch.forEach { item ->
          val cacheKey = item.uri.toString()

          // Skip if already in cache (LruCache is thread-safe)
          if (metadataCache.get(cacheKey) == null) {
            // Extract metadata
            val (durationStr, resolutionStr) = getVideoMetadata(item.uri)

            // Update cache and track update
            updateMetadataCache(cacheKey, durationStr to resolutionStr)
            updates[cacheKey] = durationStr to resolutionStr
          }
        }

        // Apply all batched updates at once (single playlist update)
        if (updates.isNotEmpty()) {
          _playlistItems.value = _playlistItems.value.map { currentItem ->
            val cacheKey = currentItem.uri.toString()
            val (durationStr, resolutionStr) = updates[cacheKey] ?: return@map currentItem
            currentItem.copy(duration = durationStr, resolution = resolutionStr)
          }
        }
      }
    }
  }

  fun hasNext(): Boolean = (host as? PlayerActivity)?.hasNext() ?: false

  fun hasPrevious(): Boolean = (host as? PlayerActivity)?.hasPrevious() ?: false

  fun playNext() {
    (host as? PlayerActivity)?.playNext()
  }

  fun playPrevious() {
    (host as? PlayerActivity)?.playPrevious()
  }

  // ==================== Repeat and Shuffle ====================

  fun applyPersistedShuffleState() {
    if (_shuffleEnabled.value) {
      val activity = host as? PlayerActivity
      activity?.onShuffleToggled(true)
    }
  }

  fun cycleRepeatMode() {
    val hasPlaylist = (host as? PlayerActivity)?.playlist?.isNotEmpty() == true

    _repeatMode.value = when (_repeatMode.value) {
      RepeatMode.OFF -> RepeatMode.ONE
      RepeatMode.ONE -> if (hasPlaylist) RepeatMode.ALL else RepeatMode.OFF
      RepeatMode.ALL -> RepeatMode.OFF
    }

    // Persist the repeat mode
    playerPreferences.repeatMode.set(_repeatMode.value)

    // Show overlay update instead of toast
    playerUpdate.value = PlayerUpdates.RepeatMode(_repeatMode.value)
  }

  fun toggleShuffle() {
    _shuffleEnabled.value = !_shuffleEnabled.value
    val activity = host as? PlayerActivity

    // Persist the shuffle state
    playerPreferences.shuffleEnabled.set(_shuffleEnabled.value)

    // Notify activity to handle shuffle state change
    activity?.onShuffleToggled(_shuffleEnabled.value)

    // Show overlay update instead of toast
    playerUpdate.value = PlayerUpdates.Shuffle(_shuffleEnabled.value)
  }

  fun shouldRepeatCurrentFile(): Boolean {
    return _repeatMode.value == RepeatMode.ONE ||
      (_repeatMode.value == RepeatMode.ALL && (host as? PlayerActivity)?.playlist?.isEmpty() == true)
  }

  fun shouldRepeatPlaylist(): Boolean {
    return _repeatMode.value == RepeatMode.ALL && (host as? PlayerActivity)?.playlist?.isNotEmpty() == true
  }

  // ==================== A-B Loop ====================

  fun toggleABLoopExpanded() {
    _isABLoopExpanded.update { !it }
  }

  fun setLoopA() {
    if (_abLoopA.value != null) {
      // Toggle off - clear point A
      _abLoopA.value = null
      MPVLib.setPropertyString("ab-loop-a", "no")
      return
    }

    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopA.value = currentPos
    MPVLib.setPropertyDouble("ab-loop-a", currentPos)
  }

  fun setLoopB() {
    if (_abLoopB.value != null) {
      // Toggle off - clear point B
      _abLoopB.value = null
      MPVLib.setPropertyString("ab-loop-b", "no")
      return
    }

    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopB.value = currentPos
    MPVLib.setPropertyDouble("ab-loop-b", currentPos)
  }

  fun clearABLoop() {
    val hadLoop = _abLoopA.value != null || _abLoopB.value != null
    _abLoopA.value = null
    _abLoopB.value = null
    MPVLib.setPropertyString("ab-loop-a", "no")
    MPVLib.setPropertyString("ab-loop-b", "no")
  }

  fun formatTimestamp(seconds: Double): String {
    val totalSec = seconds.toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
  }

  // ==================== Mirroring ====================

  fun toggleMirroring() {
    val newMirrorState = !_isMirrored.value
    _isMirrored.value = newMirrorState
    
    // Use labeled video filter for mirroring to avoid state desync
    if (newMirrorState) {
      MPVLib.command("vf", "add", "@mpvex_hflip:hflip")
    } else {
      MPVLib.command("vf", "remove", "@mpvex_hflip")
    }
    playerUpdate.value = PlayerUpdates.ShowText(if (newMirrorState) "H-Flip On" else "H-Flip Off")
  }

  fun toggleVerticalFlip() {
    val newState = !_isVerticalFlipped.value
    _isVerticalFlipped.value = newState

    // Use labeled video filter for vflip to avoid state desync
    if (newState) {
      MPVLib.command("vf", "add", "@mpvex_vflip:vflip")
    } else {
      MPVLib.command("vf", "remove", "@mpvex_vflip")
    }

    playerUpdate.value = PlayerUpdates.ShowText(if (newState) "V-Flip On" else "V-Flip Off")
  }

  // ==================== Utility ====================

  fun showToast(message: String) {
    Toast.makeText(host.context, message, Toast.LENGTH_SHORT).show()
  }
}

// Extension functions
fun Float.normalize(
  inMin: Float,
  inMax: Float,
  outMin: Float,
  outMax: Float,
): Float = (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

fun <T> Flow<T>.collectAsState(
  scope: CoroutineScope,
  initialValue: T? = null,
) = object : ReadOnlyProperty<Any?, T?> {
  private var value: T? = initialValue

  init {
    scope.launch { collect { value = it } }
  }

  override fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = value
}
