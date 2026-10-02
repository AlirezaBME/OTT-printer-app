package com.example.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbDeviceRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "UsbDeviceRepository"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val permissionManager = UsbPermissionManager(context, usbManager)
    val transport: UsbTransport = UsbPrinterTransport(usbManager)

    private val _activeDevice = MutableStateFlow<UsbDevice?>(null)
    val activeDevice: StateFlow<UsbDevice?> = _activeDevice.asStateFlow()

    private val _activeDeviceInfo = MutableStateFlow<UsbDeviceInfo?>(null)
    val activeDeviceInfo: StateFlow<UsbDeviceInfo?> = _activeDeviceInfo.asStateFlow()

    private val _allDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val allDevices: StateFlow<List<UsbDeviceInfo>> = _allDevices.asStateFlow()

    private val detector = UsbPrinterDetector(context, usbManager) {
        refreshDevices()
    }

    init {
        detector.start()
        refreshDevices()
    }

    fun refreshDevices() {
        scope.launch {
            val rawList = usbManager.deviceList.values.toList()
            val infos = mutableListOf<UsbDeviceInfo>()
            var bestPrinterDevice: UsbDevice? = null

            for (device in rawList) {
                val hasPerm = permissionManager.hasPermission(device)
                var conn: android.hardware.usb.UsbDeviceConnection? = null
                if (hasPerm) {
                    try {
                        conn = usbManager.openDevice(device)
                    } catch (_: Exception) {}
                }

                val info = UsbDescriptorReader.readDeviceInfo(
                    device = device,
                    connection = conn,
                    permissionGranted = hasPerm,
                    interfaceClaimed = transport.isConnected() && _activeDevice.value == device
                )
                conn?.close()
                infos.add(info)

                // Select preferred printer
                if (bestPrinterDevice == null) {
                    if (info.isLbp6030Family) {
                        bestPrinterDevice = device
                    } else if (info.isCanonFamily && info.primaryPrinterInterface != null) {
                        bestPrinterDevice = device
                    } else if (info.primaryPrinterInterface != null) {
                        bestPrinterDevice = device
                    }
                }
            }

            _allDevices.value = infos

            val current = _activeDevice.value
            if (current == null || !rawList.contains(current)) {
                if (current != null) {
                    UsbTraceLogger.log(TAG, "Active device detached. Closing USB transport.")
                    transport.close()
                }
                _activeDevice.value = bestPrinterDevice
                _activeDeviceInfo.value = infos.firstOrNull { it.deviceName == bestPrinterDevice?.deviceName }
            } else {
                _activeDeviceInfo.value = infos.firstOrNull { it.deviceName == current.deviceName }
            }
        }
    }

    fun requestPermissionForActiveDevice(onResult: (Boolean) -> Unit) {
        val dev = _activeDevice.value
        if (dev == null) {
            onResult(false)
            return
        }
        permissionManager.requestPermission(dev) { granted ->
            refreshDevices()
            onResult(granted)
        }
    }

    suspend fun safeProbe(): Result<UsbDeviceInfo> = withContext(Dispatchers.IO) {
        val dev = _activeDevice.value
            ?: return@withContext Result.failure(IllegalStateException("No USB device selected"))
        
        if (!permissionManager.hasPermission(dev)) {
            return@withContext Result.failure(IllegalStateException("USB permission required for safe probe"))
        }

        try {
            UsbTraceLogger.log(TAG, "Starting safe USB probe on ${dev.deviceName}...")
            val openRes = transport.open(dev)
            if (openRes.isFailure) {
                return@withContext Result.failure(openRes.exceptionOrNull() ?: IllegalStateException("Failed to open USB transport"))
            }

            // Find printer interface
            var printerIfIndex = 0
            for (i in 0 until dev.interfaceCount) {
                val uif = dev.getInterface(i)
                if (uif.interfaceClass == 7) {
                    printerIfIndex = uif.id
                    break
                }
            }

            val claimRes = transport.claimInterface(printerIfIndex)
            if (claimRes.isFailure) {
                transport.close()
                return@withContext Result.failure(claimRes.exceptionOrNull() ?: IllegalStateException("Failed to claim interface"))
            }

            // Query IEEE-1284
            val ieeeRes = transport.queryIeee1284DeviceId(printerIfIndex)
            val portRes = transport.queryPortStatus(printerIfIndex)

            val conn = usbManager.openDevice(dev)
            val info = UsbDescriptorReader.readDeviceInfo(
                device = dev,
                connection = conn,
                permissionGranted = true,
                interfaceClaimed = true
            ).copy(
                ieee1284 = ieeeRes.getOrNull(),
                portStatus = portRes.getOrNull()
            )
            conn?.close()

            _activeDeviceInfo.value = info
            UsbTraceLogger.log(TAG, "Safe probe completed: MDL=${info.ieee1284?.model}, Status=${info.portStatus?.toDisplayString()}")
            Result.success(info)
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Safe probe failed", e)
            Result.failure(e)
        } finally {
            transport.close()
            refreshDevices()
        }
    }

    fun selectDevice(device: UsbDevice) {
        _activeDevice.value = device
        refreshDevices()
    }
}
