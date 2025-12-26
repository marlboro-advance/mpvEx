package app.marlboroadvance.mpvex.ui.preferences

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
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.DecoderPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.Debanding
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
        }
      }
    }
  }
}
