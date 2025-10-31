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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsGroup
import app.marlboroadvance.mpvex.ui.theme.spacing
import org.koin.compose.koinInject

@Composable
fun TopLeftPlayerControls(
  mediaTitle: String,
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  playlistInfo: String? = null,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()

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
        color =
          if (hideBackground) {
            Color.Transparent
          } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(
              alpha = 0.55f,
            )
          },
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (hideBackground) 0.dp else 2.dp,
        shadowElevation = 0.dp,
        border =
          if (hideBackground) {
            null
          } else {
            BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
          },
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.padding(
              horizontal = MaterialTheme.spacing.medium,
              vertical = MaterialTheme.spacing.small,
            ),
        ) {
          Text(
            mediaTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
          )
          // Playlist indicator
          if (playlistInfo != null) {
            Text(
              " • $playlistInfo",
              maxLines = 1,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}
