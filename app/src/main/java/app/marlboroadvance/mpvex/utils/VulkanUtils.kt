package app.marlboroadvance.mpvex.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object VulkanUtils {
    private const val TAG = "VulkanUtils"
    
    /**
     * Checks if the device supports Vulkan for MPV rendering
     * 
     * Requirements for MPV androidvk context:
     * - Android 13 (API 33) minimum for Vulkan 1.3
     * - Vulkan 1.3 (0x00403000) hardware version
     * - GPU must also support OpenGL ES 3.1 or higher
     * 
     * @return true if Vulkan 1.3+ is supported for MPV, false otherwise
     */
    fun isVulkanSupported(context: Context): Boolean {
        try {
            // Vulkan 1.3 requires Android 13 (API 33) minimum
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Vulkan not supported: Android version ${Build.VERSION.SDK_INT} < 33 (Tiramisu)")
                return false
            }

            val packageManager = context.packageManager
            
            // Check for OpenGL ES 3.1+ support (required by Android for Vulkan)
            val configInfo = packageManager.systemAvailableFeatures
                .firstOrNull { it.name == null }
            
            val glesVersion = configInfo?.reqGlEsVersion ?: 0
            val glesMajor = glesVersion shr 16
            val glesMinor = glesVersion and 0xFFFF
            
            Log.d(TAG, "Device OpenGL ES version: $glesMajor.$glesMinor (raw: 0x${glesVersion.toString(16)})")
            
            // OpenGL ES 3.1 = 0x00030001
            if (glesVersion < 0x00030001) {
                Log.d(TAG, "Vulkan not supported: OpenGL ES $glesMajor.$glesMinor < 3.1")
                return false
            }
            
            // Check for Vulkan 1.3 hardware version (required for proper MPV support)
            if (packageManager.hasSystemFeature(
                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 
                    0x00403000 // Vulkan 1.3
                )) {
                Log.d(TAG, "Vulkan 1.3 supported ✓")
                return true
            }
            
            Log.d(TAG, "Vulkan not supported: Vulkan 1.3 not available")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Vulkan support", e)
            return false
        }
    }
}
