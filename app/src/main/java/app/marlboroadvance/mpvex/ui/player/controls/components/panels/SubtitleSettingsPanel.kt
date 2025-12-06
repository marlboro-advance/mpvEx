package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.components.MultiCardPanel

@Composable
fun SubtitleSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MultiCardPanel(
    onDismissRequest = onDismissRequest,
    titleRes = R.string.player_sheets_subtitles_settings_title,
    cardCount = 3,
    modifier = modifier,
  ) { index, cardModifier ->
    when (index) {
      0 -> SubtitleSettingsTypographyCard(cardModifier)
      1 -> SubtitleSettingsColorsCard(cardModifier)
      2 -> SubtitlesMiscellaneousCard(cardModifier)
      else -> {}
    }
  }
}
