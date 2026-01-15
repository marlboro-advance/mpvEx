package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

// =============================================================================
// Motion Orchestrator - Centralized Animation System
// =============================================================================

/**
 * MotionSpec defines animation specifications for the entire application.
 *
 * This interface provides four tiers of animation quality:
 * - [HighQualityMotion]: Fluid animations with scale effects
 * - [SlideMotion]: Clean slide in/out transitions
 * - [MinimalMotion]: Fast 150ms tweens for snappy feel
 * - [NoMotion]: Instant transitions for accessibility
 */
interface MotionSpec {
    /** Forward navigation transition. */
    fun screenTransition(): ContentTransform
    
    /** Backward navigation transition. */
    fun popTransition(): ContentTransform
    
    /** Predictive back gesture transition. */
    fun predictivePopTransition(): ContentTransform
    
    /** Animation spec for control fade. */
    fun controlFadeSpec(): AnimationSpec<Float>
    
    /** Animation spec for control slide. */
    fun controlSlideSpec(): AnimationSpec<IntOffset>
    
    /** Animation spec for scale transformations. */
    fun scaleSpec(): AnimationSpec<Float>
    
    /** Whether shared element transitions are enabled. */
    val sharedElementsEnabled: Boolean
    
    /** Whether blur effects are enabled. */
    val blurEffectsEnabled: Boolean
}

// =============================================================================
// CompositionLocal for global access
// =============================================================================

val LocalMotionSpec = staticCompositionLocalOf<MotionSpec> { HighQualityMotion }

// =============================================================================
// Fluid Motion
// =============================================================================

/**
 * Fluid motion with smooth deceleration and scale effects.
 */
object HighQualityMotion : MotionSpec {
    
    private const val TRANSITION_DURATION = 500
    private val TRANSITION_EASING = FastOutSlowInEasing
    
    override fun screenTransition(): ContentTransform {
        val enter = slideInHorizontally(
            animationSpec = tween(
                durationMillis = TRANSITION_DURATION,
                easing = androidx.compose.animation.core.EaseOutCubic
            )
        ) { it }
        
        val exit = fadeOut(
            animationSpec = tween(
                durationMillis = TRANSITION_DURATION / 2,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            targetAlpha = 0.3f
        )
        
        return enter togetherWith exit
    }
    
    override fun popTransition(): ContentTransform {
        val enter = slideInHorizontally(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            initialOffsetX = { -it / 3 }
        ) + scaleIn(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            initialScale = 0.9f
        )
        
        val exit = slideOutHorizontally(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            targetOffsetX = { it }
        ) + scaleOut(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            targetScale = 0.75f,
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        )
        
        return enter togetherWith exit
    }
    
    override fun predictivePopTransition(): ContentTransform = popTransition()
    
    override fun controlFadeSpec(): AnimationSpec<Float> = tween(200, easing = TRANSITION_EASING)
    
    override fun controlSlideSpec(): AnimationSpec<IntOffset> = tween(TRANSITION_DURATION, easing = TRANSITION_EASING)
    
    override fun scaleSpec(): AnimationSpec<Float> = tween(250, easing = TRANSITION_EASING)
    
    override val sharedElementsEnabled: Boolean = true
    override val blurEffectsEnabled: Boolean = true
}

// =============================================================================
// Slide Motion (Clean Slide In/Out)
// =============================================================================

/**
 * Clean slide-only motion with no fade or scale.
 */
object SlideMotion : MotionSpec {
    
    private const val SLIDE_DURATION = 400
    private val SLIDE_EASING = FastOutSlowInEasing
    
    override fun screenTransition(): ContentTransform {
        val enter = slideInHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = SLIDE_EASING)
        ) { it }
        
        val exit = slideOutHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = SLIDE_EASING)
        ) { -it }
        
        return enter togetherWith exit
    }
    
    override fun popTransition(): ContentTransform {
        val enter = slideInHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = SLIDE_EASING)
        ) { -it }
        
        val exit = slideOutHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = SLIDE_EASING)
        ) { it }
        
        return enter togetherWith exit
    }
    
    override fun predictivePopTransition(): ContentTransform = popTransition()
    
    override fun controlFadeSpec(): AnimationSpec<Float> = tween(200, easing = SLIDE_EASING)
    
    override fun controlSlideSpec(): AnimationSpec<IntOffset> = tween(SLIDE_DURATION, easing = SLIDE_EASING)
    
    override fun scaleSpec(): AnimationSpec<Float> = tween(200, easing = SLIDE_EASING)
    
    override val sharedElementsEnabled: Boolean = false
    override val blurEffectsEnabled: Boolean = false
}

// =============================================================================
// Minimal Motion
// =============================================================================

/**
 * Fast 150ms animations for snappy transitions.
 */
object MinimalMotion : MotionSpec {
    
    private const val TWEEN_DURATION = 150
    private val EASING = FastOutSlowInEasing
    
    override fun screenTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(TWEEN_DURATION)) +
            slideInHorizontally(
                animationSpec = tween(TWEEN_DURATION, easing = EASING),
                initialOffsetX = { it / 4 }
            )
        val exit = fadeOut(animationSpec = tween(TWEEN_DURATION)) +
            slideOutHorizontally(
                animationSpec = tween(TWEEN_DURATION, easing = EASING),
                targetOffsetX = { -it / 4 }
            )
        return enter togetherWith exit
    }
    
    override fun popTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(TWEEN_DURATION)) +
            slideInHorizontally(
                animationSpec = tween(TWEEN_DURATION, easing = EASING),
                initialOffsetX = { -it / 4 }
            )
        val exit = fadeOut(animationSpec = tween(TWEEN_DURATION)) +
            slideOutHorizontally(
                animationSpec = tween(TWEEN_DURATION, easing = EASING),
                targetOffsetX = { it / 4 }
            )
        return enter togetherWith exit
    }
    
    override fun predictivePopTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(TWEEN_DURATION)) +
            scaleIn(
                animationSpec = tween(TWEEN_DURATION),
                initialScale = 0.95f,
                transformOrigin = TransformOrigin.Center
            )
        val exit = fadeOut(animationSpec = tween(TWEEN_DURATION)) +
            scaleOut(
                animationSpec = tween(TWEEN_DURATION),
                targetScale = 0.95f,
                transformOrigin = TransformOrigin.Center
            )
        return enter togetherWith exit
    }
    
    override fun controlFadeSpec(): AnimationSpec<Float> = tween(TWEEN_DURATION)
    
    override fun controlSlideSpec(): AnimationSpec<IntOffset> = tween(TWEEN_DURATION)
    
    override fun scaleSpec(): AnimationSpec<Float> = tween(TWEEN_DURATION)
    
    override val sharedElementsEnabled: Boolean = false
    override val blurEffectsEnabled: Boolean = false
}

// =============================================================================
// No Motion (Accessibility)
// =============================================================================

/**
 * Instant transitions for accessibility or performance.
 */
object NoMotion : MotionSpec {
    
    private val snapSpec = tween<IntOffset>(durationMillis = 0)
    private val snapFadeSpec = tween<Float>(durationMillis = 0)
    
    override fun screenTransition(): ContentTransform {
        return fadeIn(animationSpec = snapFadeSpec) togetherWith fadeOut(animationSpec = snapFadeSpec)
    }
    
    override fun popTransition(): ContentTransform {
        return fadeIn(animationSpec = snapFadeSpec) togetherWith fadeOut(animationSpec = snapFadeSpec)
    }
    
    override fun predictivePopTransition(): ContentTransform {
        return fadeIn(animationSpec = snapFadeSpec) togetherWith fadeOut(animationSpec = snapFadeSpec)
    }
    
    override fun controlFadeSpec(): AnimationSpec<Float> = snapFadeSpec
    
    override fun controlSlideSpec(): AnimationSpec<IntOffset> = snapSpec
    
    override fun scaleSpec(): AnimationSpec<Float> = snapFadeSpec
    
    override val sharedElementsEnabled: Boolean = false
    override val blurEffectsEnabled: Boolean = false
}

// =============================================================================
// Motion Quality Enum for Settings
// =============================================================================

/**
 * Motion quality tiers for user preferences.
 */
enum class MotionQuality(val titleRes: Int, val descriptionRes: Int) {
    HIGH(
        app.marlboroadvance.mpvex.R.string.pref_motion_high_quality,
        app.marlboroadvance.mpvex.R.string.pref_motion_high_quality_desc
    ),
    SLIDE(
        app.marlboroadvance.mpvex.R.string.pref_motion_slide,
        app.marlboroadvance.mpvex.R.string.pref_motion_slide_desc
    ),
    MINIMAL(
        app.marlboroadvance.mpvex.R.string.pref_motion_minimal,
        app.marlboroadvance.mpvex.R.string.pref_motion_minimal_desc
    ),
    NONE(
        app.marlboroadvance.mpvex.R.string.pref_motion_none,
        app.marlboroadvance.mpvex.R.string.pref_motion_none_desc
    );
    
    fun toMotionSpec(): MotionSpec = when (this) {
        HIGH -> HighQualityMotion
        SLIDE -> SlideMotion
        MINIMAL -> MinimalMotion
        NONE -> NoMotion
    }
}
