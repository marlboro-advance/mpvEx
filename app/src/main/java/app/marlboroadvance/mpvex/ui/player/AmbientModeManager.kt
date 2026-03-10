package app.marlboroadvance.mpvex.ui.player

import android.util.Log
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages ambient mode functionality for the video player.
 * Handles shader generation, video scaling, and parameter management.
 */
class AmbientModeManager(
  private val playerPreferences: PlayerPreferences,
  private val cacheDir: File,
  private val scope: CoroutineScope,
  private val onShowText: (String) -> Unit
) {
  companion object {
    private const val TAG = "AmbientModeManager"
  }

  // ==================== State Management ====================
  
  private val _isAmbientEnabled = MutableStateFlow(playerPreferences.isAmbientEnabled.get())
  val isAmbientEnabled: StateFlow<Boolean> = _isAmbientEnabled.asStateFlow()

  private val _isAmbientLoading = MutableStateFlow(false)
  val isAmbientLoading: StateFlow<Boolean> = _isAmbientLoading.asStateFlow()

  private var lastAmbientScaleX = -1.0
  private var lastAmbientScaleY = -1.0
  private var ambientDebounceJob: Job? = null
  private var ambientShaderSeq = 0
  private var ambientShaderFile: File? = null

  // ==================== Public API ====================

  fun toggleAmbientMode() {
    _isAmbientEnabled.value = !_isAmbientEnabled.value

    // Save the Ambient Mode ON/OFF state permanently to preferences
    playerPreferences.isAmbientEnabled.set(_isAmbientEnabled.value)
    if (_isAmbientEnabled.value) {
      lastAmbientScaleX = -1.0 // Force rewrite
      updateAmbientStretch()
      onShowText("Ambience Mode: ON")
    } else {
      disableAmbientShader()
      onShowText("Ambience Mode: OFF")
    }
  }

  /** Called when the device orientation changes. Refreshes ambient shader for new dimensions. */
  fun onOrientationChanged(isPortrait: Boolean) {
    if (_isAmbientEnabled.value) {
      // Force shader refresh to adapt to new screen dimensions
      lastAmbientScaleX = -1.0
      lastAmbientScaleY = -1.0
      // Small delay to let the new OSD dimensions settle
      ambientDebounceJob?.cancel()
      ambientDebounceJob = scope.launch {
        delay(200)
        updateAmbientStretch()
      }
    }
  }

  /** Resets ambient mode to OFF when a new video file is loaded. */
  fun resetAmbientMode() {
    if (!_isAmbientEnabled.value) return
    
    // Ambient Mode Persistent Fix for Next/Previous files
    // DO NOT set _isAmbientEnabled.value = false
    // Just temporarily remove the old shader and reset the scale 
    // so the new video starts with a clean slate before recalculating.
    disableAmbientShader()
    lastAmbientScaleX = -1.0
    lastAmbientScaleY = -1.0
  }

  /**
   * Re-injects the ambient shader if ambient mode is currently ON.
   * Called after Anime4K shader changes, since setPropertyString("glsl-shaders", ...)
   * wipes ALL glsl-shaders including the ambient one.
   */
  fun restartAmbientIfActive() {
    if (!_isAmbientEnabled.value) return
    // The old ambient shader file was wiped by the glsl-shaders reset.
    // Clean up our local reference without trying to remove from MPV.
    ambientShaderFile?.delete()
    ambientShaderFile = null
    lastAmbientScaleX = -1.0  // Force rewrite
    // Small delay to let Anime4K shaders settle
    ambientDebounceJob?.cancel()
    ambientDebounceJob = scope.launch {
      delay(200)
      updateAmbientStretch()
    }
  }



  fun updateAmbientStretch() {
    if (!_isAmbientEnabled.value) return

    runCatching {
      val osdW = MPVLib.getPropertyInt("osd-width") ?: 1920
      val osdH = MPVLib.getPropertyInt("osd-height") ?: 1080

      // Portrait mode: ambient glow goes on top/bottom (letterbox)
      // Landscape mode: ambient glow goes on left/right (pillarbox)
      // Both are handled by the same scaleX/scaleY math below

      var vidW = (MPVLib.getPropertyInt("video-params/w") ?: 1920).toDouble()
      var vidH = (MPVLib.getPropertyInt("video-params/h") ?: 1080).toDouble()
      val par  = MPVLib.getPropertyDouble("video-params/par") ?: 1.0
      val rot  = MPVLib.getPropertyInt("video-params/rotate") ?: 0

      // Intercept autocrop boundaries — if a crop is active, use the cropped dimensions
      // so the shader's aspect-ratio math matches the actual visible video area
      val crop = MPVLib.getPropertyString("video-crop") ?: ""
      val cropMatch = Regex("""^(\d+)x(\d+)""").find(crop)
      if (cropMatch != null) {
        vidW = cropMatch.groupValues[1].toDouble()
        vidH = cropMatch.groupValues[2].toDouble()
      }

      if (osdW <= 0 || osdH <= 0 || vidW <= 0.0 || vidH <= 0.0) return

      // Apply pixel aspect ratio (non-square pixels)
      vidW *= par
      // Swap dimensions for 90°/270° rotated videos (portrait shot stored as landscape)
      if (rot == 90 || rot == 270) { val tmp = vidW; vidW = vidH; vidH = tmp }

      val screenAr = osdW.toDouble() / osdH.toDouble()
      val vidAr    = vidW / vidH
      
      // Scale the video to fill the screen — the shader remaps it back to the
      // correct aspect ratio, so only the "overflow" area receives ambient glow.
      val scaleX = if (screenAr > vidAr) screenAr / vidAr else 1.0
      val scaleY = if (vidAr > screenAr) vidAr / screenAr else 1.0

      if (Math.abs(scaleX - lastAmbientScaleX) > 0.001 ||
          Math.abs(scaleY - lastAmbientScaleY) > 0.001) {
        lastAmbientScaleX = scaleX
        lastAmbientScaleY = scaleY
        MPVLib.setPropertyDouble("video-scale-x", scaleX)
        MPVLib.setPropertyDouble("video-scale-y", scaleY)
      }

      // ── Generate GLSL shader ───────────────────────────────────────────────
      val shaderCode = buildAmbientShader(
        sx = lastAmbientScaleX,
        sy = lastAmbientScaleY
      )

      // Each reload gets a unique filename so MPV never reuses a cached
      // compiled shader — incrementing seq guarantees a fresh compile every time.
      val newFile = File(cacheDir, "ambient_${++ambientShaderSeq}.glsl")
      newFile.writeText(shaderCode)
      ambientShaderFile?.let { oldFile ->
        runCatching { MPVLib.command("change-list", "glsl-shaders", "remove", oldFile.absolutePath) }
        oldFile.delete()
      }
      MPVLib.command("change-list", "glsl-shaders", "append", newFile.absolutePath)
      ambientShaderFile = newFile
    }.onFailure { e ->
      Log.e(TAG, "Failed to update ambient stretch", e)
    }
  }

  // ==================== Private Methods ====================

  /** Disables the ambient shader and resets video scale. Safe to call from any state. */
  private fun disableAmbientShader() {
    ambientDebounceJob?.cancel()
    ambientShaderFile?.let { file ->
      runCatching { MPVLib.command("change-list", "glsl-shaders", "remove", file.absolutePath) }
      file.delete()
    }
    ambientShaderFile = null
    runCatching {
      MPVLib.setPropertyDouble("video-scale-x", 1.0)
      MPVLib.setPropertyDouble("video-scale-y", 1.0)
    }
  }

  /**
   * Builds a simplified YouTube-style ambient GLSL shader.
   * Samples random areas from entire video with temporal stability and debanding.
   */
  private fun buildAmbientShader(
    sx: Double,
    sy: Double
  ): String = """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC YouTube-Style Ambient Mode

#define SCALE_X    $sx
#define SCALE_Y    $sy

// Simple hash function for pseudo-random sampling
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 hook() {
    vec2 uv = HOOKED_pos;
    vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // Return video pixel if inside video bounds
    if (video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
        video_uv.y >= 0.0 && video_uv.y <= 1.0) {
        return HOOKED_tex(video_uv);
    }

    // Ambient region - sample random areas from entire video
    // Use fixed seed for temporal stability (color doesn't flicker)
    vec3 avg_color = vec3(0.0);
    int samples = 20;
    float base_seed = 42.0; // Fixed seed for stability
    
    // Sample random positions across the entire video
    for (int i = 0; i < samples; i++) {
        float seed = base_seed + float(i) * 0.618034;
        float x = hash(vec2(seed, 0.123));
        float y = hash(vec2(seed, 0.456));
        
        vec2 sample_pos = vec2(x, y);
        avg_color += HOOKED_tex(sample_pos).rgb;
    }
    
    avg_color /= float(samples);

    // Boost saturation slightly for more vibrant glow
    float luma = dot(avg_color, vec3(0.2126, 0.7152, 0.0722));
    avg_color = mix(vec3(luma), avg_color, 1.3); // 30% saturation boost

    // Increased brightness for more visible glow (30% instead of 20%)
    avg_color *= 0.30;

    // Smooth fade based on distance from video edge
    vec2 edge_uv = clamp(video_uv, 0.0, 1.0);
    float dist = length(video_uv - edge_uv);
    float fade = exp(-dist * 2.5);
    avg_color *= fade;

    // Debanding: add subtle dither noise to eliminate color banding
    float dither = hash(uv * 1000.0) * 0.004 - 0.002; // ±0.002 range
    avg_color = clamp(avg_color + dither, 0.0, 1.0);

    return vec4(avg_color, 1.0);
}
  """.trimIndent()

  // ==================== Cleanup ====================

  fun cleanup() {
    ambientDebounceJob?.cancel()
    ambientShaderFile?.delete()
    ambientShaderFile = null
  }
}
