package app.sfsakhawat999.mpvrex.ui.player.controls.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.ui.player.Decoder
import kotlinx.collections.immutable.toImmutableList

@Composable
fun DecodersSheet(
  selectedDecoder: Decoder,
  onSelect: (Decoder) -> Unit,
  onDismissRequest: () -> Unit,
) {
  GenericTracksSheet(
    Decoder.entries.minusElement(Decoder.Auto).toImmutableList(),
    track = {
      AudioTrackRow(
        title = stringResource(R.string.player_sheets_decoder_formatted, it.title, it.value),
        isSelected = selectedDecoder == it,
        onClick = { onSelect(it) },
      )
    },
    onDismissRequest = onDismissRequest,
  )
}
