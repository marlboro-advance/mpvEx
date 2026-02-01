package app.marlboroadvance.mpvex.ui.player

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import androidx.annotation.RequiresApi

/**
 * Helper class for managing Adaptive Refresh Rate (ARR) functionality.
 *
 * ARR allows the display refresh rate to dynamically match the video content's frame rate,
 * reducing power consumption. This is especially beneficial on tablets and for long video playback.
 *
 * **Requirements:**
 * - Android 15 (API 35) or higher
 * - Device must implement ARR HAL APIs (check via Display.hasArrSupport())
 *
 * @see <a href="https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate">Android ARR Documentation</a>
 */
class AdaptiveRefreshRateHelper(private val activity: Activity) {

  companion object {
    private const val TAG = "AdaptiveRefreshRate"
    
    /**
     * Minimum API level required for ARR support.
     * ARR requires Android 15-QPR1 (API 35).
     */
    const val MIN_API_LEVEL = Build.VERSION_CODES.VANILLA_ICE_CREAM // API 35

    /**
     * Check if the current Android version supports ARR APIs.
     */
    fun isApiLevelSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_API_LEVEL
  }

  /**
   * Check if ARR is supported on this device.
   *
   * This checks both:
   * 1. Android version is 15+ (API 35)
   * 2. Device implements the ARR HAL (via Display.hasArrSupport())
   *
   * @return true if ARR is fully supported, false otherwise
   */
  @RequiresApi(MIN_API_LEVEL)
  fun isArrSupported(): Boolean {
    if (!isApiLevelSupported()) return false
    
    return try {
      activity.display?.hasArrSupport() ?: false
    } catch (e: Exception) {
      Log.w(TAG, "Failed to check ARR support", e)
      false
    }
  }

  /**
   * Apply the video's frame rate to the surface for ARR optimization.
   *
   * This tells the display to match the content's frame rate, reducing
   * unnecessary high refresh rate usage for video content (typically 24-30fps).
   *
   * @param surfaceView The SurfaceView rendering the video
   * @param fps The video's frame rate (e.g., 23.976, 24, 29.97, 30, 60)
   */
  fun applyVideoFrameRate(surfaceView: SurfaceView, fps: Float) {
    if (!isApiLevelSupported()) {
      Log.d(TAG, "ARR not supported on this API level")
      return
    }
    
    if (fps <= 0) {
      Log.w(TAG, "Invalid frame rate: $fps")
      return
    }

    try {
      val surface = surfaceView.holder.surface
      if (surface == null || !surface.isValid) {
        Log.w(TAG, "Surface not available or invalid")
        return
      }

      // Use FRAME_RATE_COMPATIBILITY_FIXED_SOURCE for video content
      // This indicates the content has a fixed frame rate that cannot change
      surface.setFrameRate(
        fps,
        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
        Surface.CHANGE_FRAME_RATE_ALWAYS,
      )
      
      Log.d(TAG, "Applied video frame rate: ${fps}fps")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to apply video frame rate", e)
    }
  }

  /**
   * Reset the surface to default frame rate behavior.
   *
   * Call this when stopping video playback to restore normal display behavior.
   *
   * @param surfaceView The SurfaceView that was rendering the video
   */
  fun resetToDefault(surfaceView: SurfaceView) {
    if (!isApiLevelSupported()) return

    try {
      val surface = surfaceView.holder.surface
      if (surface == null || !surface.isValid) return

      // Setting frame rate to 0 clears the frame rate setting
      surface.setFrameRate(
        0f,
        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
      )
      
      Log.d(TAG, "Reset frame rate to default")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset frame rate", e)
    }
  }
}
