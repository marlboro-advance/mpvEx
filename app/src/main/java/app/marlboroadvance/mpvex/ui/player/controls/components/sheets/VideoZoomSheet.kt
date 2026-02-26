package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@Composable
fun VideoZoomSheet(
  videoZoom: Float,
  onSetVideoZoom: (Float) -> Unit,
  onResetVideoPan: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val defaultZoom by playerPreferences.defaultVideoZoom.collectAsState()
  val panAndZoomEnabled by playerPreferences.panAndZoomEnabled.collectAsState()
  var zoom by remember { mutableFloatStateOf(videoZoom) }

  val currentOnSetVideoZoom by rememberUpdatedState(onSetVideoZoom)

  LaunchedEffect(Unit) {
    val mpvZoom = MPVLib.getPropertyDouble("video-zoom")?.toFloat() ?: videoZoom
    zoom = mpvZoom
  }

  LaunchedEffect(zoom) {
    currentOnSetVideoZoom(zoom)
  }

  PlayerSheet(onDismissRequest = onDismissRequest) {
    ZoomVideoSheet(
      zoom = zoom,
      defaultZoom = defaultZoom,
      panAndZoomEnabled = panAndZoomEnabled,
      onZoomChange = { newZoom -> zoom = newZoom },
      onSetAsDefault = {
        playerPreferences.defaultVideoZoom.set(zoom)
      },
      onReset = {
        zoom = 0f
        playerPreferences.defaultVideoZoom.set(0f)
        onResetVideoPan()
      },
      onPanAndZoomToggle = { enabled ->
        playerPreferences.panAndZoomEnabled.set(enabled)
        if (!enabled) {
          onResetVideoPan()
        }
      },
      modifier = modifier,
    )
  }
}

@Composable
private fun ZoomVideoSheet(
  zoom: Float,
  defaultZoom: Float,
  panAndZoomEnabled: Boolean,
  onZoomChange: (Float) -> Unit,
  onSetAsDefault: () -> Unit,
  onReset: () -> Unit,
  onPanAndZoomToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isDefault = zoom == defaultZoom
  val isZero = zoom == 0f

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(vertical = MaterialTheme.spacing.medium),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    // Zoom slider with +/- buttons
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      FilledTonalIconButton(
        onClick = {
          val newZoom = (zoom - 0.01f).coerceAtLeast(-1f)
          onZoomChange(newZoom)
        },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(Icons.Default.Remove, contentDescription = "Decrease zoom", modifier = Modifier.size(18.dp))
      }

      SliderItem(
        label = stringResource(id = R.string.player_sheets_zoom_slider_label),
        value = zoom,
        valueText = "%.2fx".format(zoom),
        onChange = onZoomChange,
        max = 3f,
        min = -1f,
        modifier = Modifier.weight(1f),
      )

      FilledTonalIconButton(
        onClick = {
          val newZoom = (zoom + 0.01f).coerceAtMost(3f)
          onZoomChange(newZoom)
        },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = "Increase zoom", modifier = Modifier.size(18.dp))
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )

    // Pan & Zoom toggle + action buttons
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Pan & Zoom toggle
      Switch(
        checked = panAndZoomEnabled,
        onCheckedChange = onPanAndZoomToggle,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Pan & Zoom",
        style = MaterialTheme.typography.bodyMedium,
        color = if (panAndZoomEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.weight(1f))

      // Action buttons
      OutlinedButton(
        onClick = onSetAsDefault,
        enabled = !isDefault,
      ) {
        Text(stringResource(R.string.set_as_default), style = MaterialTheme.typography.labelMedium)
      }

      Spacer(modifier = Modifier.width(8.dp))

      Button(
        onClick = onReset,
        enabled = !isZero,
      ) {
        Text(stringResource(R.string.generic_reset), style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}
