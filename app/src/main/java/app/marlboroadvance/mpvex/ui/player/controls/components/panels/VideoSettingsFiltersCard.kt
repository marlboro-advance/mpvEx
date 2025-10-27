package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.presentation.components.ExpandableCard
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.player.VideoFilters
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import me.zhanghai.compose.preference.FooterPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
fun VideoSettingsFiltersCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  var isExpanded by remember { mutableStateOf(true) }

  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.Default.Tune, null)
        Text(stringResource(R.string.player_sheets_filters_title))
      }
    },
    colors = panelCardsColors(),
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
  ) {
    ProvidePreferenceLocals {
      Column {
        TextButton(
          onClick = {
            VideoFilters.entries.forEach {
              MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
            }
          },
        ) {
          Text(text = stringResource(id = R.string.generic_reset))
        }

        VideoFilters.entries.forEach { filter ->
          val value by filter.preference(decoderPreferences).collectAsState()
          SliderItem(
            label = stringResource(filter.titleRes),
            value = value,
            valueText = value.toString(),
            onChange = {
              filter.preference(decoderPreferences).set(it)
              MPVLib.setPropertyInt(filter.mpvProperty, it)
            },
            max = 100,
            min = -100,
          )
        }

        if (!decoderPreferences.gpuNext.get()) {
          FooterPreference(
            summary = {
              Text(text = stringResource(id = R.string.player_sheets_filters_warning))
            },
          )
        }
      }
    }
  }
}
