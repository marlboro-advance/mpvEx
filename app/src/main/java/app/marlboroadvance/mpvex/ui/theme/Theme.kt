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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpvexTheme(content: @Composable () -> Unit) {
    val preferences = koinInject<AppearancePreferences>()
    val darkMode by preferences.darkMode.collectAsState()
    val amoledMode by preferences.amoledMode.collectAsState()
    val appTheme by preferences.appTheme.collectAsState()
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    // Determine if we should use dark mode
    val useDarkTheme = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> darkTheme
    }

    val colorScheme = when {
        // Dynamic theme (Material You) - only on Android 12+
        appTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when {
                useDarkTheme && amoledMode -> {
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
                }
                useDarkTheme -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }
        // Static themes
        useDarkTheme && amoledMode -> appTheme.getAmoledColorScheme()
        useDarkTheme -> appTheme.getDarkColorScheme()
        else -> appTheme.getLightColorScheme()
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
