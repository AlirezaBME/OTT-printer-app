package com.example.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.Build

object UsbDescriptorReader {
    private const val TAG = "UsbDescriptorReader"

    fun readDeviceInfo(
        device: UsbDevice,
        connection: UsbDeviceConnection?,
        permissionGranted: Boolean,
        interfaceClaimed: Boolean = false
    ): UsbDeviceInfo {
        val interfaces = mutableListOf<UsbInterfaceInfo>()
        for (i in 0 until device.interfaceCount) {
            val usbIf = device.getInterface(i)
            val endpoints = mutableListOf<UsbEndpointInfo>()
            for (e in 0 until usbIf.endpointCount) {
                val ep = usbIf.getEndpoint(e)
                val directionStr = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val typeStr = when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                    UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
                    else -> "UNKNOWN(${ep.type})"
                }
                endpoints.add(
                    UsbEndpointInfo(
                        endpointNumber = ep.endpointNumber,
                        address = ep.address,
                        direction = directionStr,
                        type = typeStr,
                        maxPacketSize = ep.maxPacketSize,
                        interval = ep.interval
                    )
                )
            }

            val ifName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                usbIf.name
            } else null

            val altSetting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                usbIf.alternateSetting
            } else 0

            interfaces.add(
                UsbInterfaceInfo(
                    id = usbIf.id,
                    alternateSetting = altSetting,
                    interfaceClass = usbIf.interfaceClass,
                    interfaceSubclass = usbIf.interfaceSubclass,
                    interfaceProtocol = usbIf.interfaceProtocol,
                    name = ifName,
                    endpoints = endpoints
                )
            )
        }

        var ieee1284: Ieee1284DeviceId? = null
        var portStatus: UsbPrinterPortStatus? = null

        if (connection != null && permissionGranted) {
            // Find printer interface index
            val printerIf = interfaces.firstOrNull { it.isPrinterClass } ?: interfaces.firstOrNull()
            val ifIndex = printerIf?.id ?: 0
            
            // 1. Query IEEE 1284 Device ID
            try {
                val buffer = ByteArray(1024)
                val bytesRead = connection.controlTransfer(
                    0xA1, // bmRequestType: Device to Host, Class, Interface
                    0,    // bRequest: GET_DEVICE_ID
                    0,    // wValue: configIndex
                    ifIndex, // wIndex: interfaceIndex
                    buffer,
                    buffer.size,
                    2000  // timeout 2s
                )
                if (bytesRead > 0) {
                    val rawData = buffer.copyOf(bytesRead)
                    ieee1284 = Ieee1284Parser.parse(rawData)
                    UsbTraceLogger.log(TAG, "IEEE-1284 Device ID read: ${ieee1284.rawString}")
                } else {
                    UsbTraceLogger.log(TAG, "GET_DEVICE_ID returned $bytesRead bytes")
                }
            } catch (e: Exception) {
                UsbTraceLogger.logError(TAG, "Failed to read IEEE-1284 ID", e)
            }

            // 2. Query Port Status
            try {
                val statusBuf = ByteArray(1)
                val bytesRead = connection.controlTransfer(
                    0xA1, // bmRequestType
                    1,    // bRequest: GET_PORT_STATUS
                    0,    // wValue
                    ifIndex,
                    statusBuf,
                    statusBuf.size,
                    1500
                )
                if (bytesRead == 1) {
                    portStatus = UsbPrinterPortStatus.fromByte(statusBuf[0])
                    UsbTraceLogger.log(TAG, "Port status: ${portStatus.toDisplayString()} (0x${String.format("%02X", statusBuf[0])})")
                }
            } catch (e: Exception) {
                UsbTraceLogger.logError(TAG, "Failed to read port status", e)
            }
        }

        val serial = try {
            if (permissionGranted) device.serialNumber else null
        } catch (_: SecurityException) {
            null
        }

        val versionStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.version
        } else null

        return UsbDeviceInfo(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturerName = device.manufacturerName,
            productName = device.productName,
            serialNumber = serial,
            version = versionStr,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            interfaceCount = device.interfaceCount,
            interfaces = interfaces,
            permissionGranted = permissionGranted,
            interfaceClaimed = interfaceClaimed,
            ieee1284 = ieee1284,
            portStatus = portStatus
        )
    }
}
