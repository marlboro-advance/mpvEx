package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.CustomKeyCodes
import app.marlboroadvance.mpvex.ui.player.SingleActionGesture
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.FooterPreference
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object GesturePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<GesturePreferences>()
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val useSingleTapForCenter by preferences.useSingleTapForCenter.collectAsState()

    var showCustomSeekDialog by remember { mutableStateOf(false) }
    var customSeekValue by remember { mutableStateOf("") }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(stringResource(R.string.pref_gesture))
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
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
            title = { Text(text = stringResource(R.string.pref_gesture_double_tap_title)) },
          )
          val doubleTapSeekDuration by preferences.doubleTapToSeekDuration.collectAsState()
          val predefinedValues = listOf(3, 5, 10, 15, 20, 25, 30)
          val isCustomValue = !predefinedValues.contains(doubleTapSeekDuration)

          ListPreference(
            value = if (isCustomValue) -1 else doubleTapSeekDuration,
            onValueChange = { newValue ->
              if (newValue == -1) {
                customSeekValue = doubleTapSeekDuration.toString()
                showCustomSeekDialog = true
              } else {
                preferences.doubleTapToSeekDuration.set(newValue)
              }
            },
            values = predefinedValues + listOf(-1),
            valueToText = { value ->
              if (value == -1) {
                AnnotatedString("Custom")
              } else {
                AnnotatedString("${value}s")
              }
            },
            title = { Text(text = stringResource(id = R.string.pref_player_double_tap_seek_duration)) },
            summary = {
              Text(
                text = if (isCustomValue) {
                  "Custom (${doubleTapSeekDuration}s)"
                } else {
                  "${doubleTapSeekDuration}s"
                },
              )
            },
          )

          if (showCustomSeekDialog) {
            AlertDialog(
              onDismissRequest = { showCustomSeekDialog = false },
              title = { Text(text = stringResource(id = R.string.pref_player_double_tap_seek_duration)) },
              text = {
                Column {
                  Text(
                    text = "Enter custom seek duration in seconds (1-120)",
                    modifier = Modifier.padding(bottom = 8.dp),
                  )
                  OutlinedTextField(
                    value = customSeekValue,
                    onValueChange = { customSeekValue = it },
                    label = { Text("Seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                  )
                }
              },
              confirmButton = {
                TextButton(
                  onClick = {
                    val value = customSeekValue.toIntOrNull()
                    if (value != null && value in 1..120) {
                      preferences.doubleTapToSeekDuration.set(value)
                      showCustomSeekDialog = false
                    }
                  },
                ) {
                  Text(stringResource(R.string.generic_ok))
                }
              },
              dismissButton = {
                TextButton(onClick = { showCustomSeekDialog = false }) {
                  Text(stringResource(R.string.generic_cancel))
                }
              },
            )
          }

          val doubleTapSeekAreaWidth by preferences.doubleTapSeekAreaWidth.collectAsState()
          val seekAreaValues = listOf(20, 25, 30, 35, 40, 45)

          ListPreference(
            value = doubleTapSeekAreaWidth,
            onValueChange = { preferences.doubleTapSeekAreaWidth.set(it) },
            values = seekAreaValues,
            valueToText = { AnnotatedString("${it}%") },
            title = { Text(text = "Double Tap Seek Area Width") },
            summary = {
              Text(
                text = "Current: ${doubleTapSeekAreaWidth}%",
              )
            },
          )

          val leftDoubleTap by preferences.leftSingleActionGesture.collectAsState()
          ListPreference(
            value = leftDoubleTap,
            onValueChange = { preferences.leftSingleActionGesture.set(it) },
            values = SingleActionGesture.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(R.string.pref_gesture_double_tap_left_title)) },
            summary = { Text(text = stringResource(leftDoubleTap.titleRes)) },
          )

          val centerDoubleTap by preferences.centerSingleActionGesture.collectAsState()
          ListPreference(
            value = centerDoubleTap,
            onValueChange = { preferences.centerSingleActionGesture.set(it) },
            values =
              listOf(
                SingleActionGesture.None,
                SingleActionGesture.PlayPause,
                SingleActionGesture.Custom,
              ),
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = {
              Text(
                text =
                  stringResource(
                    if (useSingleTapForCenter) R.string.pref_gesture_single_tap_center_title else R.string.pref_gesture_double_tap_center_title,
                  ),
              )
            },
            summary = { Text(text = stringResource(centerDoubleTap.titleRes)) },
          )

          val rightDoubleTap by preferences.rightSingleActionGesture.collectAsState()
          ListPreference(
            value = rightDoubleTap,
            onValueChange = { preferences.rightSingleActionGesture.set(it) },
            values = SingleActionGesture.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(R.string.pref_gesture_double_tap_right_title)) },
            summary = { Text(text = stringResource(rightDoubleTap.titleRes)) },
          )

          val useSingleTapForCenter by preferences.useSingleTapForCenter.collectAsState()
          SwitchPreference(
            value = useSingleTapForCenter,
            onValueChange = { preferences.useSingleTapForCenter.set(it) },
            title = {
              Text(
                text = stringResource(id = R.string.pref_gesture_use_single_tap_for_center_title),
              )
            },
            summary = {
              Text(
                text = stringResource(id = R.string.pref_gesture_use_single_tap_for_center_summary),
              )
            },
          )

          val doubleTapKeyCodes =
            listOf(
              CustomKeyCodes.DoubleTapLeft,
              CustomKeyCodes.DoubleTapCenter,
              CustomKeyCodes.DoubleTapRight,
            ).map { it.keyCode }.toImmutableList()
          FooterPreference(
            summary = {
              var annotatedString =
                buildAnnotatedString {
                  append(stringResource(R.string.pref_gesture_double_tap_custom_info))
                }

              doubleTapKeyCodes.forEach { keyCode ->
                annotatedString =
                  buildAnnotatedString {
                    val startIndex = annotatedString.indexOf(keyCode)
                    val endIndex = startIndex + keyCode.length
                    append(annotatedString)
                    addStyle(
                      style = SpanStyle(fontWeight = FontWeight.Bold),
                      start = startIndex,
                      end = endIndex,
                    )
                  }
              }

              Text(text = annotatedString)
            },
          )

          PreferenceCategory(
            title = { Text(text = stringResource(R.string.pref_gesture_media_title)) },
          )
          val mediaPreviousGesture by preferences.mediaPreviousGesture.collectAsState()
          ListPreference(
            value = mediaPreviousGesture,
            onValueChange = { preferences.mediaPreviousGesture.set(it) },
            values = SingleActionGesture.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(R.string.pref_gesture_media_previous)) },
            summary = { Text(text = stringResource(mediaPreviousGesture.titleRes)) },
          )
          val mediaPlayGesture by preferences.mediaPlayGesture.collectAsState()
          ListPreference(
            value = mediaPlayGesture,
            onValueChange = { preferences.mediaPlayGesture.set(it) },
            values =
              listOf(
                SingleActionGesture.None,
                SingleActionGesture.PlayPause,
                SingleActionGesture.Custom,
              ),
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(R.string.pref_gesture_media_play)) },
            summary = { Text(text = stringResource(mediaPlayGesture.titleRes)) },
          )
          val mediaNextGesture by preferences.mediaNextGesture.collectAsState()
          ListPreference(
            value = mediaNextGesture,
            onValueChange = { preferences.mediaNextGesture.set(it) },
            values = SingleActionGesture.entries,
            valueToText = { AnnotatedString(context.getString(it.titleRes)) },
            title = { Text(text = stringResource(R.string.pref_gesture_media_next)) },
            summary = { Text(text = stringResource(mediaNextGesture.titleRes)) },
          )

          val mediaKeyCodes =
            listOf(
              CustomKeyCodes.MediaPrevious,
              CustomKeyCodes.MediaPlay,
              CustomKeyCodes.MediaNext,
            ).map { it.keyCode }.toImmutableList()
          FooterPreference(
            summary = {
              var annotatedString =
                buildAnnotatedString {
                  append(stringResource(R.string.pref_gesture_media_custom_info))
                }

              mediaKeyCodes.forEach { keyCode ->
                annotatedString =
                  buildAnnotatedString {
                    val startIndex = annotatedString.indexOf(keyCode)
                    val endIndex = startIndex + keyCode.length
                    append(annotatedString)
                    addStyle(
                      style = SpanStyle(fontWeight = FontWeight.Bold),
                      start = startIndex,
                      end = endIndex,
                    )
                  }
              }

              Text(text = annotatedString)
            },
          )
        }
      }
    }
  }
}
