package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.TemporalScaler
import app.marlboroadvance.mpvex.ui.player.VideoSync
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object InterpolationSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<DecoderPreferences>()
    val backStack = LocalBackStack.current

    val isEnabled by preferences.videoInterpolation.collectAsState()
    val videoSync by preferences.videoSync.collectAsState()
    val videoTscale by preferences.videoTscale.collectAsState()
    val videoTscaleParam1 by preferences.videoTscaleParam1.collectAsState()
    val videoTscaleParam2 by preferences.videoTscaleParam2.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Frame Interpolation",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
      ) {
        PreferenceCard {
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              text = "Reduce judder caused by mismatched video FPS and display refresh rate",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
            )

            // Enable interpolation switch
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
              ) {
                Text(
                  text = "Enable Interpolation",
                  style = MaterialTheme.typography.bodyLarge,
                )
                if (!isEnabled && videoSync.value.startsWith("display-")) {
                  Text(
                    text = "Enable to reduce judder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                  )
                }
              }
              Switch(
                checked = isEnabled,
                onCheckedChange = { newEnabled ->
                  preferences.videoInterpolation.set(newEnabled)
                  // Auto-select display-resample mode when enabling
                  if (newEnabled && !videoSync.value.startsWith("display-")) {
                    preferences.videoSync.set(VideoSync.DisplayResample)
                  }
                },
              )
            }

            // Gray out everything below when disabled
            androidx.compose.runtime.LaunchedEffect(isEnabled) {
              // Effect to trigger recomposition when enabled state changes
            }

            val contentAlpha = if (isEnabled) 1f else 0.38f

            PreferenceDivider()

            androidx.compose.foundation.layout.Row(
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier =
                  Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                Text(
                  text = "Video Sync Mode",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )

                // Video sync options
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  VideoSync.entries.forEach { mode ->
                    FilterChip(
                      selected = videoSync == mode,
                      onClick = {
                        if (!isEnabled) return@FilterChip
                        // If selecting non-display mode, disable interpolation
                        if (!mode.value.startsWith("display-")) {
                          preferences.videoInterpolation.set(false)
                        }
                        preferences.videoSync.set(mode)
                      },
                      label = { Text(mode.displayName) },
                    )
                  }
                }
              }
            }

            if (isEnabled && !videoSync.value.startsWith("display-")) {
              Text(
                text = "⚠ Interpolation only works with display-resample modes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }

        // Temporal Scaler card - also grayed out when interpolation disabled
        PreferenceCard {
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .alpha(if (isEnabled) 1f else 0.38f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              text = "Temporal Scaler",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            Text(
              text = "Controls how frames are interpolated when interpolation is enabled",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              TemporalScaler.entries.forEach { ts ->
                FilterChip(
                  selected = videoTscale == ts,
                  onClick = {
                    if (!isEnabled) return@FilterChip
                    preferences.videoTscale.set(ts)
                  },
                  label = { Text(ts.displayName) },
                )
              }
            }

            PreferenceDivider()

            Text(
              text = "Filter Parameters",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            Text(
              text = "Optional parameters for fine-tuning temporal filter behavior",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f)
              ) {
                OutlinedTextField(
                  value = videoTscaleParam1,
                  onValueChange = { if (isEnabled) preferences.videoTscaleParam1.set(it) },
                  label = { Text("Parameter 1") },
                  modifier = Modifier.fillMaxWidth(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  enabled = isEnabled,
                )
              }

              androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f)
              ) {
                OutlinedTextField(
                  value = videoTscaleParam2,
                  onValueChange = { if (isEnabled) preferences.videoTscaleParam2.set(it) },
                  label = { Text("Parameter 2") },
                  modifier = Modifier.fillMaxWidth(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  enabled = isEnabled,
                )
              }
            }

            Text(
              text =
                "Current: ${videoTscale.displayName}" +
                  if (videoTscaleParam1.isNotBlank()) " (p1: $videoTscaleParam1)" else "" +
                  if (videoTscaleParam2.isNotBlank()) " (p2: $videoTscaleParam2)" else "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}
