package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.presentation.components.ExpandableCard
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.player.DebandSettings
import app.marlboroadvance.mpvex.ui.player.Debanding
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
fun VideoSettingsDebandCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  val deband by decoderPreferences.debanding.collectAsState()
  var isExpanded by remember { mutableStateOf(true) }

  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Default.Gradient, null)
        Text(stringResource(R.string.player_sheets_deband_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        Row(
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Debanding.entries.forEach {
            IconToggleButton(
              checked = deband == it,
              onCheckedChange = { _ ->
                decoderPreferences.debanding.set(it)
                when (it) {
                  Debanding.None -> {
                    MPVLib.setOptionString("deband", "no")
                    MPVLib.command("vf", "remove", "@deband")
                  }
                  Debanding.CPU -> {
                    MPVLib.setOptionString("deband", "no")
                    MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
                  }
                  Debanding.GPU -> {
                    MPVLib.setOptionString("deband", "yes")
                    MPVLib.command("vf", "remove", "@deband")
                  }
                }
              },
            ) {
              when (it) {
                Debanding.None -> Icon(Icons.Default.NotInterested, null)
                Debanding.CPU -> Icon(Icons.Default.Memory, null)
                Debanding.GPU -> Icon(painterResource(R.drawable.expansion_card), null)
              }
            }
          }

          Text(stringResource(deband.titleRes))

          Spacer(Modifier.weight(1f))
          TextButton(
            onClick = {
              decoderPreferences.debanding.set(Debanding.None)
              MPVLib.setOptionString("deband", "no")
              MPVLib.command("vf", "remove", "@deband")
              DebandSettings.entries.forEach {
                MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
              }
            },
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(painterResource(R.drawable.ic_reset), null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }

        DebandSettings.entries.forEach { debandSettings ->
          val value by debandSettings.preference(decoderPreferences).collectAsState()
          SliderItem(
            label = stringResource(debandSettings.titleRes),
            value = value,
            valueText = value.toString(),
            onChange = {
              debandSettings.preference(decoderPreferences).set(it)
              MPVLib.setPropertyInt(debandSettings.mpvProperty, it)
            },
            min = debandSettings.start,
            max = debandSettings.end,
          )
        }
      }
    }
  }
}
