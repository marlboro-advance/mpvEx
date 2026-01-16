package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.koin.compose.koinInject

import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.domain.anime4k.Anime4KManager
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext

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
  val decoderPreferences = koinInject<DecoderPreferences>()
  val anime4kManager = koinInject<Anime4KManager>()
  koinInject<PlayerPreferences>()
  val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()
  
  val enableAnime4K by decoderPreferences.enableAnime4K.collectAsState()
  val anime4kMode by decoderPreferences.anime4kMode.collectAsState()
  val anime4kQuality by decoderPreferences.anime4kQuality.collectAsState()
  val gpuNext by decoderPreferences.gpuNext.collectAsState()
  
  val context = LocalContext.current
val scope = rememberCoroutineScope()
var infoDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }

if (infoDialogData != null) {
    AlertDialog(
        onDismissRequest = { infoDialogData = null },
        title = { Text(infoDialogData!!.first) },
        text = { Text(infoDialogData!!.second) },
        confirmButton = {
            TextButton(onClick = { infoDialogData = null }) {
                Text(stringResource(R.string.generic_ok))
            }
        }
    )
}

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
      SectionHeaderWithInfo(
        title = stringResource(R.string.player_sheets_stats_page_title),
        onInfoClick = {
             val descResName = "player_sheets_stats_page_${statisticsPage}_desc"
             val resId = context.resources.getIdentifier(descResName, "string", context.packageName)
             val description = if (resId != 0) context.getString(resId) else ""
             
             // Title for dialog: "Page X" or "Direct Title"
             val titleRes = if (statisticsPage == 0) R.string.player_sheets_tracks_off else R.string.player_sheets_stats_page_chip
             val title = if (statisticsPage == 0) context.getString(titleRes) else context.getString(titleRes, statisticsPage)
             
             infoDialogData = Pair(context.getString(R.string.player_sheets_stats_page_title), "$title: $description")
        }
      )
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
              if ((page == 0) xor (statisticsPage == 0)) MPVLib.command("script-binding", "stats/display-stats-toggle")
              if (page != 0) MPVLib.command("script-binding", "stats/display-page-$page")
              advancedPreferences.enabledStatisticsPage.set(page)
            },
            selected = statisticsPage == page,
          )
        }
      }
      
      // Shaders Controls
      if (enableAnime4K && !gpuNext) {
        // Presets (Mode) - Now on Top
        SectionHeaderWithInfo(
            title = stringResource(R.string.anime4k_mode_title),
            onInfoClick = {
                val modeEnum = try { Anime4KManager.Mode.valueOf(anime4kMode) } catch (e: Exception) { Anime4KManager.Mode.OFF }
                val descResName = "anime4k_mode_${modeEnum.name.lowercase()}_desc"
                val resId = context.resources.getIdentifier(descResName, "string", context.packageName)
                val description = if (resId != 0) context.getString(resId) else ""
                
                infoDialogData = Pair(context.getString(modeEnum.titleRes), description)
            }
        )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          items(Anime4KManager.Mode.entries) { mode ->
            FilterChip(
              label = { Text(stringResource(mode.titleRes)) },
              selected = anime4kMode == mode.name,
              onClick = {
                decoderPreferences.anime4kMode.set(mode.name)
                
                // Apply shaders immediately (runtime change)
                scope.launch(Dispatchers.IO) {
                  runCatching {
                    val qualityStr = decoderPreferences.anime4kQuality.get()
                    val quality = try {
                      Anime4KManager.Quality.valueOf(qualityStr)
                    } catch (e: IllegalArgumentException) {
                      Anime4KManager.Quality.BALANCED
                    }
                    val currentMode = try {
                        Anime4KManager.Mode.valueOf(mode.name)
                    } catch (e: IllegalArgumentException) {
                        Anime4KManager.Mode.OFF
                    }
                    
                    val shaderChain = anime4kManager.getShaderChain(currentMode, quality)
                    
                    // Use setPropertyString for runtime changes
                    MPVLib.setPropertyString("glsl-shaders", if (shaderChain.isNotEmpty()) shaderChain else "")
                    
                    // Show toast on main thread
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                      Toast.makeText(context, context.getString(R.string.anime4k_mode_toast, context.getString(mode.titleRes)), Toast.LENGTH_SHORT).show()
                    }
                  }
                }
              }
            )
          }
        }

        // Variants (Quality) - Now on Bottom, Hidden if Presets is OFF
        if (anime4kMode != "OFF") {
            SectionHeaderWithInfo(
                title = stringResource(R.string.anime4k_quality_title),
                onInfoClick = {
                     val qualityEnum = try { Anime4KManager.Quality.valueOf(anime4kQuality) } catch (e: Exception) { Anime4KManager.Quality.BALANCED }
                     val descResName = "anime4k_quality_${qualityEnum.name.lowercase()}_desc"
                     val resId = context.resources.getIdentifier(descResName, "string", context.packageName)
                     val description = if (resId != 0) context.getString(resId) else ""
                     
                     infoDialogData = Pair(context.getString(qualityEnum.titleRes), description)
                }
            )
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            ) {
              items(Anime4KManager.Quality.entries) { quality ->
                 FilterChip(
                  label = { Text(stringResource(quality.titleRes)) },
                  selected = anime4kQuality == quality.name,
                  onClick = {
                    decoderPreferences.anime4kQuality.set(quality.name)

                    // Apply shaders immediately (runtime change)
                    scope.launch(Dispatchers.IO) {
                      runCatching {
                        val modeStr = decoderPreferences.anime4kMode.get()
                        val modeEnum = try {
                          Anime4KManager.Mode.valueOf(modeStr)
                        } catch (e: IllegalArgumentException) {
                          Anime4KManager.Mode.OFF
                        }
                        val currentQuality = try {
                            Anime4KManager.Quality.valueOf(quality.name)
                        } catch (e: IllegalArgumentException) {
                            Anime4KManager.Quality.BALANCED
                        }
                        
                        val shaderChain = anime4kManager.getShaderChain(modeEnum, currentQuality)
                        
                        // Use setPropertyString for runtime changes
                        MPVLib.setPropertyString("glsl-shaders", if (shaderChain.isNotEmpty()) shaderChain else "")
                        
                        // Show toast on main thread
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                          Toast.makeText(context, context.getString(R.string.anime4k_quality_toast, context.getString(quality.titleRes)), Toast.LENGTH_SHORT).show()
                        }
                      }
                    }
                  }
                )
              }
            }
        }
      }
      val audioChannels by audioPreferences.audioChannels.collectAsState()
      SectionHeaderWithInfo(
        title = stringResource(id = R.string.pref_audio_channels),
        onInfoClick = {
            val descResName = "pref_audio_channels_${audioChannels.value.replace("-safe", "_safe").replace("-", "_")}_desc"
            // Special handling for reversed stereo if value string doesn't match resource convention
            val finalDescResName = if (audioChannels.name == "ReverseStereo") "pref_audio_channels_reverse_stereo_desc" else descResName
            
            val resId = context.resources.getIdentifier(finalDescResName, "string", context.packageName)
            val description = if (resId != 0) context.getString(resId) else ""
            
            infoDialogData = Pair(context.getString(audioChannels.title), description)
        }
      )
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        items(AudioChannels.entries) {
          FilterChip(
            selected = audioChannels == it,
            onClick = {
              audioPreferences.audioChannels.set(it)
              if (it == AudioChannels.ReverseStereo) {
                MPVLib.setPropertyString(AudioChannels.AutoSafe.property, AudioChannels.AutoSafe.value)
              } else {
                MPVLib.setPropertyString(AudioChannels.ReverseStereo.property, "")
              }
              MPVLib.setPropertyString(it.property, it.value)
            },
            label = { Text(text = stringResource(id = it.title)) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      modifier = modifier
          .width(360.dp) // Fixed wide width to fit presets
          .padding(MaterialTheme.spacing.medium),
    ) {
      Column(
        modifier =
          Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        var currentLayoutType by rememberSaveable { mutableIntStateOf(0) }
        
        // Header
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                  text = stringResource(R.string.timer_title), // "Sleep Timer"
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
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
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Toggle Button
             IconButton(
                onClick = { currentLayoutType = if (currentLayoutType == 0) 1 else 0 },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }

        val state =
          rememberTimePickerState(
            remainingTime / 3600,
            (remainingTime % 3600) / 60,
            is24Hour = true,
          )
          
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxWidth()
        ) {
          if (currentLayoutType == 1) {
            TimePicker(state = state)
          } else {
            TimeInput(state = state)
          }
        }
        
        // Quick Presets
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Quick Presets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(15, 30, 45, 60)
                presets.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { 
                            onTimeSelect(minutes * 60)
                            onDismissRequest()
                        },
                        label = { Text("${minutes}m") }
                    )
                }
            }
        }

        // Actions
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          TextButton(onClick = {
             onTimeSelect(0)
             onDismissRequest()
          }) {
              Text(stringResource(id = R.string.generic_reset))
          }
          Spacer(Modifier.weight(1f))
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TextButton(onClick = onDismissRequest) {
              Text(stringResource(id = R.string.generic_cancel))
            }
            Button(
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


@Composable
fun SectionHeaderWithInfo(
  title: String,
  onInfoClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.width(8.dp))
    IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = "Info",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

