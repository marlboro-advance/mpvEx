package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object ListPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val browserPreferences = koinInject<BrowserPreferences>()
    val backstack = LocalBackStack.current

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_browser_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(padding),
        ) {
          PreferenceCategory(
            title = { Text(text = stringResource(id = R.string.pref_browser_category_scanning)) },
          )

          val respectNomedia by browserPreferences.respectNomedia.collectAsState()
          SwitchPreference(
            value = respectNomedia,
            onValueChange = { browserPreferences.respectNomedia.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_browser_recognize_nomedia_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_browser_recognize_nomedia_summary)) },
          )

          val showHiddenFiles by browserPreferences.showHiddenFiles.collectAsState()
          SwitchPreference(
            value = showHiddenFiles,
            onValueChange = { browserPreferences.showHiddenFiles.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_browser_show_hidden_title)) },
            summary = { Text(text = stringResource(id = R.string.pref_browser_show_hidden_summary)) },
          )

          PreferenceCategory(
            title = { Text(text = stringResource(id = R.string.pref_folders_title)) },
          )

          Preference(
            title = { Text(text = stringResource(id = R.string.pref_folders_summary)) }, // Manage folder blacklist
            summary = { Text(text = stringResource(id = R.string.pref_folders_title)) },
            icon = { Icon(Icons.Outlined.FolderOff, null) },
            onClick = { backstack.add(FoldersPreferencesScreen) },
          )
        }
      }
    }
  }
}
