package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

class UsbPermissionManager(
    private val context: Context,
    private val usbManager: UsbManager
) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.example.lbpotgprint.USB_PERMISSION"
        private const val TAG = "UsbPermissionManager"
    }

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var isReceiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    UsbTraceLogger.log(TAG, "Permission result received for device ${device?.deviceName}: granted=$granted")
                    permissionCallback?.invoke(granted)
                    permissionCallback = null
                    unregisterReceiverSafe()
                }
            }
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (hasPermission(device)) {
            UsbTraceLogger.log(TAG, "Permission already granted for ${device.deviceName}")
            onResult(true)
            return
        }

        permissionCallback = onResult
        registerReceiverSafe()

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )

        UsbTraceLogger.log(TAG, "Requesting USB permission for ${device.deviceName} (VID 0x${String.format("%04X", device.vendorId)})")
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun registerReceiverSafe() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiverSafe() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(permissionReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }
}
