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
// import androidx.compose.material.icons.outlined.VideoLabel // <-- No longer needed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlLayoutPreview(
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
        backArrow, title, topRight,
        centerControls,
        positionTime, seekbar, durationTime,
        bottomLeft, bottomRight,
      ) = createRefs()

      // --- TOP BAR ---
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
          .size(18.dp)
          .constrainAs(backArrow) {
            start.linkTo(parent.start)
            top.linkTo(parent.top)
          },
      )

      Text(
        "Video Title.mp4", // Demo Title
        modifier = Modifier.constrainAs(title) {
          start.linkTo(backArrow.end, 8.dp) // Link to back arrow
          top.linkTo(parent.top) // Align with top
          bottom.linkTo(backArrow.bottom) // Center vertically with arrow
          end.linkTo(topRight.start, 8.dp)
          width = Dimension.fillToConstraints
        },
        fontSize = 10.sp, // Make text small
        color = Color.White,
        maxLines = 1,
      )
      FlowRow(
        modifier = Modifier.constrainAs(topRight) {
          end.linkTo(parent.end)
          top.linkTo(parent.top) // Align with top
          width = Dimension.preferredWrapContent // Don't grow
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        topRightButtons.forEach {
          PreviewButton(icon = it.icon)
        }
      }

      // --- CENTER CONTROLS ---
      Row(
        modifier = Modifier.constrainAs(centerControls) {
          start.linkTo(parent.start)
          end.linkTo(parent.end)
          top.linkTo(topRight.bottom, 12.dp)
          bottom.linkTo(seekbar.top, 12.dp)
          height = Dimension.fillToConstraints // Allow it to fill vertical space
        },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PreviewButton(icon = Icons.Default.SkipPrevious, size = 28.dp)
        PreviewButton(icon = Icons.Default.PlayArrow, size = 36.dp)
        PreviewButton(icon = Icons.Default.SkipNext, size = 28.dp)
      }

      // --- SEEKBAR ---
      Text(
        "1:23", // Demo Time
        modifier = Modifier.constrainAs(positionTime) {
          start.linkTo(parent.start)
          bottom.linkTo(bottomLeft.top, 4.dp) // Constrain to top of bottom bar
        },
        fontSize = 10.sp,
        color = Color.White,
      )
      Text(
        "4:56", // Demo Time
        modifier = Modifier.constrainAs(durationTime) {
          end.linkTo(parent.end)
          bottom.linkTo(bottomRight.top, 4.dp) // Constrain to top of bottom bar
        },
        fontSize = 10.sp,
        color = Color.White,
      )
      LinearProgressIndicator(
        progress = { 0.3f }, // Demo Progress
        modifier = Modifier.constrainAs(seekbar) {
          start.linkTo(positionTime.end, 8.dp)
          end.linkTo(durationTime.start, 8.dp)
          bottom.linkTo(positionTime.bottom) // Align with text
          top.linkTo(positionTime.top)       // Align with text
          width = Dimension.fillToConstraints
        },
        color = Color.White,
        trackColor = Color.Gray,
      )

      // --- BOTTOM BAR ---
      FlowRow(
        modifier = Modifier.constrainAs(bottomLeft) {
          start.linkTo(parent.start)
          bottom.linkTo(parent.bottom) // Anchor to parent bottom
          end.linkTo(bottomRight.start, 8.dp)
          width = Dimension.fillToConstraints
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        bottomLeftButtons.forEach {
          PreviewButton(icon = it.icon)
        }
        // --- REMOVED FIXED CHAPTER BUTTON ---
      }

      FlowRow(
        modifier = Modifier.constrainAs(bottomRight) {
          end.linkTo(parent.end)
          bottom.linkTo(parent.bottom) // Anchor to parent bottom
          width = Dimension.preferredWrapContent // Don't grow
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        bottomRightButtons.forEach {
          PreviewButton(icon = it.icon)
        }
      }
    }
  }
}

/**
 * A small icon composable for the preview.
 */
@Composable
private fun PreviewButton(icon: ImageVector, size: Dp = 18.dp) { // Smaller default
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier
      .size(size) // Use dynamic size
      .padding(horizontal = 2.dp), // <-- More horizontal padding for spacing
    tint = Color.White, // Tint for black background
  )
}
