package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.FrameNavigationCard
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.FrameNavigationCardTitle
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FrameNavigationSheet(
  currentFrame: Int,
  totalFrames: Int,
  onUpdateFrameInfo: () -> Unit,
  onPause: () -> Unit,
  onUnpause: () -> Unit,
  onPauseUnpause: () -> Unit,
  onSeekTo: (Int, Boolean) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isSnapshotLoading by remember { mutableStateOf(false) }
  var isFrameStepping by remember { mutableStateOf(false) }

  // Use rememberUpdatedState for lambda parameters used in effects
  val currentOnPause by rememberUpdatedState(onPause)
  val currentOnUnpause by rememberUpdatedState(onUnpause)
  val currentOnUpdateFrameInfo by rememberUpdatedState(onUpdateFrameInfo)

  // Use the same logic as PlayerControls for pause state
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val isPaused = paused ?: false

  // Use the same logic as PlayerControls for position and duration
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val pos = position ?: 0
  val dur = duration ?: 0

  // Format timestamp based on current position
  val timestamp =
    remember(pos) {
      val currentPos = pos
      val hours = currentPos / 3600
      val minutes = (currentPos % 3600) / 60
      val seconds = currentPos % 60
      String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

  // Pause playback when the sheet opens
  LaunchedEffect(Unit) {
    currentOnPause()
  }

  // Continuously update frame info as video plays
  LaunchedEffect(Unit) {
    while (true) {
      currentOnUpdateFrameInfo()
      kotlinx.coroutines.delay(100L)
    }
  }

  // Always resume playback when closing the sheet
  DisposableEffect(Unit) {
    onDispose {
      currentOnUnpause()
    }
  }

  PlayerSheet(onDismissRequest = onDismissRequest) {
    FrameNavigationCard(
      onPreviousFrame = {
        if (!isFrameStepping) {
          isFrameStepping = true
          coroutineScope.launch {
            // Pause if not already paused
            if (!isPaused) {
              currentOnPause()
              kotlinx.coroutines.delay(50)
            }
            MPVLib.command("no-osd", "frame-back-step")
            kotlinx.coroutines.delay(100)
            currentOnUpdateFrameInfo()
            isFrameStepping = false
          }
        }
      },
      onNextFrame = {
        if (!isFrameStepping) {
          isFrameStepping = true
          coroutineScope.launch {
            // Pause if not already paused
            if (!isPaused) {
              currentOnPause()
              kotlinx.coroutines.delay(50)
            }
            MPVLib.command("no-osd", "frame-step")
            kotlinx.coroutines.delay(100)
            currentOnUpdateFrameInfo()
            isFrameStepping = false
          }
        }
      },
      onPlayPause = {
        coroutineScope.launch {
          onPauseUnpause()
        }
      },
      isPaused = isPaused,
      onSnapshot = {
        coroutineScope.launch {
          isSnapshotLoading = true
          app.marlboroadvance.mpvex.ui.player.controls.components.panels.takeSnapshot(
            context,
          )
          isSnapshotLoading = false
        }
      },
      isSnapshotLoading = isSnapshotLoading,
      isFrameStepping = isFrameStepping,
      currentFrame = currentFrame,
      totalFrames = totalFrames,
      timestamp = timestamp,
      duration = dur.toFloat(),
      pos = pos.toFloat(),
      onSeekTo = onSeekTo,
      title = { FrameNavigationCardTitle(onClose = onDismissRequest) },
      modifier = modifier,
    )
  }
}
