package app.marlboroadvance.mpvex.preferences

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.preferences.preference.getEnum
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

// PlayerButton enum is now in PlayerButton.kt

class AppearancePreferences(
  private val preferenceStore: PreferenceStore,
) {
  // Default to Dark theme and Material You enabled
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.System)
  val materialYou = preferenceStore.getBoolean("material_you", true)
  val amoledMode = preferenceStore.getBoolean("amoled_mode", false)
  val unlimitedNameLines = preferenceStore.getBoolean("unlimited_name_lines", false)
  val hidePlayerButtonsBackground = preferenceStore.getBoolean("hide_player_buttons_background", false)

  // --- Player Control Preferences ---

  /**
   * Comma-separated list of [PlayerButton] enum names for the top left controls.
   * Change the order by rearranging button names in the comma-separated string.
   * Available buttons: BOOKMARKS_CHAPTERS, PLAYBACK_SPEED, DECODER, SCREEN_ROTATION,
   * FRAME_NAVIGATION, VIDEO_ZOOM, PICTURE_IN_PICTURE, ASPECT_RATIO, LOCK_CONTROLS,
   * AUDIO_TRACK, SUBTITLES, CURRENT_CHAPTER
   *
   * Note: BACK_ARROW, VIDEO_TITLE, and MORE_OPTIONS are constant and cannot be customized.
   */
  val topLeftControls =
    preferenceStore.getString(
      "top_left_controls",
      "BACK_ARROW,VIDEO_TITLE", // <-- Change default order here
    )

  /**
   * Comma-separated list of [PlayerButton] enum names for the top right controls.
   * Change the order by rearranging button names in the comma-separated string.
   */
  val topRightControls =
    preferenceStore.getString(
      "top_right_controls",
      "DECODER,AUDIO_TRACK,SUBTITLES,MORE_OPTIONS", // <-- Change default order here (removed BOOKMARKS_CHAPTERS)
    )

  /**
   * Comma-separated list of [PlayerButton] enum names for the bottom right controls.
   * Change the order by rearranging button names in the comma-separated string.
   */
  val bottomRightControls =
    preferenceStore.getString(
      "bottom_right_controls",
      "FRAME_NAVIGATION,VIDEO_ZOOM,PICTURE_IN_PICTURE,ASPECT_RATIO", // <-- Change default order here
    )

  /**
   * Comma-separated list of [PlayerButton] enum names for the bottom left controls.
   * Change the order by rearranging button names in the comma-separated string.
   */
  val bottomLeftControls =
    preferenceStore.getString(
      "bottom_left_controls",
      "LOCK_CONTROLS,SCREEN_ROTATION,PLAYBACK_SPEED,CURRENT_CHAPTER", // <-- Change default order here
    )

  /**
   * Parses a comma-separated string of button names and filters duplicates.
   *
   * @param csv The comma-separated string from preferences.
   * @param usedButtons A [MutableSet] to track buttons already used in other regions
   * to enforce the "no duplicates" rule.
   * @return A [List] of [PlayerButton]s to be rendered.
   */
  fun parseButtons(
    csv: String,
    usedButtons: MutableSet<PlayerButton>,
  ): List<PlayerButton> =
    csv
      .splitToSequence(',')
      .map { it.trim().uppercase() }
      .mapNotNull { name ->
        try {
          PlayerButton.valueOf(name)
        } catch (_: IllegalArgumentException) {
          null
        }
      }.filter { it != PlayerButton.NONE }
      .filter { usedButtons.add(it) } // add() returns true if item was added (i.e., not a duplicate)
      .toList()
}

@Composable
fun MultiChoiceSegmentedButton(
  choices: ImmutableList<String>,
  selectedIndices: ImmutableList<Int>,
  onClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  MultiChoiceSegmentedButtonRow(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.medium),
  ) {
    choices.forEachIndexed { index, choice ->
      SegmentedButton(
        checked = selectedIndices.contains(index),
        onCheckedChange = { onClick(index) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
      ) {
        Text(text = choice)
      }
    }
  }
}
