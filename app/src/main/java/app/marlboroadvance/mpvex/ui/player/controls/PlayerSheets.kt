package app.marlboroadvance.mpvex.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.Decoder
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.TrackNode
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.AspectRatioSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.AudioTracksSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.ChaptersSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.DecodersSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.MoreSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaybackSpeedSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.SubtitlesSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.sheets.VideoZoomSheet
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState as composeCollectAsState

@Composable
fun PlayerSheets(
  sheetShown: Sheets,
  viewModel: app.marlboroadvance.mpvex.ui.player.PlayerViewModel,
  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri) -> Unit,
  onSelectSubtitle: (Int) -> Unit,
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
  onDismissRequest: () -> Unit,
) {
  val externalSubtitleMetadata by viewModel.externalSubtitleMetadata.composeCollectAsState()

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
      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onSelect = onSelectSubtitle,
        onAddSubtitle = {
          subtitlesPicker.launch(
            arrayOf(
              "text/plain",
              "text/srt",
              "text/vtt",
              "application/x-subrip",
              "application/x-subtitle",
              "text/x-ssa",
              "*/*", // Fallback for systems that don't recognize subtitle MIME types
            ),
          )
        },
        onRemoveSubtitle = onRemoveSubtitle,
        onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
        onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
        onDismissRequest = onDismissRequest,
        externalSubtitleMetadata = externalSubtitleMetadata,
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
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.AspectRatios -> {
      val playerPreferences = koinInject<app.marlboroadvance.mpvex.preferences.PlayerPreferences>()
      val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
      val currentRatio by playerPreferences.currentAspectRatio.collectAsState()
      val customRatios =
        customRatiosSet.mapNotNull { str ->
          val parts = str.split("|")
          if (parts.size == 2) {
            app.marlboroadvance.mpvex.ui.player.controls.components.sheets.AspectRatio(
              label = parts[0],
              ratio = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
              isCustom = true,
            )
          } else {
            null
          }
        }

      app.marlboroadvance.mpvex.ui.player.controls.components.sheets.AspectRatioSheet(
        currentRatio = currentRatio.toDouble(),
        customRatios = customRatios,
        onSelectRatio = { ratio ->
          viewModel.setCustomAspectRatio(ratio)
        },
        onAddCustomRatio = { label, ratio ->
          playerPreferences.customAspectRatios.set(customRatiosSet + "$label|$ratio")
          viewModel.setCustomAspectRatio(ratio)
        },
        onDeleteCustomRatio = { ratio ->
          val toRemove = "${ratio.label}|${ratio.ratio}"
          playerPreferences.customAspectRatios.set(customRatiosSet - toRemove)
          // If the deleted ratio is currently active, reset to default
          if (kotlin.math.abs(currentRatio - ratio.ratio) < 0.01) {
            viewModel.setCustomAspectRatio(-1.0)
          }
        },
        onDismissRequest = onDismissRequest,
      )
    }
  }
}
