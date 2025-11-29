package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.LeftSideOvalShape
import app.marlboroadvance.mpvex.presentation.components.RightSideOvalShape
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerUpdates
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.controls.components.DoubleTapSeekTriangles
import app.marlboroadvance.mpvex.ui.theme.playerRippleConfiguration
import org.koin.compose.koinInject
import kotlin.math.abs

@Suppress("CyclomaticComplexMethod", "MultipleEmitters")
@Composable
fun GestureHandler(
  viewModel: PlayerViewModel,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  val panelShown by viewModel.panelShown.collectAsState()
  val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
  var isDoubleTapSeeking by remember { mutableStateOf(false) }
  LaunchedEffect(seekAmount) {
    delay(800)
    isDoubleTapSeeking = false
    viewModel.updateSeekAmount(0)
    viewModel.updateSeekText(null)
    delay(100)
    viewModel.hideSeekBar()
  }
  val multipleSpeedGesture by playerPreferences.holdForMultipleSpeed.collectAsState()
  val brightnessGesture = playerPreferences.brightnessGesture.get()
  val volumeGesture by playerPreferences.volumeGesture.collectAsState()
  val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
  val seekGesture by playerPreferences.horizontalSeekGesture.collectAsState()
  val showSeekbarWhenSeeking by playerPreferences.showSeekBarWhenSeeking.collectAsState()
  val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
  var isLongPressing by remember { mutableStateOf(false) }
  val currentVolume by viewModel.currentVolume.collectAsState()
  val currentMPVVolume by MPVLib.propInt["volume"].collectAsState()
  val currentBrightness by viewModel.currentBrightness.collectAsState()
  val volumeBoostingCap = audioPreferences.volumeBoostCap.get()
  val haptics = LocalHapticFeedback.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .pointerInput(Unit) {
        var originalSpeed = 1f
        detectTapGestures(
          onTap = {
            if (controlsShown) viewModel.hideControls() else viewModel.showControls()
          },
          onDoubleTap = {
            if (areControlsLocked || isDoubleTapSeeking) return@detectTapGestures
            if (it.x > size.width * 3 / 5) {
              if (!isSeekingForwards) viewModel.updateSeekAmount(0)
              viewModel.handleRightDoubleTap()
              isDoubleTapSeeking = true
            } else if (it.x < size.width * 2 / 5) {
              if (isSeekingForwards) viewModel.updateSeekAmount(0)
              viewModel.handleLeftDoubleTap()
              isDoubleTapSeeking = true
            } else {
              viewModel.handleCenterDoubleTap()
            }
          },
          onPress = {
            if (panelShown != Panels.None && !allowGesturesInPanels) {
              viewModel.panelShown.update { Panels.None }
            }
            if (!areControlsLocked && isDoubleTapSeeking && seekAmount != 0) {
              if (it.x > size.width * 3 / 5) {
                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleRightDoubleTap()
              } else if (it.x < size.width * 2 / 5) {
                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleLeftDoubleTap()
              } else {
                viewModel.handleCenterDoubleTap()
              }
            } else {
              isDoubleTapSeeking = false
            }
            val press = PressInteraction.Press(
              it.copy(x = if (it.x > size.width * 3 / 5) it.x - size.width * 0.6f else it.x),
            )
            interactionSource.emit(press)
            tryAwaitRelease()
            if (isLongPressing) {
              isLongPressing = false
              MPVLib.setPropertyFloat("speed", originalSpeed)
              viewModel.playerUpdate.update { PlayerUpdates.None }
            }
            interactionSource.emit(PressInteraction.Release(press))
          },
          onLongPress = {
            if (multipleSpeedGesture == 0f || areControlsLocked) return@detectTapGestures
            if (!isLongPressing && paused == false) {
              originalSpeed = MPVLib.getPropertyFloat("speed") ?: playbackSpeed ?: 1f
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              isLongPressing = true
              MPVLib.setPropertyFloat("speed", multipleSpeedGesture)
              viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
            }
          },
        )
      }
      .pointerInput(areControlsLocked) {
        if (!seekGesture || areControlsLocked) return@pointerInput
        var startingPosition = position ?: 0
        var startingX = 0f
        var wasPlayerAlreadyPause = false
        detectHorizontalDragGestures(
          onDragStart = {
            startingPosition = position ?: 0
            startingX = it.x
            wasPlayerAlreadyPause = paused ?: false
            viewModel.pause()
          },
          onDragEnd = {
            viewModel.gestureSeekAmount.update { null }
            viewModel.hideSeekBar()
            if (!wasPlayerAlreadyPause) viewModel.unpause()
          },
        ) { change, dragAmount ->
          if ((position ?: 0) <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
          if ((position ?: 0) >= (duration ?: 0) && dragAmount > 0) return@detectHorizontalDragGestures
          calculateNewHorizontalGestureValue(
            startingPosition,
            startingX,
            change.position.x,
            0.15f,
          ).let {
            viewModel.gestureSeekAmount.update { _ ->
              Pair(
                startingPosition,
                (it - startingPosition)
                  .coerceIn(0 - startingPosition, ((duration ?: 0) - startingPosition)),
              )
            }
            viewModel.seekTo(it)
          }

          if (showSeekbarWhenSeeking) viewModel.showSeekBar()
        }
      }
      .pointerInput(areControlsLocked) {
        if ((!brightnessGesture && !volumeGesture) || areControlsLocked) return@pointerInput
        var startingY = 0f
        var mpvVolumeStartingY = 0f
        var originalVolume = currentVolume
        var originalMPVVolume = currentMPVVolume
        var originalBrightness = currentBrightness
        var lastVolumeValue = currentVolume
        var lastMPVVolumeValue = currentMPVVolume ?: 100
        var lastBrightnessValue = currentBrightness
        val brightnessGestureSens = 0.001f
        val volumeGestureSens = 0.03f
        val mpvVolumeGestureSens = 0.02f
        val isIncreasingVolumeBoost: (Float) -> Boolean = {
          volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
            (currentMPVVolume ?: 100) - 100 < volumeBoostingCap && it < 0
        }
        val isDecreasingVolumeBoost: (Float) -> Boolean = {
          volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
            (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap && it > 0
        }
        detectVerticalDragGestures(
          onDragEnd = {
            startingY = 0f
            lastVolumeValue = currentVolume
            lastMPVVolumeValue = currentMPVVolume ?: 100
            lastBrightnessValue = currentBrightness
          },
          onDragStart = {
            startingY = 0f
            mpvVolumeStartingY = 0f
            originalVolume = currentVolume
            originalMPVVolume = currentMPVVolume
            originalBrightness = currentBrightness
            lastVolumeValue = currentVolume
            lastMPVVolumeValue = currentMPVVolume ?: 100
            lastBrightnessValue = currentBrightness
          },
        ) { change, amount ->
          val changeVolume: () -> Unit = {
            if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
              if (mpvVolumeStartingY == 0f) {
                startingY = 0f
                originalVolume = currentVolume
                mpvVolumeStartingY = change.position.y
              }
              val newMPVVolume = calculateNewVerticalGestureValue(
                originalMPVVolume ?: 100,
                mpvVolumeStartingY,
                change.position.y,
                mpvVolumeGestureSens,
              ).coerceIn(100..volumeBoostingCap + 100)

              if (newMPVVolume != lastMPVVolumeValue) {
                viewModel.changeMPVVolumeTo(newMPVVolume)
                lastMPVVolumeValue = newMPVVolume
              }
            } else {
              if (startingY == 0f) {
                mpvVolumeStartingY = 0f
                originalMPVVolume = currentMPVVolume
                startingY = change.position.y
              }
              val newVolume = calculateNewVerticalGestureValue(
                originalVolume,
                startingY,
                change.position.y,
                volumeGestureSens,
              )

              if (newVolume != lastVolumeValue) {
                viewModel.changeVolumeTo(newVolume)
                lastVolumeValue = newVolume
              }
            }

            // Always display slider during gesture (even at max/min)
            viewModel.displayVolumeSlider()
          }
          val changeBrightness: () -> Unit = {
            if (startingY == 0f) startingY = change.position.y
            val newBrightness = calculateNewVerticalGestureValue(
              originalBrightness,
              startingY,
              change.position.y,
              brightnessGestureSens,
            )

            // Only update if brightness changed (avoid floating-point noise)
            if (abs(newBrightness - lastBrightnessValue) > 0.001f) {
              viewModel.changeBrightnessTo(newBrightness)
              lastBrightnessValue = newBrightness
            }

            // Always display slider during gesture (even at max/min)
            viewModel.displayBrightnessSlider()
          }
          when {
            volumeGesture && brightnessGesture -> {
              if (swapVolumeAndBrightness) {
                if (change.position.x > size.width / 2) changeBrightness() else changeVolume()
              } else {
                if (change.position.x < size.width / 2) changeBrightness() else changeVolume()
              }
            }

            brightnessGesture -> changeBrightness()
            // it's not always true, AS is drunk
            volumeGesture -> changeVolume()
            else -> {}
          }
        }
      }
      .pointerInput(pinchToZoomGesture, areControlsLocked) {
        if (!pinchToZoomGesture || areControlsLocked) return@pointerInput

        awaitEachGesture {
          var zoom = 0f
          var isZoomGestureStarted = false
          var initialDistance = 0f

          // Wait for at least one pointer
          awaitFirstDown(requireUnconsumed = false)

          do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }

            // Check if we have exactly 2 fingers (pinch gesture)
            if (pointerCount == 2) {
              val pointers = event.changes.filter { it.pressed }

              if (pointers.size == 2) {
                val pointer1 = pointers[0].position
                val pointer2 = pointers[1].position

                // Calculate distance between two fingers
                val currentDistance = kotlin.math.sqrt(
                  ((pointer2.x - pointer1.x) * (pointer2.x - pointer1.x) +
                    (pointer2.y - pointer1.y) * (pointer2.y - pointer1.y)).toDouble(),
                ).toFloat()

                if (initialDistance == 0f) {
                  // First time detecting pinch - record initial distance and zoom
                  initialDistance = currentDistance
                  zoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
                  isZoomGestureStarted = false
                }

                val distanceChange = abs(currentDistance - initialDistance)

                // Only start zoom if movement is significant (reduces accidental zooms)
                if (distanceChange > 10f) {
                  if (!isZoomGestureStarted) {
                    isZoomGestureStarted = true
                    viewModel.playerUpdate.update { PlayerUpdates.VideoZoom }
                  }

                  if (initialDistance > 0) {
                    // Calculate zoom based on distance ratio
                    val zoomScale = currentDistance / initialDistance
                    val zoomDelta = kotlin.math.ln(zoomScale.toDouble()).toFloat() * 1.5f
                    val newZoom = (zoom + zoomDelta).coerceIn(-2f, 3f)
                    viewModel.setVideoZoom(newZoom)
                  }
                }

                // Consume the events to prevent other gestures
                pointers.forEach { it.consume() }
              }
            } else if (pointerCount < 2 && initialDistance != 0f) {
              // User lifted a finger, end the gesture
              break
            }
          } while (event.changes.any { it.pressed })
        }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapToSeekOvals(
  amount: Int,
  text: String?,
  showOvals: Boolean,
  showSeekIcon: Boolean,
  showSeekTime: Boolean,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    CompositionLocalProvider(
      LocalRippleConfiguration provides playerRippleConfiguration,
    ) {
      if (amount != 0) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.4f), // 2 fifths
          contentAlignment = Alignment.Center,
        ) {
          if (showOvals) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                .background(Color.White.copy(alpha))
                .indication(interactionSource, ripple()),
            )
          }
          if (showSeekIcon || showSeekTime) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              DoubleTapSeekTriangles(isForward = amount > 0)
              Text(
                text = text ?: pluralStringResource(R.plurals.seconds, amount, amount),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.White,
              )
            }
          }
        }
      }
    }
  }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
  return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
  return originalValue + ((startingY - newY) * sensitivity)
}

fun calculateNewHorizontalGestureValue(originalValue: Int, startingX: Float, newX: Float, sensitivity: Float): Int {
  return originalValue + ((newX - startingX) * sensitivity).toInt()
}

fun calculateNewHorizontalGestureValue(originalValue: Float, startingX: Float, newX: Float, sensitivity: Float): Float {
  return originalValue + ((newX - startingX) * sensitivity)
}
