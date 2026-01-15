package app.marlboroadvance.mpvex.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.getPlayerButtonLabel

/**
 * A simple "Quick Settings" style chip for a player button.
 * Renders text or icons based on the button type.
 */
@Composable
fun PlayerButtonChip(
  button: PlayerButton,
  enabled: Boolean,
  onClick: (() -> Unit)? = null,
  badgeIcon: ImageVector? = null,
  badgeColor: Color? = null,
) {
  val label = getPlayerButtonLabel(button) // Kept for accessibility

  Box(
    modifier = Modifier.padding(4.dp), // Padding for the badge
  ) {
    Card(
      modifier = Modifier, // Let the card wrap its content
      shape = MaterialTheme.shapes.medium,
      elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp),
      colors =
        CardDefaults.cardColors(
          containerColor =
            if (enabled) {
              MaterialTheme.colorScheme.surfaceVariant
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
          contentColor =
            if (enabled) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        ),
      onClick = { onClick?.invoke() },
      enabled = enabled && onClick != null,
    ) {
      // Use a Box to center content and set size constraints
      Box(
        modifier =
          Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp) // Smaller min size
            .padding(horizontal = 12.dp, vertical = 8.dp),
        // Padding inside the card
        contentAlignment = Alignment.Center,
      ) {
        when (button) {
          PlayerButton.VIDEO_TITLE -> {
            Text(
              text = "Video Title", // TODO: strings
              fontSize = 15.sp, // Increased font size
              textAlign = TextAlign.Center,
              lineHeight = 14.sp,
            )
          }
          PlayerButton.CURRENT_CHAPTER -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = button.icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp), 
              )
              Text(
                text = "1:06 • Chapter 1", // TODO: strings
                fontSize = 15.sp, 
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
          else -> {
            // Default: Icon only
            Icon(
              imageVector = button.icon,
              contentDescription = label,
              modifier = Modifier.size(24.dp), 
            )
          }
        }
      }
    }

    // Badge Icon Overlay
    if (badgeIcon != null && badgeColor != null) {
      Icon(
        imageVector = badgeIcon,
        contentDescription = null, // Decorative
        tint = badgeColor,
        modifier =
          Modifier
            .size(20.dp)
            .align(Alignment.BottomEnd)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
      )
    }
  }
}
