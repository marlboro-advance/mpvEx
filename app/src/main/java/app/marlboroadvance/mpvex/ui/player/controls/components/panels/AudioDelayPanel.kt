@file:Suppress("ktlint:standard:no-wildcard-imports")

package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun AudioDelayPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<AudioPreferences>()

  ConstraintLayout(
    modifier =
      modifier
        .fillMaxSize()
        .padding(MaterialTheme.spacing.medium),
  ) {
    val delayControlCard = createRef()

    val delay by MPVLib.propDouble["audio-delay"].collectAsState()
    DelayCard(
      delayMs = ((delay ?: 0.0) * 1000).roundToInt(),
      onDelayChange = {
        val delayInSeconds = it / 1000.0
        MPVLib.setPropertyDouble("audio-delay", delayInSeconds)
      },
      onApply = { preferences.defaultAudioDelay.set(((delay ?: 0.0) * 1000).roundToInt()) },
      onReset = { MPVLib.setPropertyDouble("audio-delay", 0.0) },
      title = { AudioDelayCardTitle(onClose = onDismissRequest) },
      delayType = DelayType.Audio,
      modifier =
        Modifier.constrainAs(delayControlCard) {
          linkTo(parent.top, parent.bottom, bias = 0.8f)
          end.linkTo(parent.end)
        },
    )
  }
}

@Composable
fun AudioDelayCardTitle(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.player_sheets_audio_delay_card_title),
      style = MaterialTheme.typography.headlineMedium,
    )
    IconButton(onClose) {
      Icon(
        Icons.Default.Close,
        null,
        modifier = Modifier.size(32.dp),
      )
    }
  }
}
