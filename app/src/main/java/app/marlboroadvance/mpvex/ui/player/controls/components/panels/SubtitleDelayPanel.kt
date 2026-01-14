package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.presentation.components.OutlinedNumericChooser
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun SubtitleDelayPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<SubtitlesPreferences>()

  ConstraintLayout(
    modifier =
      modifier
        .fillMaxSize()
        .padding(MaterialTheme.spacing.medium),
  ) {
    val delayControlCard = createRef()

    val delay by MPVLib.propDouble["sub-delay"].collectAsState()
    val delayFloat by remember { derivedStateOf { (delay ?: 0.0).toFloat() } }
    val speed by MPVLib.propDouble["sub-speed"].collectAsState()
    val speedFloat by remember { derivedStateOf { (speed ?: 1.0).toFloat() } }
    SubtitleDelayCard(
      delay = delayFloat,
      onDelayChange = {
        MPVLib.setPropertyDouble("sub-delay", it.toDouble())
      },
      speed = speedFloat,
      onSpeedChange = { MPVLib.setPropertyDouble("sub-speed", it.toDouble()) },
      onApply = {
        preferences.defaultSubDelay.set((delayFloat * 1000).roundToInt())
        val currentSpeed = speed ?: 1.0
        if (currentSpeed in 0.1..10.0) preferences.defaultSubSpeed.set(currentSpeed.toFloat())
      },
      onReset = {
        MPVLib.setPropertyDouble("sub-delay", preferences.defaultSubDelay.get() / 1000.0)
        MPVLib.setPropertyDouble("sub-speed", preferences.defaultSubSpeed.get().toDouble())
      },
      onClose = onDismissRequest,
      modifier =
        Modifier.constrainAs(delayControlCard) {
          top.linkTo(parent.top)
          end.linkTo(parent.end)
        },
    )
  }
}

@Composable
fun SubtitleDelayCard(
  delay: Float,
  onDelayChange: (Float) -> Unit,
  speed: Float,
  onSpeedChange: (Float) -> Unit,
  onApply: () -> Unit,
  onReset: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DelayCard(
    delay = delay,
    onDelayChange = onDelayChange,
    onApply = onApply,
    onReset = onReset,
    title = {
      SubtitleDelayTitle(onClose = onClose)
    },
    extraSettings = {
      OutlinedNumericChooser(
        label = { Text(stringResource(R.string.player_sheets_sub_delay_card_speed)) },
        value = speed,
        onChange = onSpeedChange,
        max = 10f,
        step = 0.01f,
        min = 0.1f,
        increaseIcon = Icons.Filled.Add,
        decreaseIcon = Icons.Filled.Remove,
        valueFormatter = { "%.2f".format(it) }
      )
    },
    delayType = DelayType.Subtitle,
    modifier = modifier,
  )
}

@Suppress("LambdaParameterInRestartableEffect") // Intentional
@Composable
fun DelayCard(
  delay: Float,
  onDelayChange: (Float) -> Unit,
  onApply: () -> Unit,
  onReset: () -> Unit,
  title: @Composable () -> Unit,
  delayType: DelayType,
  modifier: Modifier = Modifier,
  extraSettings: @Composable ColumnScope.() -> Unit = {},
) {
  Card(
    modifier =
      modifier
        .widthIn(max = CARDS_MAX_WIDTH)
        .animateContentSize(),
    colors = panelCardsColors(),
    shape = MaterialTheme.shapes.large,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    ),
    elevation = androidx.compose.material3.CardDefaults.cardElevation(
      defaultElevation = 0.dp,
      pressedElevation = 0.dp,
      focusedElevation = 0.dp,
      hoveredElevation = 0.dp,
      draggedElevation = 0.dp,
      disabledElevation = 0.dp,
    ),
  ) {
    Column(
      Modifier
        .verticalScroll(rememberScrollState())
        .padding(MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      title()
      OutlinedNumericChooser(
        label = { Text(stringResource(R.string.player_sheets_sub_delay_card_delay)) },
        value = delay,
        onChange = onDelayChange,
        step = 0.1f,
        min = Float.NEGATIVE_INFINITY,
        max = Float.POSITIVE_INFINITY,
        suffix = { Text("s") },
        increaseIcon = Icons.Filled.Add,
        decreaseIcon = Icons.Filled.Remove,
        valueFormatter = { "%.1f".format(it) }
      )
      Column(
        modifier = Modifier.animateContentSize(),
      ) { extraSettings() }
      // true (heard -> spotted), false (spotted -> heard)
      var isDirectionPositive by remember { mutableStateOf<Boolean?>(null) }
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        var timerStart by remember { mutableStateOf<Long?>(null) }
        var finalDelay by remember { mutableStateOf(delay) }
        LaunchedEffect(isDirectionPositive) {
          if (isDirectionPositive == null) {
            onDelayChange(finalDelay)
            return@LaunchedEffect
          }
          finalDelay = delay
          val startTime = System.currentTimeMillis()
          timerStart = startTime
          val startingDelay: Float = finalDelay
          while (isDirectionPositive != null && timerStart != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val direction = isDirectionPositive ?: break
            finalDelay = startingDelay + (if (direction) elapsed / 1000f else -elapsed / 1000f)
            // Arbitrary delay of 20ms
            delay(20)
          }
        }
        Button(
          onClick = {
            isDirectionPositive = if (isDirectionPositive == null) delayType == DelayType.Audio else null
          },
          modifier = Modifier.weight(1f),
          enabled = isDirectionPositive != (delayType == DelayType.Audio),
        ) {
          Text(
            stringResource(
              if (delayType == DelayType.Audio) {
                R.string.player_sheets_sub_delay_audio_sound_heard
              } else {
                R.string.player_sheets_sub_delay_subtitle_voice_heard
              },
            ),
          )
        }
        Button(
          onClick = {
            isDirectionPositive = if (isDirectionPositive == null) delayType != DelayType.Audio else null
          },
          modifier = Modifier.weight(1f),
          enabled = isDirectionPositive != (delayType == DelayType.Subtitle),
        ) {
          Text(
            stringResource(
              if (delayType == DelayType.Audio) {
                R.string.player_sheets_sub_delay_sound_sound_spotted
              } else {
                R.string.player_sheets_sub_delay_subtitle_text_seen
              },
            ),
          )
        }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        Button(
          onClick = onApply,
          modifier = Modifier.weight(1f),
          enabled = isDirectionPositive == null,
        ) {
          Text(stringResource(R.string.player_sheets_delay_set_as_default))
        }
        FilledIconButton(
          onClick = onReset,
          enabled = isDirectionPositive == null,
        ) {
          Icon(Icons.Default.Refresh, null)
        }
      }
    }
  }
}

@Composable
fun SubtitleDelayTitle(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      stringResource(R.string.player_sheets_sub_delay_card_title),
      style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onClose) {
      Icon(Icons.Default.Close, null, modifier = Modifier.size(32.dp))
    }
  }
}

enum class DelayType {
  Audio,
  Subtitle,
}
