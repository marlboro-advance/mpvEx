package app.gyrolet.mpvrx.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.player.Decoder
import app.gyrolet.mpvrx.ui.player.Panels
import app.gyrolet.mpvrx.ui.player.Sheets
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.AspectRatioSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.AudioTracksSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.ChaptersSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.DecodersSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.FrameNavigationSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.MoreSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaybackSpeedSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.PlaylistSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.SubtitlesSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.OnlineSubtitleSearchSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.VideoZoomSheet
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.AmbientSheet
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState as composeCollectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PlayerSheets(
  sheetShown: Sheets,
  viewModel: app.gyrolet.mpvrx.ui.player.PlayerViewModel,
  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri) -> Unit,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  onRemoveSubtitle: (Int) -> Unit,
  // audio sheet
  audioTracks: ImmutableList<TrackNode>,
  onAddAudio: (Uri) -> Unit,
  onSelectAudio: (TrackNode) -> Unit,
  // chapters sheet
  chapter: Segment?,
  chapters: ImmutableList<Segment>,
  onSeekToChapter: (Int) -> Unit,
  // Decoders sheet
  decoder: Decoder,
  onUpdateDecoder: (Decoder) -> Unit,
  // Speed sheet
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetSpeedPresets: () -> Unit,
  onMakeDefaultSpeed: (Float) -> Unit,
  onResetDefaultSpeed: () -> Unit,
  // More sheet
  sleepTimerTimeRemaining: Int,
  onStartSleepTimer: (Int) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  onShowSheet: (Sheets) -> Unit,
  onDismissRequest: () -> Unit,
) {
  when (sheetShown) {
    Sheets.None -> {}
    Sheets.SubtitleTracks -> {
      val subtitlesPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddSubtitle(it)
        }

      val subtitlesPreferences = koinInject<app.gyrolet.mpvrx.preferences.SubtitlesPreferences>()
      val savedPickerPath = subtitlesPreferences.pickerPath.get()

      val currentMediaTitle = viewModel.currentMediaTitle
      val matchToName = if (currentMediaTitle.isNotBlank()) {
          // Remove extension if present to improve matching
          currentMediaTitle.substringBeforeLast(".")
      } else null

      var showFilePicker by remember { mutableStateOf(false) }

      if (showFilePicker) {
          app.gyrolet.mpvrx.ui.browser.dialogs.FilePickerDialog(
              isOpen = true,
              currentPath = savedPickerPath,
              onDismiss = { showFilePicker = false },
              onPathChanged = { path ->
                  if (path != null) {
                      subtitlesPreferences.pickerPath.set(path)
                  }
              },
              onFileSelected = { path ->
                  showFilePicker = false
                   onAddSubtitle(Uri.parse("file://$path"))
              },
              onSystemPickerRequest = {
                  showFilePicker = false
                  subtitlesPicker.launch(
                    arrayOf(
                      "text/plain",
                      "text/srt",
                      "text/vtt",
                      "application/x-subrip",
                      "application/x-subtitle",
                      "text/x-ssa",
                      "*/*",
                    ),
                  )
              },
              matchToName = matchToName
          )
      }

      val isTranslating by viewModel.isTranslatingSub.composeCollectAsState()
      val translationProgress by viewModel.translationProgress.composeCollectAsState()
      val translationStatus by viewModel.translationStatus.composeCollectAsState()
      val translatingTrackId by viewModel.translatingTrackId.composeCollectAsState()
      val translatingTrackName by viewModel.translatingTrackName.composeCollectAsState()
      val isGeneratingSubtitles by viewModel.isGeneratingSubtitles.composeCollectAsState()
      val subtitleGenerationProgress by viewModel.subtitleGenerationProgress.composeCollectAsState()
      val subtitleGenerationStatus by viewModel.subtitleGenerationStatus.composeCollectAsState()
      val aiPreferences = koinInject<app.gyrolet.mpvrx.preferences.AiPreferences>()
      val translationEnabled by aiPreferences.subtitleTranslationEnabled.collectAsState()
      val provider by aiPreferences.provider.collectAsState()
      val autoTranslateLanguages by aiPreferences.autoTranslateLanguages.collectAsState()

      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onToggleSubtitle = onToggleSubtitle,
        isSubtitleSelected = isSubtitleSelected,
        onAddSubtitle = { showFilePicker = true },
        onRemoveSubtitle = onRemoveSubtitle,
        onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
        onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
        onOpenOnlineSearch = { onShowSheet(Sheets.OnlineSubtitleSearch) },
        onDismissRequest = onDismissRequest,
        onTranslateSubtitle = { track, lang -> viewModel.translateSubtitle(track, lang) },
        onGenerateSubtitle = { viewModel.generateSubtitles("", "") },
        onCancelTranslation = { viewModel.cancelTranslation() },
        isTranslating = isTranslating,
        translationProgress = translationProgress,
        translationStatus = translationStatus,
        translationEnabled = translationEnabled,
        isGeneratingSubtitles = isGeneratingSubtitles,
        subtitleGenerationProgress = subtitleGenerationProgress,
        subtitleGenerationStatus = subtitleGenerationStatus,
        translatingTrackId = translatingTrackId,
        translatingTrackName = translatingTrackName,
        provider = provider,
        autoTranslateLanguages = autoTranslateLanguages,
      )
    }

    Sheets.OnlineSubtitleSearch -> {
      val isSearching by viewModel.isSearchingSub.composeCollectAsState()
      val isDownloading by viewModel.isDownloadingSub.composeCollectAsState()
      val results by viewModel.onlineSubtitleSearchResults.composeCollectAsState()
      val isOnlineSectionExpanded by viewModel.isOnlineSectionExpanded.composeCollectAsState()
      val subtitlesPreferences = koinInject<app.gyrolet.mpvrx.preferences.SubtitlesPreferences>()
      val subtitleSearchMode by subtitlesPreferences.onlineSubtitleSearchMode.collectAsState()

      // Media Search / Autocomplete
      val mediaResults by viewModel.mediaSearchResults.composeCollectAsState()
      val isSearchingMedia by viewModel.isSearchingMedia.composeCollectAsState()
      
      // TV Show / Seasons / Episodes
      val selectedTvShow by viewModel.selectedTvShow.composeCollectAsState()
      val isFetchingTvDetails by viewModel.isFetchingTvDetails.composeCollectAsState()
      val selectedSeason by viewModel.selectedSeason.composeCollectAsState()
      val seasonEpisodes by viewModel.seasonEpisodes.composeCollectAsState()
      val isFetchingEpisodes by viewModel.isFetchingEpisodes.composeCollectAsState()
      val selectedEpisode by viewModel.selectedEpisode.composeCollectAsState()

      OnlineSubtitleSearchSheet(
        onDismissRequest = onDismissRequest,
        onDownloadOnline = { viewModel.downloadSubtitle(it) },
        isSearching = isSearching,
        isDownloading = isDownloading,
        searchResults = results.toImmutableList(),
        isOnlineSectionExpanded = isOnlineSectionExpanded,
        onToggleOnlineSection = { viewModel.toggleOnlineSection() },
        mediaTitle = viewModel.currentMediaTitle,
        showWyzieSelection = subtitleSearchMode != app.gyrolet.mpvrx.repository.subtitle.OnlineSubtitleSearchMode.SUBHUB,
        // Autocomplete & Series Selection
        mediaSearchResults = mediaResults.toImmutableList(),
        isSearchingMedia = isSearchingMedia,
        onSearchMedia = viewModel::searchOnlineSubtitles,
        onSelectMedia = { viewModel.selectMedia(it) },
        selectedTvShow = selectedTvShow,
        isFetchingTvDetails = isFetchingTvDetails,
        selectedSeason = selectedSeason,
        onSelectSeason = { viewModel.selectSeason(it) },
        seasonEpisodes = seasonEpisodes.toImmutableList(),
        isFetchingEpisodes = isFetchingEpisodes,
        selectedEpisode = selectedEpisode,
        onSelectEpisode = { viewModel.selectEpisode(it) },
        onClearMediaSelection = { viewModel.clearMediaSelection() }
      )
    }

    Sheets.AudioTracks -> {
      val audioPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddAudio(it)
        }
      AudioTracksSheet(
        tracks = audioTracks,
        onSelect = onSelectAudio,
        onAddAudioTrack = { audioPicker.launch(arrayOf("*/*")) },
        onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
        onDismissRequest,
      )
    }

    Sheets.Chapters -> {
      if (chapter == null) return
      ChaptersSheet(
        chapters,
        currentChapter = chapter,
        onClick = { onSeekToChapter(chapters.indexOf(it)) },
        onDismissRequest,
      )
    }

    Sheets.Decoders -> {
      DecodersSheet(
        selectedDecoder = decoder,
        onSelect = onUpdateDecoder,
        onDismissRequest,
      )
    }

    Sheets.More -> {
      MoreSheet(
        remainingTime = sleepTimerTimeRemaining,
        onStartTimer = onStartSleepTimer,
        onDismissRequest = onDismissRequest,
        onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
        onEnterLuaScriptsPanel = { onOpenPanel(Panels.LuaScripts) },
        onAnime4KChanged = viewModel::restartHdrScreenOutputAndAmbientIfActive,
      )
    }

    Sheets.PlaybackSpeed -> {
      PlaybackSpeedSheet(
        speed,
        onSpeedChange = onSpeedChange,
        speedPresets = speedPresets,
        onAddSpeedPreset = onAddSpeedPreset,
        onRemoveSpeedPreset = onRemoveSpeedPreset,
        onResetPresets = onResetSpeedPresets,
        onMakeDefault = onMakeDefaultSpeed,
        onResetDefault = onResetDefaultSpeed,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.VideoZoom -> {
      val videoZoom by viewModel.videoZoom.composeCollectAsState()
      VideoZoomSheet(
        videoZoom = videoZoom,
        onSetVideoZoom = viewModel::setVideoZoom,
        onResetVideoPan = viewModel::resetVideoPan,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.AspectRatios -> {
      val playerPreferences = koinInject<app.gyrolet.mpvrx.preferences.PlayerPreferences>()
      val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
      val currentRatio by viewModel.currentAspectRatio.composeCollectAsState()
      val customRatios =
        customRatiosSet.mapNotNull { str ->
          val parts = str.split("|")
          if (parts.size == 2) {
            app.gyrolet.mpvrx.ui.player.controls.components.sheets.AspectRatio(
              label = parts[0],
              ratio = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
              isCustom = true,
            )
          } else {
            null
          }
        }

      AspectRatioSheet(
        currentRatio = currentRatio,
        customRatios = customRatios,
        onSelectRatio = { ratio ->
          if (ratio < 0) {
            // Default selected - apply Fit mode
            viewModel.changeVideoAspect(app.gyrolet.mpvrx.ui.player.VideoAspect.Fit)
          } else {
            // Custom ratio selected
            viewModel.setCustomAspectRatio(ratio)
          }
        },
        onAddCustomRatio = { label, ratio ->
          playerPreferences.customAspectRatios.set(customRatiosSet + "$label|$ratio")
          viewModel.setCustomAspectRatio(ratio)
        },
        onDeleteCustomRatio = { ratio ->
          val toRemove = "${ratio.label}|${ratio.ratio}"
          playerPreferences.customAspectRatios.set(customRatiosSet - toRemove)
          // If the deleted ratio is currently active, reset to default (Fit)
          if (kotlin.math.abs(currentRatio - ratio.ratio) < 0.01) {
            viewModel.changeVideoAspect(app.gyrolet.mpvrx.ui.player.VideoAspect.Fit)
          }
        },
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.FrameNavigation -> {
      val currentFrame by viewModel.currentFrame.composeCollectAsState()
      val totalFrames by viewModel.totalFrames.composeCollectAsState()
      FrameNavigationSheet(
        currentFrame = currentFrame,
        totalFrames = totalFrames,
        onUpdateFrameInfo = viewModel::updateFrameInfo,
        onPause = viewModel::pause,
        onUnpause = viewModel::unpause,
        onPauseUnpause = viewModel::pauseUnpause,
        onSeekTo = { position, _ -> viewModel.seekTo(position) },
        onDismissRequest = onDismissRequest,
      )
    }


    Sheets.Playlist -> {
      // Refresh playlist items when sheet is shown
      LaunchedEffect(Unit) {
        viewModel.refreshPlaylistItems()
      }

      // Observe playlist updates
      val playlist by viewModel.playlistItems.collectAsState()
      val playerPreferences = koinInject<app.gyrolet.mpvrx.preferences.PlayerPreferences>()

      if (playlist.isNotEmpty()) {
        val playlistImmutable = playlist.toImmutableList()
        val totalCount = viewModel.getPlaylistTotalCount()
        val isM3U = viewModel.isPlaylistM3U()
        PlaylistSheet(
          playlist = playlistImmutable,
          onDismissRequest = onDismissRequest,
          onItemClick = { item ->
            viewModel.playPlaylistItem(item.index)
          },
          totalCount = totalCount,
          isM3UPlaylist = isM3U,
          playerPreferences = playerPreferences,
        )
      }
    }

    Sheets.AmbientConfig -> {
      AmbientSheet(
        viewModel = viewModel,
        onDismissRequest = onDismissRequest
      )
    }
  }
}
