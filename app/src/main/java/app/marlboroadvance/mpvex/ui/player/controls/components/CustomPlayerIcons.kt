package app.marlboroadvance.mpvex.ui.player.controls.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedPlayPauseIcon(
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
) {
  val transition = updateTransition(targetState = isPlaying, label = "play_pause_icon")
  val playAlpha = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "play_alpha",
  ) { playing -> if (playing) 0f else 1f }
  val playScale = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "play_scale",
  ) { playing -> if (playing) 0.82f else 1f }
  val playRotation = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "play_rotation",
  ) { playing -> if (playing) -12f else 0f }

  val pauseAlpha = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "pause_alpha",
  ) { playing -> if (playing) 1f else 0f }
  val pauseScale = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "pause_scale",
  ) { playing -> if (playing) 1f else 0.82f }
  val barSpread = transition.animateFloat(
    transitionSpec = { tween(durationMillis = 260, easing = FastOutSlowInEasing) },
    label = "pause_spread",
  ) { playing -> if (playing) 1f else 0f }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer(
            alpha = playAlpha.value,
            scaleX = playScale.value,
            scaleY = playScale.value,
            rotationZ = playRotation.value,
          ),
    ) {
      val triangle = Path().apply {
        moveTo(size.width * 0.34f, size.height * 0.20f)
        quadraticTo(size.width * 0.30f, size.height * 0.24f, size.width * 0.30f, size.height * 0.31f)
        lineTo(size.width * 0.30f, size.height * 0.69f)
        quadraticTo(size.width * 0.30f, size.height * 0.76f, size.width * 0.34f, size.height * 0.80f)
        lineTo(size.width * 0.76f, size.height * 0.56f)
        quadraticTo(size.width * 0.83f, size.height * 0.52f, size.width * 0.83f, size.height * 0.50f)
        quadraticTo(size.width * 0.83f, size.height * 0.48f, size.width * 0.76f, size.height * 0.44f)
        close()
      }
      drawPath(
        path = triangle,
        color = tint,
      )
    }

    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer(
            alpha = pauseAlpha.value,
            scaleX = pauseScale.value,
            scaleY = pauseScale.value,
          ),
    ) {
      val barWidth = size.width * 0.18f
      val barHeight = size.height * 0.58f
      val top = (size.height - barHeight) / 2f
      val spread = size.width * (0.015f + (0.11f * barSpread.value))
      val leftX = (size.width / 2f) - spread - barWidth / 2f
      val rightX = (size.width / 2f) + spread - barWidth / 2f
      val radius = CornerRadius(barWidth * 0.7f, barWidth * 0.7f)

      drawRoundRect(
        color = tint,
        topLeft = Offset(leftX, top),
        size = Size(barWidth, barHeight),
        cornerRadius = radius,
      )
      drawRoundRect(
        color = tint,
        topLeft = Offset(rightX, top),
        size = Size(barWidth, barHeight),
        cornerRadius = radius,
      )
    }
  }
}

@Composable
fun AbLoopIcon(
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  isASet: Boolean = true,
  isBSet: Boolean = true,
  textA: String = "A",
  textB: String = "B",
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(1.dp),
    ) {
      val iconScale = size.minDimension
      if (iconScale <= 0f) return@Canvas

      fun tintWith(multiplier: Float): Color = tint.copy(alpha = tint.alpha * multiplier)

      val aColor = tint
      val bColor = tint
      val labelAColor = tint
      val labelBColor = tint
      val shadingColor = tintWith(if (isASet && isBSet) 0.88f else 0.62f)

      val circleDiameter = iconScale * 0.54f
      val circleRadius = circleDiameter / 2f
      val circleSeparation = iconScale * 0.36f
      val centerX = size.width / 2f
      val centerY = size.height / 2f
      val circleA = Offset(centerX - (circleSeparation / 2f), centerY)
      val circleB = Offset(centerX + (circleSeparation / 2f), centerY)

      val outlineWidth = iconScale * 0.065f
      val shadingStroke = iconScale * 0.018f
      val shadingGap = iconScale * 0.03f
      val textSize = iconScale * 0.22f
      val labelHorizontalOffset = circleRadius * 0.20f
      val labelVerticalOffset = circleRadius * 0.04f

      val pathA =
        Path().apply {
          addOval(
            Rect(
              left = circleA.x - circleRadius,
              top = circleA.y - circleRadius,
              right = circleA.x + circleRadius,
              bottom = circleA.y + circleRadius,
            ),
          )
        }
      val pathB =
        Path().apply {
          addOval(
            Rect(
              left = circleB.x - circleRadius,
              top = circleB.y - circleRadius,
              right = circleB.x + circleRadius,
              bottom = circleB.y + circleRadius,
            ),
          )
        }
      val intersectionPath =
        Path().apply {
          op(pathA, pathB, PathOperation.Intersect)
        }

      clipPath(intersectionPath) {
        var startX = -size.height
        while (startX < size.width + size.height) {
          drawLine(
            color = shadingColor,
            start = Offset(startX, 0f),
            end = Offset(startX + size.height, size.height),
            strokeWidth = shadingStroke,
            cap = StrokeCap.Round,
          )
          startX += shadingStroke + shadingGap
        }
      }

      drawCircle(
        color = aColor,
        center = circleA,
        radius = circleRadius,
        style = Stroke(width = outlineWidth),
      )
      drawCircle(
        color = bColor,
        center = circleB,
        radius = circleRadius,
        style = Stroke(width = outlineWidth),
      )

      drawIntoCanvas { canvas ->
        fun drawLabel(text: String, center: Offset, color: Color, horizontalOffset: Float) {
          val paint =
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
              this.color = color.toArgb()
              this.textSize = textSize
              typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
              textAlign = AndroidPaint.Align.CENTER
            }
          val fontMetrics = paint.fontMetrics
          val labelCenter = Offset(center.x + horizontalOffset, center.y - labelVerticalOffset)
          val labelBaseline = labelCenter.y - ((fontMetrics.ascent + fontMetrics.descent) / 2f)
          canvas.nativeCanvas.drawText(text, labelCenter.x, labelBaseline, paint)
        }

        drawLabel(textA, circleA, labelAColor, -labelHorizontalOffset)
        drawLabel(textB, circleB, labelBColor, labelHorizontalOffset)
      }
    }
  }
}
