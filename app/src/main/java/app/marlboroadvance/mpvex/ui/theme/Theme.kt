package app.marlboroadvance.mpvex.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject
import kotlin.math.hypot

// ============================================================================
// Theme Transition Animation State & Components
// ============================================================================
/**
 * State for managing theme transition animations.
 * This class holds the animation state and provides methods to trigger transitions.
 */
class ThemeTransitionState {
    var isAnimating by mutableStateOf(false)
        private set
    var clickPosition by mutableStateOf(Offset.Zero)
        private set
    var screenshotBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var animationProgress = Animatable(0f)
        private set
    
    private var captureView: View? = null
    
    fun setView(view: View) {
        captureView = view
    }
    
    /**
     * Start a theme transition animation from the given position.
     * Captures the current screen and begins the reveal animation.
     * Will NOT start a new animation if one is already in progress.
     */
    fun startTransition(position: Offset) {
        // Don't allow new animation while one is in progress
        if (isAnimating) return
        
        captureView?.let { view ->
            try {
                // Capture before setting isAnimating to ensure we get the current state
                val bitmap = view.drawToBitmap()
                screenshotBitmap = bitmap
                clickPosition = position
                isAnimating = true
            } catch (e: Exception) {
                // If capture fails, just skip the animation
                screenshotBitmap = null
                isAnimating = false
            }
        }
    }
    
    fun finishTransition() {
        val oldBitmap = screenshotBitmap
        screenshotBitmap = null
        clickPosition = Offset.Zero
        isAnimating = false
        // Recycle after state is cleared
        oldBitmap?.recycle()
    }
    
    suspend fun resetProgress() {
        animationProgress.snapTo(0f)
    }
}

/**
 * CompositionLocal to provide ThemeTransitionState down the composition tree
 */
val LocalThemeTransitionState = staticCompositionLocalOf<ThemeTransitionState?> { null }

@Composable
fun rememberThemeTransitionState(): ThemeTransitionState {
    return remember { ThemeTransitionState() }
}

/**
 * Overlay composable that handles the circular reveal animation.
 * Uses Shape-based clipping for smooth Telegram-like rendering.
 */
@Composable
private fun ThemeTransitionOverlay(
    state: ThemeTransitionState,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var containerSize by remember { mutableStateOf(Size.Zero) }
    
    // Location array for status bar offset
    val location = remember { IntArray(2) }
    
    LaunchedEffect(view) {
        state.setView(view)
    }
    
    // Read state values 
    val isAnimating = state.isAnimating
    val screenshotBitmap = state.screenshotBitmap
    val progress = state.animationProgress.value
    val clickPosition = state.clickPosition
    
    // Animate with smooth, buttery easing for elegant feel
    LaunchedEffect(isAnimating, screenshotBitmap) {
        if (isAnimating && screenshotBitmap != null) {
            state.resetProgress()
            state.animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1400,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                )
            )
            state.finishTransition()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
    ) {
        // New theme content (shown underneath the old screenshot)
        content()
        
        // Old theme screenshot with circular clip that expands to reveal new theme
        if (isAnimating && screenshotBitmap != null && !screenshotBitmap.isRecycled) {
            // Get the view's position in the window to account for status bar
            view.getLocationInWindow(location)
            val statusBarOffset = location[1].toFloat()
            
            // Calculate adjusted click position for the clipping shape
            val adjustedClickPos = Offset(clickPosition.x, clickPosition.y - statusBarOffset)
            
            // Calculate the radius for the expanding circle
            val maxRadius = hypot(
                maxOf(adjustedClickPos.x, containerSize.width - adjustedClickPos.x),
                maxOf(adjustedClickPos.y, containerSize.height - adjustedClickPos.y)
            ) * 1.2f
            val currentRadius = maxRadius * progress
            
            // Blur edge width for soft gradient
            val blurWidth = 60f
            
            // Draw the screenshot with blur circle edge effect
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val imageBitmap = screenshotBitmap.asImageBitmap()
                
                // First draw the solid part (outside the blur zone)
                val solidPath = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = adjustedClickPos,
                            radius = (currentRadius + blurWidth).coerceAtLeast(0f)
                        )
                    )
                }
                
                clipPath(solidPath, clipOp = ClipOp.Difference) {
                    drawImage(
                        image = imageBitmap,
                        topLeft = Offset.Zero
                    )
                }
                
                // Draw the blur gradient ring (soft edge)
                if (currentRadius > 0f) {
                    val innerRadius = currentRadius.coerceAtLeast(0.1f)
                    val outerRadius = (currentRadius + blurWidth).coerceAtLeast(innerRadius + 0.1f)
                    
                    // Create radial gradient from transparent (inner) to opaque (outer)
                    val gradientBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0f),
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 1f)
                        ),
                        center = adjustedClickPos,
                        radius = outerRadius - innerRadius + 0.1f,
                    )
                    
                    // Draw the blurred ring by masking with gradient
                    val ringPath = Path().apply {
                        // Outer circle
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = adjustedClickPos,
                                radius = outerRadius
                            )
                        )
                    }
                    val innerHolePath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = adjustedClickPos,
                                radius = innerRadius
                            )
                        )
                    }
                    
                    // Clip to ring area and draw with gradient alpha
                    clipPath(ringPath) {
                        clipPath(innerHolePath, clipOp = ClipOp.Difference) {
                            // Draw the image in the ring area with gradient mask effect
                            // Use multiple steps for smooth gradient
                            val steps = 12
                            for (i in 0 until steps) {
                                val stepInner = innerRadius + (outerRadius - innerRadius) * (i.toFloat() / steps)
                                val stepOuter = innerRadius + (outerRadius - innerRadius) * ((i + 1).toFloat() / steps)
                                val stepAlpha = i.toFloat() / (steps - 1)
                                
                                val stepPath = Path().apply {
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            center = adjustedClickPos,
                                            radius = stepOuter
                                        )
                                    )
                                }
                                val stepInnerPath = Path().apply {
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            center = adjustedClickPos,
                                            radius = stepInner
                                        )
                                    )
                                }
                                
                                clipPath(stepPath) {
                                    clipPath(stepInnerPath, clipOp = ClipOp.Difference) {
                                        drawImage(
                                            image = imageBitmap,
                                            topLeft = Offset.Zero,
                                            alpha = stepAlpha
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Shape that creates an inverse circular reveal effect.
 * The circle expands from center, and the shape clips TO THE AREA OUTSIDE the circle.
 */
private class CircularRevealShape(
    private val progress: Float,
    private val center: Offset,
    private val containerSize: Size,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val actualCenter = if (center == Offset.Zero) {
            Offset(size.width / 2f, size.height / 2f)
        } else {
            center
        }
        
        // Calculate the maximum radius needed to cover entire screen from center point
        val maxRadius = longestDistanceToCorner(size, actualCenter) * 1.1f
        val currentRadius = maxRadius * progress
        
        // Create a path that represents the area OUTSIDE the circle (inverse clip)
        val path = android.graphics.Path().apply {
            // Add the entire rectangle
            addRect(0f, 0f, size.width, size.height, android.graphics.Path.Direction.CW)
            // Subtract the circle (creates hole in the middle)
            addCircle(actualCenter.x, actualCenter.y, currentRadius, android.graphics.Path.Direction.CCW)
        }
        
        return androidx.compose.ui.graphics.Outline.Generic(
            path.asComposePath()
        )
    }
    
    private fun longestDistanceToCorner(size: Size, center: Offset): Float {
        val topLeft = hypot(center.x, center.y)
        val topRight = hypot(size.width - center.x, center.y)
        val bottomLeft = hypot(center.x, size.height - center.y)
        val bottomRight = hypot(size.width - center.x, size.height - center.y)
        return maxOf(topLeft, topRight, bottomLeft, bottomRight)
    }
}

/**
 * Extension to convert Android Path to Compose Path
 */
private fun android.graphics.Path.asComposePath(): androidx.compose.ui.graphics.Path {
    val composePath = androidx.compose.ui.graphics.Path()
    composePath.asAndroidPath().set(this)
    return composePath
}

@Composable
private fun ThemeTransitionContent(content: @Composable () -> Unit) {
    val state = LocalThemeTransitionState.current
    
    if (state != null) {
        ThemeTransitionOverlay(state = state, content = content)
    } else {
        content()
    }
}

// ============================================================================
// Main Theme
// ============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpvexTheme(content: @Composable () -> Unit) {
    val preferences = koinInject<AppearancePreferences>()
    val darkMode by preferences.darkMode.collectAsState()
    val amoledMode by preferences.amoledMode.collectAsState()
    val appTheme by preferences.appTheme.collectAsState()
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val useDarkTheme = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> darkTheme
    }

    val colorScheme = when {
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
        useDarkTheme && amoledMode -> appTheme.getAmoledColorScheme()
        useDarkTheme -> appTheme.getDarkColorScheme()
        else -> appTheme.getLightColorScheme()
    }

    // Provide theme transition state first, OUTSIDE MaterialTheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalThemeTransitionState provides rememberThemeTransitionState(),
    ) {
        ThemeTransitionContent {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = AppTypography,
                content = content,
                motionScheme = MotionScheme.expressive(),
            )
        }
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
