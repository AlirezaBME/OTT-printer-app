package com.example

import android.app.Application
import com.example.usb.UsbDeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LbpOtgApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val usbRepository by lazy { UsbDeviceRepository(this, applicationScope) }
}
