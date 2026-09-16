package app.marlboroadvance.mpvex.ui.player.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max

/**
 * Composable that renders a smooth, YouTube-like Ambient Mode glow around the video display area.
 *
 * Fills letterbox (top/bottom) and pillarbox (left/right) margins completely with seamless,
 * soft ambient light without any black gaps or abrupt color changes at screen edges.
 */
@Composable
fun AmbientModeGlow(
  ambientColors: AmbientColors,
  enabled: Boolean,
  intensity: Float,
  videoAspect: Double?,
  isCropMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  if (!enabled || isCropMode) return

  // Calm, YouTube-like 1.4-second smooth color glide
  val animSpec = tween<Color>(durationMillis = 1400, easing = LinearEasing)

  val animatedTop by animateColorAsState(
    targetValue = ambientColors.top,
    animationSpec = animSpec,
    label = "ambient_glow_top",
  )
  val animatedBottom by animateColorAsState(
    targetValue = ambientColors.bottom,
    animationSpec = animSpec,
    label = "ambient_glow_bottom",
  )
  val animatedLeft by animateColorAsState(
    targetValue = ambientColors.left,
    animationSpec = animSpec,
    label = "ambient_glow_left",
  )
  val animatedRight by animateColorAsState(
    targetValue = ambientColors.right,
    animationSpec = animSpec,
    label = "ambient_glow_right",
  )
  val animatedCenter by animateColorAsState(
    targetValue = ambientColors.center,
    animationSpec = animSpec,
    label = "ambient_glow_center",
  )

  val clampedIntensity = intensity.coerceIn(0.1f, 1.0f)

  Canvas(modifier = modifier.fillMaxSize()) {
    val sw = size.width
    val sh = size.height
    if (sw <= 0f || sh <= 0f) return@Canvas

    val va = videoAspect?.toFloat()?.takeIf { it > 0.001f } ?: (sw / sh)
    val sa = sw / sh

    val (videoWidth, videoHeight) = if (va >= sa) {
      sw to (sw / va)
    } else {
      (sh * va) to sh
    }

    val videoLeft = (sw - videoWidth) / 2f
    val videoRight = videoLeft + videoWidth
    val videoTop = (sh - videoHeight) / 2f
    val videoBottom = videoTop + videoHeight

    // Base ambient color for uniform background diffusion
    val baseWashColor = animatedCenter.copy(alpha = clampedIntensity * 0.35f)

    // 1. Top Letterbox Area (Completely filled, no black edge at the top)
    if (videoTop > 0.5f) {
      // Background wash
      drawRect(
        color = baseWashColor,
        topLeft = Offset(0f, 0f),
        size = Size(sw, videoTop),
      )

      // Directional gradient: glowing all the way to screen top
      val topBrush = Brush.verticalGradient(
        colors = listOf(
          animatedTop.copy(alpha = clampedIntensity * 0.55f), // Continuous glow at screen bezel
          animatedTop.copy(alpha = clampedIntensity * 0.78f),
          animatedTop.copy(alpha = clampedIntensity * 0.95f), // Peak glow adjacent to video
        ),
        startY = 0f,
        endY = videoTop,
      )
      drawRect(
        brush = topBrush,
        topLeft = Offset(0f, 0f),
        size = Size(sw, videoTop),
      )
    }

    // 2. Bottom Letterbox Area (Completely filled, no black edge at the bottom)
    if (videoBottom < sh - 0.5f) {
      val barHeight = sh - videoBottom

      // Background wash
      drawRect(
        color = baseWashColor,
        topLeft = Offset(0f, videoBottom),
        size = Size(sw, barHeight),
      )

      // Directional gradient: glowing all the way to screen bottom
      val bottomBrush = Brush.verticalGradient(
        colors = listOf(
          animatedBottom.copy(alpha = clampedIntensity * 0.95f), // Peak glow adjacent to video
          animatedBottom.copy(alpha = clampedIntensity * 0.78f),
          animatedBottom.copy(alpha = clampedIntensity * 0.55f), // Continuous glow at screen bezel
        ),
        startY = videoBottom,
        endY = sh,
      )
      drawRect(
        brush = bottomBrush,
        topLeft = Offset(0f, videoBottom),
        size = Size(sw, barHeight),
      )
    }

    // 3. Left Pillarbox Area (Completely filled, no black edge at the left)
    if (videoLeft > 0.5f) {
      // Background wash
      drawRect(
        color = baseWashColor,
        topLeft = Offset(0f, 0f),
        size = Size(videoLeft, sh),
      )

      // Directional gradient: glowing all the way to screen left
      val leftBrush = Brush.horizontalGradient(
        colors = listOf(
          animatedLeft.copy(alpha = clampedIntensity * 0.55f), // Continuous glow at screen bezel
          animatedLeft.copy(alpha = clampedIntensity * 0.78f),
          animatedLeft.copy(alpha = clampedIntensity * 0.95f), // Peak glow adjacent to video
        ),
        startX = 0f,
        endX = videoLeft,
      )
      drawRect(
        brush = leftBrush,
        topLeft = Offset(0f, 0f),
        size = Size(videoLeft, sh),
      )
    }

    // 4. Right Pillarbox Area (Completely filled, no black edge at the right)
    if (videoRight < sw - 0.5f) {
      val barWidth = sw - videoRight

      // Background wash
      drawRect(
        color = baseWashColor,
        topLeft = Offset(videoRight, 0f),
        size = Size(barWidth, sh),
      )

      // Directional gradient: glowing all the way to screen right
      val rightBrush = Brush.horizontalGradient(
        colors = listOf(
          animatedRight.copy(alpha = clampedIntensity * 0.95f), // Peak glow adjacent to video
          animatedRight.copy(alpha = clampedIntensity * 0.78f),
          animatedRight.copy(alpha = clampedIntensity * 0.55f), // Continuous glow at screen bezel
        ),
        startX = videoRight,
        endX = sw,
      )
      drawRect(
        brush = rightBrush,
        topLeft = Offset(videoRight, 0f),
        size = Size(barWidth, sh),
      )
    }

    // 5. Corner Diffusion Radial Blooms for seamless corner transitions
    drawCornerBlooms(
      videoLeft = videoLeft,
      videoTop = videoTop,
      videoRight = videoRight,
      videoBottom = videoBottom,
      topColor = animatedTop,
      bottomColor = animatedBottom,
      leftColor = animatedLeft,
      rightColor = animatedRight,
      intensity = clampedIntensity,
    )
  }
}

private fun DrawScope.drawCornerBlooms(
  videoLeft: Float,
  videoTop: Float,
  videoRight: Float,
  videoBottom: Float,
  topColor: Color,
  bottomColor: Color,
  leftColor: Color,
  rightColor: Color,
  intensity: Float,
) {
  val cornerRadius = max(videoLeft, videoTop).coerceAtLeast(40f)

  // Top-Left Corner
  if (videoLeft > 0.5f && videoTop > 0.5f) {
    val tlColor = blendColors(topColor, leftColor)
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(
          tlColor.copy(alpha = intensity * 0.85f),
          tlColor.copy(alpha = intensity * 0.45f),
        ),
        center = Offset(videoLeft, videoTop),
        radius = cornerRadius * 2f,
      ),
      topLeft = Offset(0f, 0f),
      size = Size(videoLeft, videoTop),
    )
  }

  // Top-Right Corner
  if (videoRight < size.width - 0.5f && videoTop > 0.5f) {
    val trColor = blendColors(topColor, rightColor)
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(
          trColor.copy(alpha = intensity * 0.85f),
          trColor.copy(alpha = intensity * 0.45f),
        ),
        center = Offset(videoRight, videoTop),
        radius = cornerRadius * 2f,
      ),
      topLeft = Offset(videoRight, 0f),
      size = Size(size.width - videoRight, videoTop),
    )
  }

  // Bottom-Left Corner
  if (videoLeft > 0.5f && videoBottom < size.height - 0.5f) {
    val blColor = blendColors(bottomColor, leftColor)
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(
          blColor.copy(alpha = intensity * 0.85f),
          blColor.copy(alpha = intensity * 0.45f),
        ),
        center = Offset(videoLeft, videoBottom),
        radius = cornerRadius * 2f,
      ),
      topLeft = Offset(0f, videoBottom),
      size = Size(videoLeft, size.height - videoBottom),
    )
  }

  // Bottom-Right Corner
  if (videoRight < size.width - 0.5f && videoBottom < size.height - 0.5f) {
    val brColor = blendColors(bottomColor, rightColor)
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(
          brColor.copy(alpha = intensity * 0.85f),
          brColor.copy(alpha = intensity * 0.45f),
        ),
        center = Offset(videoRight, videoBottom),
        radius = cornerRadius * 2f,
      ),
      topLeft = Offset(videoRight, videoBottom),
      size = Size(size.width - videoRight, size.height - videoBottom),
    )
  }
}

private fun blendColors(c1: Color, c2: Color): Color {
  return Color(
    red = (c1.red + c2.red) / 2f,
    green = (c1.green + c2.green) / 2f,
    blue = (c1.blue + c2.blue) / 2f,
    alpha = (c1.alpha + c2.alpha) / 2f,
  )
}
