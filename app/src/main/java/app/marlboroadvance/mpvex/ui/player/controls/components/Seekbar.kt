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
// Correct import assuming usage of preference delegate extension
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

  // Animation state
  var phaseOffset by remember { mutableFloatStateOf(0f) }
  var heightFraction by remember { mutableFloatStateOf(1f) }
  
  // Animation for shrinking end bar (Wavy/Simple) or Circle thumb (Circular) on press
  val thumbScale by animateFloatAsState(
      targetValue = if (isScrubbing && shrinkOnPress) 0f else 1f, 
      animationSpec = tween(durationMillis = 200),
      label = "thumbScale"
  )
  // For Circular: target is slightly smaller, not zero
  val circularThumbScale by animateFloatAsState(
      targetValue = if (isScrubbing && shrinkOnPress) 0.8f else 1f,
      animationSpec = tween(durationMillis = 200),
      label = "circularThumbScale"
  )

  val scope = rememberCoroutineScope()

  // Wave parameters - matching Gramophone exactly
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
          detectTapGestures { offset ->
            val newPosition = (offset.x / size.width) * duration
            onSeek(newPosition.coerceIn(0f, duration))
            onSeekFinished()
          }
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { },
            onDragEnd = { onSeekFinished() },
            onDragCancel = { onSeekFinished() },
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

    // Calculate wave progress with matched endpoint logic (from Gramophone)
    val waveProgressPx =
      if (!transitionEnabled || progress > matchedWaveEndpoint) {
        totalWidth * progress
      } else {
        // Linear interpolation between minWaveEndpoint and matchedWaveEndpoint
        val t =
          (progress / matchedWaveEndpoint).coerceIn(0f, 1f)
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

    // Draw path up to progress position using clipping (with chapter gaps)
    val clipTop = lineAmplitude + strokeWidth
    val gapHalf = 1.dp.toPx() // half width of gap around chapter

    // Helper to draw segmented clipped path between [startX, endX]
    fun drawPathWithGaps(
      startX: Float,
      endX: Float,
      color: Color,
    ) {
      if (endX <= startX) return
      // If duration is zero or negative, avoid division and just draw full segment
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
      // Build sorted list of gap ranges within [startX, endX]
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

    // Played segment with gaps
    drawPathWithGaps(0f, totalProgressPx, primaryColor)

    // Read-ahead buffer segment (between current position and buffered position)
    if (totalReadAheadPx > totalProgressPx) {
      val bufferAlpha = 0.5f
      drawPathWithGaps(totalProgressPx, totalReadAheadPx, primaryColor.copy(alpha = bufferAlpha))
    }

    if (transitionEnabled) {
      val disabledAlpha = 77f / 255f
      // Unplayed segment (dimmed) with the same gaps - start from readAhead position
      val unplayedStart = maxOf(totalProgressPx, totalReadAheadPx)
      drawPathWithGaps(unplayedStart, totalWidth, primaryColor.copy(alpha = disabledAlpha))
    } else {
      // No transition: draw a flat line to the end (hidden under thumb in original)
      val flatLineStart = maxOf(totalProgressPx, totalReadAheadPx)
      drawLine(
        color = surfaceVariant.copy(alpha = 0.4f),
        start = Offset(flatLineStart, centerY),
        end = Offset(totalWidth, centerY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }

    // Chapter markers are represented by gaps; no dots needed

    // Draw round cap at the beginning of the wave
    val startAmp = kotlin.math.cos(kotlin.math.abs(waveStart) / waveLength * (2f * kotlin.math.PI.toFloat()))
    drawCircle(
      color = primaryColor,
      radius = strokeWidth / 2f,
      center = Offset(0f, centerY + startAmp * lineAmplitude * heightFraction),
    )

    // Draw thumb
    if (seekbarStyle == SeekbarStyle.Circular) {
         // Draw Circle Thumb for Circular Style
         val thumbRadius = 10.dp.toPx() * circularThumbScale
         drawCircle(
            color = primaryColor,
            radius = thumbRadius,
            center = Offset(totalProgressPx, centerY)
         )
    } else {
        // Draw Vertical Bar for Wavy and Simple
        // Bar height shrinks to 0 on press if shrinkOnPress is enabled
        var barCurrentHeightFraction = 1f
        if (shrinkOnPress) {
            barCurrentHeightFraction = thumbScale
        }
        
        val barHalfHeight = (lineAmplitude + strokeWidth) * barCurrentHeightFraction
        val barWidth = 5.dp.toPx()

        if (barHalfHeight > 0.5f) { // Only draw if visible
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
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  shrinkOnPress: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed
    
    val targetWidth = if (shrinkOnPress && isInteracting) 2.dp else 4.dp
    val thumbWidth by animateDpAsState(targetValue = targetWidth, label = "thumbWidth")

    Slider(
        value = position,
        onValueChange = onSeek,
        onValueChangeFinished = onSeekFinished,
        valueRange = 0f..duration.coerceAtLeast(0.1f),
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                thumbTrackGapSize = 6.dp
            )
        },
        thumb = {
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
            )
        }
    )
}

@Composable
fun DiamondSeekbar(
  position: Float,
  duration: Float,
  readAheadValue: Float,
  onSeek: (Float) -> Unit,
  onSeekFinished: () -> Unit,
  shrinkOnPress: Boolean = true,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed
    
    val scale by animateFloatAsState(
        targetValue = if (shrinkOnPress && isInteracting) 0.8f else 1f,
        label = "diamondScale"
    )

    // Animation state for wave
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    // We keep heightFraction at 1f for Diamond style unless we want it to flatten on pause, 
    // but user image shows typical wavy line. Let's keep it animated.
    val useWavySeekbar = true
    val isPaused = false 
    val isScrubbing = isInteracting

    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 10f
    
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
        .height(32.dp)
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            val newPosition = (offset.x / size.width) * duration
            onSeek(newPosition.coerceIn(0f, duration))
            onSeekFinished()
          }
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { },
            onDragEnd = { onSeekFinished() },
            onDragCancel = { onSeekFinished() },
          ) { change, _ ->
            change.consume()
            val newPosition = (change.position.x / size.width) * duration
            onSeek(newPosition.coerceIn(0f, duration))
            onSeekFinished()
          }
        },
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val progressPx = width * progress
        
        // --- Draw Wavy Played Part (Left) ---
        val path = Path()
        val waveStart = -phaseOffset - waveLength / 2f
        
        path.moveTo(waveStart, centerY)
        var currentX = waveStart
        var waveSign = 1f
        
        // Compute path slightly past progress to ensure continuity
        val waveEnd = progressPx + waveLength 

         // Helper function to compute amplitude
        fun computeAmplitude(x: Float, sign: Float): Float {
             return sign * lineAmplitude // Simple constant amplitude
        }
        
        val dist = waveLength / 2f
        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val midX = currentX + dist / 2f
            val currentAmp = computeAmplitude(currentX, -waveSign) 
            val nextAmp = computeAmplitude(nextX, waveSign)

            path.cubicTo(
                midX, centerY + currentAmp,
                midX, centerY + nextAmp,
                nextX, centerY + nextAmp
            )
            currentX = nextX
        }
        
        // Clip and draw played path
        clipRect(right = progressPx) {
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // --- Draw Straight Unplayed Part (Right) ---
        // Thick rounded rect from progressPx to end
        val unplayedStart = progressPx + 15.dp.toPx() // Gap for thumb
        if (unplayedStart < width) {
            val trackHeight = 8.dp.toPx()
             drawRoundRect(
                color = surfaceVariant.copy(alpha = 0.3f), // Dim color
                topLeft = Offset(unplayedStart, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(width - unplayedStart, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
            )
        }
        
        
        // --- Draw Diamond Thumb ---
        val thumbX = progressPx
        val thumbSize = 20.dp.toPx() * scale
        
        val thumbPath = Path().apply {
            moveTo(thumbX, centerY - thumbSize / 2f) // Top
            lineTo(thumbX + thumbSize / 2f, centerY) // Right
            lineTo(thumbX, centerY + thumbSize / 2f) // Bottom
            lineTo(thumbX - thumbSize / 2f, centerY) // Left
            close()
        }
        
        drawPath(path = thumbPath, color = primaryColor) // Filled diamond 
        // Optional border if needed, but image shows solid
    }
}

@Composable
fun SeekbarPreview(
  style: SeekbarStyle,
  modifier: Modifier = Modifier,
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
            readAheadValue = position, // Simple buffer simulation
            onSeek = {},
            onSeekFinished = {},
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
            shrinkOnPress = true, // Force true for preview or pass param?
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
            shrinkOnPress = true,
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
            shrinkOnPress = true,
            onSeek = {},
            onSeekFinished = {},
          )
        }
        SeekbarStyle.Diamond -> {
             DiamondSeekbar(
            position = position,
            duration = duration,
            readAheadValue = position,
            onSeek = {},
            onSeekFinished = {},
          )
        }
      }
  }
}
