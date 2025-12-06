package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.marlboroadvance.mpvex.preferences.PlayerButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlLayoutPreview(
  topLeftButtons: List<PlayerButton>,
  topRightButtons: List<PlayerButton>,
  bottomRightButtons: List<PlayerButton>,
  bottomLeftButtons: List<PlayerButton>,
  portraitBottomButtons: List<PlayerButton>,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    // Landscape Preview
    Text(
      "Landscape Mode",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    LandscapePreview(
      topLeftButtons = topLeftButtons,
      topRightButtons = topRightButtons,
      bottomLeftButtons = bottomLeftButtons,
      bottomRightButtons = bottomRightButtons,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Portrait Preview
    Text(
      "Portrait Mode",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(bottom = 8.dp),
    )
    PortraitPreview(
      topButtons = topLeftButtons, // Back + Title
      bottomButtons = portraitBottomButtons,
    )
  }
}

@Composable
private fun LandscapePreview(
  topLeftButtons: List<PlayerButton>,
  topRightButtons: List<PlayerButton>,
  bottomLeftButtons: List<PlayerButton>,
  bottomRightButtons: List<PlayerButton>,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(2.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Black),
  ) {
    ConstraintLayout(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(12.dp)
          .height(180.dp), // Slightly taller
    ) {
      val (
        topLeft, topRight,
        centerControls,
        bottomLeft, bottomRight,
        positionTime, seekbar, durationTime,
      ) = createRefs()

      // --- TOP BAR (LEFT) ---
      FlowRow(
        modifier =
          Modifier.constrainAs(topLeft) {
            start.linkTo(parent.start)
            top.linkTo(parent.top)
            end.linkTo(topRight.start, 2.dp) // extraSmall spacing
            width = Dimension.fillToConstraints
          },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // extraSmall
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      ) {
        topLeftButtons.forEach { button ->
          PreviewButton(button = button, size = 20.dp) // Scaled down from 45dp
        }
      }

      // --- TOP BAR (RIGHT) ---
      FlowRow(
        modifier =
          Modifier.constrainAs(topRight) {
            end.linkTo(parent.end)
            top.linkTo(parent.top)
            width = Dimension.preferredWrapContent
          },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // extraSmall
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        topRightButtons.forEach { button ->
          PreviewButton(button = button, size = 20.dp)
        }
      }

      // --- CENTER CONTROLS ---
      Row(
        modifier =
          Modifier.constrainAs(centerControls) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(topRight.bottom, 12.dp)
            bottom.linkTo(bottomLeft.top, 12.dp)
            height = Dimension.fillToConstraints
          },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PreviewIconButton(icon = Icons.Default.SkipPrevious, size = 28.dp)
        PreviewIconButton(icon = Icons.Default.PlayArrow, size = 36.dp)
        PreviewIconButton(icon = Icons.Default.SkipNext, size = 28.dp)
      }

      // --- BOTTOM BAR (LEFT) ---
      FlowRow(
        modifier =
          Modifier.constrainAs(bottomLeft) {
            start.linkTo(parent.start)
            bottom.linkTo(seekbar.top, 10.dp) // More spacing above seekbar
            end.linkTo(bottomRight.start, 8.dp)
            width = Dimension.fillToConstraints
          },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // extraSmall
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      ) {
        bottomLeftButtons.forEach { button ->
          PreviewButton(button = button, size = 20.dp)
        }
      }

      // --- BOTTOM BAR (RIGHT) ---
      FlowRow(
        modifier =
          Modifier.constrainAs(bottomRight) {
            end.linkTo(parent.end)
            bottom.linkTo(seekbar.top, 10.dp) // More spacing above seekbar
            width = Dimension.preferredWrapContent
          },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // extraSmall
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        bottomRightButtons.forEach { button ->
          PreviewButton(button = button, size = 20.dp)
        }
      }

      // --- SEEKBAR (Now at bottom) ---
      Text(
        "1:23",
        modifier =
          Modifier.constrainAs(positionTime) {
            start.linkTo(parent.start)
            bottom.linkTo(parent.bottom)
          },
        fontSize = 10.sp,
        color = Color.White,
      )
      Text(
        "4:56",
        modifier =
          Modifier.constrainAs(durationTime) {
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom)
          },
        fontSize = 10.sp,
        color = Color.White,
      )
      LinearProgressIndicator(
        progress = { 0.3f },
        modifier =
          Modifier.constrainAs(seekbar) {
            start.linkTo(positionTime.end, 8.dp)
            end.linkTo(durationTime.start, 8.dp)
            bottom.linkTo(positionTime.bottom)
            top.linkTo(positionTime.top)
            width = Dimension.fillToConstraints
          },
        color = Color.White,
        trackColor = Color.Gray,
      )
    }
  }
}

@Composable
private fun PortraitPreview(
  topButtons: List<PlayerButton>,
  bottomButtons: List<PlayerButton>,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(2.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Black),
  ) {
    ConstraintLayout(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(12.dp)
          .height(260.dp), // Taller for better spacing
    ) {
      val (
        topControls,
        centerControls,
        bottomControls,
        positionTime, seekbar, durationTime,
      ) = createRefs()

      // --- TOP BAR (Full Width) ---
      FlowRow(
        modifier =
          Modifier.constrainAs(topControls) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(parent.top, 16.dp) // extraLarge spacing
            width = Dimension.fillToConstraints
          },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // extraSmall
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
      ) {
        topButtons.forEach { button ->
          PreviewButton(button = button, size = 20.dp)
        }
      }

      // --- CENTER CONTROLS ---
      Row(
        modifier =
          Modifier.constrainAs(centerControls) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(topControls.bottom, 16.dp)
            bottom.linkTo(bottomControls.top, 16.dp)
            height = Dimension.fillToConstraints
          },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PreviewIconButton(icon = Icons.Default.SkipPrevious, size = 28.dp)
        PreviewIconButton(icon = Icons.Default.PlayArrow, size = 36.dp)
        PreviewIconButton(icon = Icons.Default.SkipNext, size = 28.dp)
      }

      // --- BOTTOM BAR (Centered, Scrollable) ---
      Row(
        modifier =
          Modifier
            .constrainAs(bottomControls) {
              start.linkTo(parent.start)
              end.linkTo(parent.end)
              bottom.linkTo(seekbar.top, 12.dp) // More spacing above seekbar
              width = Dimension.fillToConstraints
            }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), // extraSmall, centered
        verticalAlignment = Alignment.CenterVertically,
      ) {
        bottomButtons.forEach { button ->
          PreviewButton(button = button, size = 22.dp) // Slightly larger (48dp scaled down)
        }
      }

      // --- SEEKBAR (Now at bottom) ---
      Text(
        "1:23",
        modifier =
          Modifier.constrainAs(positionTime) {
            start.linkTo(parent.start)
            bottom.linkTo(parent.bottom)
          },
        fontSize = 10.sp,
        color = Color.White,
      )
      Text(
        "4:56",
        modifier =
          Modifier.constrainAs(durationTime) {
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom)
          },
        fontSize = 10.sp,
        color = Color.White,
      )
      LinearProgressIndicator(
        progress = { 0.3f },
        modifier =
          Modifier.constrainAs(seekbar) {
            start.linkTo(positionTime.end, 8.dp)
            end.linkTo(durationTime.start, 8.dp)
            bottom.linkTo(positionTime.bottom)
            top.linkTo(positionTime.top)
            width = Dimension.fillToConstraints
          },
        color = Color.White,
        trackColor = Color.Gray,
      )
    }
  }
}

/**
 * A simple icon for the preview (play, next, etc.).
 */
@Composable
private fun PreviewIconButton(
  icon: ImageVector,
  size: Dp,
) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier =
      Modifier
        .size(size)
        .padding(horizontal = 2.dp),
    tint = Color.White,
  )
}

/**
 * A simple button/icon for the preview that renders icons or text.
 */
@Composable
private fun PreviewButton(
  button: PlayerButton,
  size: Dp,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
  ) {
    when (button) {
      PlayerButton.VIDEO_TITLE -> {
        Text(
          "Video Title",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
          color = Color.White,
        )
      }
      PlayerButton.CURRENT_CHAPTER -> {
        Icon(
          imageVector = button.icon,
          contentDescription = null,
          modifier = Modifier.size(size * 0.7f),
          tint = Color.White,
        )
        Text(
          "1:06",
          maxLines = 1,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
          modifier = Modifier.padding(start = 2.dp),
          color = Color.White,
        )
      }
      else -> {
        Icon(
          imageVector = button.icon,
          contentDescription = null,
          modifier = Modifier.size(size * 0.7f),
          tint = Color.White,
        )
      }
    }
  }
}
