package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import app.marlboroadvance.mpvex.ui.player.controls.LocalPlayerButtonsClickEvent
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.preferences.SeekbarStyle
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import org.koin.compose.koinInject
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.marlboroadvance.mpvex.preferences.preference.collectAsState

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
  readAheadValue: Float = position,
  seekbarStyle: SeekbarStyle = SeekbarStyle.Wavy,
  modifier: Modifier = Modifier,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val shrinkOnPress by appearancePreferences.shrinkOnPress.collectAsState()
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

    // Seekbar
    Box(
      modifier =
        Modifier
          .weight(1f)
          .height(48.dp),
      contentAlignment = Alignment.Center,
    ) {
      when (seekbarStyle) {
        SeekbarStyle.Standard -> {
          StandardSeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            shrinkOnPress = shrinkOnPress,
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
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Wavy,
            shrinkOnPress = shrinkOnPress,
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
        SeekbarStyle.Circular -> {
           SquigglySeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Circular,
            shrinkOnPress = shrinkOnPress,
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
        SeekbarStyle.Simple -> {
             SquigglySeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
            isPaused = paused,
            isScrubbing = isUserInteracting,
            useWavySeekbar = false,
            seekbarStyle = SeekbarStyle.Simple, 
            shrinkOnPress = shrinkOnPress,
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
        SeekbarStyle.Diamond -> {
             DiamondSeekbar(
            position = if (isUserInteracting) userPosition else animatedPosition.value,
            duration = duration,
            readAheadValue = readAheadValue,
            chapters = chapters,
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
            shrinkOnPress = shrinkOnPress,
          )
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
  readAheadValue: Float,
  chapters: ImmutableList<Segment>,
  isPaused: Boolean,
  isScrubbing: Boolean,
  useWavySeekbar: Boolean,
  seekbarStyle: SeekbarStyle,
  shrinkOnPress: Boolean,
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
  var heightFraction by remember { mutableFloatStateOf(useWavySeekbar.let { if (it) 1f else 0f }) }
  
  // Animation for shrinking end bar (Wavy/Simple) or Circle thumb (Circular) on press
  val thumbScale by animateFloatAsState(
      targetValue = if (isInteracting && shrinkOnPress) 0f else 1f, 
      animationSpec = tween(durationMillis = 200),
      label = "thumbScale"
  )
  // For Circular: target is slightly smaller, not zero
  val circularThumbScale by animateFloatAsState(
      targetValue = if (isInteracting && shrinkOnPress) 0.8f else 1f,
      animationSpec = tween(durationMillis = 200),
      label = "circularThumbScale"
  )

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
        .height(48.dp)
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            },
            onTap = { offset ->
                val newPosition = (offset.x / size.width) * duration
                onSeek(newPosition.coerceIn(0f, duration))
                onSeekFinished()
            }
          )
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { isDragged = true },
            onDragEnd = { 
                isDragged = false
                onSeekFinished() 
            },
            onDragCancel = { 
                isDragged = false
                onSeekFinished() 
            },
          ) { change, _ ->
            change.consume()
            val newPosition = (change.position.x / size.width) * duration
            onSeek(newPosition.coerceIn(0f, duration))
          }
        },
  ) {
    val strokeWidth = 5.dp.toPx()
    val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    val readAheadProgress = if (duration > 0f) (readAheadValue / duration).coerceIn(0f, 1f) else 0f
    val totalWidth = size.width
    val totalProgressPx = totalWidth * progress
    val totalReadAheadPx = totalWidth * readAheadProgress
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
    val gapHalf = 1.5.dp.toPx()

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

    // Buffer segment
    if (totalReadAheadPx > totalProgressPx) {
      val bufferAlpha = 0.5f
      drawPathWithGaps(totalProgressPx, totalReadAheadPx, primaryColor.copy(alpha = bufferAlpha))
    }

    if (transitionEnabled) {
      val disabledAlpha = 0.2f // Darker/Neutral background
      val unplayedStart = maxOf(totalProgressPx, totalReadAheadPx)
      val unplayedColor = Color.White.copy(alpha = disabledAlpha)
      drawPathWithGaps(unplayedStart, totalWidth, unplayedColor)
    } else {
      val flatLineStart = maxOf(totalProgressPx, totalReadAheadPx)
      drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = Offset(flatLineStart, centerY),
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

    // SquigglySeekbar (Circular Thumb for Circular and Standard styles)
    if (seekbarStyle == SeekbarStyle.Circular || seekbarStyle == SeekbarStyle.Standard) {
         val thumbRadius = 10.dp.toPx() * circularThumbScale
         drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(totalProgressPx, centerY)
         )
    } else {
        // Vertical Bar (Wavy/Simple Thumb)
        var barCurrentHeightFraction = 1f
        if (shrinkOnPress) {
            barCurrentHeightFraction = thumbScale
        }
        val baseHeight = if (lineAmplitude > 0f) lineAmplitude + strokeWidth else 8.dp.toPx()
        val barHalfHeight = baseHeight * barCurrentHeightFraction
        val barWidth = 5.dp.toPx()

        if (barHalfHeight > 0.5f) {
            drawLine(
              color = Color.White,
              start = Offset(totalProgressPx, centerY - barHalfHeight),
              end = Offset(totalProgressPx, centerY + barHalfHeight),
              strokeWidth = barWidth,
              cap = StrokeCap.Round,
            )
        }
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
  readAheadValue: Float,
  chapters: ImmutableList<Segment>,
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  shrinkOnPress: Boolean = true,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Manual Interaction Tracking
    var isPressed by remember { mutableStateOf(false) }
    var isDragged by remember { mutableStateOf(false) }
    val isInteracting = isPressed || isDragged
    
    // Animate thumb width: 4.dp (Normal) -> 2.dp (Pressed)
    val thumbWidth by animateDpAsState(
        targetValue = if (shrinkOnPress && isInteracting) 2.dp else 4.dp,
        label = "thumbWidth"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { offset ->
                        val newPosition = (offset.x / size.width) * duration
                        onSeek(newPosition.coerceIn(0f, duration))
                        onSeekFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragged = true },
                    onDragEnd = {
                        isDragged = false
                        onSeekFinished()
                    },
                    onDragCancel = {
                        isDragged = false
                        onSeekFinished()
                    },
                ) { change, _ ->
                    change.consume()
                    val newPosition = (change.position.x / size.width) * duration
                    onSeek(newPosition.coerceIn(0f, duration))
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokeWidth = 4.dp.toPx()
        val gapHalf = 2.dp.toPx() // Gap around chapters
        val thumbGap = 8.dp.toPx() // Gap around the thumb
        
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val progressPx = width * progress
        
        fun drawWithGaps(startX: Float, endX: Float, color: Color, excludeThumbGap: Boolean = false) {
            if (endX <= startX || duration <= 0f) return
            
            val allGaps = mutableListOf<Pair<Float, Float>>()
            
            chapters
                .map { (it.start / duration).coerceIn(0f, 1f) * width }
                .filter { it in startX..endX }
                .sorted()
                .mapTo(allGaps) { x -> (x - gapHalf).coerceAtLeast(startX) to (x + gapHalf).coerceAtMost(endX) }
            
            // Draw segments
            var segmentStart = startX
            for ((gapStart, gapEnd) in allGaps.sortedBy { it.first }) {
                if (gapStart > segmentStart) {
                    drawLine(
                        color = color,
                        start = Offset(segmentStart, centerY),
                        end = Offset(gapStart, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                segmentStart = gapEnd
            }
            if (segmentStart < endX) {
                drawLine(
                    color = color,
                    start = Offset(segmentStart, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Draw played segment (up to thumb gap)
        val playedEnd = (progressPx - thumbGap).coerceAtLeast(0f)
        drawWithGaps(0f, playedEnd, primaryColor)
        
        // Draw unplayed segment (after thumb gap)
        val unplayedStart = progressPx + thumbGap
        val trackColor = Color(0xFF3D3D3D) // Dark gray like in image
        if (unplayedStart < width) {
            drawWithGaps(unplayedStart, width, trackColor)
        }
        
        // Draw vertical thumb bar
        val thumbWidthPx = thumbWidth.toPx()
        val thumbHeight = 24.dp.toPx()
        
        drawLine(
            color = Color.White,
            start = Offset(progressPx, centerY - thumbHeight / 2f),
            end = Offset(progressPx, centerY + thumbHeight / 2f),
            strokeWidth = thumbWidthPx,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun DiamondSeekbar(
  position: Float,
  duration: Float,
  readAheadValue: Float,
  chapters: ImmutableList<Segment>,
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  shrinkOnPress: Boolean = true,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Manual Interaction Tracking
    var isPressed by remember { mutableStateOf(false) }
    var isDragged by remember { mutableStateOf(false) }
    val isInteracting = isPressed || isDragged
    
    // Animate Scale: 1f (Normal) -> 0.7f (Small Pressed)
    val scale by animateFloatAsState(
        targetValue = if (shrinkOnPress && isInteracting) 0.7f else 1f,
        label = "diamondScale",
        animationSpec = tween(durationMillis = 200, easing = LinearEasing)
    )

    // Animate Rotation: 45f (Diamond) -> 0f (Square)
    val rotation by animateFloatAsState(
        targetValue = if (shrinkOnPress && isInteracting) 0f else 45f,
        label = "diamondRotation",
        animationSpec = tween(durationMillis = 200, easing = LinearEasing)
    )

    var phaseOffset by remember { mutableFloatStateOf(0f) }
    val waveLength = 60f 
    val maxAmplitude = 12f 
    val phaseSpeed = 25f 

    LaunchedEffect(Unit) {
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
      Modifier
        .fillMaxWidth()
        .height(40.dp)
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            },
            onTap = { offset ->
                val newPosition = (offset.x / size.width) * duration
                onSeek(newPosition.coerceIn(0f, duration))
                onSeekFinished()
            }
          )
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { isDragged = true },
            onDragEnd = { 
                isDragged = false 
                onSeekFinished()
            },
            onDragCancel = { 
                isDragged = false 
                onSeekFinished()
            },
          ) { change, _ ->
            change.consume()
            val newPosition = (change.position.x / size.width) * duration
            onSeek(newPosition.coerceIn(0f, duration))
          }
        },
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val progressPx = width * progress
        
        val strokeWidth = 3.dp.toPx()
        val gapHalf = 2.dp.toPx() 
        val thumbSize = 14.dp.toPx() * scale
        val thumbGap = thumbSize * 1.8f 
        
        val rampUpZone = width * 0.30f 
        val constantZoneStart = width * 0.50f
        
        fun getAmplitudeAtX(xPos: Float): Float {
            val playedWidth = progressPx
            if (playedWidth <= 0f) return 0f
            
            val relativePos = (xPos / playedWidth).coerceIn(0f, 1f)
            
            return when {
                relativePos < 0.30f -> {
                    val t = relativePos / 0.30f
                    maxAmplitude * (0.15f + 0.85f * t * t) 
                }
                relativePos < 0.50f -> {
                    maxAmplitude
                }
                else -> maxAmplitude
            }
        }
        
        val wavyPath = Path()
        val waveStart = -phaseOffset - waveLength
        
        wavyPath.moveTo(waveStart, centerY)
        
        var x = waveStart
        var sign = 1f
        while (x < width + waveLength) {
            val halfWave = waveLength / 2f
            val nextX = x + halfWave
            val midX = x + halfWave * 0.5f
            
            val amp = getAmplitudeAtX(midX.coerceIn(0f, progressPx))
            
            wavyPath.cubicTo(
                midX, centerY + sign * amp,
                midX, centerY + sign * amp,
                nextX, centerY
            )
            x = nextX
            sign *= -1f
        }
        
        val clipTop = maxAmplitude + strokeWidth + 4f
        
        fun drawWavyWithGaps(startX: Float, endX: Float, color: Color) {
            if (endX <= startX) return
            
            val chapterGaps = if (duration > 0f) {
                chapters
                    .map { (it.start / duration).coerceIn(0f, 1f) * width }
                    .filter { it in startX..endX }
                    .sorted()
                    .map { cx -> (cx - gapHalf).coerceAtLeast(startX) to (cx + gapHalf).coerceAtMost(endX) }
            } else emptyList()
            
            var segStart = startX
            for ((gapStart, gapEnd) in chapterGaps) {
                if (gapStart > segStart) {
                    clipRect(left = segStart, top = centerY - clipTop, right = gapStart, bottom = centerY + clipTop) {
                        drawPath(wavyPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }
                }
                segStart = gapEnd
            }
            if (segStart < endX) {
                clipRect(left = segStart, top = centerY - clipTop, right = endX, bottom = centerY + clipTop) {
                    drawPath(wavyPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
            }
        }
        
        val playedEnd = (progressPx - thumbGap / 2f).coerceAtLeast(0f)
        drawWavyWithGaps(0f, playedEnd, primaryColor)
        
        val trackHeight = 8.dp.toPx()
        val unplayedStart = progressPx + thumbGap / 2f
        val trackColor = Color(0xFF3D3D3D) 
        
        if (unplayedStart < width) {
            // Calculate chapter gaps for unplayed section
            val unplayedGaps = if (duration > 0f) {
                chapters
                    .map { (it.start / duration).coerceIn(0f, 1f) * width }
                    .filter { it in unplayedStart..width }
                    .sorted()
                    .map { cx -> (cx - gapHalf).coerceAtLeast(unplayedStart) to (cx + gapHalf).coerceAtMost(width) }
            } else emptyList()
            
            var currentStart = unplayedStart
            for ((gStart, gEnd) in unplayedGaps) {
                if (gStart > currentStart) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(currentStart, centerY - trackHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(gStart - currentStart, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
                    )
                }
                currentStart = gEnd
            }
            if (currentStart < width) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(currentStart, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(width - currentStart, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
                )
            }
        }
        
        // --- Draw Diamond Thumb ---
        withTransform({
            rotate(rotation, pivot = Offset(progressPx, centerY))
        }) {
            val rectPath = Path().apply {
                addRect(
                    androidx.compose.ui.geometry.Rect(
                        left = progressPx - thumbSize / 2f,
                        top = centerY - thumbSize / 2f,
                        right = progressPx + thumbSize / 2f,
                        bottom = centerY + thumbSize / 2f
                    )
                )
            }
            drawPath(path = rectPath, color = Color.White)
        }
    }
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
    readAheadValue = 90f,
  )
}

@Composable
fun SeekbarPreview(
  style: SeekbarStyle,
  modifier: Modifier = Modifier,
  shrinkOnPress: Boolean = true,
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

  Box(
    modifier = modifier.height(32.dp),
    contentAlignment = Alignment.Center
  ) {
      when (style) {
        SeekbarStyle.Standard -> {
          StandardSeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            chapters = persistentListOf(),
            onSeek = {},
            onSeekFinished = {},
            shrinkOnPress = shrinkOnPress,
          )
        }
        SeekbarStyle.Wavy -> {
          SquigglySeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            chapters = persistentListOf(),
            isPaused = false,
            isScrubbing = false,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Wavy,
            shrinkOnPress = shrinkOnPress,
            onSeek = {},
            onSeekFinished = {},
          )
        }
        SeekbarStyle.Circular -> {
          SquigglySeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            chapters = persistentListOf(),
            isPaused = false,
            isScrubbing = false,
            useWavySeekbar = true,
            seekbarStyle = SeekbarStyle.Circular,
            shrinkOnPress = shrinkOnPress,
            onSeek = {},
            onSeekFinished = {},
          )
        }
        SeekbarStyle.Simple -> {
             SquigglySeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            chapters = persistentListOf(),
            isPaused = false,
            isScrubbing = false,
            useWavySeekbar = false,
            seekbarStyle = SeekbarStyle.Simple,
            shrinkOnPress = shrinkOnPress,
            onSeek = {},
            onSeekFinished = {},
          )
        }
        SeekbarStyle.Diamond -> {
             DiamondSeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            chapters = persistentListOf(),
            onSeek = {},
            onSeekFinished = {},
            shrinkOnPress = shrinkOnPress
          )
        }
      }
  }
}