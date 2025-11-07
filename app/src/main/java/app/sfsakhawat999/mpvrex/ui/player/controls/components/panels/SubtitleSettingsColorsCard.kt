package app.sfsakhawat999.mpvrex.ui.player.controls.components.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.toColorInt
import app.sfsakhawat999.mpvrex.R
import app.sfsakhawat999.mpvrex.preferences.SubtitlesPreferences
import app.sfsakhawat999.mpvrex.preferences.preference.Preference
import app.sfsakhawat999.mpvrex.preferences.preference.deleteAndGet
import app.sfsakhawat999.mpvrex.presentation.components.ExpandableCard
import app.sfsakhawat999.mpvrex.presentation.components.TintedSliderItem
import app.sfsakhawat999.mpvrex.ui.player.controls.CARDS_MAX_WIDTH
import app.sfsakhawat999.mpvrex.ui.player.controls.panelCardsColors
import app.sfsakhawat999.mpvrex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import org.koin.compose.koinInject

@Composable
fun SubtitleSettingsColorsCard(modifier: Modifier = Modifier) {
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by remember { mutableStateOf(true) }
  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.Default.Palette, null)
        Text(stringResource(R.string.player_sheets_sub_colors_card_title))
      }
    },
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    Column {
      var currentColorType by remember { mutableStateOf(SubColorType.Text) }
      var currentColor by remember { mutableIntStateOf(getCurrentMPVColor(currentColorType)) }
      LaunchedEffect(currentColorType) {
        currentColor = getCurrentMPVColor(currentColorType)
      }
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
      ) {
        SubColorType.entries.forEach { type ->
          IconToggleButton(
            checked = currentColorType == type,
            onCheckedChange = { currentColorType = type },
          ) {
            Icon(
              when (type) {
                SubColorType.Text -> Icons.Default.FormatColorText
                SubColorType.Border -> Icons.Default.BorderColor
                SubColorType.Background -> Icons.Default.FormatColorFill
              },
              null,
            )
          }
        }
        Text(stringResource(currentColorType.titleRes))
        Spacer(Modifier.weight(1f))
        TextButton(
          onClick = {
            resetColors(preferences, currentColorType)
            currentColor = getCurrentMPVColor(currentColorType)
          },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FormatColorReset, null)
            Text(stringResource(R.string.generic_reset))
          }
        }
      }
      SubtitlesColorPicker(
        currentColor,
        onColorChange = {
          currentColor = it
          currentColorType.preference(preferences).set(it)
          MPVLib.setPropertyString(currentColorType.property, it.toColorHexString())
        },
      )
    }
  }
}

fun Int.copyAsArgb(
  alpha: Int = this.alpha,
  red: Int = this.red,
  green: Int = this.green,
  blue: Int = this.blue,
) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

@OptIn(ExperimentalStdlibApi::class)
fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

enum class SubColorType(
  @StringRes val titleRes: Int,
  val property: String,
  val preference: (SubtitlesPreferences) -> Preference<Int>,
) {
  Text(
    R.string.player_sheets_subtitles_color_text,
    "sub-color",
    preference = SubtitlesPreferences::textColor,
  ),
  Border(
    R.string.player_sheets_subtitles_color_border,
    "sub-border-color",
    preference = SubtitlesPreferences::borderColor,
  ),
  Background(
    R.string.player_sheets_subtitles_color_background,
    "sub-back-color",
    preference = SubtitlesPreferences::backgroundColor,
  ),
}

fun resetColors(
  preferences: SubtitlesPreferences,
  type: SubColorType,
) {
  when (type) {
    SubColorType.Text -> {
      MPVLib.setPropertyString("sub-color", preferences.textColor.deleteAndGet().toColorHexString())
    }

    SubColorType.Border -> {
      MPVLib.setPropertyString("sub-border-color", preferences.borderColor.deleteAndGet().toColorHexString())
    }

    SubColorType.Background -> {
      MPVLib.setPropertyString("sub-back-color", preferences.backgroundColor.deleteAndGet().toColorHexString())
    }
  }
}

val getCurrentMPVColor: (SubColorType) -> Int = {
  MPVLib.getPropertyString(it.property)?.uppercase()?.toColorInt() ?: 0xFFFFFFFF.toInt()
}

@Composable
fun SubtitlesColorPicker(
  color: Int,
  onColorChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_red),
      color.red,
      color.red.toString(),
      onChange = { onColorChange(color.copyAsArgb(red = it)) },
      max = 255,
      tint = Color.Red,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_green),
      color.green,
      color.green.toString(),
      onChange = { onColorChange(color.copyAsArgb(green = it)) },
      max = 255,
      tint = Color.Green,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_blue),
      color.blue,
      color.blue.toString(),
      onChange = { onColorChange(color.copyAsArgb(blue = it)) },
      max = 255,
      tint = Color.Blue,
    )

    TintedSliderItem(
      stringResource(R.string.player_sheets_sub_color_alpha),
      color.alpha,
      color.alpha.toString(),
      onChange = { onColorChange(color.copyAsArgb(alpha = it)) },
      max = 255,
      tint = Color.White,
    )
  }
}
