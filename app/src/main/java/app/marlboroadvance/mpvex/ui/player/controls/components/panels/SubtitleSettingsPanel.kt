package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing

@Composable
fun SubtitleSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DraggablePanel(
    modifier = modifier,
    header = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium)
          .padding(top = MaterialTheme.spacing.small),
      ) {
        Text(
          stringResource(R.string.player_sheets_subtitles_settings_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, null, modifier = Modifier.size(32.dp))
        }
      }
    }
  ) {
      Column(
        Modifier.padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        SubtitleSettingsTypographyCard()
        SubtitleSettingsColorsCard()
        SubtitlesMiscellaneousCard()
      }
  }
}
