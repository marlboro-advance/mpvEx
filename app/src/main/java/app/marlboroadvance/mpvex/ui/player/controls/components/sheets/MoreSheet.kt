package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.AudioChannels
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
  remainingTime: Int,
  onStartTimer: (Int) -> Unit,
  onDismissRequest: () -> Unit,
  onEnterFiltersPanel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val audioPreferences = koinInject<AudioPreferences>()
  koinInject<PlayerPreferences>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  val scope = rememberCoroutineScope()

  PlayerSheet(
    onDismissRequest,
    modifier,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(MaterialTheme.spacing.medium)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(id = R.string.player_sheets_more_title),
          style = MaterialTheme.typography.headlineMedium,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          var isSleepTimerDialogShown by remember { mutableStateOf(false) }
          TextButton(onClick = { isSleepTimerDialogShown = true }) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
              Icon(imageVector = Icons.Outlined.Timer, contentDescription = null)
              Text(
                text =
                  if (remainingTime == 0) {
                    stringResource(R.string.timer_title)
                  } else {
                    stringResource(
                      R.string.timer_remaining,
                      DateUtils.formatElapsedTime(remainingTime.toLong()),
                    )
                  },
              )
              if (isSleepTimerDialogShown) {
                TimePickerDialog(
                  remainingTime = remainingTime,
                  onDismissRequest = { isSleepTimerDialogShown = false },
                  onTimeSelect = onStartTimer,
                )
              }
            }
          }
          TextButton(onClick = onEnterFiltersPanel) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
              Icon(imageVector = Icons.Default.Tune, contentDescription = null)
              Text(text = stringResource(id = R.string.player_sheets_filters_title))
            }
          }
        }
      }
      Text(stringResource(R.string.player_sheets_stats_page_title))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        items(6) { page ->
          FilterChip(
            label = {
              Text(
                stringResource(
                  if (page ==
                    0
                  ) {
                    R.string.player_sheets_tracks_off
                  } else {
                    R.string.player_sheets_stats_page_chip
                  },
                  page,
                ),
              )
            },
            onClick = {
              scope.launch(Dispatchers.IO) {
                if ((page == 0) xor
                  (statisticsPage == 0)
                ) {
                  MPVLib.command("script-binding", "stats/display-stats-toggle")
                }
                if (page != 0) MPVLib.command("script-binding", "stats/display-page-$page")
              }
              advancedPreferences.enabledStatisticsPage.set(page)
            },
            selected = statisticsPage == page,
          )
        }
      }
      Text(text = stringResource(id = R.string.pref_audio_channels))
      val audioChannels by audioPreferences.audioChannels.collectAsState()
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        items(AudioChannels.entries) {
          FilterChip(
            selected = audioChannels == it,
            onClick = {
              audioPreferences.audioChannels.set(it)
              scope.launch(Dispatchers.IO) {
                if (it == AudioChannels.ReverseStereo) {
                  MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
                } else {
                  MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
                }
                MPVLib.setPropertyString(it.property, it.value)
              }
            },
            label = { Text(text = stringResource(id = it.title)) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
  onDismissRequest: () -> Unit,
  onTimeSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  remainingTime: Int = 0,
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.padding(MaterialTheme.spacing.medium),
    ) {
      Column(
        modifier =
          Modifier
            .verticalScroll(rememberScrollState())
            .width(IntrinsicSize.Max)
            .animateContentSize()
            .padding(MaterialTheme.spacing.medium),
      ) {
        var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
        Text(
          text =
            stringResource(
              id =
                if (currentLayoutType == 1) {
                  R.string.timer_picker_pick_time
                } else {
                  R.string.timer_picker_enter_timer
                },
            ),
        )

        val state =
          rememberTimePickerState(
            remainingTime / 3600,
            (remainingTime % 3600) / 60,
            is24Hour = true,
          )
        Box(
          contentAlignment = Alignment.Center,
        ) {
          if (currentLayoutType == 1) {
            TimePicker(state = state)
          } else {
            TimeInput(state = state)
          }
        }
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          IconButton(onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 }) {
            Icon(
              imageVector =
                if (currentLayoutType ==
                  0
                ) {
                  Icons.Outlined.Schedule
                } else {
                  Icons.Default.KeyboardAlt
                },
              contentDescription = null,
            )
          }
          Row {
            TextButton(onClick = onDismissRequest) {
              Text(stringResource(id = R.string.generic_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
              onClick = {
                onTimeSelect(state.hour * 3600 + state.minute * 60)
                onDismissRequest()
              },
            ) {
              Text(stringResource(id = R.string.generic_ok))
            }
          }
        }
      }
    }
  }
}
