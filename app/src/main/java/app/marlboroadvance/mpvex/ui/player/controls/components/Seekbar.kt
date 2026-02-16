package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.marlboroadvance.mpvex.ui.player.controls.LocalPlayerButtonsClickEvent
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.preferences.SeekbarStyle
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SeekbarWithTimers(
  position: Float,
  duration: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  timersInverted: Pair<Boolean, Boolean>,
  positionTimerOnClick: () -> Unit,
  durationTimerOnCLick: () -> Unit,
  chapters: ImmutableList<Segment>,
  paused: Boolean,
  seekbarStyle: SeekbarStyle = SeekbarStyle.Wavy,
  modifier: Modifier = Modifier,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isUserInteracting by remember { mutableStateOf(false) }
  var userPosition by remember { mutableFloatStateOf(position) }

  // Animated position for smooth transitions
  val animatedPosition = remember { Animatable(position) }
  val scope = rememberCoroutineScope()

  // Only animate position updates when user is not interacting
  LaunchedEffect(position, isUserInteracting) {
    if (!isUserInteracting && position != animatedPosition.value) {
      scope.launch {
        animatedPosition.animateTo(
          targetValue = position,
          animationSpec =
            tween(
              durationMillis = 200,
              easing = LinearEasing,
            ),
        )
      }
    }
  }

  Row(
    modifier = modifier.height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    VideoTimer(
      value = if (isUserInteracting) userPosition else position,
      timersInverted.first,
      onClick = {
        clickEvent()
        positionTimerOnClick()
      },
      modifier = Modifier.width(92.dp),
    )

    // Seekbar with expanded touch area
    Box(
      modifier =
        Modifier
          .weight(1f)
          .height(48.dp)
          .padding(vertical = 8.dp), // Add vertical padding for larger touch area
      contentAlignment = Alignment.Center,
    ) {
      // Invisible expanded touch area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp) // Larger touch area
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { offset ->
                val newPosition = (offset.x / size.width) * duration
                if (!isUserInteracting) isUserInteracting = true
                userPosition = newPosition.coerceIn(0f, duration)
                onValueChange(userPosition)
                scope.launch { animatedPosition.snapTo(userPosition) }
                isUserInteracting = false
                onValueChangeFinished()
              }
            )
          }
          .pointerInput(Unit) {
            detectDragGestures(
              onDragStart = { 
                isUserInteracting = true 
              },
              onDragEnd = { 
                scope.launch { animatedPosition.snapTo(userPosition) }
                isUserInteracting = false
                onValueChangeFinished()
              },
              onDragCancel = { 
                scope.launch { animatedPosition.snapTo(userPosition) }
                isUserInteracting = false
                onValueChangeFinished()
              },
            ) { change, _ ->
              change.consume()
              val newPosition = (change.position.x / size.width) * duration
              userPosition = newPosition.coerceIn(0f, duration)
              onValueChange(userPosition)
            }
          }
      )
      
      // Visual seekbar (smaller, centered)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(32.dp),
        contentAlignment = Alignment.Center,
      ) {
      when (seekbarStyle) {
        SeekbarStyle.Standard -> {
          StandardSeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            seekbarStyle = SeekbarStyle.Standard,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
        SeekbarStyle.Wavy -> {
          SquigglySeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Wavy,
            onSeek = { }, // Touch handled by parent
            onSeekFinished = { }, // Touch handled by parent
          )
        }
        SeekbarStyle.Thick -> {
          StandardSeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            seekbarStyle = SeekbarStyle.Thick,
            onSeek = { newPosition ->
              if (!isUserInteracting) isUserInteracting = true
              userPosition = newPosition
              onValueChange(newPosition)
            },
            onSeekFinished = {
              scope.launch { animatedPosition.snapTo(userPosition) }
              isUserInteracting = false
              onValueChangeFinished()
            },
          )
        }
      }
    }
  }

    VideoTimer(
      value = if (timersInverted.second) position - duration else duration,
      isInverted = timersInverted.second,
      onClick = {
        clickEvent()
        durationTimerOnCLick()
      },
      modifier = Modifier.width(92.dp),
    )
  }
}

@Composable
private fun SquigglySeekbar(
  position: Float,
  duration: Float,
  chapters: ImmutableList<Segment>,
  isPaused: Boolean,
  isScrubbing: Boolean,
  useWavySeekbar: Boolean,
  seekbarStyle: SeekbarStyle,
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

  // Manual Interaction State Tracking
  var isPressed by remember { mutableStateOf(false) }
  var isDragged by remember { mutableStateOf(false) }
  val isInteracting = isPressed || isDragged || isScrubbing 

  // Animation state
  var phaseOffset by remember { mutableFloatStateOf(0f) }
  var heightFraction by remember { mutableFloatStateOf(1f) }

  val scope = rememberCoroutineScope()

  // Wave parameters
  val waveLength = 80f
  val lineAmplitude = if (useWavySeekbar) 6f else 0f
  val phaseSpeed = 10f // px per second
  val transitionPeriods = 1.5f
  val minWaveEndpoint = 0f
  val matchedWaveEndpoint = 1f
  val transitionEnabled = true

  // Animate height fraction based on paused state and scrubbing state
  LaunchedEffect(isPaused, isScrubbing, useWavySeekbar) {
    if (!useWavySeekbar) {
      heightFraction = 0f
      return@LaunchedEffect
    }

    scope.launch {
      val shouldFlatten = isPaused || isScrubbing
      val targetHeight = if (shouldFlatten) 0f else 1f
      val duration = if (shouldFlatten) 550 else 800
      val startDelay = if (shouldFlatten) 0L else 60L

      kotlinx.coroutines.delay(startDelay)

      val animator = Animatable(heightFraction)
      animator.animateTo(
        targetValue = targetHeight,
        animationSpec =
          tween(
            durationMillis = duration,
            easing = LinearEasing,
          ),
      ) {
        heightFraction = value
      }
    }
  }

  // Animate wave movement only when not paused
  LaunchedEffect(isPaused, useWavySeekbar) {
    if (isPaused || !useWavySeekbar) return@LaunchedEffect

    var lastFrameTime = withFrameMillis { it }
    while (isActive) {
      withFrameMillis { frameTimeMillis ->
        val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
        phaseOffset += deltaTime * phaseSpeed
        phaseOffset %= waveLength
        lastFrameTime = frameTimeMillis
      }
    }
  }

  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(48.dp),
  ) {
    val strokeWidth = 5.dp.toPx()
    val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    val totalWidth = size.width
    val totalProgressPx = totalWidth * progress
    val centerY = size.height / 2f

    // Calculate wave progress
    val waveProgressPx =
      if (!transitionEnabled || progress > matchedWaveEndpoint) {
        totalWidth * progress
      } else {
        val t = (progress / matchedWaveEndpoint).coerceIn(0f, 1f)
        totalWidth * (minWaveEndpoint + (matchedWaveEndpoint - minWaveEndpoint) * t)
      }

    // Helper function to compute amplitude
    fun computeAmplitude(
      x: Float,
      sign: Float,
    ): Float =
      if (transitionEnabled) {
        val length = transitionPeriods * waveLength
        val coeff = ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
        sign * heightFraction * lineAmplitude * coeff
      } else {
        sign * heightFraction * lineAmplitude
      }

    // Build wavy path for played portion
    val path = Path()
    val waveStart = -phaseOffset - waveLength / 2f
    val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx

    path.moveTo(waveStart, centerY)

    var currentX = waveStart
    var waveSign = 1f
    var currentAmp = computeAmplitude(currentX, waveSign)
    val dist = waveLength / 2f

    while (currentX < waveEnd) {
      waveSign = -waveSign
      val nextX = currentX + dist
      val midX = currentX + dist / 2f
      val nextAmp = computeAmplitude(nextX, waveSign)

      path.cubicTo(
        midX,
        centerY + currentAmp,
        midX,
        centerY + nextAmp,
        nextX,
        centerY + nextAmp,
      )

      currentAmp = nextAmp
      currentX = nextX
    }

    // Draw path up to progress position using clipping
    val clipTop = lineAmplitude + strokeWidth
    val gapHalf = 1.dp.toPx()

    fun drawPathWithGaps(
      startX: Float,
      endX: Float,
      color: Color,
    ) {
      if (endX <= startX) return
      if (duration <= 0f) {
        clipRect(
          left = startX,
          top = centerY - clipTop,
          right = endX,
          bottom = centerY + clipTop,
        ) {
          drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
        }
        return
      }
      val gaps =
        chapters
          .map { (it.start / duration).coerceIn(0f, 1f) * totalWidth }
          .filter { it in startX..endX }
          .sorted()
          .map { x -> (x - gapHalf).coerceAtLeast(startX) to (x + gapHalf).coerceAtMost(endX) }

      var segmentStart = startX
      for ((gapStart, gapEnd) in gaps) {
        if (gapStart > segmentStart) {
          clipRect(
            left = segmentStart,
            top = centerY - clipTop,
            right = gapStart,
            bottom = centerY + clipTop,
          ) {
            drawPath(
              path = path,
              color = color,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }
        segmentStart = gapEnd
      }
      if (segmentStart < endX) {
        clipRect(
          left = segmentStart,
          top = centerY - clipTop,
          right = endX,
          bottom = centerY + clipTop,
        ) {
          drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
        }
      }
    }

    // Played segment
    drawPathWithGaps(0f, totalProgressPx, primaryColor)

    if (transitionEnabled) {
      val disabledAlpha = 77f / 255f
      drawPathWithGaps(totalProgressPx, totalWidth, primaryColor.copy(alpha = disabledAlpha))
    } else {
      drawLine(
        color = surfaceVariant.copy(alpha = 0.4f),
        start = Offset(totalProgressPx, centerY),
        end = Offset(totalWidth, centerY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }

    // Draw round cap
    val startAmp = kotlin.math.cos(kotlin.math.abs(waveStart) / waveLength * (2f * kotlin.math.PI.toFloat()))
    drawCircle(
      color = primaryColor,
      radius = strokeWidth / 2f,
      center = Offset(0f, centerY + startAmp * lineAmplitude * heightFraction),
    )

    // Vertical Bar Thumb
    val barHalfHeight = (lineAmplitude + strokeWidth)
    val barWidth = 5.dp.toPx()

    if (barHalfHeight > 0.5f) {
        drawLine(
          color = primaryColor,
          start = Offset(totalProgressPx, centerY - barHalfHeight),
          end = Offset(totalProgressPx, centerY + barHalfHeight),
          strokeWidth = barWidth,
          cap = StrokeCap.Round,
        )
    }
  }
}

@Composable
fun VideoTimer(
  value: Float,
  isInverted: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  Text(
    modifier =
      modifier
        .fillMaxHeight()
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          onClick = onClick,
        )
        .wrapContentHeight(Alignment.CenterVertically),
    text = Utils.prettyTime(value.toInt(), isInverted),
    color = Color.White,
    textAlign = TextAlign.Center,
  )
}

@Composable
fun StandardSeekbar(
    position: Float,
    duration: Float,
    chapters: ImmutableList<Segment>,
    isPaused: Boolean = false,
    isScrubbing: Boolean = false,
    useWavySeekbar: Boolean = false,
    seekbarStyle: SeekbarStyle = SeekbarStyle.Standard,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animation state (same as SquigglySeekbar)
    var heightFraction by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    
    // Animate height fraction based on paused state and scrubbing state (same as SquigglySeekbar)
    LaunchedEffect(isPaused, isScrubbing) {
        scope.launch {
            val shouldFlatten = isPaused || isScrubbing
            val targetHeight = if (shouldFlatten) 0.7f else 1f // Slightly less dramatic for standard seekbar
            val animationDuration = if (shouldFlatten) 550 else 800
            val startDelay = if (shouldFlatten) 0L else 60L

            kotlinx.coroutines.delay(startDelay)

            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(
                    durationMillis = animationDuration,
                    easing = LinearEasing,
                ),
            ) {
                heightFraction = value
            }
        }
    }
    
    val isThick = seekbarStyle == SeekbarStyle.Thick
    val baseTrackHeight = if (isThick) 16.dp else 8.dp
    val trackHeightDp = baseTrackHeight * heightFraction // Apply animation to track height
    val thumbWidth = 6.dp
    val thumbHeight = if (isThick) 16.dp else 24.dp
    val thumbShape = if (isThick) RoundedCornerShape(thumbWidth / 2) else CircleShape

    Slider(
        value = position,
        onValueChange = onSeek,
        onValueChangeFinished = onSeekFinished,
        valueRange = 0f..duration.coerceAtLeast(0.1f),
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        track = { sliderState ->
            val disabledAlpha = 0.3f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp),
            ) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f

                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)

                val playedPx = size.width * playedFraction
                val trackHeight = size.height
                
                // Radius for the outer ends of the seekbar
                val outerRadius = trackHeight / 2f
                
                // MODIFIED: For Thick style, inner corners now match the outer rounding
                val innerRadius = if (isThick) outerRadius else 2.dp.toPx()
                
                val thumbTrackGapSize = 14.dp.toPx()
                val gapHalf = thumbTrackGapSize / 2f
                val chapterGapHalf = 1.dp.toPx()
                
                val thumbGapStart = (playedPx - gapHalf).coerceIn(0f, size.width)
                val thumbGapEnd = (playedPx + gapHalf).coerceIn(0f, size.width)
                
                val chapterGaps = chapters
                    .map { (it.start / duration).coerceIn(0f, 1f) * size.width }
                    .filter { it > 0f && it < size.width }
                    .map { x -> (x - chapterGapHalf) to (x + chapterGapHalf) }
                
                fun drawSegment(startX: Float, endX: Float, color: Color) {
                    if (endX - startX < 0.5f) return
                    
                    val path = Path()
                    val isOuterLeft = startX <= 0.5f
                    val isInnerLeft = kotlin.math.abs(startX - thumbGapEnd) < 0.5f
                    
                    val cornerRadiusLeft = when {
                        isOuterLeft -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                        isInnerLeft -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                        else -> androidx.compose.ui.geometry.CornerRadius.Zero
                    }

                    val isOuterRight = endX >= size.width - 0.5f
                    val isInnerRight = kotlin.math.abs(endX - thumbGapStart) < 0.5f

                    val cornerRadiusRight = when {
                        isOuterRight -> androidx.compose.ui.geometry.CornerRadius(outerRadius)
                        isInnerRight -> androidx.compose.ui.geometry.CornerRadius(innerRadius)
                        else -> androidx.compose.ui.geometry.CornerRadius.Zero
                    }
                    
                    path.addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = startX,
                            top = 0f,
                            right = endX,
                            bottom = trackHeight,
                            topLeftCornerRadius = cornerRadiusLeft,
                            bottomLeftCornerRadius = cornerRadiusLeft,
                            topRightCornerRadius = cornerRadiusRight,
                            bottomRightCornerRadius = cornerRadiusRight
                        )
                    )
                    drawPath(path, color)
                }
                
                fun drawRangeWithGaps(
                    rangeStart: Float, 
                    rangeEnd: Float, 
                    gaps: List<Pair<Float, Float>>, 
                    color: Color
                ) {
                    if (rangeEnd <= rangeStart) return
                    val relevantGaps = gaps
                        .filter { (gStart, gEnd) -> gEnd > rangeStart && gStart < rangeEnd }
                        .sortedBy { it.first }
                    
                    var currentPos = rangeStart
                    for ((gStart, gEnd) in relevantGaps) {
                        val segmentEnd = gStart.coerceAtMost(rangeEnd)
                        if (segmentEnd > currentPos) {
                            drawSegment(currentPos, segmentEnd, color)
                        }
                        currentPos = gEnd.coerceAtLeast(currentPos)
                    }
                    if (currentPos < rangeEnd) {
                        drawSegment(currentPos, rangeEnd, color)
                    }
                }
                
                // 1. Unplayed Background
                drawRangeWithGaps(thumbGapEnd, size.width, chapterGaps, primaryColor.copy(alpha = disabledAlpha))
                
                // 2. Played
                if (thumbGapStart > 0) {
                    drawRangeWithGaps(0f, thumbGapStart, chapterGaps, primaryColor)
                }
            }
        },
            thumb = {
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .background(primaryColor, thumbShape)
                )
            }
        )
    }

@Preview
@Composable
private fun PreviewSeekBar() {
  SeekbarWithTimers(
    position = 30f,
    duration = 180f,
    onValueChange = {},
    onValueChangeFinished = {},
    timersInverted = Pair(false, true),
    positionTimerOnClick = {},
    durationTimerOnCLick = {},
    chapters = persistentListOf(),
    paused = false,
  )
}

@Composable
fun SeekbarPreview(
  style: SeekbarStyle,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "seekbar_preview")
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(3000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "progress"
  )
  val duration = 100f
  val position = progress * duration

  // Dummy chapters for preview to visualize chapter separation
  val dummyChapters = persistentListOf(
    Segment(name = "Chapter 1", start = 0f),
    Segment(name = "Chapter 2", start = duration * 0.35f),
    Segment(name = "Chapter 3", start = duration * 0.65f),
  )
  
  Box(
    modifier = modifier
      .height(32.dp),
    contentAlignment = Alignment.Center
  ) {
    // Seekbar content
    when (style) {
      SeekbarStyle.Standard -> {
        StandardSeekbar(
          position = position,
          duration = duration,
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Standard,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Wavy -> {
        SquigglySeekbar(
          position = position,
          duration = duration,
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Wavy,
          onSeek = {},
          onSeekFinished = {},
        )
      }
      SeekbarStyle.Thick -> {
        StandardSeekbar(
          position = position,
          duration = duration,
          chapters = dummyChapters,
          isPaused = false,
          isScrubbing = false,
          useWavySeekbar = true,
          seekbarStyle = SeekbarStyle.Thick,
          onSeek = {},
          onSeekFinished = {},
        )
      }
    }
    
    // Invisible overlay that intercepts all touch events and triggers onClick
    if (onClick != null) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // No ripple on overlay itself
            onClick = onClick
          )
      )
    }
  }
}
