package com.example.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One application-wide repository and one lock for all USB jobs and probes. */
class UsbDeviceRepository(private val context: Context, private val scope: CoroutineScope) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val permissionManager = UsbPermissionManager(context, usbManager)
    val transport: UsbTransport = UsbPrinterTransport(usbManager)
    val operationMutex = Mutex()
    private val _activeDevice = MutableStateFlow<UsbDevice?>(null)
    val activeDevice = _activeDevice.asStateFlow()
    private val _activeDeviceInfo = MutableStateFlow<UsbDeviceInfo?>(null)
    val activeDeviceInfo = _activeDeviceInfo.asStateFlow()
    private val _allDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val allDevices = _allDevices.asStateFlow()
    private val detector = UsbPrinterDetector(context, usbManager) { refreshDevices() }
    init { detector.start(); refreshDevices() }

    private var refreshTask: kotlinx.coroutines.Job? = null
    suspend fun awaitRefresh() { refreshTask?.join() }

    fun refreshDevices() {
        refreshTask = scope.launch(Dispatchers.IO) {
            operationMutex.withLock {
                val devices = usbManager.deviceList.values.toList()
                scope.launch(Dispatchers.Main) { permissionManager.onDevicesChanged(devices) }
                val previous = _activeDeviceInfo.value
                val infos = devices.map { device ->
                    val info = UsbDescriptorReader.readDeviceInfo(device, null, permissionManager.hasPermission(device))
                    if (device.deviceName == previous?.deviceName && info.permissionGranted) {
                        info.copy(ieee1284 = previous.ieee1284, portStatus = previous.portStatus)
                    } else info
                }
                val chosen = devices.firstOrNull { it.deviceName == _activeDevice.value?.deviceName }
                    ?: devices.sortedByDescending { it.vendorId == 0x04a9 }.firstOrNull { dev ->
                        infos.first { it.deviceName == dev.deviceName }.primaryPrinterInterface != null
                    }
                if (_activeDevice.value?.deviceName != chosen?.deviceName) transport.close()
                _activeDevice.value = chosen
                _allDevices.value = infos
                _activeDeviceInfo.value = infos.firstOrNull { it.deviceName == chosen?.deviceName }
            }
        }
    }

    fun requestPermissionForActiveDevice(onResult: (Boolean) -> Unit) {
        val dev = _activeDevice.value
        if (dev == null) { onResult(false); return }
        permissionManager.requestPermission(dev) { granted ->
            refreshDevices()
            onResult(granted)
        }
    }

    suspend fun probeConnectedDevice(): UsbDeviceInfo {
        val device = _activeDevice.value ?: error("Connect a printer using a USB OTG cable.")
        check(permissionManager.hasPermission(device)) { "Grant USB permission first." }
        val info = UsbDescriptorReader.readDeviceInfo(device, null, true)
        val printer = info.primaryPrinterInterface ?: error("No supported USB printer interface.")
        transport.open(device).getOrThrow()
        transport.claimInterface(printer.id).getOrThrow()
        val updated = info.copy(
            ieee1284 = transport.queryIeee1284DeviceId(printer.id).getOrNull(),
            portStatus = transport.queryPortStatus(printer.id).getOrNull(),
            interfaceClaimed = true
        )
        _activeDeviceInfo.value = updated
        _allDevices.value = _allDevices.value.map { if (it.deviceName == updated.deviceName) updated else it }
        return updated
    }

    suspend fun safeProbe(): Result<UsbDeviceInfo> = withContext(Dispatchers.IO) {
        if (!operationMutex.tryLock()) return@withContext Result.failure(IllegalStateException("Printer is busy. Wait for the current job."))
        try { Result.success(probeConnectedDevice()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally {
            transport.close()
            _activeDeviceInfo.value = _activeDeviceInfo.value?.copy(interfaceClaimed = false)
            operationMutex.unlock()
        }
    }

    fun selectDeviceByName(name: String) {
        if (operationMutex.isLocked) return
        _activeDevice.value = usbManager.deviceList.values.firstOrNull { it.deviceName == name }
        refreshDevices()
    }
}
