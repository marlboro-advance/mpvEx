package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.ExpandableCard
import app.marlboroadvance.mpvex.ui.player.FilterPreset
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoSettingsFilterPresetsCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  var isExpanded by remember { mutableStateOf(true) }

  // Collect current filter values
  val brightness by decoderPreferences.brightnessFilter.collectAsState()
  val saturation by decoderPreferences.saturationFilter.collectAsState()
  val contrast by decoderPreferences.contrastFilter.collectAsState()
  val gamma by decoderPreferences.gammaFilter.collectAsState()
  val hue by decoderPreferences.hueFilter.collectAsState()
  val sharpness by decoderPreferences.sharpnessFilter.collectAsState()

  // Find matching preset based on current filter values
  val currentPreset = FilterPreset.entries.find { preset ->
    preset.brightness == brightness &&
      preset.saturation == saturation &&
      preset.contrast == contrast &&
      preset.gamma == gamma &&
      preset.hue == hue &&
      preset.sharpness == sharpness
  } ?: FilterPreset.NONE.takeIf {
    brightness == 0 && saturation == 0 && contrast == 0 && gamma == 0 && hue == 0 && sharpness == 0
  }

  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.Default.AutoAwesome, null)
        Text("Filter Presets")
      }
    },
    colors = panelCardsColors(),
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterPreset.entries.forEach { preset ->
          FilterChip(
            selected = currentPreset == preset,
            onClick = {
              // Apply preset values to all filters
              decoderPreferences.brightnessFilter.set(preset.brightness)
              decoderPreferences.saturationFilter.set(preset.saturation)
              decoderPreferences.contrastFilter.set(preset.contrast)
              decoderPreferences.gammaFilter.set(preset.gamma)
              decoderPreferences.hueFilter.set(preset.hue)
              decoderPreferences.sharpnessFilter.set(preset.sharpness)

              // Apply to MPV
              MPVLib.setPropertyInt("brightness", preset.brightness)
              MPVLib.setPropertyInt("saturation", preset.saturation)
              MPVLib.setPropertyInt("contrast", preset.contrast)
              MPVLib.setPropertyInt("gamma", preset.gamma)
              MPVLib.setPropertyInt("hue", preset.hue)
              MPVLib.setPropertyInt("sharpen", preset.sharpness)
            },
            label = { Text(preset.displayName) },
          )
        }
      }

      // Show description for selected preset
      currentPreset?.let { preset ->
        if (preset.description.isNotEmpty()) {
          Text(
            text = preset.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
      }
    }
  }
}
