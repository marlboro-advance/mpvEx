package app.gyrolet.mpvrx.ui.player

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
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.hdr.HdrToysManager
import app.gyrolet.mpvrx.preferences.AudioPreferences
import app.gyrolet.mpvrx.preferences.GesturePreferences
import app.gyrolet.mpvrx.preferences.IntroSegmentProvider
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.preferences.SubtitlesPreferences
import app.gyrolet.mpvrx.repository.IntroDbLookupOutcome
import app.gyrolet.mpvrx.repository.IntroDbLookupRequest
import app.gyrolet.mpvrx.repository.IntroDbRepository
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitle
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleOrchestrator
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleSearchRequest
import app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleSearchMode
import app.gyrolet.mpvrx.repository.ai.SubtitleGenerationService
import app.gyrolet.mpvrx.repository.wyzie.WyzieSearchRepository
import app.gyrolet.mpvrx.utils.media.ChecksumUtils
import app.gyrolet.mpvrx.utils.media.MediaInfoParser
import app.gyrolet.mpvrx.utils.media.ParsedMediaInfo
import app.gyrolet.mpvrx.utils.media.SubtitleHashUtils
import app.gyrolet.mpvrx.utils.media.resolveSubtitleLookupDirectories
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import app.gyrolet.mpvrx.ui.preferences.CustomButton
import app.gyrolet.mpvrx.ui.preferences.CustomButtonScriptLanguage
import java.io.File
import java.security.MessageDigest
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.webkit.MimeTypeMap
import app.gyrolet.mpvrx.preferences.AdvancedPreferences
import app.gyrolet.mpvrx.preferences.DecoderPreferences
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.math.roundToInt


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
  enum class IntroDbStatusState {
    IDLE,
    LOOKING_UP,
    LOADED,
    NO_SEGMENTS,
    UNRESOLVED,
    ERROR,
    DISABLED,
  }

  data class IntroDbStatus(
    val state: IntroDbStatusState = IntroDbStatusState.IDLE,
    val message: String = "",
    val imdbId: String? = null,
    val segmentCount: Int = 0,
  )

  @Serializable
  private data class IntroMarkerCacheEntry(
    val providerSourceKey: String,
    val outcomeType: String,
    val imdbId: String? = null,
    val message: String = "",
    val segments: List<app.gyrolet.mpvrx.repository.IntroDbSegment> = emptyList(),
    val cachedAtMs: Long = System.currentTimeMillis(),
  )

  private val playerPreferences: PlayerPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val aiPreferences: app.gyrolet.mpvrx.preferences.AiPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val decoderPreferences: DecoderPreferences by inject()
  private val hdrToysManager: HdrToysManager by inject()
  private val json: Json by inject()
  private val playbackStateDao: app.gyrolet.mpvrx.database.dao.PlaybackStateDao by inject()
  private val aiService: app.gyrolet.mpvrx.repository.ai.AiService by inject()
  private val subtitleGenerationService: SubtitleGenerationService by inject()
  private val realtimeSubtitleService: app.gyrolet.mpvrx.repository.ai.RealtimeSubtitleService by inject()
  private val wyzieRepository: WyzieSearchRepository by inject()
  private val onlineSubtitleOrchestrator: OnlineSubtitleOrchestrator by inject()
  private val introDbRepository: IntroDbRepository by inject()
  private val introMarkerCachePrefs by lazy {
    host.context.getSharedPreferences(INTRO_MARKER_CACHE_PREFS, Context.MODE_PRIVATE)
  }

  // Playlist items for the playlist sheet
  private val _playlistItems = kotlinx.coroutines.flow.MutableStateFlow<List<app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistItem>>(emptyList())
  val playlistItems: kotlinx.coroutines.flow.StateFlow<List<app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistItem>> = _playlistItems.asStateFlow()

  private val _onlineSubtitleSearchResults = MutableStateFlow<List<OnlineSubtitle>>(emptyList())
  val onlineSubtitleSearchResults: StateFlow<List<OnlineSubtitle>> = _onlineSubtitleSearchResults.asStateFlow()

  private val _isDownloadingSub = MutableStateFlow(false)
  val isDownloadingSub: StateFlow<Boolean> = _isDownloadingSub.asStateFlow()

  private val _isSearchingSub = MutableStateFlow(false)
  val isSearchingSub: StateFlow<Boolean> = _isSearchingSub.asStateFlow()

  private val _isOnlineSectionExpanded = MutableStateFlow(true)
  val isOnlineSectionExpanded: StateFlow<Boolean> = _isOnlineSectionExpanded.asStateFlow()

  private val _isTranslatingSub = MutableStateFlow(false)
  val isTranslatingSub: StateFlow<Boolean> = _isTranslatingSub.asStateFlow()

  private val _translatingTrackId = MutableStateFlow<Int?>(null)
  val translatingTrackId: StateFlow<Int?> = _translatingTrackId.asStateFlow()

  private val _translatingTrackName = MutableStateFlow("")
  val translatingTrackName: StateFlow<String> = _translatingTrackName.asStateFlow()

  private val _translationProgress = MutableStateFlow(0f)
  val translationProgress: StateFlow<Float> = _translationProgress.asStateFlow()

  private val _translationStatus = MutableStateFlow("")
  val translationStatus: StateFlow<String> = _translationStatus.asStateFlow()

  private val _isGeneratingSubtitles = MutableStateFlow(false)
  val isGeneratingSubtitles: StateFlow<Boolean> = _isGeneratingSubtitles.asStateFlow()

  private val _subtitleGenerationProgress = MutableStateFlow(0f)
  val subtitleGenerationProgress: StateFlow<Float> = _subtitleGenerationProgress.asStateFlow()

  private val _subtitleGenerationStatus = MutableStateFlow("")
  val subtitleGenerationStatus: StateFlow<String> = _subtitleGenerationStatus.asStateFlow()

  private val _isRealtimeSubsActive = MutableStateFlow(false)
  val isRealtimeSubsActive: StateFlow<Boolean> = _isRealtimeSubsActive.asStateFlow()

  private val _realtimeSubsLanguage = MutableStateFlow("")
  val realtimeSubsLanguage: StateFlow<String> = _realtimeSubsLanguage.asStateFlow()

  private val _realtimeSubsProgress = MutableStateFlow(0f)
  val realtimeSubsProgress: StateFlow<Float> = _realtimeSubsProgress.asStateFlow()

  private var realtimeSubsJob: Job? = null
  private var realtimeSrtFile: java.io.File? = null

  private var playlistMetadataJob: Job? = null
  private var controlsVisibleForPolling = false
  private var seekBarVisibleForPolling = false
  private val skippedSegmentTypes = mutableSetOf<SkipSegmentType>()
  private var chapterDerivedSegments: List<SkipSegment> = emptyList()
  private var introDbSegments: List<app.gyrolet.mpvrx.repository.IntroDbSegment> = emptyList()
  private var introDbSourceKey: String = IntroSegmentProvider.INTRO_DB.sourceKey
  private var introLookupJob: Job? = null
  private val introKeywordPatterns =
    listOf(
      // English/general
      "intro",
      "opening",
      "opening theme",
      "theme song",
      "title song",
      "creditless opening",
      "clean opening",
      "cold open",
      "prologue",
      "prelude",
      "op",
      "op1",
      "op2",
      "op3",
      "ncop",
      "ncop1",
      "ncop2",
      "nco",
      // Japanese
      "オープニング",
      "オープニングテーマ",
      "主題歌",
      "主題歌op",
      "ノンクレジットop",
      "ノンテロップop",
      "ノンクレop",
      "前期op",
      "後期op",
      "冒頭",
    )

  private val outroKeywordPatterns =
    listOf(
      // English/general
      "outro",
      "ending",
      "ending theme",
      "end credits",
      "credits",
      "credit roll",
      "epilogue",
      "postlude",
      "ed",
      "ed1",
      "ed2",
      "ed3",
      "nced",
      "nced1",
      "nced2",
      "nce",
      "preview",
      "next episode",
      // Japanese
      "エンディング",
      "エンディングテーマ",
      "エンドロール",
      "次回予告",
      "ノンクレジットed",
      "ノンテロップed",
      "ノンクレed",
      "前期ed",
      "後期ed",
      "予告",
      "終幕",
    )

  private val recapKeywordPatterns =
    listOf(
      "recap",
      "summary",
      "story so far",
      "previously on",
      "last time",
      "digest",
      "catch up",
      "振り返り",
      "前回まで",
      "これまで",
      "総集編",
      "おさらい",
    )

  private val creditsKeywordPatterns =
    listOf(
      "credits",
      "end credits",
      "credit roll",
      "rolling credits",
      "staff roll",
      "ã‚¨ãƒ³ãƒ‰ãƒ­ãƒ¼ãƒ«",
      "ã‚¯ãƒ¬ã‚¸ãƒƒãƒˆ",
    )

  private val previewKeywordPatterns =
    listOf(
      "preview",
      "next episode",
      "next week on",
      "up next",
      "teaser",
      "æ¬¡å›žäºˆå‘Š",
      "äºˆå‘Š",
      "æ¬¡å›ž",
    )

  private val _skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
  val skipSegments: StateFlow<List<SkipSegment>> = _skipSegments.asStateFlow()
  @Volatile private var skipSegmentsSnapshot: List<SkipSegment> = emptyList()

  private val _currentSkippableSegment = MutableStateFlow<SkipSegment?>(null)
  val currentSkippableSegment: StateFlow<SkipSegment?> = _currentSkippableSegment.asStateFlow()
  private val _introDbStatus = MutableStateFlow(
    if (playerPreferences.enableIntroDb.get()) {
      IntroDbStatus()
    } else {
      IntroDbStatus(
        state = IntroDbStatusState.DISABLED,
        message = "Online skip markers are disabled",
      )
    },
  )
  val introDbStatus: StateFlow<IntroDbStatus> = _introDbStatus.asStateFlow()

  // Media Search / Autocomplete
  private val _mediaSearchResults = MutableStateFlow<List<app.gyrolet.mpvrx.repository.wyzie.WyzieTmdbResult>>(emptyList())
  val mediaSearchResults: StateFlow<List<app.gyrolet.mpvrx.repository.wyzie.WyzieTmdbResult>> = _mediaSearchResults.asStateFlow()

  private val _isSearchingMedia = MutableStateFlow(false)
  val isSearchingMedia: StateFlow<Boolean> = _isSearchingMedia.asStateFlow()

  // TV Show Details
  private val _selectedTvShow = MutableStateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieTvShowDetails?>(null)
  val selectedTvShow: StateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieTvShowDetails?> = _selectedTvShow.asStateFlow()

  private val _isFetchingTvDetails = MutableStateFlow(false)
  val isFetchingTvDetails: StateFlow<Boolean> = _isFetchingTvDetails.asStateFlow()

  // Season / Episode
  private val _selectedSeason = MutableStateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieSeason?>(null)
  val selectedSeason: StateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieSeason?> = _selectedSeason.asStateFlow()

  private val _seasonEpisodes = MutableStateFlow<List<app.gyrolet.mpvrx.repository.wyzie.WyzieEpisode>>(emptyList())
  val seasonEpisodes: StateFlow<List<app.gyrolet.mpvrx.repository.wyzie.WyzieEpisode>> = _seasonEpisodes.asStateFlow()

  private val _isFetchingEpisodes = MutableStateFlow(false)
  val isFetchingEpisodes: StateFlow<Boolean> = _isFetchingEpisodes.asStateFlow()

  private val _selectedEpisode = MutableStateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieEpisode?>(null)
  val selectedEpisode: StateFlow<app.gyrolet.mpvrx.repository.wyzie.WyzieEpisode?> = _selectedEpisode.asStateFlow()

  fun toggleOnlineSection() {
      _isOnlineSectionExpanded.value = !_isOnlineSectionExpanded.value
  }

  // Cache for video metadata to avoid re-extracting — LruCache handles bounds + thread-safety
  private val metadataCache = object : android.util.LruCache<String, Pair<String, String>>(100) {}
  private val playbackStateDispatcher = Dispatchers.Default.limitedParallelism(1)
  private val renderPrepDispatcher = Dispatchers.Default.limitedParallelism(1)
  private val ambientCropRegex = Regex("""^(\d+)x(\d+)""")

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

  // These MPV-backed state flows must be initialized before any init block collects them.
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

  // Audio state
  val maxVolume = host.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  val currentVolume = MutableStateFlow(host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  val currentVolumePercent = MutableStateFlow(systemVolumeToPercent(currentVolume.value))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)

  init {
    // Single adaptive polling loop for playback position.
    //
    // Previously there were TWO concurrent mechanisms writing to _precisePosition:
    //  1. An event-driven collect on MPVLib.propInt["time-pos"]
    //  2. This polling loop via MPVLib.getPropertyDouble("time-pos")
    // Having both caused redundant StateFlow emissions and double recompositions of the
    // seek bar on every MPV property event.  The polling loop alone is sufficient:
    //  - It provides Double precision (vs integer from the observer)
    //  - It drives maybeAutoSkipIntro() which needs sub-second accuracy
    //  - The adaptive interval keeps CPU cost proportional to actual UI demand
    //
    // Intervals:
    //   50 ms  – seek bar / controls visible (smooth scrubbing)
    //   500 ms – uninterrupted playback (halved from original 250 ms to cut idle overhead)
    //   500 ms – paused
    viewModelScope.launch(playbackStateDispatcher) {
      while (isActive) {
        runCatching {
          val time = MPVLib.getPropertyDouble("time-pos")
          if (time != null) {
            _precisePosition.value = time.toFloat()
            maybeAutoSkipIntro(time)
          }
        }.onFailure { error ->
          if (isActive) {
            Log.w(TAG, "Playback position polling failed", error)
          }
        }
        val intervalMs =
          when {
            paused == false && (seekBarVisibleForPolling || controlsVisibleForPolling) -> 50L
            paused == false -> 500L   // was 250 ms — halved to reduce idle CPU wake-ups
            else -> 500L
          }
        delay(intervalMs)
      }
    }

    // ── Thermal monitor ────────────────────────────────────────────────────────
    // Sample Android's PowerManager.getThermalHeadroom() every 10 s during active
    // playback.  When thermal margin shrinks the ambient shader sample budget is
    // capped automatically, preventing the device from entering hard CPU/GPU throttling
    // which would otherwise manifest as dropped frames and accelerated battery drain.
    viewModelScope.launch(playbackStateDispatcher) {
      while (isActive) {
        if (paused == false) {
          val newHeadroom = ThermalMonitor.getHeadroom(host.context)
          if (kotlin.math.abs(newHeadroom - thermalHeadroom) > 0.08f) {
            thermalHeadroom = newHeadroom
            if (_isAmbientEnabled.value) {
              // Invalidate the shader cache so the new budget cap is applied on the
              // next scheduled ambient update.
              lastCompiledShaderCode = null
              scheduleAmbientUpdate()
            }
            Log.d(TAG, "Thermal headroom updated: %.2f".format(newHeadroom))
          }
        }
        delay(10_000L)
      }
    }

    // Update precise duration when the integer duration changes (avoid polling)
    viewModelScope.launch(playbackStateDispatcher) {
      MPVLib.propInt["duration"].collect { _ ->
        val dur = MPVLib.getPropertyDouble("duration")
        if (dur != null && dur > 0) {
            _preciseDuration.value = dur.toFloat()
            mergeSkipSegments()

            // --- AMBIENT FIX: Adapt shader to new file dimensions by @Chinna95P ---
            if (_isAmbientEnabled.value) {
                lastAmbientScaleX = -1.0 // Force a complete shader rewrite
                ambientDebounceJob?.cancel()
                ambientDebounceJob = viewModelScope.launch(renderPrepDispatcher) {
                    // Slight delay ensures MPV's video-params (w/h/crop) are fully populated
                    delay(250)
                    updateAmbientStretch()
                }
            }
            // --------------------------------------------------------
        }
      }
    }

    viewModelScope.launch(playbackStateDispatcher) {
      chapters
        .collect { chapterList ->
          refreshChapterDerivedSegments(chapterList)
      }
    }
  }

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
  private val _videoOpenAnimationState = MutableStateFlow(VideoOpenAnimationState())
  val videoOpenAnimationState: StateFlow<VideoOpenAnimationState> = _videoOpenAnimationState.asStateFlow()

  // Seek state — combined to allow atomic updates and reduce flow count
  data class SeekState(val text: String? = null, val amount: Int = 0, val isForwards: Boolean = false)
  private val _seekState = MutableStateFlow(SeekState())
  val seekState: StateFlow<SeekState> = _seekState.asStateFlow()

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

  // Video aspect ratio (persisted in player preferences)
  private val _videoAspect = MutableStateFlow(VideoAspect.Fit)
  val videoAspect: StateFlow<VideoAspect> = _videoAspect.asStateFlow()

  // Current aspect ratio value (for custom ratios and tracking)
  private val _currentAspectRatio = MutableStateFlow(-1.0)
  val currentAspectRatio: StateFlow<Double> = _currentAspectRatio.asStateFlow()

  // Timer
  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

  // Media title for subtitle association
  var currentMediaTitle: String = ""
  private var lastAutoSelectedMediaTitle: String? = null
  private val _videoHash = MutableStateFlow<String?>(null)
  val videoHash: StateFlow<String?> = _videoHash.asStateFlow()
  private var videoHashJob: Job? = null
  @Volatile
  private var videoHashGeneration = 0

  // External subtitle tracking
  private val _externalSubtitles = mutableListOf<String>()
  val externalSubtitles: List<String> get() = _externalSubtitles.toList()
  // Mutex to prevent race-condition duplicates when scan adds multiple subtitle URIs concurrently
  private val subtitleAddMutex = Mutex()

  // Mapping from mpv internal path/URI to the original source URI (resolves deletion issues)
  private val mpvPathToUriMap = mutableMapOf<String, String>()

  fun calculateVideoHash(uri: Uri) {
    _videoHash.value = null
    val generation = ++videoHashGeneration
    videoHashJob?.cancel()
    videoHashJob = viewModelScope.launch(Dispatchers.IO) {
      val hash = SubtitleHashUtils.computeHash(host.context, uri)
      if (videoHashGeneration == generation) {
        _videoHash.value = hash
      }
      Log.d(TAG, "Computed video hash for $uri: ${hash ?: "unavailable"}")
    }
  }

  // Repeat and Shuffle state
  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  // A-B Loop state — combined for atomic updates
  data class ABLoopState(val a: Double? = null, val b: Double? = null, val isExpanded: Boolean = false)
  private val _abLoopState = MutableStateFlow(ABLoopState())
  val abLoopState: StateFlow<ABLoopState> = _abLoopState.asStateFlow()

  // Transform state (mirror + flip) — combined, saves 1 StateFlow object
  data class TransformState(val isMirrored: Boolean = false, val isVerticalFlipped: Boolean = false)
  private val _transformState = MutableStateFlow(TransformState())
  val transformState: StateFlow<TransformState> = _transformState.asStateFlow()

  private val _hdrScreenMode = MutableStateFlow(initialHdrScreenMode())
  val hdrScreenMode: StateFlow<HdrScreenMode> = _hdrScreenMode.asStateFlow()

  private val _isHdrScreenOutputPipelineReady = MutableStateFlow(isHdrScreenOutputAvailable())
  val isHdrScreenOutputPipelineReady: StateFlow<Boolean> = _isHdrScreenOutputPipelineReady.asStateFlow()

  private val _isHdrScreenOutputEnabled =
    MutableStateFlow(_isHdrScreenOutputPipelineReady.value && _hdrScreenMode.value != HdrScreenMode.OFF)
  val isHdrScreenOutputEnabled: StateFlow<Boolean> = _isHdrScreenOutputEnabled.asStateFlow()

  // ==================== Ambience Mode ======================================
  private val _isAmbientEnabled = MutableStateFlow(playerPreferences.isAmbientEnabled.get())
  val isAmbientEnabled: StateFlow<Boolean> = _isAmbientEnabled.asStateFlow()

  private val _ambientVisualMode = MutableStateFlow(playerPreferences.ambientVisualMode.get())
  val ambientVisualMode: StateFlow<AmbientVisualMode> = _ambientVisualMode.asStateFlow()

  private val _ambientBlurSamples = MutableStateFlow(playerPreferences.ambientBlurSamples.get())
  val ambientBlurSamples: StateFlow<Int> = _ambientBlurSamples.asStateFlow()

  private val _ambientMaxRadius = MutableStateFlow(playerPreferences.ambientMaxRadius.get())
  val ambientMaxRadius: StateFlow<Float> = _ambientMaxRadius.asStateFlow()

  private val _ambientGlowIntensity = MutableStateFlow(playerPreferences.ambientGlowIntensity.get())
  val ambientGlowIntensity: StateFlow<Float> = _ambientGlowIntensity.asStateFlow()

  private val _ambientSatBoost = MutableStateFlow(playerPreferences.ambientSatBoost.get())
  val ambientSatBoost: StateFlow<Float> = _ambientSatBoost.asStateFlow()

  private val _ambientDitherNoise = MutableStateFlow(playerPreferences.ambientDitherNoise.get())
  val ambientDitherNoise: StateFlow<Float> = _ambientDitherNoise.asStateFlow()

  private val _ambientBezelDepth = MutableStateFlow(playerPreferences.ambientBezelDepth.get())
  val ambientBezelDepth: StateFlow<Float> = _ambientBezelDepth.asStateFlow()

  private val _ambientVignetteStrength = MutableStateFlow(playerPreferences.ambientVignetteStrength.get())
  val ambientVignetteStrength: StateFlow<Float> = _ambientVignetteStrength.asStateFlow()

  private val _ambientWarmth = MutableStateFlow(playerPreferences.ambientWarmth.get())
  val ambientWarmth: StateFlow<Float> = _ambientWarmth.asStateFlow()

  private val _ambientFadeCurve = MutableStateFlow(playerPreferences.ambientFadeCurve.get())
  val ambientFadeCurve: StateFlow<Float> = _ambientFadeCurve.asStateFlow()

  private val _ambientOpacity = MutableStateFlow(playerPreferences.ambientOpacity.get())
  val ambientOpacity: StateFlow<Float> = _ambientOpacity.asStateFlow()

  private val _frameExtendStrength = MutableStateFlow(playerPreferences.ambientExtendStrength.get())
  val frameExtendStrength: StateFlow<Float> = _frameExtendStrength.asStateFlow()

  private val _frameExtendDetailProtection = MutableStateFlow(playerPreferences.ambientExtendDetailProtection.get())
  val frameExtendDetailProtection: StateFlow<Float> = _frameExtendDetailProtection.asStateFlow()

  private val _frameExtendGlowMix = MutableStateFlow(playerPreferences.ambientExtendGlowMix.get())
  val frameExtendGlowMix: StateFlow<Float> = _frameExtendGlowMix.asStateFlow()

  private var lastAmbientScaleX = -1.0
  private var lastAmbientScaleY = -1.0
  private var ambientDebounceJob: kotlinx.coroutines.Job? = null
  private var ambientShaderSeq = 0
  private var ambientShaderFile: java.io.File? = null
  /**
   * Caches the last compiled GLSL shader source. When [updateAmbientStretch] is called
   * but every parameter is identical to the previously compiled shader, the expensive
   * file-write + MPV shader-reload cycle is skipped entirely.
   */
  private var lastCompiledShaderCode: String? = null
  /**
   * Latest device thermal headroom reading ([0f] = at thermal limit, [1f] = cool).
   * Sampled every 10 s by the thermal-monitor coroutine and used to cap the ambient
   * shader sample budget before the SoC enters hard throttling.
   */
  @Volatile private var thermalHeadroom: Float = 1.0f

  init {
    // Track selection is now handled by TrackSelector in PlayerActivity

    // Restore repeat mode and shuffle state from preferences
    _repeatMode.value = playerPreferences.repeatMode.get()
    _shuffleEnabled.value = playerPreferences.shuffleEnabled.get()

    // Observe volume boost cap changes to enforce limits dynamically (in PiP)
    viewModelScope.launch(playbackStateDispatcher) {
      audioPreferences.volumeBoostCap.changes().collect { cap ->
        val maxVol = 100 + cap
        runCatching {
          MPVLib.setPropertyString("volume-max", maxVol.toString())

          // Clamp current volume if it exceeds the new limit
          val currentMpvVol = MPVLib.getPropertyInt("volume") ?: 100
          if (currentMpvVol > maxVol) {
            MPVLib.setPropertyInt("volume", maxVol)
          }
        }.onFailure { e ->
          Log.e(TAG, "Error setting volume-max: $maxVol", e)
        }
      }
    }

    // Monitor duration and AB loop changes to automatically enable precise seeking
    viewModelScope.launch(playbackStateDispatcher) {
      combine(MPVLib.propInt["duration"], abLoopState) { duration, abLoop ->
        Pair(duration, abLoop)
      }.collect { (duration, abLoop) ->
        val videoDuration = duration ?: 0
        val isLoopActive = abLoop.a != null || abLoop.b != null
        val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || videoDuration < 120 || isLoopActive
        MPVLib.setPropertyString("hr-seek", if (shouldUsePreciseSeeking) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", if (shouldUsePreciseSeeking) "no" else "yes")
      }
    }


    // Refresh custom buttons whenever their configuration changes.
    viewModelScope.launch {
      playerPreferences.customButtons.changes().drop(1).collect {
        setupCustomButtons()
      }
    }

    viewModelScope.launch {
      sheetShown.collect { shownSheet ->
        if (shownSheet == Sheets.Playlist) {
          refreshPlaylistItems(forceMetadata = true)
        } else {
          playlistMetadataJob?.cancel()
        }
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
  private var customButtonsSetupJob: Job? = null
  private val customButtonsLoadMutex = Mutex()
  @Volatile
  private var isMpvReadyForCustomButtons = false
  @Volatile
  private var customButtonsScriptPaths: Map<CustomButtonScriptLanguage, String> = emptyMap()
  private val legacyCustomButtonsLoadedFlagProperty = "user-data/mpvrx/custombuttons_loaded"
  private val legacyCustomButtonsVersionProperty = "user-data/mpvrx/custombuttons_version"
  private val customButtonScriptTargets =
    listOf(
      CustomButtonScriptTarget(
        language = CustomButtonScriptLanguage.LUA,
        fileName = "custombuttons.lua",
        loadedFlagProperty = "user-data/mpvrx/custombuttons_lua_loaded",
        versionProperty = "user-data/mpvrx/custombuttons_lua_version",
      ),
      CustomButtonScriptTarget(
        language = CustomButtonScriptLanguage.JS,
        fileName = "custombuttons.js",
        loadedFlagProperty = "user-data/mpvrx/custombuttons_js_loaded",
        versionProperty = "user-data/mpvrx/custombuttons_js_version",
      ),
    )
  private val customButtonScriptTargetsByLanguage =
    customButtonScriptTargets.associateBy { it.language }

  private data class CustomButtonScriptTarget(
    val language: CustomButtonScriptLanguage,
    val fileName: String,
    val loadedFlagProperty: String,
    val versionProperty: String,
  )

  fun onMpvCoreInitialized() {
    isMpvReadyForCustomButtons = true
    reloadCustomButtonsScript("mpv_core_initialized")
  }

  fun onVideoLoadStarted() {
    _videoOpenAnimationState.update {
      it.copy(
        loadToken = it.loadToken + 1,
        isWaitingForVideo = true,
      )
    }
  }

  fun onVideoLoadCompleted() {
    _videoOpenAnimationState.update { current ->
      if (current.isWaitingForVideo) {
        current.copy(isWaitingForVideo = false)
      } else {
        current
      }
    }
  }

  private fun setupCustomButtons() {
    customButtonsSetupJob?.cancel()
    customButtonsSetupJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val buttons = mutableListOf<CustomButtonState>()
        val scriptBodies = linkedMapOf<CustomButtonScriptLanguage, StringBuilder>()
        val jsonString = playerPreferences.customButtons.get()
        if (jsonString.isNotBlank()) {
          try {
            // Try new slot-based format first
            val slotsData = json.decodeFromString<app.gyrolet.mpvrx.ui.preferences.CustomButtonSlots>(jsonString)
            slotsData.slots.forEachIndexed { index, btn ->
              if (btn != null && btn.enabled) {
                val safeId = btn.id.replace("-", "_")
                val isLeft = index < 4 // Slots 0-3 are left, 4-7 are right
                processButton(
                  originalId = btn.id,
                  safeId = safeId,
                  label = btn.title,
                  command = btn.content,
                  longPressCommand = btn.longPressContent,
                  onStartup = btn.onStartup,
                  language = btn.scriptLanguage,
                  scriptBuilder = scriptBodies.getOrPut(btn.scriptLanguage) { StringBuilder() },
                  isLeft = isLeft,
                  uiList = buttons,
                )
              }
            }
          } catch (e: Exception) {
            // Fallback to old format for backward compatibility
            try {
              val customButtonsList = json.decodeFromString<List<app.gyrolet.mpvrx.ui.preferences.CustomButton>>(jsonString)
              customButtonsList.forEachIndexed { index, btn ->
                val safeId = btn.id.replace("-", "_")
                val isLeft = index < 4 // First 4 are left buttons, rest are right
                processButton(
                  originalId = btn.id,
                  safeId = safeId,
                  label = btn.title,
                  command = btn.content,
                  longPressCommand = btn.longPressContent,
                  onStartup = btn.onStartup,
                  language = btn.scriptLanguage,
                  scriptBuilder = scriptBodies.getOrPut(btn.scriptLanguage) { StringBuilder() },
                  isLeft = isLeft,
                  uiList = buttons,
                )
              }
            } catch (e2: Exception) {
              e2.printStackTrace()
            }
          }
        }

        _customButtons.value = buttons

        val generatedPaths = mutableMapOf<CustomButtonScriptLanguage, String>()
        val scriptsDir = File(host.context.filesDir, "scripts")
        if (!scriptsDir.exists()) scriptsDir.mkdirs()

        scriptBodies.forEach { (language, bodyBuilder) ->
          val rawScriptContent = bodyBuilder.toString()
          if (rawScriptContent.isBlank()) return@forEach

          val target = customButtonScriptTargetsByLanguage[language] ?: return@forEach
          val scriptVersion = rawScriptContent.md5()
          val scriptContent = buildCustomButtonsScript(rawScriptContent, scriptVersion, target)

          val file = File(scriptsDir, target.fileName)
          file.writeText(scriptContent)
          generatedPaths[language] = file.absolutePath
        }

        customButtonsScriptPaths = generatedPaths.toMap()
        deleteCustomButtonsScriptFiles(activePaths = generatedPaths.values.toSet())
        customButtonScriptTargets
          .filter { it.language !in generatedPaths.keys }
          .forEach(::deactivateCustomButtonsScript)

        if (generatedPaths.isNotEmpty()) {
          if (isMpvReadyForCustomButtons) {
            customButtonsLoadMutex.withLock {
              deactivateLegacyCustomButtonsScript()
              generatedPaths.forEach { (language, path) ->
                val target = customButtonScriptTargetsByLanguage[language] ?: return@forEach
                val loaded = loadCustomButtonsScript(File(path), target)
                if (!loaded) {
                  android.util.Log.w("PlayerViewModel", "Failed to load ${target.fileName}")
                }
              }
            }
          } else {
            android.util.Log.d("PlayerViewModel", "Deferring custom button scripts until MPV is ready")
          }
        } else {
          customButtonsScriptPaths = emptyMap()
          deleteCustomButtonsScriptFiles()
          customButtonScriptTargets.forEach(::deactivateCustomButtonsScript)
          deactivateLegacyCustomButtonsScript()
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error setting up custom buttons", e)
      }
    }
  }

  private fun reloadCustomButtonsScript(reason: String) {
    if (!isMpvReadyForCustomButtons) return

    viewModelScope.launch(Dispatchers.IO) {
      var rebuildNeeded = false
      customButtonsLoadMutex.withLock {
        val scriptPaths = customButtonsScriptPaths
        if (scriptPaths.isEmpty()) return@withLock

        deactivateLegacyCustomButtonsScript()

        for ((language, scriptPath) in scriptPaths) {
          val target = customButtonScriptTargetsByLanguage[language] ?: continue
          if (isCustomButtonsScriptLoaded(target)) continue

          val file = File(scriptPath)
          if (!file.exists()) {
            android.util.Log.w("PlayerViewModel", "${target.fileName} missing during $reason, rebuilding")
            rebuildNeeded = true
            break
          }

          val loaded = loadCustomButtonsScript(file, target)
          if (!loaded) {
            android.util.Log.w("PlayerViewModel", "${target.fileName} load failed during $reason")
          }
        }
      }
      if (rebuildNeeded) {
        setupCustomButtons()
      }
    }
  }

  private fun isCustomButtonsScriptLoaded(target: CustomButtonScriptTarget): Boolean =
    runCatching { MPVLib.getPropertyString(target.loadedFlagProperty) == "1" }
      .getOrDefault(false)

  private fun loadCustomButtonsScript(
    file: File,
    target: CustomButtonScriptTarget,
  ): Boolean {
    runCatching { MPVLib.setPropertyString(target.loadedFlagProperty, "0") }

    return runCatching {
      MPVLib.command("load-script", file.absolutePath)
      true
    }.getOrElse {
      android.util.Log.w("PlayerViewModel", "load-script failed for ${target.fileName}: ${it.message}")
      false
    }
  }

  private fun deactivateCustomButtonsScript(target: CustomButtonScriptTarget) {
    runCatching {
      MPVLib.setPropertyString(target.loadedFlagProperty, "0")
      MPVLib.setPropertyString(target.versionProperty, "")
    }
  }

  private fun deactivateLegacyCustomButtonsScript() {
    runCatching {
      MPVLib.setPropertyString(legacyCustomButtonsLoadedFlagProperty, "0")
      MPVLib.setPropertyString(legacyCustomButtonsVersionProperty, "")
    }
  }

  private fun deleteCustomButtonsScriptFiles(activePaths: Set<String> = emptySet()) {
    runCatching {
      val activeNames = activePaths.map { File(it).name }.toSet()
      val scriptsDir = File(host.context.filesDir, "scripts")
      customButtonScriptTargets.forEach { target ->
        val file = File(scriptsDir, target.fileName)
        if (file.exists() && file.name !in activeNames) {
          file.delete()
        }
      }
    }
  }

  private fun buildCustomButtonsScript(
    body: String,
    version: String,
    target: CustomButtonScriptTarget,
  ): String =
    when (target.language) {
      CustomButtonScriptLanguage.LUA ->
        buildString {
          appendLine("local loaded_flag_property = '${target.loadedFlagProperty.toScriptLiteral()}'")
          appendLine("local version_property = '${target.versionProperty.toScriptLiteral()}'")
          appendLine("local instance_version = '${version.toScriptLiteral()}'")
          appendLine("if mp.get_property_native(version_property) == instance_version then")
          appendLine("    mp.set_property_native(loaded_flag_property, '1')")
          appendLine("    return")
          appendLine("end")
          appendLine("mp.set_property_native(version_property, instance_version)")
          appendLine("mp.set_property_native(loaded_flag_property, '1')")
          appendLine("local function is_active_instance()")
          appendLine("    return mp.get_property_native(version_property) == instance_version")
          appendLine("end")
          appendLine()
          append(body)
        }

      CustomButtonScriptLanguage.JS ->
        buildString {
          appendLine("var loadedFlagProperty = '${target.loadedFlagProperty.toScriptLiteral()}';")
          appendLine("var versionProperty = '${target.versionProperty.toScriptLiteral()}';")
          appendLine("var instanceVersion = '${version.toScriptLiteral()}';")
          appendLine("if (mp.get_property_native(versionProperty) === instanceVersion) {")
          appendLine("    mp.set_property_native(loadedFlagProperty, '1');")
          appendLine("} else {")
          appendLine("    mp.set_property_native(versionProperty, instanceVersion);")
          appendLine("    mp.set_property_native(loadedFlagProperty, '1');")
          appendLine("    var isActiveInstance = function() {")
          appendLine("        return mp.get_property_native(versionProperty) === instanceVersion;")
          appendLine("    };")
          appendLine()
          append(body.prependIndent("    "))
          appendLine()
          appendLine("}")
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

  private fun processButton(
    originalId: String,
    safeId: String,
    label: String,
    command: String,
    longPressCommand: String,
    onStartup: String,
    language: CustomButtonScriptLanguage,
    scriptBuilder: StringBuilder,
    isLeft: Boolean,
    uiList: MutableList<CustomButtonState>
  ) {
    if (label.isNotBlank()) {
      uiList.add(CustomButtonState(originalId, label, isLeft))

      // On Startup Code
      if (onStartup.isNotBlank()) {
        scriptBuilder.append(onStartup)
        scriptBuilder.append("\n")
      }

      // Click Handler
      if (command.isNotBlank()) {
        scriptBuilder.appendButtonHandler(
          functionName = "button_$safeId",
          messageName = "call_button_$safeId",
          command = command,
          language = language,
        )
      }

      // Long Press Handler
      if (longPressCommand.isNotBlank()) {
        scriptBuilder.appendButtonHandler(
          functionName = "button_long_$safeId",
          messageName = "call_button_long_$safeId",
          command = longPressCommand,
          language = language,
        )
      }
    }

  }

  private fun StringBuilder.appendButtonHandler(
    functionName: String,
    messageName: String,
    command: String,
    language: CustomButtonScriptLanguage,
  ) {
    when (language) {
      CustomButtonScriptLanguage.LUA -> {
        append(
          """
          function $functionName()
              if not is_active_instance() then return end
              $command
          end
          mp.register_script_message('$messageName', $functionName)
          """.trimIndent()
        )
      }
      CustomButtonScriptLanguage.JS -> {
        append(
          """
          var $functionName = function() {
              if (!isActiveInstance()) return;
              $command
          };
          mp.register_script_message('$messageName', $functionName);
          """.trimIndent()
        )
      }
    }
    append("\n")
  }

  private fun String.toScriptLiteral(): String =
    replace("\\", "\\\\").replace("'", "\\'")

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
    const val PLAYLIST_METADATA_PREFETCH_RADIUS = 40
    const val PLAYLIST_METADATA_PREFETCH_LIMIT = 120
    const val INTRO_MARKER_CACHE_PREFS = "intro_marker_cache"
    const val INTRO_MARKER_CACHE_PREFIX = "intro_marker:"
    const val INTRO_MARKER_CACHE_MAX_ENTRIES = 200
    const val INTRO_MARKER_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    const val INTRO_MARKER_CACHE_LOADED = "loaded"
    const val INTRO_MARKER_CACHE_NO_SEGMENTS = "no_segments"
    const val INTRO_MARKER_CACHE_UNRESOLVED = "unresolved"
    val VALID_SUBTITLE_EXTENSIONS =
      setOf(
        // Common & modern
        "srt", "vtt", "ass", "ssa",
        // DVD / Blu-ray
        "sub", "idx", "sup",
        // Streaming / XML / Professional
        "xml", "ttml", "dfxp", "itt", "ebu", "imsc", "usf",
        // Online platforms
        "sbv", "srv1", "srv2", "srv3", "json",
        // Legacy & niche
        "sami", "smi", "mpl", "pjs", "stl", "rt", "psb", "cap",
        // Broadcast captions
        "scc", "vttx",
        // Karaoke / lyrics
        "lrc", "krc",
        // Fallback / raw text
        "txt", "pgs"
      )
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
      subtitleAddMutex.withLock {
        val uriString = uri.toString()
        if (_externalSubtitles.contains(uriString)) {
          android.util.Log.d("PlayerViewModel", "Subtitle already tracked, skipping: $uriString")
          return@withLock
        }

        runCatching {
          val fileName = getFileNameFromUri(uri) ?: "subtitle.srt"

          if (!isValidSubtitleFile(fileName)) {
            return@withLock withContext(Dispatchers.Main) {
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
  }

  private var translationJob: Job? = null

  fun translateSubtitle(track: TrackNode, targetLanguage: String) {
    val externalPath = track.externalFilename ?: return
    val uriString = mpvPathToUriMap[externalPath] ?: externalPath
    
    // Convert file path to proper URI if needed
    val uri = if (uriString.startsWith("/")) {
      File(uriString).toUri()
    } else {
      Uri.parse(uriString)
    }

    translationJob?.cancel()
    translationJob = viewModelScope.launch(Dispatchers.IO) {
      _isTranslatingSub.value = true
      _translatingTrackId.value = track.id
      _translatingTrackName.value = getFileNameFromUri(uri)?.let { it.substringBeforeLast(".") }?.lowercase() ?: "subtitle"
      _translationProgress.value = 0f
      _translationStatus.value = "Preparing translation"

      try {
        val content = host.context.contentResolver.openInputStream(uri)?.use {
          it.readBytes().decodeToString()
        } ?: throw Exception("Could not read subtitle file")

        val originalFileName = getFileNameFromUri(uri) ?: "subtitle.srt"
        val extension = originalFileName.substringAfterLast('.', "srt")

        val result = aiService.translateSubtitle(content, targetLanguage, extension) { progress ->
          _translationProgress.value = progress.progress
          _translationStatus.value = buildString {
            append(if (progress.isResuming) "Resuming" else "Translating")
            append(" ${progress.completedChunks}/${progress.totalChunks}")
          }
        }

        result.onSuccess { translatedContent ->
          val baseName = originalFileName.substringBeforeLast(".")
          val newFileName = "${baseName}.${targetLanguage}.AI.${extension}"

          val savedUri = saveTranslatedSubtitle(uri, newFileName, extension, targetLanguage, translatedContent)
            ?: throw Exception("Could not save translated subtitle")

          withContext(Dispatchers.Main) {
            addSubtitle(savedUri, select = true)
            showToast("Translation complete: $newFileName")
          }
        }.onFailure { error ->
          withContext(Dispatchers.Main) {
            showToast("Translation failed: ${error.message}")
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          showToast("Error: ${e.message}")
        }
      } finally {
        _isTranslatingSub.value = false
        _translatingTrackId.value = null
        _translatingTrackName.value = ""
        _translationProgress.value = 0f
        _translationStatus.value = ""
        translationJob = null
      }
    }
  }

  fun cancelTranslation() {
    translationJob?.cancel()
    translationJob = null
    _isTranslatingSub.value = false
    _translatingTrackId.value = null
    _translatingTrackName.value = ""
    _translationProgress.value = 0f
    _translationStatus.value = ""
    val cacheDir = java.io.File(host.context.filesDir, "ai_translation_cache")
    if (cacheDir.exists()) {
      cacheDir.listFiles()?.forEach { it.delete() }
      cacheDir.delete()
    }
    showToast("Translation cancelled")
  }

  fun generateSubtitles(language: String, outputFormat: String = "srt") {
    val videoUri = currentVideoUriForSubtitleGeneration()
    if (videoUri == null) {
      showToast("Could not find current video path")
      return
    }

    val actualLanguage = if (language.isBlank()) aiPreferences.sttLanguage.get().ifBlank { "en" } else language
    val actualFormat = if (outputFormat.isBlank()) aiPreferences.subtitleGenerationOutputFormat.get() else outputFormat

    viewModelScope.launch(Dispatchers.IO) {
      _isGeneratingSubtitles.value = true
      _subtitleGenerationProgress.value = 0f
      _subtitleGenerationStatus.value = "Preparing audio"

      try {
        val result = subtitleGenerationService.generateSubtitles(
          videoUri = videoUri,
          language = actualLanguage,
          outputFormat = actualFormat,
        ) { progress ->
          _subtitleGenerationProgress.value = progress.progress
          _subtitleGenerationStatus.value = progress.stage
        }

        result.onSuccess { generated ->
          val baseName = currentMediaTitle.substringBeforeLast(".").ifBlank { "generated-subtitles" }
          val newFileName = "${baseName}.${language}.AI.${generated.extension}"
          val savedUri = saveTranslatedSubtitle(videoUri, newFileName, generated.extension, language, generated.content)
            ?: throw Exception("Could not save generated subtitles")
          withContext(Dispatchers.Main) {
            addSubtitle(savedUri, select = true)
            showToast("Generated subtitles: $newFileName")
          }
        }.onFailure { error ->
          withContext(Dispatchers.Main) {
            showToast("Subtitle generation failed: ${error.message}")
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          showToast("Subtitle generation error: ${e.message}")
        }
      } finally {
        _isGeneratingSubtitles.value = false
        _subtitleGenerationProgress.value = 0f
        _subtitleGenerationStatus.value = ""
      }
    }
  }

  private fun currentVideoUriForSubtitleGeneration(): Uri? {
    val media = host.currentMediaLookupHint()?.takeIf { it.isNotBlank() } ?: return null
    return if (media.startsWith("/")) File(media).toUri() else Uri.parse(media)
  }

  fun startRealtimeSubtitles(language: String) {
    val videoUri = currentVideoUriForSubtitleGeneration()
    if (videoUri == null) {
      showToast("Could not find current video path")
      return
    }
    val videoDurationMs = (_preciseDuration.value * 1000f).toLong()
    if (videoDurationMs <= 0) {
      showToast("Video duration unknown")
      return
    }

    realtimeSrtFile = java.io.File.createTempFile("realtime_subs_", ".srt", host.context.cacheDir)

    _isRealtimeSubsActive.value = true
    _realtimeSubsLanguage.value = language
    _realtimeSubsProgress.value = 0f

    realtimeSubtitleService.start(
      videoUri = videoUri,
      videoDurationMs = videoDurationMs,
      language = language,
      scope = viewModelScope,
      onProgress = { progress ->
        _realtimeSubsProgress.value = progress.chunkIndex.toFloat() / progress.totalChunks.coerceAtLeast(1)
        _translationStatus.value = "Chunk ${progress.chunkIndex + 1}/${progress.totalChunks}"
      },
      onNewContent = { srtContent ->
        realtimeSrtFile?.writeText(srtContent)
        val srtPath = realtimeSrtFile?.absolutePath ?: return@start
        if (realtimeSrtFileAdded) {
          MPVLib.command("sub-reload", srtPath)
        } else {
          MPVLib.command("sub-add", srtPath, "select")
          realtimeSrtFileAdded = true
        }
      },
      onComplete = {
        _isRealtimeSubsActive.value = false
        _realtimeSubsLanguage.value = ""
        _realtimeSubsProgress.value = 0f
        _translationStatus.value = ""
        realtimeSrtFile = null
        showToast("Real-time subtitles complete")
      },
      onError = { error ->
        _isRealtimeSubsActive.value = false
        _realtimeSubsLanguage.value = ""
        _realtimeSubsProgress.value = 0f
        _translationStatus.value = ""
        showToast("Real-time subtitles error: $error")
      },
    )
  }

  fun stopRealtimeSubtitles() {
    realtimeSubtitleService.stop()
    _isRealtimeSubsActive.value = false
    _realtimeSubsLanguage.value = ""
    _realtimeSubsProgress.value = 0f
    _translationStatus.value = ""
    realtimeSrtFile?.delete()
    realtimeSrtFile = null
    realtimeSrtFileAdded = false
    showToast("Real-time subtitles stopped")
  }

  private var realtimeSrtFileAdded = false

  private fun saveTranslatedSubtitle(
    originalUri: Uri,
    newFileName: String,
    extension: String,
    targetLanguage: String,
    translatedContent: String,
  ): Uri? {
    if (originalUri.scheme == "file") {
      val parent = originalUri.path?.let { File(it).parentFile }
      if (parent?.exists() == true) {
        val saved = File(parent, newFileName).also { it.writeText(translatedContent) }.toUri()
        // Clean up any misnamed file (just language.AI.ext) in the same directory
        val misnamed = File(parent, "${targetLanguage}.AI.${extension}")
        if (misnamed.exists() && misnamed.name != newFileName) misnamed.delete()
        return saved
      }
    }

    if (originalUri.scheme == "content") {
      val sourceDocument = DocumentFile.fromSingleUri(host.context, originalUri)
      val parentDocument = sourceDocument?.parentFile
      if (parentDocument?.canWrite() == true) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
          ?: "text/plain"
        val targetDocument = parentDocument.findFile(newFileName)
          ?: parentDocument.createFile(mimeType, newFileName)

        if (targetDocument != null) {
          host.context.contentResolver.openOutputStream(targetDocument.uri)?.use { output ->
            output.write(translatedContent.toByteArray())
          }
          // Clean up any misnamed file in the same directory
          parentDocument.findFile("${targetLanguage}.AI.${extension}")?.let { misnamed ->
            if (misnamed.uri != targetDocument.uri) misnamed.delete()
          }
          return targetDocument.uri
        }
      }
    }

    val fallbackDir = host.context.getExternalFilesDir(null) ?: host.context.filesDir
    val backupFile = File(File(fallbackDir, "Subtitles"), newFileName).apply {
      parentFile?.mkdirs()
    }
    backupFile.writeText(translatedContent)
    // Clean up any misnamed file in fallback dir
    val misnamed = File(File(fallbackDir, "Subtitles"), "${targetLanguage}.AI.${extension}")
    if (misnamed.exists() && misnamed.name != newFileName) misnamed.delete()
    return backupFile.toUri()
  }

  private fun scanLocalSubtitles(mediaTitle: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val saveFolderUri = subtitlesPreferences.subtitleSaveFolder.get()
      if (saveFolderUri.isBlank()) return@launch

      var addedCount = 0
      try {
        val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
        val fullTitle = mediaTitle.substringBeforeLast(".")
        val checksumTitle = ChecksumUtils.getCRC32(mediaTitle)
        val parentDirs = resolveSubtitleLookupDirectories(host.context, saveFolderUri)
        if (parentDirs.isEmpty()) return@launch

        // Scan potential folder names for compatibility: checksum, full, and sanitized
        // Use seenUris so the same file found via multiple folder name variants isn't double-added
        val seenUris = mutableSetOf<String>()
        parentDirs.forEach { parentDir ->
          listOf(checksumTitle, fullTitle, sanitizedTitle).distinct().forEach { folderName ->
            val movieDir = parentDir.findFile(folderName) ?: return@forEach
            if (movieDir.isDirectory) {
              movieDir.listFiles().forEach { file ->
                val uriStr = file.uri.toString()
                if (file.isFile && isValidSubtitleFile(file.name ?: "") && seenUris.add(uriStr)) {
                  // Don't auto-select during scan, just make available.
                  addSubtitle(file.uri, select = false, silent = true)
                  addedCount++
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error scanning local subtitles: ${e.message}", e)
      }

      // Auto-select the first external subtitle if the video has no embedded subtitle track active
      if (addedCount > 0) {
        // Give MPV time to register the sub-add commands
        kotlinx.coroutines.delay(300)
        val activeSid = getTrackSelectionId("sid")
        if (activeSid == 0) {
          val firstExternal = subtitleTracks.value.firstOrNull { it.external == true }
          if (firstExternal != null) {
            runCatching { setTrackSelectionId("sid", firstExternal.id) }
          }
        }
      }
    }
  }

  fun setMediaTitle(mediaTitle: String) {
    if (currentMediaTitle != mediaTitle) {
      currentMediaTitle = mediaTitle
      lastAutoSelectedMediaTitle = null
      introLookupJob?.cancel()
      videoHashJob?.cancel()
      // Clear external subtitles when media changes
      _externalSubtitles.clear()
      // Reset subtitle hash when media changes.
      _videoHash.value = null
      // Scan for previously downloaded/added subtitles
      scanLocalSubtitles(mediaTitle)

      // Restore persisted aspect mode, while zoom and pan continue to reset per file.
      restoreSavedVideoAspect(showUpdate = false)
      skippedSegmentTypes.clear()
      chapterDerivedSegments = emptyList()
      introDbSegments = emptyList()
      _skipSegments.value = emptyList()
      skipSegmentsSnapshot = emptyList()
      _currentSkippableSegment.value = null
      _introDbStatus.value =
        if (playerPreferences.enableIntroDb.get()) {
          IntroDbStatus()
        } else {
          IntroDbStatus(
            state = IntroDbStatusState.DISABLED,
            message = "Online skip markers are disabled",
          )
        }
      lookupIntroSegments(mediaTitle)

      // 2. Reset Video Zoom
      if (_videoZoom.value != 0f) {
          _videoZoom.value = 0f
          runCatching { MPVLib.setPropertyDouble("video-zoom", 0.0) }
      }

      // 3. Reset Video Pan
      if (_videoPanX.value != 0f || _videoPanY.value != 0f) {
          _videoPanX.value = 0f
          _videoPanY.value = 0f
          runCatching {
              MPVLib.setPropertyDouble("video-pan-x", 0.0)
              MPVLib.setPropertyDouble("video-pan-y", 0.0)
          }
      }
      // ---------------------------------------------------
    }
  }

  private fun maybeAutoSkipIntro(positionSeconds: Double) {
    val activeSegment =
      skipSegmentsSnapshot.firstOrNull { segment ->
        positionSeconds in segment.startSeconds..segment.endSeconds && (segment.endSeconds - positionSeconds) >= 1.0
      }
    runCatching {
      _currentSkippableSegment.value = activeSegment
    }

    if (paused == true || activeSegment == null) return
    if (skippedSegmentTypes.contains(activeSegment.type)) return
    val autoSkipEnabled =
      when (activeSegment.type) {
        SkipSegmentType.INTRO -> playerPreferences.autoSkipIntro.get()
        SkipSegmentType.RECAP -> playerPreferences.autoSkipIntro.get()
        SkipSegmentType.OUTRO -> playerPreferences.autoSkipOutro.get()
        SkipSegmentType.CREDITS -> playerPreferences.autoSkipOutro.get()
        SkipSegmentType.PREVIEW -> playerPreferences.autoSkipOutro.get()
      }
    if (!autoSkipEnabled) return

    skippedSegmentTypes += activeSegment.type
    MPVLib.setPropertyDouble("time-pos", activeSegment.endSeconds)
    showToast("${activeSegment.label} (auto)")
  }

  fun skipActiveSegment() {
    val segment = _currentSkippableSegment.value ?: return
    skippedSegmentTypes += segment.type
    MPVLib.setPropertyDouble("time-pos", segment.endSeconds)
    showToast("${segment.label}")
  }

  private fun mergeSkipSegments() {
    val merged =
      (resolveIntroDbSegments() + chapterDerivedSegments)
        .filter { it.isValid }
        .sortedBy { it.startSeconds }
        .distinctBy { Triple(it.type, it.startSeconds.toInt(), it.endSeconds.toInt()) }
    skipSegmentsSnapshot = merged
    _skipSegments.value = merged
  }

  private fun currentDurationSeconds(): Double =
    (_preciseDuration.value.takeIf { it > 0f } ?: (duration ?: 0).toFloat()).toDouble()

  private fun resolveIntroDbSegments(): List<SkipSegment> {
    val durationSec = currentDurationSeconds()
    return introDbSegments.mapNotNull { it.toSkipSegment(durationSec) }
  }

  private fun lookupIntroSegments(mediaTitle: String) {
    if (!playerPreferences.enableIntroDb.get()) {
      introDbSegments = emptyList()
      mergeSkipSegments()
      return
    }

    val lookupKey = mediaTitle
    val provider = playerPreferences.introSegmentProvider.get()
    val lookupHints = host.currentPlayerLookupHints()
    val lookupRequest =
      IntroDbLookupRequest(
        mediaTitle = mediaTitle,
        canonicalTitle = lookupHints.canonicalTitle,
        lookupHint = host.currentMediaLookupHint(),
        imdbId = lookupHints.imdbId,
        tmdbId = lookupHints.tmdbId,
        mediaType = lookupHints.mediaType,
        season = lookupHints.season,
        episode = lookupHints.episode,
        provider = provider,
      )
    val cacheKey = buildIntroMarkerCacheKey(lookupRequest)

    introDbSourceKey = provider.sourceKey
    readIntroMarkerCacheEntry(cacheKey)?.let { cachedEntry ->
      applyIntroMarkerCacheEntry(provider, cachedEntry)
      mergeSkipSegments()
      playerUpdate.value = PlayerUpdates.ShowText(_introDbStatus.value.message)
      return
    }
    _introDbStatus.value =
      IntroDbStatus(
        state = IntroDbStatusState.LOOKING_UP,
        message = "${provider.displayName}: matching title",
      )

    introLookupJob?.cancel()
    introLookupJob =
      viewModelScope.launch {
        val outcome = introDbRepository.lookupSegments(lookupRequest)
        if (currentMediaTitle != lookupKey) return@launch

        applyIntroDbOutcome(outcome)
        cacheIntroDbOutcome(cacheKey, outcome)
        mergeSkipSegments()
        playerUpdate.value = PlayerUpdates.ShowText(_introDbStatus.value.message)
      }
  }

  private fun buildIntroMarkerCacheKey(request: IntroDbLookupRequest): String =
    buildString {
      append(request.provider.sourceKey)
      append('|')
      append(request.lookupHint.orEmpty())
      append('|')
      append(request.mediaTitle)
      append('|')
      append(request.canonicalTitle.orEmpty())
      append('|')
      append(request.imdbId.orEmpty())
      append('|')
      append(request.tmdbId?.toString().orEmpty())
      append('|')
      append(request.mediaType.orEmpty())
      append('|')
      append(request.season?.toString().orEmpty())
      append('|')
      append(request.episode?.toString().orEmpty())
    }.md5()

  private fun readIntroMarkerCacheEntry(
    cacheKey: String,
  ): IntroMarkerCacheEntry? {
    val prefKey = INTRO_MARKER_CACHE_PREFIX + cacheKey
    val rawValue = introMarkerCachePrefs.getString(prefKey, null) ?: return null
    val entry =
      runCatching { json.decodeFromString<IntroMarkerCacheEntry>(rawValue) }
        .getOrElse {
          introMarkerCachePrefs.edit().remove(prefKey).apply()
          return null
        }

    if ((System.currentTimeMillis() - entry.cachedAtMs) > INTRO_MARKER_CACHE_TTL_MS) {
      introMarkerCachePrefs.edit().remove(prefKey).apply()
      return null
    }

    return entry
  }

  private fun cacheIntroDbOutcome(
    cacheKey: String,
    outcome: IntroDbLookupOutcome,
  ) {
    val cacheEntry =
      when (outcome) {
        is IntroDbLookupOutcome.Loaded ->
          IntroMarkerCacheEntry(
            providerSourceKey = outcome.provider.sourceKey,
            outcomeType = INTRO_MARKER_CACHE_LOADED,
            imdbId = outcome.imdbId,
            message = outcome.message,
            segments = outcome.segments,
          )

        is IntroDbLookupOutcome.NoSegments ->
          IntroMarkerCacheEntry(
            providerSourceKey = outcome.provider.sourceKey,
            outcomeType = INTRO_MARKER_CACHE_NO_SEGMENTS,
            imdbId = outcome.imdbId,
            message = outcome.message,
          )

        is IntroDbLookupOutcome.Unresolved ->
          IntroMarkerCacheEntry(
            providerSourceKey = outcome.provider.sourceKey,
            outcomeType = INTRO_MARKER_CACHE_UNRESOLVED,
            message = outcome.message,
          )

        is IntroDbLookupOutcome.Error -> null
      } ?: return

    introMarkerCachePrefs
      .edit()
      .putString(
        INTRO_MARKER_CACHE_PREFIX + cacheKey,
        json.encodeToString(IntroMarkerCacheEntry.serializer(), cacheEntry),
      ).apply()
    trimIntroMarkerCache()
  }

  private fun trimIntroMarkerCache() {
    val cacheEntries =
      introMarkerCachePrefs.all
        .mapNotNull { (key, value) ->
          if (!key.startsWith(INTRO_MARKER_CACHE_PREFIX) || value !is String) {
            return@mapNotNull null
          }
          val entry =
            runCatching { json.decodeFromString<IntroMarkerCacheEntry>(value) }.getOrNull()
              ?: return@mapNotNull key to null
          key to entry
        }

    if (cacheEntries.size <= INTRO_MARKER_CACHE_MAX_ENTRIES) return

    val keysToRemove =
      cacheEntries
        .sortedBy { (_, entry) -> entry?.cachedAtMs ?: Long.MIN_VALUE }
        .take(cacheEntries.size - INTRO_MARKER_CACHE_MAX_ENTRIES)
        .map { it.first }

    introMarkerCachePrefs.edit().apply {
      keysToRemove.forEach(::remove)
    }.apply()
  }

  private fun applyIntroMarkerCacheEntry(
    provider: IntroSegmentProvider,
    entry: IntroMarkerCacheEntry,
  ) {
    introDbSourceKey = entry.providerSourceKey
    when (entry.outcomeType) {
      INTRO_MARKER_CACHE_LOADED -> {
        introDbSegments = entry.segments
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.LOADED,
            message = cacheStatusMessage(provider, entry.message, "loaded ${entry.segments.size} marker${if (entry.segments.size == 1) "" else "s"}"),
            imdbId = entry.imdbId,
            segmentCount = entry.segments.size,
          )
      }

      INTRO_MARKER_CACHE_NO_SEGMENTS -> {
        introDbSegments = emptyList()
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.NO_SEGMENTS,
            message = cacheStatusMessage(provider, entry.message, "no markers cached"),
            imdbId = entry.imdbId,
          )
      }

      else -> {
        introDbSegments = emptyList()
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.UNRESOLVED,
            message = cacheStatusMessage(provider, entry.message, "cached title match failed"),
          )
      }
    }
  }

  private fun applyIntroDbOutcome(outcome: IntroDbLookupOutcome) {
    when (outcome) {
      is IntroDbLookupOutcome.Loaded -> {
        introDbSegments = outcome.segments
        introDbSourceKey = outcome.provider.sourceKey
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.LOADED,
            message = outcome.message,
            imdbId = outcome.imdbId,
            segmentCount = outcome.segments.size,
          )
      }

      is IntroDbLookupOutcome.NoSegments -> {
        introDbSegments = emptyList()
        introDbSourceKey = outcome.provider.sourceKey
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.NO_SEGMENTS,
            message = outcome.message,
            imdbId = outcome.imdbId,
          )
      }

      is IntroDbLookupOutcome.Unresolved -> {
        introDbSegments = emptyList()
        introDbSourceKey = outcome.provider.sourceKey
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.UNRESOLVED,
            message = outcome.message,
          )
      }

      is IntroDbLookupOutcome.Error -> {
        introDbSegments = emptyList()
        introDbSourceKey = outcome.provider.sourceKey
        _introDbStatus.value =
          IntroDbStatus(
            state = IntroDbStatusState.ERROR,
            message = outcome.message,
          )
      }
    }
  }

  private fun cacheStatusMessage(
    provider: IntroSegmentProvider,
    message: String,
    fallback: String,
  ): String {
    val sourceMessage = message.ifBlank { "${provider.displayName}: $fallback" }
    return if (sourceMessage.contains("(cached)")) sourceMessage else "$sourceMessage (cached)"
  }

  private fun refreshChapterDerivedSegments(chapters: List<dev.vivvvek.seeker.Segment>) {
    if (!playerPreferences.detectIntroOutroFromChapters.get()) {
      chapterDerivedSegments = emptyList()
      mergeSkipSegments()
      return
    }
    val durationSec = currentDurationSeconds()
    if (durationSec <= 0.0 || chapters.isEmpty()) {
      chapterDerivedSegments = emptyList()
      mergeSkipSegments()
      return
    }

    val derived =
      chapters.mapIndexedNotNull { index, segment ->
        val type = chapterTitleToType(segment.name) ?: return@mapIndexedNotNull null
        val start = segment.start.toDouble()
        val end = chapters.getOrNull(index + 1)?.start?.toDouble() ?: durationSec
        val normalizedEnd = end.coerceAtMost(durationSec)
        if (normalizedEnd - start < 5.0) return@mapIndexedNotNull null
        val durationFraction = start / durationSec
        when (type) {
          SkipSegmentType.INTRO -> if (durationFraction > 0.5) return@mapIndexedNotNull null
          SkipSegmentType.OUTRO -> if (durationFraction < 0.4) return@mapIndexedNotNull null
          else -> {}
        }
        SkipSegment(type = type, startSeconds = start, endSeconds = normalizedEnd, source = "chapter")
      }

    chapterDerivedSegments = derived
    mergeSkipSegments()
  }

  private fun chapterTitleToType(title: String?): SkipSegmentType? {
    if (title.isNullOrBlank()) return null
    val lowered = title.lowercase()
    val normalizedLatin = lowered.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    val compactLatin = normalizedLatin.replace(" ", "")
    val compactRaw = lowered.replace(Regex("""[\s\p{Punct}・_]+"""), "")

    fun hasKeyword(keywords: List<String>): Boolean =
      keywords.any { rawKeyword ->
        val keyword = rawKeyword.lowercase()
        when {
          keyword.matches(Regex("""[a-z0-9]+""")) -> {
            normalizedLatin.contains(Regex("""(?:^|\s)${Regex.escape(keyword)}(?:\s|$)""")) ||
              (keyword.length >= 4 && compactLatin.contains(keyword))
          }
          else -> compactRaw.contains(keyword.replace(" ", ""))
        }
      }

    val hasIntro = hasKeyword(introKeywordPatterns)
    val hasRecap = hasKeyword(recapKeywordPatterns)
    val hasCredits = hasKeyword(creditsKeywordPatterns)
    val hasPreview = hasKeyword(previewKeywordPatterns)
    val hasOutro = hasKeyword(outroKeywordPatterns)
    return when {
      hasRecap -> SkipSegmentType.RECAP
      hasCredits -> SkipSegmentType.CREDITS
      hasPreview -> SkipSegmentType.PREVIEW
      hasOutro && !hasIntro -> SkipSegmentType.OUTRO
      hasIntro -> SkipSegmentType.INTRO
      else -> null
    }
  }

  private fun app.gyrolet.mpvrx.repository.IntroDbSegment.toSkipSegment(durationSec: Double): SkipSegment? {
    val loweredType = segmentType?.lowercase().orEmpty()
    val type =
      when {
        "recap" in loweredType || "summary" in loweredType -> SkipSegmentType.RECAP
        "credit" in loweredType -> SkipSegmentType.CREDITS
        "preview" in loweredType -> SkipSegmentType.PREVIEW
        "out" in loweredType || "ending" in loweredType -> SkipSegmentType.OUTRO
        else -> SkipSegmentType.INTRO
      }
    val endSeconds =
      endSecondsOrNull ?: durationSec.takeIf {
        (type == SkipSegmentType.CREDITS || type == SkipSegmentType.PREVIEW) && it > normalizedStart
      }
      ?: return null
    if (endSeconds <= normalizedStart) return null
    return SkipSegment(
      type = type,
      startSeconds = normalizedStart,
      endSeconds = endSeconds,
      source = introDbSourceKey,
    )
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

  private data class WyzieSearchPlan(
    val request: OnlineSubtitleSearchRequest?,
    val missingSelectionMessage: String? = null,
  )

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

  fun selectMedia(result: app.gyrolet.mpvrx.repository.wyzie.WyzieTmdbResult) {
    _mediaSearchResults.value = emptyList() // Clear results after selection
    _onlineSubtitleSearchResults.value = emptyList() // Clear old subtitle results
    val fileInfo = MediaInfoParser.parse(currentMediaTitle)

    if (result.mediaType == "tv") {
      fetchTvShowDetails(
        id = result.id,
        preferredSeason = fileInfo.season,
        preferredEpisode = fileInfo.episode,
      )
    } else {
      searchSubtitles(result.title, year = result.releaseYear ?: fileInfo.year, tmdbId = result.id)
    }
  }

  private fun fetchTvShowDetails(
    id: Int,
    preferredSeason: Int? = null,
    preferredEpisode: Int? = null,
  ) {
    viewModelScope.launch {
      _isFetchingTvDetails.value = true
      wyzieRepository.getTvShowDetails(id)
        .onSuccess { details ->
          val validSeasons = details.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
          _selectedTvShow.value = details.copy(seasons = validSeasons)
          _selectedSeason.value = null
          _seasonEpisodes.value = emptyList()
          val matchingSeason = preferredSeason?.let { wanted ->
            validSeasons.firstOrNull { it.season_number == wanted }
          }
          if (matchingSeason != null) {
            selectSeason(matchingSeason, preferredEpisode)
          }
        }
        .onFailure {
          showToast("Failed to load series details: ${it.message}")
        }
      _isFetchingTvDetails.value = false
    }
  }

  fun selectSeason(
    season: app.gyrolet.mpvrx.repository.wyzie.WyzieSeason,
    preferredEpisode: Int? = null,
  ) {
    val tvShowId = _selectedTvShow.value?.id ?: return
    _selectedSeason.value = season

    viewModelScope.launch {
      _isFetchingEpisodes.value = true
      wyzieRepository.getSeasonEpisodes(tvShowId, season.season_number)
        .onSuccess { episodes ->
          val validEpisodes = episodes.filter { it.episode_number > 0 }.sortedBy { it.episode_number }
          _seasonEpisodes.value = validEpisodes
          val matchingEpisode = preferredEpisode?.let { wanted ->
            validEpisodes.firstOrNull { it.episode_number == wanted }
          }
          _selectedEpisode.value = matchingEpisode
          matchingEpisode?.let { episode ->
            val tvShowName = _selectedTvShow.value?.name ?: currentMediaTitle
            searchSubtitles(
              query = tvShowName,
              season = season.season_number,
              episode = episode.episode_number,
              tmdbId = tvShowId,
            )
          }
        }
        .onFailure {
          showToast("Failed to load episodes: ${it.message}")
        }
      _isFetchingEpisodes.value = false
    }
  }

  fun selectEpisode(episode: app.gyrolet.mpvrx.repository.wyzie.WyzieEpisode) {
    _selectedEpisode.value = episode
    val tvShowName = _selectedTvShow.value?.name ?: currentMediaTitle
    searchSubtitles(tvShowName, episode.season_number, episode.episode_number, tmdbId = _selectedTvShow.value?.id)
  }

  fun clearMediaSelection() {
    _selectedTvShow.value = null
    _selectedSeason.value = null
    _seasonEpisodes.value = emptyList()
    _selectedEpisode.value = null
    _mediaSearchResults.value = emptyList()
  }

  // --- Subtitle Search ---
  fun searchOnlineSubtitles(query: String) {
    val queryInfo = MediaInfoParser.parse(query)
    val fileInfo = MediaInfoParser.parse(currentMediaTitle)
    val searchTitle = queryInfo.title.ifBlank { query.trim() }.ifBlank { fileInfo.title }
    if (searchTitle.isBlank()) return

    val mode = subtitlesPreferences.onlineSubtitleSearchMode.get()
    if (mode != OnlineSubtitleSearchMode.SUBHUB) {
      searchMedia(searchTitle)
    } else {
      _mediaSearchResults.value = emptyList()
    }

    val year = queryInfo.year ?: fileInfo.year
    val wyziePlan = buildWyzieSearchPlan(searchTitle, year, queryInfo, fileInfo)
    val includeWyzie = mode != OnlineSubtitleSearchMode.SUBHUB && wyziePlan.request != null
    val includeSubtitleHub = mode != OnlineSubtitleSearchMode.WYZIE

    if (mode == OnlineSubtitleSearchMode.WYZIE && wyziePlan.request == null) {
      wyziePlan.missingSelectionMessage?.let(::showToast)
      _onlineSubtitleSearchResults.value = emptyList()
      return
    }

    val wyzieRequest = wyziePlan.request ?: OnlineSubtitleSearchRequest(query = searchTitle, year = year)
    searchSubtitles(
      query = wyzieRequest.query,
      season = wyzieRequest.season,
      episode = wyzieRequest.episode,
      year = wyzieRequest.year,
      tmdbId = wyzieRequest.tmdbId,
      includeWyzie = includeWyzie,
      includeSubtitleHub = includeSubtitleHub,
    )
  }

  fun searchSubtitles(
    query: String,
    season: Int? = null,
    episode: Int? = null,
    year: String? = null,
    tmdbId: Int? = null,
    includeWyzie: Boolean = true,
    includeSubtitleHub: Boolean = true,
  ) {
     viewModelScope.launch {
          _isSearchingSub.value = true
          val cleanSubHubTitle = MediaInfoParser.parse(query).title.ifBlank { query.trim() }
          val wyzieRequest =
              OnlineSubtitleSearchRequest(
                  query = query,
                  tmdbId = tmdbId,
                  season = season,
                  episode = episode,
                  year = year,
                  movieHash = _videoHash.value,
              )
          val subtitleHubRequest =
              OnlineSubtitleSearchRequest(
                  query = cleanSubHubTitle,
                  year = year,
              )
          onlineSubtitleOrchestrator.search(
              wyzieRequest,
              subtitlesPreferences.onlineSubtitleSearchMode.get(),
              subtitleHubRequest = subtitleHubRequest,
              includeWyzie = includeWyzie,
              includeSubtitleHub = includeSubtitleHub,
          )
              .onSuccess { results ->
                  _onlineSubtitleSearchResults.value = results
             }
             .onFailure {
                 showToast("Search failed: ${it.message}")
             }
          _isSearchingSub.value = false
      }
   }

  private fun buildWyzieSearchPlan(
    searchTitle: String,
    year: String?,
    queryInfo: ParsedMediaInfo,
    fileInfo: ParsedMediaInfo,
  ): WyzieSearchPlan {
    val selectedShow = _selectedTvShow.value
    val selectedSeason = _selectedSeason.value?.season_number
    val selectedEpisode = _selectedEpisode.value?.episode_number

    if (selectedShow != null) {
      if (selectedSeason == null || selectedEpisode == null) {
        return WyzieSearchPlan(
          request = null,
          missingSelectionMessage = "Select season and episode for Wyzie.",
        )
      }
      return WyzieSearchPlan(
        request =
          OnlineSubtitleSearchRequest(
            query = selectedShow.name,
            tmdbId = selectedShow.id,
            season = selectedSeason,
            episode = selectedEpisode,
            year = year,
            movieHash = _videoHash.value,
          ),
      )
    }

    val detectedSeason = queryInfo.season ?: fileInfo.season
    val detectedEpisode = queryInfo.episode ?: fileInfo.episode
    if (detectedSeason != null || detectedEpisode != null) {
      return WyzieSearchPlan(
        request = null,
        missingSelectionMessage = "Select the show, season, and episode for Wyzie.",
      )
    }

    return WyzieSearchPlan(
      request =
        OnlineSubtitleSearchRequest(
          query = searchTitle,
          year = year,
          movieHash = _videoHash.value,
        ),
    )
  }

  fun downloadSubtitle(subtitle: OnlineSubtitle) {
      viewModelScope.launch {
          _isDownloadingSub.value = true
          onlineSubtitleOrchestrator.download(subtitle, currentMediaTitle)
              .onSuccess { uri ->
                  if (subtitle.isHashMatch) {
                    MPVLib.setPropertyDouble("sub-delay", 0.0)
                    Log.d(TAG, "Applied perfect-sync subtitle match for ${subtitle.displayName}")
                  }
                  addSubtitle(uri)
              }
              .onFailure {
                  showToast("Download failed: ${it.message}")
              }
          _isDownloadingSub.value = false
      }
  }


  fun toggleSubtitle(id: Int) {
    val primarySid = getTrackSelectionId("sid")
    val secondarySid = getTrackSelectionId("secondary-sid")

    when {
      id == primarySid -> setTrackSelectionId("sid", null)
      id == secondarySid -> setTrackSelectionId("secondary-sid", null)
      primarySid <= 0 -> setTrackSelectionId("sid", id)
      secondarySid <= 0 -> setTrackSelectionId("secondary-sid", id)
      else -> setTrackSelectionId("sid", id)
    }

    syncSubtitleLayout()
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val primarySid = getTrackSelectionId("sid")
    val secondarySid = getTrackSelectionId("secondary-sid")
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
    controlsVisibleForPolling = true
  }

  fun hideControls() {
    try {
      host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = false
    controlsVisibleForPolling = false
    seekBarVisibleForPolling = false
  }

  fun autoHideControls() {
    try {
      host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide system bars", e)
    }
    _controlsShown.value = false
    _seekBarShown.value = true
    controlsVisibleForPolling = false
    seekBarVisibleForPolling = true
  }

  fun showSeekBar() {
    if (sheetShown.value == Sheets.None) {
      _seekBarShown.value = true
      seekBarVisibleForPolling = true
    }
  }

  fun hideSeekBar() {
    _seekBarShown.value = false
    seekBarVisibleForPolling = false
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
      val loopA = _abLoopState.value.a
      val loopB = _abLoopState.value.b
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
    _seekState.update { s ->
      s.copy(amount = if ((pos ?: 0) > 0) s.amount - doubleTapToSeekDuration else s.amount, isForwards = false)
    }
    seekBy(-doubleTapToSeekDuration)
  }

  fun rightSeek() {
    _seekState.update { s ->
      s.copy(amount = if ((pos ?: 0) < (duration ?: 0)) s.amount + doubleTapToSeekDuration else s.amount, isForwards = true)
    }
    seekBy(doubleTapToSeekDuration)
  }

  fun updateSeekAmount(amount: Int) {
    _seekState.update { it.copy(amount = amount) }
  }

  fun updateSeekText(text: String?) {
    _seekState.update { it.copy(text = text) }
  }

  fun updateIsSeekingForwards(isForwards: Boolean) {
    _seekState.update { it.copy(isForwards = isForwards) }
  }

  private fun seekToWithText(
    seekValue: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    _seekState.value = SeekState(text = text, amount = seekValue - currentPos, isForwards = seekValue > currentPos)
    seekTo(seekValue)
  }

  private fun seekByWithText(
    value: Int,
    text: String?,
  ) {
    val currentPos = pos ?: return
    val maxDuration = duration ?: return

    _seekState.update { s ->
      val newAmount = if ((value < 0 && s.amount < 0) || currentPos + value > maxDuration) 0 else s.amount + value
      SeekState(text = text, amount = newAmount, isForwards = value > 0)
    }
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
    val currentSystemVolume = syncCurrentSystemVolume()
    val mpvVolume = MPVLib.getPropertyInt("volume") ?: 100
    val absoluteMaxVolume = volumeBoostCap ?: (audioPreferences.volumeBoostCap.get() + 100)

    if (currentSystemVolume < maxVolume && mpvVolume > 100) {
      changeMPVVolumeTo(100)
    }

    if (absoluteMaxVolume > 100 && currentSystemVolume == maxVolume) {
      if (mpvVolume == 100 && change < 0) {
        changeVolumeTo(currentSystemVolume + change)
      }
      val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
      if (finalMPVVolume in 100..absoluteMaxVolume) {
        return changeMPVVolumeTo(finalMPVVolume)
      }
    }

    changeVolumeTo(currentSystemVolume + change)
  }

  fun changeVolumePercentTo(volumePercent: Int) {
    val newPercent = volumePercent.coerceIn(0, 100)
    val newVolume = percentToSystemVolume(newPercent)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    currentVolume.value = syncCurrentSystemVolume()
    currentVolumePercent.value = newPercent

    if (currentVolume.value < maxVolume) {
      val currentMpvVolume = MPVLib.getPropertyInt("volume") ?: 100
      if (currentMpvVolume > 100) {
        changeMPVVolumeTo(100)
      }
    }
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    currentVolume.value = syncCurrentSystemVolume()

    if (currentVolume.value < maxVolume) {
      val currentMpvVolume = MPVLib.getPropertyInt("volume") ?: 100
      if (currentMpvVolume > 100) {
        changeMPVVolumeTo(100)
      }
    }
  }

  fun changeMPVVolumeTo(volume: Int) {
    MPVLib.setPropertyInt("volume", volume)
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.value = true
    volumeSliderTimestamp.value = System.currentTimeMillis()
  }

  fun changeSubtitlePositionTo(position: Int) {
    val newPosition = clampSubtitlePosition(position)
    subtitlesPreferences.subPos.set(newPosition)
    syncSubtitleLayout(newPosition)
    playerUpdate.value = PlayerUpdates.ShowText("Subtitle Position: $newPosition")
  }

  private fun syncSubtitleLayout(primaryPosition: Int = subtitlesPreferences.subPos.get()) {
    applySubtitleLayout(primaryPosition, subtitlesPreferences.overrideAssSubs.get())
  }

  private fun syncCurrentSystemVolume(): Int {
    val systemVolume = host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    currentVolume.value = systemVolume
    currentVolumePercent.value = systemVolumeToPercent(systemVolume)
    return systemVolume
  }

  fun syncCurrentVolumeState() {
    syncCurrentSystemVolume()
  }

  private fun systemVolumeToPercent(systemVolume: Int): Int {
    if (maxVolume <= 0) return 0
    return ((systemVolume.toFloat() / maxVolume.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
  }

  private fun percentToSystemVolume(volumePercent: Int): Int {
    if (maxVolume <= 0) return 0
    return ((volumePercent / 100f) * maxVolume.toFloat()).roundToInt().coerceIn(0, maxVolume)
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
        // To CROP: Reset aspect override first, then set panscan
        MPVLib.setPropertyDouble("video-aspect-override", -1.0)
        MPVLib.setPropertyDouble("panscan", 1.0)
      }
      VideoAspect.Stretch -> {
        // To STRETCH: Calculate screen ratio accounting for video rotation
        @Suppress("DEPRECATION")
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        host.hostWindowManager.defaultDisplay.getRealMetrics(dm)

        // Get video rotation from metadata
        val rotate = MPVLib.getPropertyInt("video-params/rotate") ?: 0
        val isVideoRotated = (rotate % 180 == 90) // 90° or 270° rotation

        // Calculate screen ratio, inverting if video is rotated
        val screenRatio = if (isVideoRotated) {
          // Video is rotated, so invert the screen ratio
          dm.heightPixels.toDouble() / dm.widthPixels.toDouble()
        } else {
          // Video is not rotated, use normal screen ratio
          dm.widthPixels.toDouble() / dm.heightPixels.toDouble()
        }

        // Set aspect override first, then reset panscan
        // This prevents the brief flash of Fit mode
        MPVLib.setPropertyDouble("video-aspect-override", screenRatio)
        MPVLib.setPropertyDouble("panscan", 0.0)
      }
    }

    // Update the state
    playerPreferences.lastVideoAspect.set(aspect)
    playerPreferences.lastCustomAspectRatio.set(-1f)
    _videoAspect.value = aspect
    _currentAspectRatio.value = -1.0 // Reset custom ratio when using standard modes

    // Notify the UI
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  fun setCustomAspectRatio(
    ratio: Double,
    showUpdate: Boolean = true,
  ) {
    MPVLib.setPropertyDouble("panscan", 0.0)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    playerPreferences.lastCustomAspectRatio.set(ratio.toFloat())
    _currentAspectRatio.value = ratio
    if (showUpdate) {
      playerUpdate.value = PlayerUpdates.AspectRatio
    }
  }

  fun restoreSavedVideoAspect(showUpdate: Boolean = false) {
    val customAspectRatio = playerPreferences.lastCustomAspectRatio.get()
    if (customAspectRatio > 0f) {
      setCustomAspectRatio(customAspectRatio.toDouble(), showUpdate)
      return
    }

    changeVideoAspect(playerPreferences.lastVideoAspect.get(), showUpdate)
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
            "lua_scripts" -> Panels.LuaScripts
            "hdr_screen_output" -> Panels.HdrScreenOutput
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

  fun getPlaylistData(): List<app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistItem>? {
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

      app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistItem(
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

    // Try MediaStore first (much faster - uses cached values)
    val mediaStoreMetadata = getVideoMetadataFromMediaStore(uri)
    if (mediaStoreMetadata != null) {
      return mediaStoreMetadata
    }

    // Fallback to MediaMetadataRetriever only if MediaStore fails
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
        formatDuration(durationMs.toLong())
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

  /**
   * Get video metadata from MediaStore (fast - uses cached system values).
   * Returns null if the video is not found in MediaStore.
   */
  private fun getVideoMetadataFromMediaStore(uri: Uri): Pair<String, String>? {
    return try {
      val projection = arrayOf(
        android.provider.MediaStore.Video.Media.DURATION,
        android.provider.MediaStore.Video.Media.WIDTH,
        android.provider.MediaStore.Video.Media.HEIGHT,
        android.provider.MediaStore.Video.Media.DATA
      )

      // Determine the query URI based on the input URI scheme
      val queryUri = when (uri.scheme) {
        "content" -> {
          // If it's already a content URI, use it directly
          if (uri.toString().startsWith(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
            uri
          } else {
            // Try to find by path if available
            null
          }
        }
        "file" -> {
          // For file:// URIs, query by path
          null
        }
        else -> null
      }

      // Query by URI if we have a content URI
      if (queryUri != null) {
        host.context.contentResolver.query(
          queryUri,
          projection,
          null,
          null,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val durationColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.HEIGHT)

            val durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
            val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
            val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0

            val durationStr = formatDuration(durationMs)

            val resolutionStr = if (width > 0 && height > 0) {
              "${width}x${height}"
            } else ""

            return durationStr to resolutionStr
          }
        }
      }

      // Query by file path if we have a file:// URI or content URI without direct match
      val filePath = when (uri.scheme) {
        "file" -> uri.path
        "content" -> {
          // Try to get the file path from content URI
          host.context.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.Video.Media.DATA),
            null,
            null,
            null
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val dataColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
              if (dataColumn >= 0) cursor.getString(dataColumn) else null
            } else null
          }
        }
        else -> null
      }

      if (filePath != null) {
        val selection = "${android.provider.MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        host.context.contentResolver.query(
          android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          selectionArgs,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val durationColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.HEIGHT)

            val durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
            val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
            val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0

            val durationStr = formatDuration(durationMs)

            val resolutionStr = if (width > 0 && height > 0) {
              "${width}x${height}"
            } else ""

            return durationStr to resolutionStr
          }
        }
      }

      null
    } catch (e: Exception) {
      android.util.Log.w("PlayerViewModel", "Failed to get metadata from MediaStore for $uri, will try MediaMetadataRetriever", e)
      null
    }
  }

  /**
   * Format duration in milliseconds to hh:mm:ss or mm:ss format
   */
  private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""

    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
      String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format("%d:%02d", minutes, seconds)
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
  fun refreshPlaylistItems(forceMetadata: Boolean = sheetShown.value == Sheets.Playlist) {
    viewModelScope.launch(Dispatchers.IO) {
      val updatedItems = getPlaylistData()
      if (updatedItems != null) {
        // Clear cache if playlist size changed
        if (_playlistItems.value.size != updatedItems.size) {
          metadataCache.evictAll()
        }

        _playlistItems.value = updatedItems

        if (forceMetadata) {
          // Load metadata only when the playlist sheet is actually in use.
          loadPlaylistMetadataAsync(updatedItems)
        }
      }
    }
  }

  /**
   * Loads metadata for all playlist items asynchronously in the background.
   * Updates the playlist items as metadata becomes available.
   * Uses batched updates to avoid O(n²) complexity with large playlists.
   * Skips metadata extraction for M3U playlists (network streams).
   */
  private fun loadPlaylistMetadataAsync(items: List<app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistItem>) {
    playlistMetadataJob?.cancel()
    playlistMetadataJob = viewModelScope.launch(Dispatchers.IO) {
      // Skip metadata extraction for M3U playlists
      val activity = host as? PlayerActivity
      if (activity?.isCurrentPlaylistM3U() == true) {
        Log.d(TAG, "Skipping metadata extraction for M3U playlist")
        return@launch
      }

      val metadataItems =
        activity?.let { currentActivity ->
          if (items.size <= PLAYLIST_METADATA_PREFETCH_LIMIT) {
            items
          } else {
            val currentIndex = currentActivity.playlistIndex.coerceIn(0, items.lastIndex)
            val startIndex = maxOf(0, currentIndex - PLAYLIST_METADATA_PREFETCH_RADIUS)
            val endIndex = minOf(items.lastIndex, currentIndex + PLAYLIST_METADATA_PREFETCH_RADIUS)
            items.subList(startIndex, endIndex + 1)
          }
        } ?: items

      // Limit concurrent metadata extraction to avoid overwhelming resources
      val batchSize = 5
      metadataItems.chunked(batchSize).forEach { batch ->
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
    _abLoopState.update { it.copy(isExpanded = !it.isExpanded) }
  }

  fun setLoopA() {
    if (_abLoopState.value.a != null) {
      _abLoopState.update { it.copy(a = null) }
      MPVLib.setPropertyString("ab-loop-a", "no")
      return
    }
    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopState.update { it.copy(a = currentPos) }
    MPVLib.setPropertyDouble("ab-loop-a", currentPos)
  }

  fun setLoopB() {
    if (_abLoopState.value.b != null) {
      _abLoopState.update { it.copy(b = null) }
      MPVLib.setPropertyString("ab-loop-b", "no")
      return
    }
    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: return
    _abLoopState.update { it.copy(b = currentPos) }
    MPVLib.setPropertyDouble("ab-loop-b", currentPos)
  }

  fun clearABLoop() {
    _abLoopState.update { it.copy(a = null, b = null) }
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
    val newMirrorState = !_transformState.value.isMirrored
    _transformState.update { it.copy(isMirrored = newMirrorState) }

    // Use labeled video filter for mirroring to avoid state desync
    if (newMirrorState) {
      MPVLib.command("vf", "add", "@mpvrx_hflip:hflip")
    } else {
      MPVLib.command("vf", "remove", "@mpvrx_hflip")
    }
    playerUpdate.value = PlayerUpdates.ShowText(if (newMirrorState) "H-Flip On" else "H-Flip Off")
  }

  fun toggleVerticalFlip() {
    val newState = !_transformState.value.isVerticalFlipped
    _transformState.update { it.copy(isVerticalFlipped = newState) }

    // Use labeled video filter for vflip to avoid state desync
    if (newState) {
      MPVLib.command("vf", "add", "@mpvrx_vflip:vflip")
    } else {
      MPVLib.command("vf", "remove", "@mpvrx_vflip")
    }

    playerUpdate.value = PlayerUpdates.ShowText(if (newState) "V-Flip On" else "V-Flip Off")
  }

  fun toggleHdrScreenOutput() {
    val nextMode =
      if (_hdrScreenMode.value == HdrScreenMode.OFF) HdrScreenMode.defaultEnabledMode
      else HdrScreenMode.OFF
    setHdrScreenMode(nextMode)
  }

  fun setHdrScreenMode(mode: HdrScreenMode) {
    val pipelineReady = isHdrScreenOutputAvailable(mode)
    if (mode != HdrScreenMode.OFF && !pipelineReady) {
      val message = if (mode == HdrScreenMode.LINEAR) "Linear HDR needs GPU Next + Vulkan"
                    else "HDR Screen output needs GPU Next"
      playerUpdate.value = PlayerUpdates.ShowText(message)
      applyHdrScreenOutput(HdrScreenMode.OFF)
      return
    }

    _hdrScreenMode.value = mode
    _isHdrScreenOutputEnabled.value = pipelineReady && mode != HdrScreenMode.OFF
    decoderPreferences.hdrScreenMode.set(mode)
    decoderPreferences.hdrScreenOutput.set(mode != HdrScreenMode.OFF)
    applyHdrScreenOutput(mode)
    playerUpdate.value = PlayerUpdates.ShowText("HDR Screen Output: ${mode.shortTitle}")
  }

  private fun isHdrScreenOutputAvailable(mode: HdrScreenMode = _hdrScreenMode.value): Boolean {
    val needsVulkan = mode == HdrScreenMode.LINEAR
    return if (needsVulkan) {
      decoderPreferences.useVulkan.get() && decoderPreferences.gpuNext.get()
    } else {
      decoderPreferences.gpuNext.get()
    }
  }

  private fun initialHdrScreenMode(): HdrScreenMode {
    val savedMode = decoderPreferences.hdrScreenMode.get()
    return if (savedMode == HdrScreenMode.OFF && decoderPreferences.hdrScreenOutput.get()) {
      HdrScreenMode.defaultEnabledMode
    } else {
      savedMode
    }
  }

  private fun refreshHdrScreenOutputPipelineState(): Boolean {
    val pipelineReady = isHdrScreenOutputAvailable()
    _isHdrScreenOutputPipelineReady.value = pipelineReady
    _isHdrScreenOutputEnabled.value = pipelineReady && _hdrScreenMode.value != HdrScreenMode.OFF
    return pipelineReady
  }

  private fun applyHdrScreenOutput(mode: HdrScreenMode) {
    val pipelineReady = refreshHdrScreenOutputPipelineState()
    runCatching {
      val boostSdr = decoderPreferences.boostSdrToHdr.get()
      applyHdrScreenOutputOptions(mode, pipelineReady, boostSdr)
      applyHdrScreenOutputProperties(mode, pipelineReady, boostSdr)
      applyHdrToysMode(mode, pipelineReady)
    }.onFailure { e ->
      Log.e(TAG, "Error applying HDR screen output: mode=$mode, pipelineReady=$pipelineReady", e)
    }
  }

  /** Re-applies the current HDR mode to a newly loaded video. */
  fun refreshHdrScreenOutputForCurrentVideo() {
    applyHdrScreenOutput(_hdrScreenMode.value)
  }

  /**
   * Called after Anime4K or file changes so HDR remains layered with the rest of
   * the shader stack, then the ambient shader is moved back to the final pass.
   */
  fun restartHdrScreenOutputAndAmbientIfActive() {
    refreshHdrScreenOutputForCurrentVideo()
    restartAmbientIfActive()
  }

  private fun applyHdrToysMode(mode: HdrScreenMode, pipelineReady: Boolean) {
    val profile = mode.hdrToysProfile
    if (!pipelineReady || profile == null) {
      hdrToysManager.clear()
      return
    }
    if (!hdrToysManager.apply(profile)) {
      playerUpdate.value = PlayerUpdates.ShowText("HDR Toys shaders unavailable")
    }
  }

  // ==================== Ambient Mode Integration ====================

  fun toggleAmbientMode() {
    _isAmbientEnabled.value = !_isAmbientEnabled.value
    playerPreferences.isAmbientEnabled.set(_isAmbientEnabled.value)
    if (_isAmbientEnabled.value) {
      lastAmbientScaleX = -1.0 // Force rewrite
      updateAmbientStretch()
      playerUpdate.value = PlayerUpdates.ShowText("Ambience Mode: ON")
    } else {
      disableAmbientShader()
      playerUpdate.value = PlayerUpdates.ShowText("Ambience Mode: OFF")
    }
  }

  /** Disables the ambient shader and resets video scale. Safe to call from any state. */
  private fun disableAmbientShader() {
    ambientDebounceJob?.cancel()
    ambientShaderFile?.let { file ->
      runCatching { MPVLib.command("change-list", "glsl-shaders", "remove", file.absolutePath) }
      file.delete()
    }
    ambientShaderFile = null
    // Reset the shader cache and scale tracking so a subsequent enable always
    // compiles a fresh shader and recalculates the correct video-scale offsets.
    lastCompiledShaderCode = null
    lastAmbientScaleX = -1.0
    lastAmbientScaleY = -1.0
    runCatching {
      MPVLib.setPropertyDouble("video-scale-x", 1.0)
      MPVLib.setPropertyDouble("video-scale-y", 1.0)
      MPVLib.setPropertyString("blend-subtitles", "no")
    }
  }

  /** Called when the device orientation changes. Refreshes ambient in both portrait and landscape. */
  fun onOrientationChanged(isPortrait: Boolean) {
    if (!_isAmbientEnabled.value) return

    // Force shader refresh to adapt to new screen dimensions.
    lastAmbientScaleX = -1.0
    lastAmbientScaleY = -1.0
    ambientDebounceJob?.cancel()
    ambientDebounceJob = viewModelScope.launch(renderPrepDispatcher) {
      delay(200)
      updateAmbientStretch()
    }
  }

  /** Removes the old file-specific ambient shader while preserving the user's selected ambient mode. */
  fun prepareAmbientForNewVideo() {
    if (!_isAmbientEnabled.value) return
    disableAmbientShader()
    lastAmbientScaleX = -1.0
    lastAmbientScaleY = -1.0
  }

  /**
   * Re-injects the ambient shader if ambient mode is currently ON.
   * Called after shader-stack changes so ambient stays as the last OUTPUT pass.
   */
  fun restartAmbientIfActive() {
    if (!_isAmbientEnabled.value) return
    ambientShaderFile?.let { oldFile ->
      runCatching { MPVLib.command("change-list", "glsl-shaders", "remove", oldFile.absolutePath) }
      oldFile.delete()
    }
    ambientShaderFile = null
    lastAmbientScaleX = -1.0         // Force scale recalculation
    lastAmbientScaleY = -1.0
    lastCompiledShaderCode = null    // Invalidate cache — the old file is gone, must recompile
    // Small delay to let Anime4K shaders settle
    ambientDebounceJob?.cancel()
    ambientDebounceJob = viewModelScope.launch(renderPrepDispatcher) {
      delay(200)
      updateAmbientStretch()
    }
  }

  fun updateAmbientVisualMode(mode: AmbientVisualMode) {
    if (_ambientVisualMode.value == mode) return

    _ambientVisualMode.value = mode
    playerPreferences.ambientVisualMode.set(mode)

    if (_isAmbientEnabled.value) {
      playerUpdate.value = PlayerUpdates.ShowText("Ambient Style: ${mode.label}")
      scheduleAmbientUpdate(75)
    }
  }

  fun updateAmbientParams(
    blurSamples: Int = _ambientBlurSamples.value,
    maxRadius: Float = _ambientMaxRadius.value,
    glowIntensity: Float = _ambientGlowIntensity.value,
    satBoost: Float = _ambientSatBoost.value,
    ditherNoise: Float = _ambientDitherNoise.value,
    bezelDepth: Float = _ambientBezelDepth.value,
    vignetteStrength: Float = _ambientVignetteStrength.value,
    warmth: Float = _ambientWarmth.value,
    fadeCurve: Float = _ambientFadeCurve.value,
    opacity: Float = _ambientOpacity.value
  ) {
    _ambientBlurSamples.value = blurSamples
    _ambientMaxRadius.value = maxRadius
    _ambientGlowIntensity.value = glowIntensity
    _ambientSatBoost.value = satBoost
    _ambientDitherNoise.value = ditherNoise
    _ambientBezelDepth.value = bezelDepth
    _ambientVignetteStrength.value = vignetteStrength
    _ambientWarmth.value = warmth
    _ambientFadeCurve.value = fadeCurve
    _ambientOpacity.value = opacity

    // Persist to preferences
    playerPreferences.ambientBlurSamples.set(blurSamples)
    playerPreferences.ambientMaxRadius.set(maxRadius)
    playerPreferences.ambientGlowIntensity.set(glowIntensity)
    playerPreferences.ambientSatBoost.set(satBoost)
    playerPreferences.ambientDitherNoise.set(ditherNoise)
    playerPreferences.ambientBezelDepth.set(bezelDepth)
    playerPreferences.ambientVignetteStrength.set(vignetteStrength)
    playerPreferences.ambientWarmth.set(warmth)
    playerPreferences.ambientFadeCurve.set(fadeCurve)
    playerPreferences.ambientOpacity.set(opacity)

    scheduleAmbientUpdate()
  }

  /** Fast profile — low GPU cost, still visually solid. */
  fun updateFrameExtendParams(
    extendStrength: Float = _frameExtendStrength.value,
    detailProtection: Float = _frameExtendDetailProtection.value,
    glowMix: Float = _frameExtendGlowMix.value,
    ditherNoise: Float = _ambientDitherNoise.value,
  ) {
    _frameExtendStrength.value = extendStrength
    _frameExtendDetailProtection.value = detailProtection
    _frameExtendGlowMix.value = glowMix
    _ambientDitherNoise.value = ditherNoise

    playerPreferences.ambientExtendStrength.set(extendStrength)
    playerPreferences.ambientExtendDetailProtection.set(detailProtection)
    playerPreferences.ambientExtendGlowMix.set(glowMix)
    playerPreferences.ambientDitherNoise.set(ditherNoise)

    scheduleAmbientUpdate()
  }

  private fun scheduleAmbientUpdate(delayMs: Long = 150L) {
    if (!_isAmbientEnabled.value) return

    ambientDebounceJob?.cancel()
    ambientDebounceJob = viewModelScope.launch(renderPrepDispatcher) {
      delay(delayMs)
      updateAmbientStretch()
    }
  }

  private fun setAmbientSampleBudget(sampleBudget: Int) {
    _ambientBlurSamples.value = sampleBudget
    playerPreferences.ambientBlurSamples.set(sampleBudget)
  }

  private fun applyFrameExtendPreset(preset: AmbientFrameExtendPreset) {
    setAmbientSampleBudget(preset.sampleBudget)

    _frameExtendStrength.value = preset.extendStrength
    _frameExtendDetailProtection.value = preset.detailProtection
    _frameExtendGlowMix.value = preset.glowMix
    _ambientDitherNoise.value = preset.ditherNoise
    playerPreferences.ambientExtendStrength.set(preset.extendStrength)
    playerPreferences.ambientExtendDetailProtection.set(preset.detailProtection)
    playerPreferences.ambientExtendGlowMix.set(preset.glowMix)
    playerPreferences.ambientDitherNoise.set(preset.ditherNoise)

    _ambientBezelDepth.value = preset.bezelDepth
    _ambientVignetteStrength.value = preset.vignetteStrength
    _ambientOpacity.value = preset.opacity
    playerPreferences.ambientBezelDepth.set(preset.bezelDepth)
    playerPreferences.ambientVignetteStrength.set(preset.vignetteStrength)
    playerPreferences.ambientOpacity.set(preset.opacity)

    scheduleAmbientUpdate()
  }

  fun applyAmbientProfileFast() {
    when (_ambientVisualMode.value) {
      AmbientVisualMode.GLOW -> {
        val preset = AmbientShaderPresets.glowFast
        updateAmbientParams(
          blurSamples = preset.blurSamples,
          maxRadius = preset.maxRadius,
          glowIntensity = preset.glowIntensity,
          satBoost = preset.satBoost,
          vignetteStrength = preset.vignetteStrength,
          warmth = preset.warmth,
          fadeCurve = preset.fadeCurve,
          opacity = preset.opacity,
        )
      }
      AmbientVisualMode.FRAME_EXTEND -> applyFrameExtendPreset(AmbientShaderPresets.frameExtendFast)
    }
  }

  /** Balanced profile — good quality/performance trade-off for most devices. */
  fun applyAmbientProfileBalanced() {
    when (_ambientVisualMode.value) {
      AmbientVisualMode.GLOW -> {
        val preset = AmbientShaderPresets.glowBalanced
        updateAmbientParams(
          blurSamples = preset.blurSamples,
          maxRadius = preset.maxRadius,
          glowIntensity = preset.glowIntensity,
          satBoost = preset.satBoost,
          vignetteStrength = preset.vignetteStrength,
          warmth = preset.warmth,
          fadeCurve = preset.fadeCurve,
          opacity = preset.opacity,
        )
      }
      AmbientVisualMode.FRAME_EXTEND -> applyFrameExtendPreset(AmbientShaderPresets.frameExtendBalanced)
    }
  }

  /** High Quality profile — maximum visual fidelity for high-end devices. */
  fun applyAmbientProfileHighQuality() {
    when (_ambientVisualMode.value) {
      AmbientVisualMode.GLOW -> {
        val preset = AmbientShaderPresets.glowHighQuality
        updateAmbientParams(
          blurSamples = preset.blurSamples,
          maxRadius = preset.maxRadius,
          glowIntensity = preset.glowIntensity,
          satBoost = preset.satBoost,
          vignetteStrength = preset.vignetteStrength,
          warmth = preset.warmth,
          fadeCurve = preset.fadeCurve,
          opacity = preset.opacity,
        )
      }
      AmbientVisualMode.FRAME_EXTEND -> applyFrameExtendPreset(AmbientShaderPresets.frameExtendHighQuality)
    }
  }

  fun updateAmbientStretch() {
    if (!_isAmbientEnabled.value) return

    runCatching {
      val osdW = MPVLib.getPropertyInt("osd-width") ?: 1920
      val osdH = MPVLib.getPropertyInt("osd-height") ?: 1080

      // Portrait mode: ambient glow goes on top/bottom (letterbox)
      // Landscape mode: ambient glow goes on left/right (pillarbox)
      // Both are handled by the same scaleX/scaleY math below

      var vidW = (MPVLib.getPropertyInt("video-params/w") ?: 1920).toDouble()
      var vidH = (MPVLib.getPropertyInt("video-params/h") ?: 1080).toDouble()
      val par  = MPVLib.getPropertyDouble("video-params/par") ?: 1.0
      val rot  = MPVLib.getPropertyInt("video-params/rotate") ?: 0

      // Intercept autocrop boundaries — if a crop is active, use the cropped dimensions
      // so the shader's aspect-ratio math matches the actual visible video area
      val crop = MPVLib.getPropertyString("video-crop") ?: ""
      val cropMatch = ambientCropRegex.find(crop)
      if (cropMatch != null) {
        vidW = cropMatch.groupValues[1].toDouble()
        vidH = cropMatch.groupValues[2].toDouble()
      }

      if (osdW <= 0 || osdH <= 0 || vidW <= 0.0 || vidH <= 0.0) return

      // Apply pixel aspect ratio (non-square pixels)
      vidW *= par
      // Swap dimensions for 90°/270° rotated videos (portrait shot stored as landscape)
      if (rot == 90 || rot == 270) { val tmp = vidW; vidW = vidH; vidH = tmp }

      val screenAr = osdW.toDouble() / osdH.toDouble()
      val vidAr    = vidW / vidH

      // Scale the video to fill the screen — the shader remaps it back to the
      // correct aspect ratio, so only the "overflow" area receives ambient glow.
      val scaleX = if (screenAr > vidAr) screenAr / vidAr else 1.0
      val scaleY = if (vidAr > screenAr) vidAr / screenAr else 1.0

      if (Math.abs(scaleX - lastAmbientScaleX) > 0.001 ||
          Math.abs(scaleY - lastAmbientScaleY) > 0.001) {
        lastAmbientScaleX = scaleX
        lastAmbientScaleY = scaleY
        MPVLib.setPropertyDouble("video-scale-x", scaleX)
        MPVLib.setPropertyDouble("video-scale-y", scaleY)
      }
      val blendMode = if (subtitlesPreferences.blendSubtitlesWithVideo.get()) "video" else "no"
      MPVLib.setPropertyString("blend-subtitles", blendMode)

      // ── Snapshot current parameter values ─────────────────────────────────
      val sx      = lastAmbientScaleX
      val sy      = lastAmbientScaleY
      // Thermal-aware sample budget: cap shader complexity before the device enters
      // hard CPU/GPU throttling.  On a cool device this is a no-op.
      val rawSamples = _ambientBlurSamples.value
      val samples = ThermalMonitor.clampAmbientSampleBudget(rawSamples, thermalHeadroom)
      val radius  = _ambientMaxRadius.value
      val glow    = _ambientGlowIntensity.value
      val sat     = _ambientSatBoost.value
      val dither  = _ambientDitherNoise.value
      val bezel   = _ambientBezelDepth.value
      val vignette= _ambientVignetteStrength.value
      val warmth  = _ambientWarmth.value
      val curve   = _ambientFadeCurve.value
      val opacity = _ambientOpacity.value

      // ── Generate GLSL shader ───────────────────────────────────────────────
      val shaderCode = buildAmbientShader(
        sx = sx, sy = sy,
        blurSamples = samples, maxRadius = radius,
        glowIntensity = glow, satBoost = sat,
        ditherNoise = dither, bezelDepth = bezel,
        vignetteStrength = vignette, warmth = warmth,
        fadeCurve = curve, opacity = opacity
      )

      // ── Shader parameter cache ─────────────────────────────────────────────
      // If every baked-in #define is identical to the last compiled shader AND the
      // shader file still exists on disk, skip the remove+write+reload cycle.
      // This prevents redundant GPU shader recompilation on no-op refreshes (e.g.
      // orientation callbacks that fire with unchanged video dimensions, or thermal
      // monitor ticks that don't change the effective sample budget).
      if (shaderCode == lastCompiledShaderCode && ambientShaderFile?.exists() == true) {
        return
      }
      lastCompiledShaderCode = shaderCode

      // Each reload gets a unique filename so MPV never reuses a cached
      // compiled shader — incrementing seq guarantees a fresh compile every time.
      val newFile = File(host.context.cacheDir, "ambient_${++ambientShaderSeq}.glsl")
      newFile.writeText(shaderCode)
      ambientShaderFile?.let { oldFile ->
        runCatching { MPVLib.command("change-list", "glsl-shaders", "remove", oldFile.absolutePath) }
        oldFile.delete()
      }
      MPVLib.command("change-list", "glsl-shaders", "append", newFile.absolutePath)
      ambientShaderFile = newFile
    }.onFailure { e ->
      Log.e(TAG, "Failed to update ambient stretch", e)
    }
  }

  /**
   * Builds the True Ambient GLSL shader string with all parameters baked in
   * as `#define` constants. The shader:
   *   1. Detects the video region using aspect-ratio correction (SCALE_X/Y).
   *   2. For interior pixels — returns the original (unscaled) video pixel.
   *   3. For ambient pixels — samples the nearest video-edge with a
   *      Fibonacci-spiral blur kernel and composites the glowing result.
   */
  private fun buildAmbientShader(
    sx: Double, sy: Double,
    blurSamples: Int, maxRadius: Float,
    glowIntensity: Float, satBoost: Float,
    ditherNoise: Float, bezelDepth: Float,
    vignetteStrength: Float, warmth: Float,
    fadeCurve: Float, opacity: Float
  ): String {
    val context = AmbientRenderContext(scaleX = sx, scaleY = sy)
    val shared =
      AmbientSharedShaderConfig(
        bezelDepth = if (_ambientVisualMode.value == AmbientVisualMode.FRAME_EXTEND) bezelDepth else 0f,
        vignetteStrength = vignetteStrength,
        opacity = opacity,
      )

    val spec: AmbientShaderSpec =
      when (_ambientVisualMode.value) {
        AmbientVisualMode.GLOW ->
          AmbientGlowShaderSpec(
            context = context,
            shared = shared,
            blurSamples = blurSamples,
            maxRadius = maxRadius,
            glowIntensity = glowIntensity,
            satBoost = satBoost,
            warmth = warmth,
            fadeCurve = fadeCurve,
          )
        AmbientVisualMode.FRAME_EXTEND ->
          AmbientFrameExtendShaderSpec(
            context = context,
            shared = shared,
            sampleBudget = blurSamples,
            extendStrength = _frameExtendStrength.value,
            detailProtection = _frameExtendDetailProtection.value,
            glowMix = _frameExtendGlowMix.value,
            ditherNoise = ditherNoise,
          )
      }

    return AmbientShaderBuilder.build(spec)
  }

  @Suppress("unused")
  private fun buildLegacyAmbientShader(
    sx: Double, sy: Double,
    blurSamples: Int, maxRadius: Float,
    glowIntensity: Float, satBoost: Float,
    ditherNoise: Float, bezelDepth: Float,
    vignetteStrength: Float, warmth: Float,
    fadeCurve: Float, opacity: Float
  ): String = """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC True Ambient Mode

// ─────────────────────────────────────────────────────────────────────────────
// CONFIGURATION  (all values injected at runtime — do not hand-edit)
// ─────────────────────────────────────────────────────────────────────────────

// Blur quality: number of spiral samples (higher = smoother, more GPU cost)
#define BLUR_SAMPLES     $blurSamples

// Maximum blur spread radius in normalised UV coordinates
#define MAX_RADIUS       $maxRadius

// Ambient brightness multiplier (1.0 = neutral)
#define GLOW_INTENSITY   $glowIntensity

// Saturation boost applied to the ambient glow (1.0 = neutral)
#define SAT_BOOST        $satBoost

// Width of the soft blend zone at the video edge (0 = hard cut)
#define BEZEL_DEPTH      $bezelDepth

// Anti-banding dither noise amplitude
#define DITHER_NOISE     $ditherNoise

// Corner vignette strength (0.0 = none, 1.0 = full darkening)
#define VIGNETTE_STR     $vignetteStrength

// Color temperature shift  (-1.0 = cooler/blue, 0.0 = neutral, +1.0 = warmer/orange)
#define WARMTH           $warmth

// Distance falloff power  (1.0 = linear, 2.0 = quadratic, higher = tighter glow)
#define FADE_CURVE       $fadeCurve

// Overall ambient opacity multiplier
#define OPACITY          $opacity

// Aspect-ratio correction factors derived from video / screen dimensions
#define SCALE_X          $sx
#define SCALE_Y          $sy

// ─────────────────────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────────────────────

const float PI  = 3.14159265358979;
const float PHI = 1.61803398874989;   // Golden ratio — drives Fibonacci spiral

// ─────────────────────────────────────────────────────────────────────────────
// UTILITY FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

// Hash-based pseudo-random scalar in [0, 1]
float rand(vec2 seed) {
    return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
}

// BT.709 perceptual luminance
float luma(vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

// Luma-preserving saturation adjustment
vec3 adjust_saturation(vec3 rgb, float amount) {
    return mix(vec3(luma(rgb)), rgb, amount);
}

// Kelvin-style warm / cool color temperature shift
vec3 apply_warmth(vec3 rgb, float amount) {
    rgb.r = clamp(rgb.r + amount * 0.060,  0.0, 1.0);
    rgb.g = clamp(rgb.g + amount * 0.025,  0.0, 1.0);
    rgb.b = clamp(rgb.b - amount * 0.080,  0.0, 1.0);
    return rgb;
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN HOOK
// ─────────────────────────────────────────────────────────────────────────────

vec4 hook() {
    // Current pixel position in normalised screen space [0, 1]
    vec2 uv = HOOKED_pos;

    // Remap screen UV → original (pre-scale) video UV space.
    // Pixels outside [0, 1] × [0, 1] are in the letterbox / pillarbox region.
    vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // ── Hard boundary: return video pixel directly — zero ambient bleeds in ──
    if (video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
        video_uv.y >= 0.0 && video_uv.y <= 1.0) {
        return HOOKED_tex(video_uv);
    }

    // ── Ambient region: compute edge-directed glow ────────────────────────────

    // Nearest point on the video border (clamped to [0, 1])
    vec2  edge_origin = clamp(video_uv, 0.0, 1.0);

    // Euclidean distance from this pixel to the video edge (used for fade-out).
    // Constant 3.0 gives ~22 % brightness at half-radius and ~5 % at full-radius —
    // visible glow across the whole pillarbox / letterbox area.
    float edge_dist   = length(video_uv - edge_origin);
    float edge_fade   = exp(-edge_dist * (3.0 / max(MAX_RADIUS, 0.001)));

    // Per-pixel rotation jitter to avoid banding in the spiral pattern
    float jitter    = rand(uv * HOOKED_size) * (PI * 2.0);
    float angle_inc = PI * 2.0 / (PHI * PHI);  // ~2.399 rad — golden angle
    float inv_n     = 1.0 / float(BLUR_SAMPLES);

    // Aspect correction keeps the blur kernel circular in screen space
    vec2 aspect_fix = vec2(HOOKED_size.y / HOOKED_size.x, 1.0);

    vec3  acc_color  = vec3(0.0);
    float acc_weight = 0.0;

    // ── Fibonacci-spiral blur kernel ──────────────────────────────────────────
    for (int i = 0; i < BLUR_SAMPLES; i++) {
        float fi    = float(i) + 0.5;
        float r     = sqrt(fi * inv_n) * MAX_RADIUS;
        float theta = fi * angle_inc + jitter;

        // Sample from the nearest video-edge origin, spreading outward.
        // This ensures ambient colours come from the actual video edge.
        vec2 offset     = vec2(cos(theta), sin(theta)) * r * aspect_fix;
        vec2 sample_uv  = clamp(edge_origin + offset, 0.0, 1.0);
        vec3 sample_rgb = HOOKED_tex(sample_uv).rgb;

        // Weight = distance falloff × luminance bloom
        // (brighter video pixels contribute more to the glow)
        float dist_w = pow(max(1.0 / (1.0 + r * 40.0), 0.0), FADE_CURVE);
        float luma_w = 1.0 + luma(sample_rgb) * 2.0;
        float w      = dist_w * luma_w;

        acc_color  += sample_rgb * w;
        acc_weight += w;
    }

    // Normalise and apply global brightness
    vec3 glow = (acc_color / max(acc_weight, 1e-5)) * GLOW_INTENSITY;

    // ── Post-processing ───────────────────────────────────────────────────────

    glow = adjust_saturation(glow, SAT_BOOST);
    glow = apply_warmth(glow, WARMTH);

    // Fade the glow out as distance from the video edge increases
    glow *= edge_fade;

    // Radial vignette: corners receive less ambient light
    float vig_r = length(uv - 0.5) * 2.0;
    glow *= mix(1.0, smoothstep(1.3, 0.1, vig_r), VIGNETTE_STR);

    // Dither noise — breaks up colour banding in the gradient
    float noise = rand(uv + vec2(fract(uv.x * 127.1), fract(uv.y * 311.7)));
    glow = clamp(glow + DITHER_NOISE * (noise - 0.5), 0.0, 1.0);

    // ── Compositing ───────────────────────────────────────────────────────────
    // Bezel blend: transition from the nearest video-edge pixel outward into
    // the ambient glow. BEZEL_DEPTH is in video-UV units; the blend lives
    // entirely in the ambient region so no glow ever bleeds into the video.
    float bezel       = max(BEZEL_DEPTH, 0.001);
    vec2  outside_dist = max(max(-video_uv, video_uv - vec2(1.0)), vec2(0.0));
    float dist_to_edge = max(outside_dist.x, outside_dist.y);
    float bezel_alpha  = smoothstep(0.0, bezel, dist_to_edge);

    // At dist=0 (right at edge): show the video edge pixel → seamless join.
    // At dist≥bezel: show full ambient glow.
    // OPACITY scales only rgb; alpha stays 1.0 so the output stays opaque.
    vec4 edge_pixel  = HOOKED_tex(edge_origin);
    vec4 ambient_out = vec4(glow * OPACITY, 1.0);

    return mix(edge_pixel, ambient_out, bezel_alpha);
}
  """.trimIndent()

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

private fun String.md5(): String {
  val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
  return digest.joinToString("") { byte -> "%02x".format(byte) }
}
