package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.components.MultiCardPanel

@Composable
fun VideoSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MultiCardPanel(
    onDismissRequest = onDismissRequest,
    titleRes = R.string.player_sheets_video_settings_title,
    cardCount = 2,
    modifier = modifier,
  ) { index, cardModifier ->
    when (index) {
      0 -> VideoSettingsDebandCard(cardModifier)
      1 -> VideoSettingsFiltersCard(cardModifier)
      else -> {}
    }
  }
}
