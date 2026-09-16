package app.marlboroadvance.mpvex.ui.player.ambient

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Data class representing the sampled edge and ambient colors of the current video frame.
 */
data class AmbientColors(
  val top: Color = Color.Transparent,
  val bottom: Color = Color.Transparent,
  val left: Color = Color.Transparent,
  val right: Color = Color.Transparent,
  val center: Color = Color.Transparent,
) {
  companion object {
    val Default = AmbientColors()

    /**
     * Boosts color saturation and conditions luminance to produce an organic,
     * vivid glowing ambient aura similar to YouTube's ambient mode.
     */
    fun processSampledColor(
      argb: Int,
      saturationBoost: Float = 1.35f,
      minLuminance: Float = 0.06f,
    ): Color {
      val hsv = FloatArray(3)
      android.graphics.Color.colorToHSV(argb, hsv)

      // If color is completely black, return transparent
      if (hsv[2] < minLuminance) {
        return Color.Transparent
      }

      // Boost saturation for richer, warmer ambient color (YouTube style)
      hsv[1] = (hsv[1] * saturationBoost).coerceIn(0.15f, 1f)

      // Clamp value/brightness so ambient glow is luminous but never over-bright
      hsv[2] = hsv[2].coerceIn(0.25f, 0.90f)

      val conditionedArgb = android.graphics.Color.HSVToColor(hsv)
      return Color(conditionedArgb)
    }

    /**
     * Blends two colors with a ratio for smooth exponential moving average (low-pass filter).
     */
    fun blend(c1: Color, c2: Color, ratio: Float = 0.45f): Color {
      if (c1 == Color.Transparent) return c2
      if (c2 == Color.Transparent) return c1
      val r = ratio.coerceIn(0f, 1f)
      return Color(
        red = c1.red + (c2.red - c1.red) * r,
        green = c1.green + (c2.green - c1.green) * r,
        blue = c1.blue + (c2.blue - c1.blue) * r,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * r,
      )
    }

    /**
     * Determines whether two AmbientColors instances have significant visual difference.
     */
    fun isSignificantChange(c1: AmbientColors, c2: AmbientColors, threshold: Float = 0.008f): Boolean {
      fun dist(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        val da = a.alpha - b.alpha
        return dr * dr + dg * dg + db * db + da * da
      }

      return dist(c1.top, c2.top) > threshold ||
        dist(c1.bottom, c2.bottom) > threshold ||
        dist(c1.left, c2.left) > threshold ||
        dist(c1.right, c2.right) > threshold
    }
  }
}
