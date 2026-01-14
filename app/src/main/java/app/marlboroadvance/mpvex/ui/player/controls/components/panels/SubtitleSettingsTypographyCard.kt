package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.SubtitleJustification
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.preferences.preference.deleteAndGet
import app.marlboroadvance.mpvex.presentation.components.ExpandableCard
import app.marlboroadvance.mpvex.presentation.components.ExposedTextDropDownMenu
import app.marlboroadvance.mpvex.presentation.components.SliderItem
import app.marlboroadvance.mpvex.ui.player.controls.CARDS_MAX_WIDTH
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import app.marlboroadvance.mpvex.ui.theme.spacing
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceTheme
import org.koin.compose.koinInject

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SubtitleSettingsTypographyCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val preferences = koinInject<SubtitlesPreferences>()
  val fileManager = koinInject<FileManager>()
  var isExpanded by remember { mutableStateOf(true) }
  val fonts by remember { mutableStateOf(mutableListOf<String>("Default")) }
  var fontsLoadingIndicator: (@Composable () -> Unit)? by remember {
    val indicator: (@Composable () -> Unit) = {
      CircularProgressIndicator(Modifier.size(32.dp))
    }
    mutableStateOf(indicator)
  }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val fontsDir = fileManager.fromPath(context.filesDir.path + "/fonts")
      if (fileManager.exists(fontsDir)) {
        fonts.addAll(
          fileManager
            .listFiles(fontsDir)
            .filter { fileManager.isFile(it) && fileManager.getName(it).lowercase().matches(".*\\.[ot]tf$".toRegex()) }
            .mapNotNull {
              runCatching {
                TTFFile.open(fileManager.getInputStream(it) ?: return@mapNotNull null).families.values.first()
              }.getOrNull()
            }.distinct()
        )
      }
      fontsLoadingIndicator = null
    }
  }

  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.Default.FormatColorText, null)
        Text(stringResource(R.string.player_sheets_sub_typography_card_title))
      }
    },
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    Column {
      val isBold by MPVLib.propBoolean["sub-bold"].collectAsState()
      val isItalic by MPVLib.propBoolean["sub-italic"].collectAsState()
      val mpvJustify by MPVLib.propString["sub-justify"].collectAsState()
      val justify by remember {
        derivedStateOf { SubtitleJustification.entries.first { it.value == mpvJustify } }
      }
      val font by MPVLib.propString["sub-font"].collectAsState()
      val fontSize by MPVLib.propInt["sub-font-size"].collectAsState()
      val mpvBorderStyle by MPVLib.propString["sub-border-style"].collectAsState()
      val borderStyle by remember {
        derivedStateOf { SubtitlesBorderStyle.entries.first { it.value == mpvBorderStyle } }
      }
      val borderSize by MPVLib.propInt["sub-outline-size"].collectAsState()
      val shadowOffset by MPVLib.propInt["sub-shadow-offset"].collectAsState()
      Row(
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconToggleButton(
          checked = isBold == true,
          onCheckedChange = {
            preferences.bold.set(it)
            MPVLib.setPropertyBoolean("sub-bold", it)
          },
        ) {
          Icon(
            Icons.Default.FormatBold,
            null,
            modifier = Modifier.size(32.dp),
          )
        }
        IconToggleButton(
          checked = isItalic == true,
          onCheckedChange = {
            preferences.italic.set(it)
            MPVLib.setPropertyBoolean("sub-italic", it)
          },
        ) {
          Icon(
            Icons.Default.FormatItalic,
            null,
            modifier = Modifier.size(32.dp),
          )
        }
        SubtitleJustification.entries.minus(SubtitleJustification.Auto).forEach { justification ->
          IconToggleButton(
            checked = justify == justification,
            onCheckedChange = {
              MPVLib.setPropertyBoolean("sub-ass-justify", it)
              if (it) {
                preferences.justification.set(justification)
                MPVLib.setPropertyString("sub-justify", justification.value)
              } else {
                preferences.justification.set(SubtitleJustification.Auto)
                MPVLib.setPropertyString("sub-justify", SubtitleJustification.Auto.value)
              }
            },
          ) {
            Icon(justification.icon, null)
          }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
          onClick = { resetTypography(preferences) },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FormatClear, null)
            Text(stringResource(R.string.generic_reset))
          }
        }
      }
      Row(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painterResource(R.drawable.outline_brand_family_24),
          null,
          modifier = Modifier.size(32.dp),
        )
        ExposedTextDropDownMenu(
          selectedValue = font!!.ifEmpty { "Default" },
          options = fonts.toImmutableList(),
          label = stringResource(R.string.player_sheets_sub_typography_font),
          onValueChangedEvent = {
            val actualFont = if (it == "Default") "" else it
            preferences.font.set(actualFont)
            MPVLib.setPropertyString("sub-font", actualFont)
            MPVLib.setPropertyString("secondary-sub-font", actualFont)
          },
          leadingIcon = fontsLoadingIndicator,
        )
      }
      SliderItem(
        label = stringResource(R.string.player_sheets_sub_typography_font_size),
        max = 100,
        min = 1,
        value = fontSize ?: preferences.fontSize.get(),
        valueText = fontSize.toString(),
        onChange = {
          preferences.fontSize.set(it)
          MPVLib.setPropertyInt("sub-font-size", it)
        },
      ) {
        Icon(Icons.Default.FormatSize, null)
      }
      ProvidePreferenceLocals(
        theme = preferenceTheme(iconContainerMinWidth = 64.dp),
      ) {
        ListPreference(
          borderStyle,
          onValueChange = {
            preferences.borderStyle.set(it)
            MPVLib.setPropertyString("sub-border-style", it.value)
          },
          title = { Text(stringResource(R.string.player_sheets_subtitles_border_style)) },
          valueToText = { AnnotatedString(context.getString(it.titleRes)) },
          values = SubtitlesBorderStyle.entries,
          type = ListPreferenceType.DROPDOWN_MENU,
          summary = { Text(stringResource(borderStyle.titleRes)) },
          icon = { Icon(Icons.Default.BorderStyle, null) },
        )
      }
      SliderItem(
        stringResource(R.string.player_sheets_sub_typography_border_size),
        value = borderSize ?: preferences.borderSize.get(),
        valueText = (borderSize ?: preferences.borderSize.get()).toString(),
        onChange = {
          preferences.borderSize.set(it)
          MPVLib.setPropertyInt("sub-outline-size", it)
        },
        max = 20,
        icon = { Icon(Icons.Default.BorderColor, null) },
      )
      SliderItem(
        stringResource(R.string.player_sheets_subtitles_shadow_offset),
        value = shadowOffset ?: preferences.shadowOffset.get(),
        valueText = (shadowOffset ?: preferences.shadowOffset.get()).toString(),
        onChange = {
          preferences.shadowOffset.set(it)
          MPVLib.setPropertyInt("sub-shadow-offset", it)
        },
        max = 100,
        icon = { Icon(painterResource(R.drawable.sharp_shadow_24), null) },
      )
    }
  }
}

fun resetTypography(preferences: SubtitlesPreferences) {
  MPVLib.setPropertyBoolean("sub-bold", preferences.bold.deleteAndGet())
  MPVLib.setPropertyBoolean("sub-italic", preferences.italic.deleteAndGet())
  MPVLib.setPropertyBoolean("sub-ass-justify", false)
  MPVLib.setPropertyString("sub-justify", preferences.justification.deleteAndGet().value)
  MPVLib.setPropertyString("sub-font", preferences.font.deleteAndGet())
  MPVLib.setPropertyString("secondary-sub-font", preferences.font.get())
  MPVLib.setPropertyInt("sub-font-size", preferences.fontSize.deleteAndGet())
  MPVLib.setPropertyInt("sub-border-size", preferences.borderSize.deleteAndGet())
  MPVLib.setPropertyInt("sub-shadow-offset", preferences.shadowOffset.deleteAndGet())
  MPVLib.setPropertyString("sub-border-style", preferences.borderStyle.deleteAndGet().value)
}

enum class SubtitlesBorderStyle(
  val value: String,
  @StringRes val titleRes: Int,
) {
  OutlineAndShadow("outline-and-shadow", R.string.player_sheets_subtitles_border_style_outline_and_shadow),
  OpaqueBox("opaque-box", R.string.player_sheets_subtitles_border_style_opaque_box),
}
