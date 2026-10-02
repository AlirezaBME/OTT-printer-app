package com.example.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

class UsbPrinterDetector(
    private val context: Context,
    private val usbManager: UsbManager,
    private val onDeviceListChanged: () -> Unit
) {
    companion object {
        private const val TAG = "UsbPrinterDetector"
    }

    private var isReceiverRegistered = false

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    UsbTraceLogger.log(TAG, "USB Device Attached: ${device?.deviceName} (VID 0x${String.format("%04X", device?.vendorId ?: 0)})")
                    onDeviceListChanged()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    UsbTraceLogger.log(TAG, "USB Device Detached: ${device?.deviceName}")
                    onDeviceListChanged()
                }
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            context.registerReceiver(deviceReceiver, filter)
            isReceiverRegistered = true
            UsbTraceLogger.log(TAG, "UsbPrinterDetector started listening for USB attach/detach events.")
        }
    }

    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(deviceReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
            UsbTraceLogger.log(TAG, "UsbPrinterDetector stopped.")
        }
    }

    fun getConnectedDevices(): List<UsbDevice> {
        val list = usbManager.deviceList.values.toList()
        UsbTraceLogger.log(TAG, "Enumerated ${list.size} USB device(s) connected.")
        return list
    }
}
