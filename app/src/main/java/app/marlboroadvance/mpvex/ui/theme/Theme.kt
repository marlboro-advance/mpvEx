package app.marlboroadvance.mpvex.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject

private val lightScheme =
  lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
  )

private val darkScheme =
  darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
  )

private val pureBlackColorScheme =
  darkColorScheme(
    primary = primaryPureBlack,
    onPrimary = onPrimaryPureBlack,
    primaryContainer = primaryContainerPureBlack,
    onPrimaryContainer = onPrimaryContainerPureBlack,
    secondary = secondaryPureBlack,
    onSecondary = onSecondaryPureBlack,
    secondaryContainer = secondaryContainerPureBlack,
    onSecondaryContainer = onSecondaryContainerPureBlack,
    tertiary = tertiaryPureBlack,
    onTertiary = onTertiaryPureBlack,
    tertiaryContainer = tertiaryContainerPureBlack,
    onTertiaryContainer = onTertiaryContainerPureBlack,
    error = errorPureBlack,
    onError = onErrorPureBlack,
    errorContainer = errorContainerPureBlack,
    onErrorContainer = onErrorContainerPureBlack,
    background = backgroundPureBlack,
    onBackground = onBackgroundPureBlack,
    surface = surfacePureBlack,
    onSurface = onSurfacePureBlack,
    surfaceVariant = surfaceVariantPureBlack,
    onSurfaceVariant = onSurfaceVariantPureBlack,
    outline = outlinePureBlack,
    outlineVariant = outlineVariantPureBlack,
    scrim = scrimPureBlack,
    inverseSurface = inverseSurfacePureBlack,
    inverseOnSurface = inverseOnSurfacePureBlack,
    inversePrimary = inversePrimaryPureBlack,
    surfaceDim = surfaceDimPureBlack,
    surfaceBright = surfaceBrightPureBlack,
    surfaceContainerLowest = surfaceContainerLowestPureBlack,
    surfaceContainerLow = surfaceContainerLowPureBlack,
    surfaceContainer = surfaceContainerPureBlack,
    surfaceContainerHigh = surfaceContainerHighPureBlack,
    surfaceContainerHighest = surfaceContainerHighestPureBlack,
  )

internal var darkColorScheme = darkColorScheme()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpvexTheme(content: @Composable () -> Unit) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val amoledMode by preferences.amoledMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val dynamicColor by preferences.materialYou.collectAsState()
  val context = LocalContext.current

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        when (darkMode) {
          DarkMode.Dark -> {
            if (amoledMode) {
              dynamicDarkColorScheme(context).copy(
                background = backgroundPureBlack,
                surface = surfacePureBlack,
                surfaceDim = surfaceDimPureBlack,
                surfaceBright = surfaceBrightPureBlack,
                surfaceContainerLowest = surfaceContainerLowestPureBlack,
                surfaceContainerLow = surfaceContainerLowPureBlack,
                surfaceContainer = surfaceContainerPureBlack,
                surfaceContainerHigh = surfaceContainerHighPureBlack,
                surfaceContainerHighest = surfaceContainerHighestPureBlack,
              )
            } else {
              dynamicDarkColorScheme(context)
            }
          }
          DarkMode.Light -> dynamicLightColorScheme(context)
          DarkMode.System -> {
            if (darkTheme) {
              if (amoledMode) {
                dynamicDarkColorScheme(context).copy(
                  background = backgroundPureBlack,
                  surface = surfacePureBlack,
                  surfaceDim = surfaceDimPureBlack,
                  surfaceBright = surfaceBrightPureBlack,
                  surfaceContainerLowest = surfaceContainerLowestPureBlack,
                  surfaceContainerLow = surfaceContainerLowPureBlack,
                  surfaceContainer = surfaceContainerPureBlack,
                  surfaceContainerHigh = surfaceContainerHighPureBlack,
                  surfaceContainerHighest = surfaceContainerHighestPureBlack,
                )
              } else {
                dynamicDarkColorScheme(context)
              }
            } else {
              dynamicLightColorScheme(context)
            }
          }
        }
      }

      darkMode == DarkMode.Dark -> {
        if (amoledMode) pureBlackColorScheme else darkScheme
      }
      darkMode == DarkMode.Light -> lightScheme
      else -> {
        if (darkTheme) {
          if (amoledMode) pureBlackColorScheme else darkScheme
        } else {
          lightScheme
        }
      }
    }

  darkColorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (amoledMode) {
          dynamicDarkColorScheme(context).copy(
            background = backgroundPureBlack,
            surface = surfacePureBlack,
            surfaceDim = surfaceDimPureBlack,
            surfaceBright = surfaceBrightPureBlack,
            surfaceContainerLowest = surfaceContainerLowestPureBlack,
            surfaceContainerLow = surfaceContainerLowPureBlack,
            surfaceContainer = surfaceContainerPureBlack,
            surfaceContainerHigh = surfaceContainerHighPureBlack,
            surfaceContainerHighest = surfaceContainerHighestPureBlack,
          )
        } else {
          dynamicDarkColorScheme(context)
        }
      }
      else -> {
        if (amoledMode) pureBlackColorScheme else darkScheme
      }
    }

  CompositionLocalProvider(
    LocalSpacing provides Spacing(),
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content,
      motionScheme = MotionScheme.expressive(),
    )
  }
}

enum class DarkMode(
  @StringRes val titleRes: Int,
) {
  Dark(R.string.pref_appearance_darkmode_dark),
  Light(R.string.pref_appearance_darkmode_light),
  System(R.string.pref_appearance_darkmode_system),
}

private const val RIPPLE_DRAGGED_ALPHA = .5f
private const val RIPPLE_FOCUSED_ALPHA = .6f
private const val RIPPLE_HOVERED_ALPHA = .4f
private const val RIPPLE_PRESSED_ALPHA = .6f

@OptIn(ExperimentalMaterial3Api::class)
val playerRippleConfiguration
  @Composable get() =
    RippleConfiguration(
      color = MaterialTheme.colorScheme.primaryContainer,
      rippleAlpha =
        RippleAlpha(
          draggedAlpha = RIPPLE_DRAGGED_ALPHA,
          focusedAlpha = RIPPLE_FOCUSED_ALPHA,
          hoveredAlpha = RIPPLE_HOVERED_ALPHA,
          pressedAlpha = RIPPLE_PRESSED_ALPHA,
        ),
    )
