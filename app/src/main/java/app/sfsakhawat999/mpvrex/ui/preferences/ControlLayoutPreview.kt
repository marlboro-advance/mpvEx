package app.sfsakhawat999.mpvrex.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.VideoLabel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlLayoutPreview(
  topLeftButtons: List<PlayerButton>,
  topRightButtons: List<PlayerButton>,
  bottomRightButtons: List<PlayerButton>,
  bottomLeftButtons: List<PlayerButton>,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(2.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Black), // Black background
  ) {
    ConstraintLayout(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
        .height(160.dp), // Set fixed height for landscape aspect
    ) {
      val (
        topLeft, topRight,
        centerControls,
        positionTime, seekbar, durationTime,
        bottomLeft, bottomRight,
      ) = createRefs()

      // --- TOP BAR (LEFT) ---
      FlowRow(
        modifier = Modifier.constrainAs(topLeft) {
          start.linkTo(parent.start)
          top.linkTo(parent.top)
          end.linkTo(topRight.start, 8.dp)
          width = Dimension.fillToConstraints
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      ) {
        topLeftButtons.forEach { button ->
          // Use the simple PreviewButton for all
          PreviewButton(button = button)
        }
      }

      // --- TOP BAR (RIGHT) ---
      FlowRow(
        modifier = Modifier.constrainAs(topRight) {
          end.linkTo(parent.end)
          top.linkTo(parent.top) // Align with top
          width = Dimension.preferredWrapContent // Don't grow
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        topRightButtons.forEach { button ->
          PreviewButton(button = button)
        }
      }

      // --- CENTER CONTROLS ---
      Row(
        modifier = Modifier.constrainAs(centerControls) {
          start.linkTo(parent.start)
          end.linkTo(parent.end)
          top.linkTo(topRight.bottom, 12.dp)
          bottom.linkTo(seekbar.top, 12.dp)
          height = Dimension.fillToConstraints
        },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PreviewIconButton(icon = Icons.Default.SkipPrevious, size = 28.dp)
        PreviewIconButton(icon = Icons.Default.PlayArrow, size = 36.dp)
        PreviewIconButton(icon = Icons.Default.SkipNext, size = 28.dp)
      }

      // --- SEEKBAR ---
      Text(
        "1:23", // Demo Time
        modifier = Modifier.constrainAs(positionTime) {
          start.linkTo(parent.start)
          bottom.linkTo(bottomLeft.top, 4.dp)
        },
        fontSize = 10.sp,
        color = Color.White,
      )
      Text(
        "4:56", // Demo Time
        modifier = Modifier.constrainAs(durationTime) {
          end.linkTo(parent.end)
          bottom.linkTo(bottomRight.top, 4.dp)
        },
        fontSize = 10.sp,
        color = Color.White,
      )
      LinearProgressIndicator(
        progress = { 0.3f }, // Demo Progress
        modifier = Modifier.constrainAs(seekbar) {
          start.linkTo(positionTime.end, 8.dp)
          end.linkTo(durationTime.start, 8.dp)
          bottom.linkTo(positionTime.bottom)
          top.linkTo(positionTime.top)
          width = Dimension.fillToConstraints
        },
        color = Color.White,
        trackColor = Color.Gray,
      )

      // --- BOTTOM BAR (LEFT) ---
      FlowRow(
        modifier = Modifier.constrainAs(bottomLeft) {
          start.linkTo(parent.start)
          bottom.linkTo(parent.bottom)
          end.linkTo(bottomRight.start, 8.dp)
          width = Dimension.fillToConstraints
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      ) {
        bottomLeftButtons.forEach { button ->
          PreviewButton(button = button)
        }
      }

      // --- BOTTOM BAR (RIGHT) ---
      FlowRow(
        modifier = Modifier.constrainAs(bottomRight) {
          end.linkTo(parent.end)
          bottom.linkTo(parent.bottom)
          width = Dimension.preferredWrapContent
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        bottomRightButtons.forEach { button ->
          PreviewButton(button = button)
        }
      }
    }
  }
}

/**
 * A simple icon for the preview (play, next, etc.).
 */
@Composable
private fun PreviewIconButton(icon: ImageVector, size: Dp) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier
      .size(size)
      .padding(horizontal = 2.dp),
    tint = Color.White,
  )
}

/**
 * A simple button/icon for the preview that renders icons or text.
 */
@Composable
private fun PreviewButton(button: PlayerButton) {
  // All icons are 20dp, text is 10sp
  val iconSize = 14.dp
  val fontSize = 10.sp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp) // Give each button some space
  ) {
    when (button) {
      PlayerButton.VIDEO_TITLE -> {
        Text(
          "Video Title.mp4",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
          color = Color.White
        )
      }
      PlayerButton.CURRENT_CHAPTER -> {
        Icon(
          imageVector = button.icon,
          contentDescription = null,
          modifier = Modifier.size(iconSize),
          tint = Color.White
        )
        Text(
          "1:06 • C1",
          maxLines = 1,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
          modifier = Modifier.padding(start = 4.dp),
          color = Color.White
        )
      }
      else -> {
        // Icon-only buttons
        Icon(
          imageVector = button.icon,
          contentDescription = null,
          modifier = Modifier.size(iconSize),
          tint = Color.White
        )
      }
    }
  }
}
