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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
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
    val backstack = LocalBackStack.current
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
                onValueChange = {
                  preferences.gpuNext.set(it)
                },
                title = { Text(stringResource(R.string.pref_decoder_gpu_next_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_decoder_gpu_next_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

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

              PreferenceDivider()

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
            }
          }
        }
      }
    }
  }
}
