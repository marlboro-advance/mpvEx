package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.theme.spacing
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Dedicated settings bottom sheet for Ambient Mode.
 *
 * Allows toggling the feature on/off and tuning the ambient glow intensity with live feedback.
 */
@Composable
fun AmbientModeSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val ambientMode by playerPreferences.ambientMode.collectAsState()
  val ambientModeIntensity by playerPreferences.ambientModeIntensity.collectAsState()

  PlayerSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          Icon(
            imageVector = Icons.Outlined.BlurOn,
            contentDescription = null,
            tint = if (ambientMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
          )
          Text(
            text = stringResource(R.string.pref_ambient_mode),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        Switch(
          checked = ambientMode,
          onCheckedChange = { playerPreferences.ambientMode.set(it) },
        )
      }

      Text(
        text = stringResource(R.string.pref_ambient_mode_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
      )

      HorizontalDivider()

      if (ambientMode) {
        // Intensity Slider
        Column(
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.pref_ambient_mode_intensity),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "${(ambientModeIntensity * 100).roundToInt()}%",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          Slider(
            value = ambientModeIntensity,
            onValueChange = { playerPreferences.ambientModeIntensity.set(it) },
            valueRange = 0.2f..1.0f,
            modifier = Modifier.fillMaxWidth(),
          )

          // Preset chips
          val presets = listOf(
            "Subtle" to 0.35f,
            "Default" to 0.60f,
            "Vivid" to 0.85f,
          )

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
          ) {
            items(presets) { (label, value) ->
              val isSelected = kotlin.math.abs(ambientModeIntensity - value) < 0.05f
              FilterChip(
                selected = isSelected,
                onClick = { playerPreferences.ambientModeIntensity.set(value) },
                label = { Text(label) },
              )
            }
          }
        }
      }
    }
  }
}
