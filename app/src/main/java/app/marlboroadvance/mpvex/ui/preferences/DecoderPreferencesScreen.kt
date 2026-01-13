package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.Debanding
import app.marlboroadvance.mpvex.ui.player.TemporalScaler
import app.marlboroadvance.mpvex.ui.player.VideoSync
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object DecoderPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<DecoderPreferences>()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val backstack = LocalBackStack.current
    var showGpuNextWarning by remember { mutableStateOf(false) }
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_decoder),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val videoInterpolation by preferences.videoInterpolation.collectAsState()
        val videoSync by preferences.videoSync.collectAsState()
        val videoTscale by preferences.videoTscale.collectAsState()
        val videoTscaleParam1 by preferences.videoTscaleParam1.collectAsState()
        val videoScale by preferences.videoScale.collectAsState()
        val videoScaleParam1 by preferences.videoScaleParam1.collectAsState()
        val videoDownscale by preferences.videoDownscale.collectAsState()
        val videoDownscaleParam1 by preferences.videoDownscaleParam1.collectAsState()

        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_decoder))
          }

          item {
            PreferenceCard {
              val tryHWDecoding by preferences.tryHWDecoding.collectAsState()
              SwitchPreference(
                value = tryHWDecoding,
                onValueChange = {
                  preferences.tryHWDecoding.set(it)
                },
                title = { Text(stringResource(R.string.pref_decoder_try_hw_dec_title)) },
              )

              PreferenceDivider()

              val gpuNext by preferences.gpuNext.collectAsState()
              SwitchPreference(
                value = gpuNext,
                onValueChange = { enabled ->
                    if (enabled && !gpuNext) {
                        showGpuNextWarning = true
                    } else {
                        preferences.gpuNext.set(enabled)
                        if (enabled) {
                            preferences.enableAnime4K.set(false)
                        }
                    }
                },
                title = { Text(stringResource(R.string.pref_decoder_gpu_next_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_decoder_gpu_next_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              if (showGpuNextWarning) {
                  AlertDialog(
                      onDismissRequest = { showGpuNextWarning = false },
                      title = { Text(stringResource(R.string.pref_decoder_gpu_next_enable_title)) },
                      text = {
                          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text(stringResource(R.string.pref_decoder_gpu_next_warning))
                              Text(stringResource(R.string.pref_decoder_gpu_next_purple_screen_fix))
                              
                              Surface(
                                  color = MaterialTheme.colorScheme.errorContainer,
                                  shape = MaterialTheme.shapes.small
                              ) {
                                  Column(modifier = Modifier.padding(8.dp)) {
                                      Text(
                                          text = stringResource(R.string.pref_anime4k_incompatibility),
                                          style = MaterialTheme.typography.titleSmall,
                                          color = MaterialTheme.colorScheme.onErrorContainer
                                      )
                                      Text(
                                          text = stringResource(R.string.pref_anime4k_gpu_next_error),
                                          style = MaterialTheme.typography.bodySmall,
                                          color = MaterialTheme.colorScheme.onErrorContainer
                                      )
                                  }
                              }
                          }
                      },
                      confirmButton = {
                          Button(onClick = {
                              preferences.gpuNext.set(true)
                              preferences.enableAnime4K.set(false)
                              showGpuNextWarning = false
                          }) {
                              Text(stringResource(R.string.pref_decoder_gpu_next_enable_anyway))
                          }
                      },
                      dismissButton = {
                          TextButton(onClick = { showGpuNextWarning = false }) {
                              Text(stringResource(R.string.generic_cancel))
                          }
                      }
                  )
              }

              PreferenceDivider()

              val debanding by preferences.debanding.collectAsState()
              ListPreference(
                value = debanding,
                onValueChange = { preferences.debanding.set(it) },
                values = Debanding.entries,
                title = { Text(stringResource(R.string.pref_decoder_debanding_title)) },
                summary = {
                  Text(
                    debanding.name,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val useYUV420p by preferences.useYUV420P.collectAsState()
              SwitchPreference(
                value = useYUV420p,
                onValueChange = {
                  preferences.useYUV420P.set(it)
                },
                title = { Text(stringResource(R.string.pref_decoder_yuv420p_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_decoder_yuv420p_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          // Video Interpolation Section
          item {
            PreferenceSectionHeader(title = "Video Interpolation")
          }

          item {
            PreferenceCard {
              me.zhanghai.compose.preference.Preference(
                title = { Text("Frame Interpolation") },
                summary = {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Text(
                      if (videoInterpolation) "Enabled - ${videoSync.displayName}" else "Disabled",
                      color = MaterialTheme.colorScheme.outline,
                    )
                    if (videoInterpolation) {
                      Text(
                        "Temporal: ${videoTscale.displayName}${if (videoTscaleParam1.isNotBlank()) " (p1: $videoTscaleParam1)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                },
                onClick = { backstack.add(InterpolationSettingsScreen) },
              )
            }
          }

          // Video Scalers Section
          item {
            PreferenceSectionHeader(title = "Video Scalers")
          }

          item {
            PreferenceCard {
              me.zhanghai.compose.preference.Preference(
                title = { Text("Upscaling Filter") },
                summary = {
                  Text(
                    "${videoScale.displayName}${if (videoScaleParam1.isNotBlank()) " (${videoScaleParam1})" else ""}",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { backstack.add(UpscaleFilterScreen) },
              )



              me.zhanghai.compose.preference.Preference(
                title = { Text("Downscaling Filter") },
                summary = {
                  Text(
                    "${videoDownscale.displayName}${if (videoDownscaleParam1.isNotBlank()) " (${videoDownscaleParam1})" else ""}",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { backstack.add(DownscaleFilterScreen) },
              )
              
              PreferenceDivider()
              
              val enableAnime4K by preferences.enableAnime4K.collectAsState()
              SwitchPreference(
                value = enableAnime4K,
                onValueChange = { enabled ->
                    preferences.enableAnime4K.set(enabled)
                    if (enabled) {
                        preferences.gpuNext.set(false)
                    }
                },
                title = { Text(stringResource(R.string.pref_anime4k_title)) },
                summary = { 
                  Text(
                    stringResource(R.string.pref_anime4k_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
              )
            }
          }
        }
      }
    }
  }
}
