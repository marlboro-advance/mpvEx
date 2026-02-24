package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.theme.spacing

@Composable
fun SpeedControlSlider(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  val speedPresets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
  val currentIndex = speedPresets.indexOfFirst { 
    kotlin.math.abs(it - currentSpeed) < 0.05f 
  }.coerceIn(0, speedPresets.size - 1)
  
  val primaryColor = MaterialTheme.colorScheme.primary
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    ),
    modifier = modifier.animateContentSize(),
  ) {
    Box(
      modifier = Modifier.padding(
        vertical = MaterialTheme.spacing.small,
        horizontal = MaterialTheme.spacing.medium,
      ),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
            Row(
              modifier = Modifier.width(280.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              speedPresets.forEach { speed ->
                val isCurrentSpeed = kotlin.math.abs(currentSpeed - speed) < 0.05f
                Text(
                  text = "${speed.format()}x",
                  fontSize = if (isCurrentSpeed) 13.sp else 10.sp,
                  fontWeight = if (isCurrentSpeed) FontWeight.Bold else FontWeight.Normal,
                  color = if (isCurrentSpeed) {
                    primaryColor
                  } else {
                    Color.White.copy(alpha = 0.5f)
                  },
                )
              }
            }
            
            Canvas(
              modifier = Modifier
                .width(280.dp)
                .height(3.dp),
            ) {
              val trackWidth = size.width
              val trackHeight = 3.dp.toPx()
              val centerY = size.height / 2
              val segmentWidth = trackWidth / (speedPresets.size - 1)
              
              drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
              )
              
              val progressX = currentIndex * segmentWidth
              drawLine(
                color = primaryColor,
                start = Offset(0f, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
              )
              
              speedPresets.forEachIndexed { index, _ ->
                val tickX = index * segmentWidth
                drawCircle(
                  color = if (index <= currentIndex) {
                    primaryColor
                  } else {
                    Color.White.copy(alpha = 0.6f)
                  },
                  radius = 2.5.dp.toPx(),
                  center = Offset(tickX, centerY),
                )
              }
            }
        
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "${currentSpeed.format()}x Speed Playing",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
          )
        }
      }
    }
  }
}

@Composable
fun CompactSpeedIndicator(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier
      .background(
        color = Color(0x80000000), 
        shape = RoundedCornerShape(100.dp)
      )
      .padding(horizontal = 14.dp, vertical = 6.dp) 
  ) {
    Text(
      text = "Playing at ${currentSpeed.format()}x",
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = Color.White
    )
  }
}

private fun Float.format(): String {
  return when {
    this % 1.0f == 0.0f -> this.toInt().toString()
    else -> String.format("%.2f", this).trimEnd('0').trimEnd('.')
  }
}