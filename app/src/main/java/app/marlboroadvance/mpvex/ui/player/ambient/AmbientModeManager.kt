package app.marlboroadvance.mpvex.ui.player.ambient

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages calm, YouTube-like periodic video frame sampling to power Ambient Mode.
 *
 * Employs low-pass color smoothing and a 1.25s cadence to prevent nervous color jumping,
 * and ensures black letterbox areas are never accidentally sampled.
 */
class AmbientModeManager(
  private val scope: CoroutineScope,
  private val playerPreferences: PlayerPreferences,
  private val surfaceViewProvider: () -> SurfaceView?,
) {
  private val _ambientColors = MutableStateFlow(AmbientColors.Default)
  val ambientColors: StateFlow<AmbientColors> = _ambientColors.asStateFlow()

  private var samplingJob: Job? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  // Micro buffer for video frame: 32x18 = 576 pixels (< 2.5 KB)
  private val sampleWidth = 32
  private val sampleHeight = 18
  private val sampleBitmap by lazy {
    Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
  }

  // Running smoothed colors (low-pass filter)
  private var smoothedColors = AmbientColors.Default

  private var isPlaybackPaused: Boolean = true
  private var isInPipMode: Boolean = false

  fun onPlaybackStateChanged(isPaused: Boolean) {
    val wasPlaying = !isPlaybackPaused
    isPlaybackPaused = isPaused
    if (!isPaused) {
      startSampling()
    } else {
      // Only sample on pause if we were actively playing
      if (wasPlaying || samplingJob != null) {
        scope.launch(Dispatchers.Default) {
          sampleCurrentFrame()
          samplingJob?.cancel()
          samplingJob = null
        }
      }
    }
  }

  fun onPipModeChanged(inPip: Boolean) {
    isInPipMode = inPip
    if (inPip) {
      stopSampling()
    } else if (!isPlaybackPaused) {
      startSampling()
    }
  }

  fun startSampling() {
    if (!playerPreferences.ambientMode.get() || isInPipMode) {
      return
    }

    if (samplingJob?.isActive == true) return

    samplingJob = scope.launch(Dispatchers.Default) {
      while (isActive) {
        if (!playerPreferences.ambientMode.get() || isInPipMode || isPlaybackPaused) {
          break
        }

        sampleCurrentFrame()
        delay(1250L) // Calm, YouTube-like cadence (~1.25s)
      }
    }
  }

  fun stopSampling() {
    samplingJob?.cancel()
    samplingJob = null
  }

  fun triggerSingleSample() {
    if (!playerPreferences.ambientMode.get() || isInPipMode) return
    scope.launch(Dispatchers.Default) {
      sampleCurrentFrame()
    }
  }

  private suspend fun sampleCurrentFrame() {
    var bitmap: Bitmap? = null

    // 1. Primary: Grab thumbnail directly from MPV.
    // This extracts the pure decoded video frame WITHOUT any player window letterbox bars!
    bitmap = runCatching { MPVLib.grabThumbnail(sampleWidth) }.getOrNull()

    // 2. Fallback: PixelCopy on the SurfaceView with calculated source rect
    if (bitmap == null) {
      val surfaceView = surfaceViewProvider()
      if (surfaceView != null && surfaceView.holder.surface?.isValid == true) {
        bitmap = copyViaPixelCopy(surfaceView)
      }
    }

    bitmap?.let { processFrameColors(it) }
  }

  private suspend fun copyViaPixelCopy(surfaceView: SurfaceView): Bitmap? {
    return suspendCancellableCoroutine { continuation ->
      try {
        val sw = surfaceView.width.toFloat()
        val sh = surfaceView.height.toFloat()
        val rawAspect = runCatching { MPVLib.getPropertyDouble("video-params/aspect") }.getOrNull()
        val va = rawAspect?.toFloat()?.takeIf { it > 0.001f } ?: (sw / sh)
        val sa = if (sh > 0f) sw / sh else 1f

        val (vw, vh) = if (va >= sa) {
          sw to (sw / va)
        } else {
          (sh * va) to sh
        }

        val left = ((sw - vw) / 2f).toInt().coerceAtLeast(0)
        val top = ((sh - vh) / 2f).toInt().coerceAtLeast(0)
        val right = (left + vw.toInt()).coerceAtMost(surfaceView.width)
        val bottom = (top + vh.toInt()).coerceAtMost(surfaceView.height)

        val srcRect = if (right > left && bottom > top) {
          Rect(left, top, right, bottom)
        } else {
          null
        }

        PixelCopy.request(
          surfaceView,
          srcRect,
          sampleBitmap,
          { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
              continuation.resume(sampleBitmap)
            } else {
              continuation.resume(null)
            }
          },
          mainHandler,
        )
      } catch (e: Exception) {
        continuation.resume(null)
      }
    }
  }

  private fun processFrameColors(bitmap: Bitmap) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return

    // Inset ~12% to protect against any hardcoded letterbox borders in the source file
    val insetX = (w * 0.12f).toInt().coerceIn(1, w / 4)
    val insetY = (h * 0.12f).toInt().coerceIn(1, h / 4)

    val topArgb = averageColor(bitmap, xRange = insetX..(w - 1 - insetX), yRange = insetY..(insetY + 2))
    val bottomArgb = averageColor(bitmap, xRange = insetX..(w - 1 - insetX), yRange = (h - 1 - insetY - 2)..(h - 1 - insetY))
    val leftArgb = averageColor(bitmap, xRange = insetX..(insetX + 2), yRange = insetY..(h - 1 - insetY))
    val rightArgb = averageColor(bitmap, xRange = (w - 1 - insetX - 2)..(w - 1 - insetX), yRange = insetY..(h - 1 - insetY))
    val centerArgb = averageColor(bitmap, xRange = (w / 4)..(w * 3 / 4), yRange = (h / 4)..(h * 3 / 4))

    val rawTop = AmbientColors.processSampledColor(topArgb)
    val rawBottom = AmbientColors.processSampledColor(bottomArgb)
    val rawLeft = AmbientColors.processSampledColor(leftArgb)
    val rawRight = AmbientColors.processSampledColor(rightArgb)
    val rawCenter = AmbientColors.processSampledColor(centerArgb)

    // Apply exponential moving average (low-pass filter) to eliminate micro-jitter
    val blendRatio = 0.45f
    val newSmoothed = AmbientColors(
      top = AmbientColors.blend(smoothedColors.top, rawTop, blendRatio),
      bottom = AmbientColors.blend(smoothedColors.bottom, rawBottom, blendRatio),
      left = AmbientColors.blend(smoothedColors.left, rawLeft, blendRatio),
      right = AmbientColors.blend(smoothedColors.right, rawRight, blendRatio),
      center = AmbientColors.blend(smoothedColors.center, rawCenter, blendRatio),
    )
    smoothedColors = newSmoothed

    // Only emit when change is visually significant, keeping the light calm like YouTube
    if (AmbientColors.isSignificantChange(_ambientColors.value, newSmoothed)) {
      _ambientColors.value = newSmoothed
    }
  }

  private fun averageColor(bitmap: Bitmap, xRange: IntRange, yRange: IntRange): Int {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0

    for (x in xRange) {
      for (y in yRange) {
        if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
          val pixel = bitmap.getPixel(x, y)
          rSum += (pixel shr 16) and 0xFF
          gSum += (pixel shr 8) and 0xFF
          bSum += pixel and 0xFF
          count++
        }
      }
    }
    if (count == 0) return 0
    val r = (rSum / count).toInt()
    val g = (gSum / count).toInt()
    val b = (bSum / count).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }
}
