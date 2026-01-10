package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import app.marlboroadvance.mpvex.ui.player.Scaler
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object DownscaleFilterScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<DecoderPreferences>()
    val backStack = LocalBackStack.current

    val videoDownscale by preferences.videoDownscale.collectAsState()
    val videoDownscaleParam1 by preferences.videoDownscaleParam1.collectAsState()
    val videoDownscaleParam2 by preferences.videoDownscaleParam2.collectAsState()

    var showAllScalers by remember { mutableStateOf(false) }

    val commonScalers =
      listOf(
        Scaler.Bilinear,
        Scaler.BicubicFast,
        Scaler.Spline36,
        Scaler.Lanczos,
        Scaler.Nearest,
      )

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Downscaling Filter",
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
              text = "Control how video frames are resampled when downscaling to a smaller size",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
            )

            Text(
              text = "Common Scalers",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              commonScalers.forEach { sc ->
                FilterChip(
                  selected = videoDownscale == sc,
                  onClick = {
                    preferences.videoDownscale.set(sc)
                  },
                  label = { Text(sc.displayName) },
                )
              }
            }

            Text(
              text = "Advanced Scalers",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            TextButton(
              onClick = { showAllScalers = !showAllScalers },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(if (showAllScalers) "Show less" else "Show all 34 scalers...")
            }

            if (showAllScalers) {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Scaler.entries.forEach { sc ->
                  if (sc !in commonScalers) {
                    FilterChip(
                      selected = videoDownscale == sc,
                      onClick = {
                        preferences.videoDownscale.set(sc)
                      },
                      label = { Text(sc.displayName) },
                    )
                  }
                }
              }
            }

            PreferenceDivider()

            Text(
              text = "Filter Parameters",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )

            Text(
              text = "Optional parameters for fine-tuning filter behavior",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = videoDownscaleParam1,
                onValueChange = { preferences.videoDownscaleParam1.set(it) },
                label = { Text("Parameter 1") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
              )

              OutlinedTextField(
                value = videoDownscaleParam2,
                onValueChange = { preferences.videoDownscaleParam2.set(it) },
                label = { Text("Parameter 2") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
              )
            }

            Text(
              text =
                "Current: ${videoDownscale.displayName}" +
                  if (videoDownscaleParam1.isNotBlank()) " (p1: $videoDownscaleParam1)" else "" +
                  if (videoDownscaleParam2.isNotBlank()) " (p2: $videoDownscaleParam2)" else "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}
