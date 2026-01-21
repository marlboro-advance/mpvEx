package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.LeftSideOvalShape
import app.marlboroadvance.mpvex.presentation.components.RightSideOvalShape
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerUpdates
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.SingleActionGesture
import app.marlboroadvance.mpvex.ui.theme.playerRippleConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

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
  val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
  val controlsShown by viewModel.controlsShown.collectAsState()
  val areControlsLocked by viewModel.areControlsLocked.collectAsState()
  val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
  val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
  val useSingleTapForCenter by gesturePreferences.useSingleTapForCenter.collectAsState()
  val doubleTapSeekAreaWidth by gesturePreferences.doubleTapSeekAreaWidth.collectAsState()
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
  val showDynamicSpeedOverlay by playerPreferences.showDynamicSpeedOverlay.collectAsState()
  val brightnessGesture = playerPreferences.brightnessGesture.get()
  val volumeGesture by playerPreferences.volumeGesture.collectAsState()
  val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
  val seekGesture by playerPreferences.horizontalSeekGesture.collectAsState()
  val showSeekbarWhenSeeking by playerPreferences.showSeekBarWhenSeeking.collectAsState()
  val seekSensitivity by playerPreferences.seekSensitivity.collectAsState()
  val pinchToZoomGesture by playerPreferences.pinchToZoomGesture.collectAsState()
  var isLongPressing by remember { mutableStateOf(false) }
  var isDynamicSpeedControlActive by remember { mutableStateOf(false) }
  var dynamicSpeedStartX by remember { mutableStateOf(0f) }
  var dynamicSpeedStartValue by remember { mutableStateOf(2f) }
  var lastAppliedSpeed by remember { mutableStateOf(2f) }
  var hasSwipedEnough by remember { mutableStateOf(false) }
  var longPressTriggeredDuringTouch by remember { mutableStateOf(false) }
  val currentVolume by viewModel.currentVolume.collectAsState()
  val currentMPVVolume by MPVLib.propInt["volume"].collectAsState()
  val currentBrightness by viewModel.currentBrightness.collectAsState()
  val volumeBoostingCap = audioPreferences.volumeBoostCap.get()
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()

  // Isolated double-tap state tracking
  var tapCount by remember { mutableStateOf(0) }
  var lastTapTime by remember { mutableStateOf(0L) }
  var lastTapPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
  var lastTapRegion by remember { mutableStateOf<String?>(null) }
  var pendingSingleTapRegion by remember { mutableStateOf<String?>(null) }
  var pendingSingleTapPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
  val doubleTapTimeout = 250L
  val multiTapContinueWindow = 650L

  // Multi-tap seeking state
  var lastSeekRegion by remember { mutableStateOf<String?>(null) }
  var lastSeekTime by remember { mutableStateOf<Long?>(null) }

  // Auto-reset tap count on timeout and execute single tap if no double tap detected
  LaunchedEffect(tapCount, longPressTriggeredDuringTouch) {
    if (tapCount == 1 && pendingSingleTapRegion != null) {
      delay(doubleTapTimeout)
      // Timeout occurred, execute single tap action only if not double-tap seeking and not triggered by long press
      if (tapCount == 1 && pendingSingleTapRegion != null && !isDoubleTapSeeking && !longPressTriggeredDuringTouch) {
        val region = pendingSingleTapRegion!!
        val isCenterTap = region == "center"
        if (useSingleTapForCenter && isCenterTap) {
          viewModel.handleCenterSingleTap()
        } else {
          if (panelShown != Panels.None && !allowGesturesInPanels) {
            viewModel.panelShown.update { Panels.None }
          }
          if (controlsShown) {
            viewModel.hideControls()
          } else {
            viewModel.showControls()
          }
        }
        pendingSingleTapRegion = null
        pendingSingleTapPosition = null
      }
      tapCount = 0
      lastTapRegion = null
      if (!isDoubleTapSeeking) {
        isDoubleTapSeeking = false
        viewModel.updateSeekAmount(0)
      }
    }
  }

  // Reset double-tap seek state when seeking stops
  LaunchedEffect(seekAmount) {
    if (seekAmount == 0) {
      delay(100)
      isDoubleTapSeeking = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .pointerInput(areControlsLocked, doubleTapSeekAreaWidth, gesturePreferences) {
        // Isolated double-tap detection that doesn't interfere with other gestures
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val downPosition = down.position
          val downTime = System.currentTimeMillis()

          // Calculate regions
          val seekAreaFraction = doubleTapSeekAreaWidth / 100f
          val leftBoundary = size.width * seekAreaFraction
          val rightBoundary = size.width * (1f - seekAreaFraction)
          val region = when {
            downPosition.x > rightBoundary -> "right"
            downPosition.x < leftBoundary -> "left"
            else -> "center"
          }

          // Track for potential drag
          var isDrag = false
          var wasConsumedByTapGesture = false

          do {
            val event = awaitPointerEvent()
            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

            // Check if this is a drag (not a tap)
            val distance = sqrt(
              (pointer.position.x - downPosition.x) * (pointer.position.x - downPosition.x) +
              (pointer.position.y - downPosition.y) * (pointer.position.y - downPosition.y)
            )

            if (distance > 10f) {
              isDrag = true
              // Don't consume - let other pointer inputs handle drag gestures
            }

            if (!pointer.pressed) {
              // Pointer lifted - this is a tap if it wasn't a drag
              if (!isDrag && !wasConsumedByTapGesture) {
                val timeSinceLastTap = downTime - lastTapTime
                val positionChange = sqrt(
                  (downPosition.x - lastTapPosition.x) * (downPosition.x - lastTapPosition.x) +
                  (downPosition.y - lastTapPosition.y) * (downPosition.y - lastTapPosition.y)
                )

                // Check if this is a continuation of multi-tap sequence
                val isMultiTapContinuation =
                  lastTapRegion == region &&
                  timeSinceLastTap < multiTapContinueWindow &&
                  positionChange < 100f &&
                  tapCount >= 2 &&
                  isDoubleTapSeeking

                // Check if this is a valid double-tap
                val isDoubleTap =
                  timeSinceLastTap < doubleTapTimeout &&
                  lastTapRegion == region &&
                  positionChange < 100f &&
                  tapCount == 1

                if (isDoubleTap && !areControlsLocked) {
                  // Valid double-tap detected
                  tapCount = 2
                  lastTapTime = downTime
                  lastTapPosition = downPosition
                  pendingSingleTapRegion = null // Cancel pending single tap
                  pendingSingleTapPosition = null
                  wasConsumedByTapGesture = true
                  pointer.consume()

                  when (region) {
                    "right" -> {
                      val rightGesture = gesturePreferences.rightSingleActionGesture.get()
                      if (rightGesture == SingleActionGesture.Seek) {
                        isDoubleTapSeeking = true
                        lastSeekRegion = "right"
                        lastSeekTime = System.currentTimeMillis()
                        if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleRightDoubleTap()
                    }
                    "left" -> {
                      val leftGesture = gesturePreferences.leftSingleActionGesture.get()
                      if (leftGesture == SingleActionGesture.Seek) {
                        isDoubleTapSeeking = true
                        lastSeekRegion = "left"
                        lastSeekTime = System.currentTimeMillis()
                        if (isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleLeftDoubleTap()
                    }
                    "center" -> {
                      viewModel.handleCenterDoubleTap()
                    }
                  }
                } else if (isMultiTapContinuation && isDoubleTapSeeking) {
                  // Continue multi-tap seeking
                  tapCount++
                  wasConsumedByTapGesture = true
                  pointer.consume()
                  lastSeekTime = System.currentTimeMillis()
                  lastTapTime = downTime
                  lastTapPosition = downPosition

                  when (region) {
                    "right" -> {
                      val rightGesture = gesturePreferences.rightSingleActionGesture.get()
                      if (rightGesture == SingleActionGesture.Seek) {
                        if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleRightDoubleTap()
                    }
                    "left" -> {
                      val leftGesture = gesturePreferences.leftSingleActionGesture.get()
                      if (leftGesture == SingleActionGesture.Seek) {
                        if (isSeekingForwards) viewModel.updateSeekAmount(0)
                      }
                      viewModel.handleLeftDoubleTap()
                    }
                    "center" -> {
                      viewModel.handleCenterDoubleTap()
                    }
                  }
                } else if (tapCount == 0 || timeSinceLastTap >= doubleTapTimeout) {
                  // Single tap or timed out - start new tap sequence
                  tapCount = 1
                  lastTapTime = downTime
                  lastTapPosition = downPosition
                  lastTapRegion = region
                  pendingSingleTapRegion = region
                  pendingSingleTapPosition = downPosition
                  wasConsumedByTapGesture = true
                  pointer.consume()
                  // Don't execute single tap action yet - wait to see if second tap comes
                }
              }
              break
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .pointerInput(areControlsLocked, multipleSpeedGesture, seekGesture, brightnessGesture, volumeGesture) {
        if ((!seekGesture && !brightnessGesture && !volumeGesture) || areControlsLocked) return@pointerInput

        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val startPosition = down.position

          // Reset long press tracking at the start of each gesture
          longPressTriggeredDuringTouch = false

          // State for horizontal seeking
          var startingPosition = position ?: 0
          var startingX = startPosition.x
          var wasPlayerAlreadyPause = false

          // State for vertical gestures (volume/brightness)
          var startingY = 0f
          var mpvVolumeStartingY = 0f
          var originalVolume = currentVolume
          var originalMPVVolume = currentMPVVolume
          var originalBrightness = currentBrightness
          var lastVolumeValue = currentVolume
          var lastMPVVolumeValue = currentMPVVolume ?: 100
          var lastBrightnessValue = currentBrightness
          val brightnessGestureSens = 0.001f
          val volumeGestureSens = 0.017f
          val mpvVolumeGestureSens = 0.017f

          // Original speed for long press
          var originalSpeed = playbackSpeed ?: 1f

          // Track long press separately
          var longPressTriggered = false
          val longPressDelay = 500L
          var longPressJob = coroutineScope.launch {
            delay(longPressDelay)
            if (!longPressTriggered && !wasPlayerAlreadyPause && paused == false) {
              val distance = sqrt(
                (down.position.x - startPosition.x) * (down.position.x - startPosition.x) +
                (down.position.y - startPosition.y) * (down.position.y - startPosition.y)
              )
              // Only trigger if still within tap threshold
              if (distance < 10f && multipleSpeedGesture > 0f) {
                longPressTriggered = true
                isLongPressing = true
                longPressTriggeredDuringTouch = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                originalSpeed = playbackSpeed ?: 1f
                MPVLib.setPropertyFloat("speed", multipleSpeedGesture)

                if (showDynamicSpeedOverlay) {
                  isDynamicSpeedControlActive = true
                  hasSwipedEnough = false
                  dynamicSpeedStartX = startPosition.x
                  dynamicSpeedStartValue = multipleSpeedGesture
                  lastAppliedSpeed = multipleSpeedGesture
                  viewModel.playerUpdate.update { PlayerUpdates.DynamicSpeedControl(multipleSpeedGesture, false) }
                } else {
                  viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
                }
              }
            }
          }

          var gestureType: String? = null

          do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }

            if (pointerCount == 1) {
              event.changes.forEach { change ->
                if (change.pressed) {
                  val currentPosition = change.position
                  val deltaX = currentPosition.x - startPosition.x
                  val deltaY = currentPosition.y - startPosition.y

                  // Determine gesture type based on initial drag direction
                  if (gestureType == null && (abs(deltaX) > 20f || abs(deltaY) > 20f)) {
                    // Cancel long press if drag started
                    longPressJob.cancel()

                    // Check if we're in long press mode with dynamic speed control
                    if (isLongPressing && isDynamicSpeedControlActive && showDynamicSpeedOverlay && abs(deltaX) > 10f) {
                      gestureType = "speed_control"
                    } else {
                      gestureType = if (abs(deltaX) > abs(deltaY) * 1.5f) {
                        "horizontal"
                      } else if (abs(deltaY) > abs(deltaX) * 1.5f) {
                        "vertical"
                      } else {
                        null
                      }
                    }

                    // Initialize gesture-specific state
                    when (gestureType) {
                      "speed_control" -> {
                        dynamicSpeedStartX = currentPosition.x
                        dynamicSpeedStartValue = MPVLib.getPropertyFloat("speed") ?: multipleSpeedGesture
                      }
                      "horizontal" -> {
                        if (seekGesture && !isLongPressing) {
                          startingPosition = position ?: 0
                          startingX = startPosition.x
                          wasPlayerAlreadyPause = paused ?: false
                          viewModel.pause()
                        }
                      }
                      "vertical" -> {
                        if ((brightnessGesture || volumeGesture) && !isLongPressing) {
                          startingY = 0f
                          mpvVolumeStartingY = 0f
                          originalVolume = currentVolume
                          originalMPVVolume = currentMPVVolume
                          originalBrightness = currentBrightness
                          lastVolumeValue = currentVolume
                          lastMPVVolumeValue = currentMPVVolume ?: 100
                          lastBrightnessValue = currentBrightness
                        }
                      }
                    }
                  }

                  // Handle the appropriate gesture
                  when (gestureType) {
                    "speed_control" -> {
                      if (!showDynamicSpeedOverlay) return@forEach
                      if (isLongPressing && isDynamicSpeedControlActive && paused == false) {
                        change.consume()

                        val speedPresets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                        val screenWidth = size.width.toFloat()

                        val deltaX = currentPosition.x - dynamicSpeedStartX
                        val swipeDetectionThreshold = 10.dp.toPx()

                        if (!hasSwipedEnough && abs(deltaX) >= swipeDetectionThreshold) {
                          hasSwipedEnough = true
                          viewModel.playerUpdate.update { PlayerUpdates.DynamicSpeedControl(lastAppliedSpeed, true) }
                        }

                        if (hasSwipedEnough) {
                          val presetsRange = speedPresets.size - 1
                          val indexDelta = (deltaX / screenWidth) * presetsRange * 3.5f

                          val startIndex = speedPresets.indexOfFirst {
                            abs(it - dynamicSpeedStartValue) < 0.01f
                          }.takeIf { it >= 0 } ?: 4

                          val newIndex = (startIndex + indexDelta.toInt()).coerceIn(0, speedPresets.size - 1)
                          val newSpeed = speedPresets[newIndex]

                          if (abs(lastAppliedSpeed - newSpeed) > 0.01f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastAppliedSpeed = newSpeed
                            MPVLib.setPropertyFloat("speed", newSpeed)
                            viewModel.playerUpdate.update { PlayerUpdates.DynamicSpeedControl(newSpeed, true) }
                          }
                        }
                      }
                    }
                    "horizontal" -> {
                      if (seekGesture && !isLongPressing) {
                        val dragAmount = currentPosition.x - startPosition.x
                        if ((position ?: 0) <= 0f && dragAmount < 0) return@forEach
                        if ((position ?: 0) >= (duration ?: 0) && dragAmount > 0) return@forEach

                        calculateNewHorizontalGestureValue(
                          startingPosition,
                          startingX,
                          currentPosition.x,
                          seekSensitivity,
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
                        change.consume()
                      }
                    }
                    "vertical" -> {
                      if ((brightnessGesture || volumeGesture) && !isLongPressing) {
                        val amount = currentPosition.y - startPosition.y

                        val changeVolume: () -> Unit = {
                          val isIncreasingVolumeBoost: (Float) -> Boolean = {
                            volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                              (currentMPVVolume ?: 100) - 100 < volumeBoostingCap && amount < 0
                          }
                          val isDecreasingVolumeBoost: (Float) -> Boolean = {
                            volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                              (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap && amount > 0
                          }

                          if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                            if (mpvVolumeStartingY == 0f) {
                              startingY = 0f
                              originalVolume = currentVolume
                              mpvVolumeStartingY = currentPosition.y
                            }
                            val newMPVVolume = calculateNewVerticalGestureValue(
                              originalMPVVolume ?: 100,
                              mpvVolumeStartingY,
                              currentPosition.y,
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
                              startingY = currentPosition.y
                            }
                            val newVolume = calculateNewVerticalGestureValue(
                              originalVolume,
                              startingY,
                              currentPosition.y,
                              volumeGestureSens,
                            )

                            if (newVolume != lastVolumeValue) {
                              viewModel.changeVolumeTo(newVolume)
                              lastVolumeValue = newVolume
                            }
                          }

                          viewModel.displayVolumeSlider()
                        }
                        val changeBrightness: () -> Unit = {
                          if (startingY == 0f) startingY = currentPosition.y
                          val newBrightness = calculateNewVerticalGestureValue(
                            originalBrightness,
                            startingY,
                            currentPosition.y,
                            brightnessGestureSens,
                          )

                          if (abs(newBrightness - lastBrightnessValue) > 0.001f) {
                            viewModel.changeBrightnessTo(newBrightness)
                            lastBrightnessValue = newBrightness
                          }

                          viewModel.displayBrightnessSlider()
                        }

                        when {
                          volumeGesture && brightnessGesture -> {
                            if (swapVolumeAndBrightness) {
                              if (currentPosition.x > size.width / 2) changeBrightness() else changeVolume()
                            } else {
                              if (currentPosition.x < size.width / 2) changeBrightness() else changeVolume()
                            }
                          }
                          brightnessGesture -> changeBrightness()
                          volumeGesture -> changeVolume()
                          else -> {}
                        }

                        change.consume()
                      }
                    }
                  }
                }
              }
            } else if (pointerCount > 1) {
              // Multi-finger gesture detected
              longPressJob.cancel()
              if (gestureType != null) {
                when (gestureType) {
                  "horizontal" -> {
                    if (seekGesture) {
                      viewModel.gestureSeekAmount.update { null }
                      viewModel.hideSeekBar()
                      if (!wasPlayerAlreadyPause) viewModel.unpause()
                    }
                  }
                  "vertical" -> {
                    if (brightnessGesture || volumeGesture) {
                      startingY = 0f
                      lastVolumeValue = currentVolume
                      lastMPVVolumeValue = currentMPVVolume ?: 100
                      lastBrightnessValue = currentBrightness
                    }
                  }
                }
                gestureType = null
              }
              break
            }
          } while (event.changes.any { it.pressed })

          // Handle gesture end
          longPressJob.cancel()

          if (isLongPressing) {
            isLongPressing = false
            isDynamicSpeedControlActive = false
            hasSwipedEnough = false
            MPVLib.setPropertyFloat("speed", originalSpeed)
            viewModel.playerUpdate.update { PlayerUpdates.None }
          }

          when (gestureType) {
            "horizontal" -> {
              if (seekGesture) {
                viewModel.gestureSeekAmount.update { null }
                viewModel.hideSeekBar()
                if (!wasPlayerAlreadyPause) viewModel.unpause()
              }
            }
            "vertical" -> {
              if (brightnessGesture || volumeGesture) {
                startingY = 0f
                lastVolumeValue = currentVolume
                lastMPVVolumeValue = currentMPVVolume ?: 100
                lastBrightnessValue = currentBrightness
              }
            }
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
                val currentDistance = sqrt(
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
                    val zoomDelta = ln(zoomScale.toDouble()).toFloat() * 1.5f
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
  val gesturePreferences = koinInject<GesturePreferences>()
  val doubleTapSeekAreaWidth by gesturePreferences.doubleTapSeekAreaWidth.collectAsState()
  val seekAreaFraction = doubleTapSeekAreaWidth / 100f
  
  val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")

  // Scale animation for text
  var scaleTarget by remember { mutableStateOf(1f) }
  val scale by animateFloatAsState(
      targetValue = scaleTarget,
      animationSpec = tween(durationMillis = 150),
      label = "text_scale"
  )
  
  LaunchedEffect(amount) {
      if (amount != 0) {
          scaleTarget = 1.2f
          delay(100)
          scaleTarget = 1f
      } else {
        scaleTarget = 1f
      }
  }

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
            .fillMaxWidth(seekAreaFraction),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (amount < 0) {
                    CombiningChevronsAnimation(isRight = false, trigger = amount)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "- ${abs(amount)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        modifier = Modifier.scale(scale)
                    )
                } else {
                    Text(
                        text = "+ ${abs(amount)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        modifier = Modifier.scale(scale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CombiningChevronsAnimation(isRight = true, trigger = amount)
                }
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

@Composable
fun CombiningChevronsAnimation(
    isRight: Boolean,
    trigger: Int,
    modifier: Modifier = Modifier
) {
    // List of active animations (unique IDs)
    val animations = remember { mutableStateListOf<Long>() }

    // Fire a new animation whenever trigger changes
    LaunchedEffect(trigger) {
        animations.add(System.nanoTime())
    }

    Row(modifier = modifier) {
        Box {
             // Static Chevron
             Icon(
                imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            
            // Render active moving chevrons
            animations.forEach { animId ->
                key(animId) {
                    MovingChevron(
                        isRight = isRight,
                        onFinished = { animations.remove(animId) }
                    )
                }
            }
        }
    }
}

@Composable
fun MovingChevron(
    isRight: Boolean,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(250, easing = LinearEasing)
        )
        onFinished()
    }
    
    val startOffset = if (isRight) -15f else 15f
    val currentOffset = startOffset * (1f - progress.value)
    val alpha = 1f - progress.value
    
    Icon(
        imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(x = currentOffset.dp.roundToPx(), y = 0)
                }
            } 
    )
}
