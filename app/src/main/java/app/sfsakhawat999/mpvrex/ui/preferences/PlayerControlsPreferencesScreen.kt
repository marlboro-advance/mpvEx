package app.sfsakhawat999.mpvrex.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VideoLabel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.AppearancePreferences
import app.sfsakhawat999.mpvrex.preferences.ControlLayoutPreview
import app.sfsakhawat999.mpvrex.preferences.PlayerButton
import app.sfsakhawat999.mpvrex.preferences.getPlayerButtonLabel
import app.sfsakhawat999.mpvrex.preferences.preference.collectAsState
import app.sfsakhawat999.mpvrex.presentation.Screen
import app.sfsakhawat999.mpvrex.presentation.components.ConfirmDialog
import app.sfsakhawat999.mpvrex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

// Enum to identify which region we are editing
@Serializable
enum class ControlRegion {
  TOP_RIGHT,
  BOTTOM_RIGHT,
  BOTTOM_LEFT
}

@Serializable
object PlayerControlsPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()

    // Get the current state for all three regions
    val topState by preferences.topRightControls.collectAsState()
    val bottomState by preferences.bottomRightControls.collectAsState()
    val leftState by preferences.bottomLeftControls.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
      ConfirmDialog(
        title = stringResource(id = R.string.pref_layout_reset_title),
        subtitle = stringResource(id = R.string.pref_layout_reset_summary),
        onConfirm = {
          preferences.topRightControls.delete()
          preferences.bottomRightControls.delete()
          preferences.bottomLeftControls.delete()
          showResetDialog = false
        },
        onCancel = {
          showResetDialog = false
        },
      )
    }

    val (topRightButtons, bottomRightButtons, bottomLeftButtons) = remember(topState, bottomState, leftState) {
      val usedButtons = mutableSetOf<PlayerButton>()
      val topR = preferences.parseButtons(topState, usedButtons)
      val bottomR = preferences.parseButtons(bottomState, usedButtons)
      val bottomL = preferences.parseButtons(leftState, usedButtons)
      Triple(topR, bottomR, bottomL)
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_layout_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            IconButton(onClick = { showResetDialog = true }) {
              Icon(Icons.Outlined.Restore, contentDescription = stringResource(id = R.string.pref_layout_reset_default))
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
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_top_right_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.TOP_RIGHT))
              },
            )
            PreferenceIconSummary(buttons = topRightButtons)
          }

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_bottom_right_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_RIGHT))
              },
            )
            PreferenceIconSummary(buttons = bottomRightButtons)
          }

          item {
            PreferenceCategoryWithEditButton(
              title = stringResource(id = R.string.pref_layout_bottom_left_controls),
              onClick = {
                backstack.add(ControlLayoutEditorScreen(ControlRegion.BOTTOM_LEFT))
              },
            )
            PreferenceIconSummary(buttons = bottomLeftButtons, showFixedChapter = false)
          }

          item {
            PreferenceCategory(title = { Text(stringResource(id = R.string.pref_layout_preview)) })
            ControlLayoutPreview(
              topRightButtons = topRightButtons,
              bottomRightButtons = bottomRightButtons,
              bottomLeftButtons = bottomLeftButtons,
              modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
          }
        }
      }
    }
  }

  /**
   * Custom composable for the category header with an Edit button.
   */
  @Composable
  private fun PreferenceCategoryWithEditButton(title: String, onClick: () -> Unit) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 8.dp), // Apply padding to Row
      verticalAlignment = Alignment.CenterVertically, // Align items vertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f) // Text takes all available space, pushing button to end
      )
      IconButton(onClick = onClick) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit $title",
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }

  /**
   * Custom composable to show a row of icons for the summary.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  private fun PreferenceIconSummary(
    buttons: List<PlayerButton>,
    showFixedChapter: Boolean = false,
  ) {
    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp), // Increased spacing
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (buttons.isEmpty() && !showFixedChapter) {
        Text(
          "None", // TODO: strings
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        buttons.forEach {
          Icon(
            imageVector = it.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (showFixedChapter) {
          Icon(
            imageVector = Icons.Outlined.VideoLabel,
            contentDescription = "Fixed Chapter",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
