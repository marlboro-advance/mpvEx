package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.spacing

@Composable
fun TopLeftPlayerControls(
  mediaTitle: String,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsGroup {
      ControlsButton(
        icon = Icons.AutoMirrored.Default.ArrowBack,
        onClick = onBackClick,
      )
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      ) {
        Text(
          mediaTitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyLarge,
          modifier =
            Modifier.padding(
              horizontal = MaterialTheme.spacing.medium,
              vertical = MaterialTheme.spacing.smaller,
            ),
        )
      }
    }
  }
}
