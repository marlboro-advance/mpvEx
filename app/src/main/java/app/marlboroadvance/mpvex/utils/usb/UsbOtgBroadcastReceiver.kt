package app.marlboroadvance.mpvex.utils.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Broadcast receiver for USB OTG device mount/unmount events
 */
class UsbOtgBroadcastReceiver(
  private val onUsbStateChanged: () -> Unit,
) : BroadcastReceiver() {
  companion object {
    private const val TAG = "UsbOtgBroadcastReceiver"

    /**
     * Creates an IntentFilter for USB events
     */
    fun createIntentFilter(): IntentFilter =
      IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_MOUNTED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(Intent.ACTION_MEDIA_REMOVED)
        addAction(Intent.ACTION_MEDIA_EJECT)
        addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addDataScheme("file")
      }
  }

  override fun onReceive(
    context: Context?,
    intent: Intent?,
  ) {
    if (context == null || intent == null) return

    val action = intent.action
    Log.d(TAG, "Received USB broadcast: $action")

    when (action) {
      Intent.ACTION_MEDIA_MOUNTED,
      Intent.ACTION_MEDIA_UNMOUNTED,
      Intent.ACTION_MEDIA_REMOVED,
      Intent.ACTION_MEDIA_EJECT,
      Intent.ACTION_MEDIA_BAD_REMOVAL,
      UsbManager.ACTION_USB_DEVICE_ATTACHED,
      UsbManager.ACTION_USB_DEVICE_DETACHED,
      -> {
        Log.d(TAG, "USB state changed, triggering rescan")
        onUsbStateChanged()
      }
    }
  }
}
