package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun PlaybackSpeedSheet(
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetPresets: () -> Unit,
  onMakeDefault: (Float) -> Unit,
  onResetDefault: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier
        .verticalScroll(rememberScrollState())
        .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      
      // Slider and +/- Buttons

        
      // Calculate slider parameters
      val minSpeed = speedPresets.firstOrNull() ?: 0.25f
      val maxSpeed = speedPresets.lastOrNull() ?: 4.0f
      
      // Helper to map speed to linear slider value (0..presets.size-1)
      fun speedToSliderProp(speed: Float): Float {
        if (speedPresets.isEmpty()) return 0f
        if (speed <= minSpeed) return 0f
        if (speed >= maxSpeed) return (speedPresets.size - 1).toFloat()
        
        for (i in 0 until speedPresets.size - 1) {
          val p1 = speedPresets[i]
          val p2 = speedPresets[i + 1]
          if (speed >= p1 && speed <= p2) {
            val fraction = (speed - p1) / (p2 - p1)
            return i + fraction
          }
        }
        return 0f
      }

      // Helper to map slider value back to speed
      fun sliderPropToSpeed(prop: Float): Float {
        if (speedPresets.isEmpty()) return 1f
        val index = prop.toInt()
        val fraction = prop - index
        
        if (index >= speedPresets.size - 1) return speedPresets.last()
        if (index < 0) return speedPresets.first()
        
        val p1 = speedPresets[index]
        val p2 = speedPresets[index + 1]
        return p1 + (p2 - p1) * fraction
      }

      // Speed Label and Value
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
          horizontalAlignment = Alignment.CenterHorizontally
      ) {
          Text(text = stringResource(R.string.player_sheets_speed_slider_label), style = MaterialTheme.typography.bodyMedium)
          Text(
              text = "${speed.toFixed(2)}x", 
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold
          )
      }

      // Slider and +/- Buttons
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
         FilledTonalIconButton(
           onClick = { onSpeedChange((speed - 0.05f).coerceAtLeast(0.05f)) },
           modifier = Modifier.size(40.dp)
        ) {
           Icon(Icons.Default.Remove, null, modifier = Modifier.size(24.dp))
        }

          Slider(
            value = speed,
            onValueChange = {
                // Snap to nearest 0.05
               val snapped = (it * 20).roundToInt() / 20f
               onSpeedChange(snapped)
            },
            valueRange = 0.1f..10f,
            modifier = Modifier.weight(1f)
          )
          
        FilledTonalIconButton(
           onClick = { onSpeedChange((speed + 0.05f).coerceAtMost(10f)) },
           modifier = Modifier.size(40.dp)
        ) {
           Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
        }
      }

      // Chips and Add/Remove Button
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
      ) {
          val defaultPresets = remember {
            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
          }

          LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(end = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            items(speedPresets.sorted()) { presetSpeed ->
              val isDefault = defaultPresets.any { kotlin.math.abs(it - presetSpeed) < 0.001f }
              
              FilterChip(
                selected = kotlin.math.abs(presetSpeed - speed) < 0.01f,
                onClick = { onSpeedChange(presetSpeed) },
                label = { Text("${presetSpeed.toFixed(2)}") },
                colors = if (!isDefault) {
                    androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    androidx.compose.material3.FilterChipDefaults.filterChipColors()
                }
              )
            }
          }

            // Add / Remove Preset Buttons
            val buttonModifier = Modifier
                .height(32.dp)
                .width(110.dp)
            
            val isCurrentSpeedSaved = speedPresets.any { kotlin.math.abs(it - speed) < 0.001f }
            val isDefaultPreset = defaultPresets.any { kotlin.math.abs(it - speed) < 0.001f }

            if (isCurrentSpeedSaved) {
                if (!isDefaultPreset) {
                     Button(
                        onClick = { onRemoveSpeedPreset(speed) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = buttonModifier
                    ) {
                       Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                       Text("Remove")
                    }
                }
            } else {
                 Button(
                    onClick = { onAddSpeedPreset(speed) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = buttonModifier
                ) {
                  Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                  Text("Add")
                }
            }
      }

      ProvidePreferenceLocals {
        val audioPreferences = koinInject<AudioPreferences>()
        
        // Audio Pitch Correction
        val pitchCorrection by audioPreferences.audioPitchCorrection.collectAsState()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    val newValue = !pitchCorrection
                    audioPreferences.audioPitchCorrection.set(newValue)
                    MPVLib.setPropertyBoolean("audio-pitch-correction", newValue)
                }
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = stringResource(id = R.string.pref_audio_pitch_correction_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.pref_audio_pitch_correction_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = pitchCorrection,
                onCheckedChange = { 
                    audioPreferences.audioPitchCorrection.set(it)
                    MPVLib.setPropertyBoolean("audio-pitch-correction", it)
                },
                modifier = Modifier.scale(0.8f) // Make switch slightly smaller
            )
        }


      }

      Row(
        modifier =
          Modifier
            .padding(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        Button(
          modifier = Modifier.weight(1f),
          onClick = { onMakeDefault(speed) },
        ) {
          Text(text = stringResource(id = R.string.player_sheets_speed_make_default))
        }
        Button(onClick = {
            onResetDefault()
            onResetPresets()
            onSpeedChange(1.0f)
        }) {
          Text(text = stringResource(id = R.string.generic_reset))
        }
      }
    }
  }
}

fun Float.toFixed(precision: Int = 1): Float {
  val factor = 10.0f.pow(precision)
  return (this * factor).roundToInt() / factor
}

