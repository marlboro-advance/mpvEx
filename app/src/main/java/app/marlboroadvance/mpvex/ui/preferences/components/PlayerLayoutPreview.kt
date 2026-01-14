package app.marlboroadvance.mpvex.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.preferences.PlayerButton

@Composable
fun PlayerLayoutPreview(
  topRightButtons: List<PlayerButton> = emptyList(),
  bottomRightButtons: List<PlayerButton> = emptyList(),
  bottomLeftButtons: List<PlayerButton> = emptyList(),
  portraitBottomButtons: List<PlayerButton> = emptyList(),
  isLandscape: Boolean = true,
  highlightRegion: ControlRegionReference? = null,
  modifier: Modifier = Modifier,
) {
  // Device Frame aspect ratio
  val aspectRatio = if (isLandscape) 16f / 9f else 9f / 16f

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    // Device Frame
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(aspectRatio)
          .padding(4.dp) // Outer margin
          .clip(RoundedCornerShape(16.dp)) // Device rounded corners
          .background(Color.Black) // Screen off black
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
      // Mock Video Content (Dark gray gradient/box)
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF121212))
      )
      
      // Static Player UI Layers
      if (isLandscape) {
        LandscapeStaticControls()
        LandscapeCustomControls(
          topRightButtons,
          bottomRightButtons,
          bottomLeftButtons,
          highlightRegion,
        )
      } else {
        PortraitStaticControls()
        PortraitCustomControls(
            portraitBottomButtons,
            highlightRegion
        )
      }
    }
  }
}

@Composable
private fun BoxScope.LandscapeStaticControls() {
    val contentColor = Color.White
    
    // --- Top Bar (Back, Title) ---
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = 16.dp, start = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Arrow
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        // Title (No background, just text)
        Text(
            "Sample Video Title.mkv • 09:12", 
            color = contentColor, 
            fontSize = 8.sp,
            maxLines = 1
        )
    }
    
    // --- Center Playback Controls ---
    Row(
        modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(24.dp), // Wide spacing
        verticalAlignment = Alignment.CenterVertically
    ) {
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SkipPrevious, /* SkipPrev */
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow, /* Play */
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(32.dp)
        )
        
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SkipNext, /* SkipNext */
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
    
    // --- Bottom Seekbar ---
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp, start = 32.dp, end = 32.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Text("00:01", color = contentColor, fontSize = 8.sp)
         Spacer(modifier = Modifier.width(8.dp))
         // Seekbar line
             Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
            ) {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                )
            }
         Spacer(modifier = Modifier.width(8.dp))
         Text("2:44:21", color = contentColor, fontSize = 8.sp)
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.LandscapeCustomControls(
  topRight: List<PlayerButton>,
  bottomRight: List<PlayerButton>,
  bottomLeft: List<PlayerButton>,
  highlight: ControlRegionReference?,
) {
  // --- Top Right ---
  // No extra static icons, just the custom ones
  
  // Custom Controls
  Box(
    modifier =
      Modifier
        .align(Alignment.TopEnd)
        .padding(top = 16.dp, end = 16.dp),
    contentAlignment = Alignment.TopEnd
  ) {
    // Single Row as per user request
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        modifier = Modifier.wrapContentWidth()
    ) {
      topRight.forEach { btn -> MiniChip(btn) }
    }
  }

  // --- Bottom Right ---
  Box(
    modifier =
      Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 32.dp, end = 16.dp) // Lifted up above seekbar
        .padding(bottom = 8.dp), // Extra space
    contentAlignment = Alignment.BottomEnd
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        modifier = Modifier.wrapContentWidth()
    ) {
        bottomRight.forEach { btn -> MiniChip(btn) }
    }
  }

  // --- Bottom Left ---
  Box(
    modifier =
      Modifier
        .align(Alignment.BottomStart)
        .padding(bottom = 32.dp, start = 16.dp) // Lifted up above seekbar
        .padding(bottom = 8.dp),
    contentAlignment = Alignment.BottomStart
  ) {
     Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
        modifier = Modifier.wrapContentWidth()
    ) {
        bottomLeft.forEach { btn -> MiniChip(btn) }
    }
  }
}

@Composable
private fun BoxScope.PortraitStaticControls() {
    val contentColor = Color.White
    
    // --- Top Bar (Back, Title, Menu icons) ---
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Arrow
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Title (Truncated, No background)
        Text(
            "Sample Video Title.mkv", 
            color = contentColor, 
            fontSize = 8.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        
        // No static Menu Icons
    }
    
    // Center Playback Controls (Play/Pause/Skip)
    Row(
        modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SkipPrevious,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(32.dp)
        )
         Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SkipNext,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
    
     // Bottom Seekbar
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Text("00:01", color = contentColor, fontSize = 8.sp)
         Spacer(modifier = Modifier.width(4.dp))
         Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
        ) {
             Box(
                modifier = Modifier
                    .fillMaxWidth(0.1f)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
            )
        }
         Spacer(modifier = Modifier.width(4.dp))
         Text("09:12", color = contentColor, fontSize = 8.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.PortraitCustomControls(
  bottomButtons: List<PlayerButton>,
  highlight: ControlRegionReference?,
) {
  // --- Portrait Bottom ---
  Box(
    modifier =
      Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        .padding(bottom = 16.dp), // Above seekbar
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
         FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
             maxItemsInEachRow = 5
        ) {
            bottomButtons.forEach { btn -> MiniChip(btn) }
        }
    }
  }
}

@Composable
private fun MiniChip(button: PlayerButton) {
  // Round circle style as per screenshot
  Box(
      modifier = Modifier
        .size(24.dp) // Fixed circular size
        .background(Color(0xFF282828), CircleShape) // Dark circle background
        .border(0.5.dp, Color.Gray.copy(alpha=0.3f), CircleShape),
      contentAlignment = Alignment.Center
  ) {
      if (button == PlayerButton.VIDEO_TITLE) {
          Text("T", fontSize = 10.sp, color = Color.White) // Symbol for Title
      } else if (button == PlayerButton.CURRENT_CHAPTER) {
          Text("C", fontSize = 10.sp, color = Color.White) // Symbol for Chapter
      } else {
          Icon(
              imageVector = button.icon,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = Color.White
          )
      }
  }
}





enum class ControlRegionReference {
  TOP_RIGHT,
  BOTTOM_RIGHT,
  BOTTOM_LEFT,
  PORTRAIT_BOTTOM
}
