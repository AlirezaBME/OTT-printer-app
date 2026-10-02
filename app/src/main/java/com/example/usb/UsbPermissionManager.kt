package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

class UsbPermissionManager(private val context: Context, private val usbManager: UsbManager) : AutoCloseable {
    private val action = "${context.packageName}.USB_PERMISSION"
    private var pendingDevice: UsbDevice? = null
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != action) return
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            if (device?.deviceName != pendingDevice?.deviceName) return
            finish(device != null && usbManager.hasPermission(device))
        }
    }
    fun hasPermission(device: UsbDevice) = usbManager.hasPermission(device)
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (hasPermission(device)) { onResult(true); return }
        if (pendingDevice != null) {
            if (pendingDevice?.deviceName == device.deviceName) callbacks.add(onResult) else onResult(false)
            return
        }
        pendingDevice = device
        callbacks.add(onResult)
        try {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            // Android UsbManager adds EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED to this immutable broadcast.
            val broadcast = PendingIntent.getBroadcast(context, device.deviceId,
                Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            usbManager.requestPermission(device, broadcast)
        } catch (_: Exception) { finish(false) }
    }
    fun onDevicesChanged(devices: Collection<UsbDevice>) {
        if (pendingDevice != null && devices.none { it.deviceName == pendingDevice?.deviceName }) finish(false)
    }
    private fun finish(granted: Boolean) {
        if (registered) { context.unregisterReceiver(receiver); registered = false }
        pendingDevice = null
        val waiting = callbacks.toList()
        callbacks.clear()
        waiting.forEach { it(granted) }
    }
    override fun close() = finish(false)
}
