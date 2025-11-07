package app.sfsakhawat999.mpvrex.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.PlayerPreferences
import app.sfsakhawat999.mpvrex.preferences.preference.collectAsState
import app.sfsakhawat999.mpvrex.presentation.components.PlayerSheet
import app.sfsakhawat999.mpvrex.presentation.components.SliderItem
import app.sfsakhawat999.mpvrex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@Composable
fun VideoZoomSheet(
  videoZoom: Float,
  onSetVideoZoom: (Float) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val playerPreferences = koinInject<PlayerPreferences>()
  val defaultZoom by playerPreferences.defaultVideoZoom.collectAsState()
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
      onZoomChange = { newZoom -> zoom = newZoom },
      onSetAsDefault = {
        playerPreferences.defaultVideoZoom.set(zoom)
      },
      onReset = {
        zoom = 0f
        playerPreferences.defaultVideoZoom.set(0f)
      },
      modifier = modifier,
    )
  }
}

@Composable
private fun ZoomVideoSheet(
  zoom: Float,
  defaultZoom: Float,
  onZoomChange: (Float) -> Unit,
  onSetAsDefault: () -> Unit,
  onReset: () -> Unit,
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
  ) {
    SliderItem(
      label = stringResource(id = R.string.player_sheets_zoom_slider_label),
      value = zoom,
      valueText =
        when {
          isZero && isDefault -> "%.2fx (default)".format(zoom)
          isDefault -> "%.2fx (default)".format(zoom)
          isZero -> "%.2fx".format(zoom)
          else -> "%.2fx".format(zoom)
        },
      onChange = onZoomChange,
      max = 3f,
      min = -2f,
      modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
    )

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller, Alignment.End),
    ) {
      Button(
        onClick = onSetAsDefault,
        enabled = !isDefault,
      ) {
        Text(stringResource(R.string.set_as_default))
      }

      Button(
        onClick = onReset,
        enabled = !isZero,
      ) {
        Text(stringResource(R.string.generic_reset))
      }
    }
  }
}
