package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.LeftSideOvalShape
import app.marlboroadvance.mpvex.presentation.components.RightSideOvalShape
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerUpdates
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.controls.components.DoubleTapSeekTriangles
import app.marlboroadvance.mpvex.ui.theme.playerRippleConfiguration
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
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
  val gesturePreferences = koinInject<GesturePreferences>()
  val panelShown by viewModel.panelShown.collectAsState()
  val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()
  val paused by MPVLib.propBoolean["pause"].collectAsState()
  val duration by MPVLib.propInt["duration"].collectAsState()
  val position by MPVLib.propInt["time-pos"].collectAsState()
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
  val useSingleTapForCenter by gesturePreferences.useSingleTapForCenter.collectAsState()
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
  val brightnessGesture by playerPreferences.brightnessGesture.collectAsState()
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

  var lastSeekRegion by remember { mutableStateOf<String?>(null) }
  var lastSeekTime by remember { mutableStateOf<Long?>(null) }
  val multiTapContinueWindow = 650L

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeGestures)
        .pointerInput(Unit) {
          val originalSpeed = MPVLib.getPropertyFloat("speed") ?: 1f

          var tapHandledInPress by mutableStateOf(false)
          var isLongPress by mutableStateOf(false)

          detectTapGestures(
            onTap = {
              if (tapHandledInPress) {
                tapHandledInPress = false
                return@detectTapGestures
              }
              if (isLongPress) {
                return@detectTapGestures
              }

              if (it.x > size.width * 1 / 4 && it.x < size.width * 3 / 4 && useSingleTapForCenter) {
                viewModel.handleCenterSingleTap()
              } else {
                if (controlsShown) viewModel.hideControls() else viewModel.showControls()
              }
            },
            onDoubleTap = {
              if (areControlsLocked || isDoubleTapSeeking) return@detectTapGestures

              if (it.x > size.width * 3 / 4) {
                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleRightDoubleTap()
              } else if (it.x < size.width * 1 / 4) {
                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                viewModel.handleLeftDoubleTap()
              } else if (!useSingleTapForCenter) {
                viewModel.handleCenterDoubleTap()
              }
            },
            onPress = {
              isLongPress = false

              if (panelShown != Panels.None && !allowGesturesInPanels) {
                viewModel.panelShown.update { Panels.None }
              }

              val now = System.currentTimeMillis()
              val region = when {
                it.x > size.width * 3 / 4 -> "right"
                it.x < size.width * 1 / 4 -> "left"
                else -> "center"
              }
              val shouldContinueSeek =
                !areControlsLocked &&
                  isDoubleTapSeeking &&
                  seekAmount != 0 &&
                  lastSeekRegion == region &&
                  lastSeekTime != null &&
                  now - lastSeekTime!! < multiTapContinueWindow

              if (shouldContinueSeek) {
                when (region) {
                  "right" -> {
                    if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                    viewModel.handleRightDoubleTap()
                  }

                  "left" -> {
                    if (isSeekingForwards) viewModel.updateSeekAmount(0)
                    viewModel.handleLeftDoubleTap()
                  }

                  else -> viewModel.handleCenterDoubleTap()
                }
              }

              val press =
                PressInteraction.Press(
                  it.copy(x = if (it.x > size.width * 3 / 4) it.x - size.width * 0.6f else it.x),
                )
              interactionSource.emit(press)

              val released = tryAwaitRelease()

              if (released && !isLongPress && !shouldContinueSeek) {
                if (it.x > size.width * 1 / 4 && it.x < size.width * 3 / 4 && useSingleTapForCenter) {
                  tapHandledInPress = true
                  viewModel.handleCenterSingleTap()
                }
              }
              if (isLongPressing) {
                MPVLib.setPropertyFloat("speed", originalSpeed)
                viewModel.playerUpdate.update { PlayerUpdates.None }
                isLongPressing = false
                isLongPress = false
              }
              interactionSource.emit(PressInteraction.Release(press))
            },
            onLongPress = {

              if (multipleSpeedGesture == 0f || areControlsLocked) return@detectTapGestures
              if (!isLongPressing && paused == false) {
                isLongPress = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                MPVLib.setPropertyFloat("speed", multipleSpeedGesture)
                viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
                isLongPressing = true
              }
            },
          )
        }
        // Unified drag gesture handler to prevent conflicts
        .pointerInput(areControlsLocked, seekGesture, brightnessGesture, volumeGesture) {
          if (areControlsLocked) return@pointerInput
          if (!seekGesture && !brightnessGesture && !volumeGesture) return@pointerInput

          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var startingPosition = position ?: 0
            var startingX = down.position.x
            var startingY = 0f
            var mpvVolumeStartingY = 0f
            var originalVolume = currentVolume
            var originalMPVVolume = currentMPVVolume
            var originalBrightness = currentBrightness
            var wasPlayerAlreadyPaused = paused ?: false

            var totalDragX = 0f
            var totalDragY = 0f
            var gestureType: GestureType? = null

            val dragThreshold = 30f // Pixels to move before committing to a direction
            val brightnessGestureSens = 0.001f
            val volumeGestureSens = 0.03f
            val mpvVolumeGestureSens = 0.02f

            val isIncreasingVolumeBoost: (Float) -> Boolean = {
              volumeBoostingCap > 0 &&
                currentVolume == viewModel.maxVolume &&
                (currentMPVVolume ?: 100) - 100 < volumeBoostingCap &&
                it < 0
            }
            val isDecreasingVolumeBoost: (Float) -> Boolean = {
              volumeBoostingCap > 0 &&
                currentVolume == viewModel.maxVolume &&
                (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap &&
                it > 0
            }

            // Track the drag
            do {
              val event = awaitPointerEvent()
              event.changes.forEach { change ->
                if (change.pressed) {
                  val dragX = change.position.x - startingX
                  change.position.y - (if (startingY == 0f) down.position.y else startingY)

                  totalDragX += change.positionChange().x
                  totalDragY += change.positionChange().y

                  // Determine gesture type if not yet determined
                  if (gestureType == null && (abs(totalDragX) > dragThreshold || abs(totalDragY) > dragThreshold)) {
                    gestureType = if (abs(totalDragX) > abs(totalDragY)) {
                      if (seekGesture) GestureType.HORIZONTAL_SEEK else null
                    } else {
                      if (brightnessGesture || volumeGesture) {
                        if (brightnessGesture && volumeGesture) {
                          if (swapVolumeAndBrightness) {
                            if (change.position.x > size.width / 2) GestureType.BRIGHTNESS else GestureType.VOLUME
                          } else {
                            if (change.position.x < size.width / 2) GestureType.BRIGHTNESS else GestureType.VOLUME
                          }
                        } else if (brightnessGesture) {
                          GestureType.BRIGHTNESS
                        } else {
                          GestureType.VOLUME
                        }
                      } else null
                    }

                    // Initialize gesture
                    when (gestureType) {
                      GestureType.HORIZONTAL_SEEK -> {
                        startingPosition = position ?: 0
                        startingX = change.position.x
                        wasPlayerAlreadyPaused = paused ?: false
                        viewModel.pause()
                      }

                      GestureType.BRIGHTNESS, GestureType.VOLUME -> {
                        startingY = change.position.y
                        mpvVolumeStartingY = change.position.y
                        originalVolume = currentVolume
                        originalMPVVolume = currentMPVVolume
                        originalBrightness = currentBrightness
                      }

                      null -> {}
                    }
                  }

                  // Handle the gesture
                  when (gestureType) {
                    GestureType.HORIZONTAL_SEEK -> {
                      if ((position ?: 0) <= 0 && dragX < 0) return@forEach
                      if ((position ?: 0) >= (duration ?: 0) && dragX > 0) return@forEach

                      val newPosition = calculateNewHorizontalGestureValue(
                        startingPosition,
                        startingX,
                        change.position.x,
                        0.15f,
                      )

                      viewModel.gestureSeekAmount.update { _ ->
                        Pair(
                          startingPosition,
                          (newPosition - startingPosition)
                            .coerceIn(0 - startingPosition, ((duration ?: 0) - startingPosition)),
                        )
                      }
                      viewModel.seekTo(newPosition)

                      if (showSeekbarWhenSeeking) viewModel.showSeekBar()
                      change.consume()
                    }

                    GestureType.VOLUME -> {
                      val amount = change.position.y - startingY

                      if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                        if (mpvVolumeStartingY == 0f) {
                          startingY = 0f
                          originalVolume = currentVolume
                          mpvVolumeStartingY = change.position.y
                        }
                        viewModel.changeMPVVolumeTo(
                          calculateNewVerticalGestureValue(
                            originalMPVVolume ?: 100,
                            mpvVolumeStartingY,
                            change.position.y,
                            mpvVolumeGestureSens,
                          ).coerceIn(100..volumeBoostingCap + 100),
                        )
                      } else {
                        if (startingY == 0f) {
                          mpvVolumeStartingY = 0f
                          originalMPVVolume = currentMPVVolume
                          startingY = change.position.y
                        }
                        viewModel.changeVolumeTo(
                          calculateNewVerticalGestureValue(
                            originalVolume,
                            startingY,
                            change.position.y,
                            volumeGestureSens,
                          ),
                        )
                      }
                      viewModel.displayVolumeSlider()
                      change.consume()
                    }

                    GestureType.BRIGHTNESS -> {
                      if (startingY == 0f) startingY = change.position.y
                      viewModel.changeBrightnessTo(
                        calculateNewVerticalGestureValue(
                          originalBrightness,
                          startingY,
                          change.position.y,
                          brightnessGestureSens,
                        ),
                      )
                      viewModel.displayBrightnessSlider()
                      change.consume()
                    }

                    null -> {} // Still determining direction
                  }
                }
              }
            } while (event.changes.any { it.pressed })

            // Gesture ended
            when (gestureType) {
              GestureType.HORIZONTAL_SEEK -> {
                viewModel.gestureSeekAmount.update { null }
                viewModel.hideSeekBar()
                if (!wasPlayerAlreadyPaused) viewModel.unpause()
              }

              else -> {}
            }
          }
        }
        .pointerInput(areControlsLocked) {
          if (areControlsLocked || !pinchToZoomGesture) return@pointerInput

          awaitEachGesture {
            var gestureStartZoom = 0f
            var isZoomGestureStarted = false
            var initialDistance = 0f
            val minDistanceChangeThreshold = 20f

            do {
              val event = awaitPointerEvent()
              val pointerCount = event.changes.count { it.pressed }

              if (pointerCount == 2) {
                val currentPointers = event.changes.filter { it.pressed }

                if (currentPointers.size == 2) {
                  val pointer1 = currentPointers[0].position
                  val pointer2 = currentPointers[1].position
                  val currentDistance =
                    kotlin.math
                      .sqrt(
                        (
                          (pointer2.x - pointer1.x) * (pointer2.x - pointer1.x) +
                            (pointer2.y - pointer1.y) * (pointer2.y - pointer1.y)
                          ).toDouble(),
                      ).toFloat()

                  if (initialDistance == 0f) {
                    initialDistance = currentDistance
                  }

                  val distanceChange = abs(currentDistance - initialDistance)

                  if (distanceChange > minDistanceChangeThreshold) {
                    if (!isZoomGestureStarted) {
                      gestureStartZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: 0f
                      isZoomGestureStarted = true
                      viewModel.playerUpdate.update { PlayerUpdates.VideoZoom }
                    }

                    if (initialDistance > 0) {
                      val zoomScale = currentDistance / initialDistance
                      val zoomDelta = kotlin.math.ln(zoomScale.toDouble()).toFloat() * 1.5f
                      val newZoom = (gestureStartZoom + zoomDelta).coerceIn(-2f, 3f)
                      viewModel.setVideoZoom(newZoom)

                      currentPointers.forEach { it.consume() }
                    }
                  }
                }
              } else if (pointerCount > 2 || (isZoomGestureStarted)) {
                break
              }
            } while (event.changes.any { it.pressed })
          }
        },
  )
}

private enum class GestureType {
  HORIZONTAL_SEEK,
  BRIGHTNESS,
  VOLUME
}

// ... existing code ...

fun calculateNewVerticalGestureValue(
  originalValue: Int,
  startingY: Float,
  newY: Float,
  sensitivity: Float,
): Int = originalValue + ((startingY - newY) * sensitivity).toInt()

fun calculateNewVerticalGestureValue(
  originalValue: Float,
  startingY: Float,
  newY: Float,
  sensitivity: Float,
): Float = originalValue + ((startingY - newY) * sensitivity)

fun calculateNewHorizontalGestureValue(
  originalValue: Int,
  startingX: Float,
  newX: Float,
  sensitivity: Float,
): Int = originalValue + ((newX - startingX) * sensitivity).toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapToSeekOvals(
  amount: Int,
  text: String?,
  showOvals: Boolean,
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
          modifier =
            Modifier
              .fillMaxHeight()
              .fillMaxWidth(0.4f),
          contentAlignment = Alignment.Center,
        ) {
          if (showOvals) {
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                  .background(Color.White.copy(alpha))
                  .indication(interactionSource, ripple()),
            )
          }
          if (showSeekTime) {
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
