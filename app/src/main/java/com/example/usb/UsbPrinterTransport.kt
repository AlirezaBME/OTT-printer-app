package com.example.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbPrinterTransport(
    private val usbManager: UsbManager
) : UsbTransport {

    companion object {
        private const val TAG = "UsbPrinterTransport"
        const val DEFAULT_CHUNK_SIZE = 16384 // 16 KB safe chunk size
        const val DEFAULT_TIMEOUT_MS = 15000 // 15s timeout
    }

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var bulkOutEndpoint: UsbEndpoint? = null
    private var bulkInEndpoint: UsbEndpoint? = null
    private var currentDevice: UsbDevice? = null

    override fun isConnected(): Boolean {
        return connection != null && claimedInterface != null && bulkOutEndpoint != null
    }

    override fun open(device: UsbDevice): Result<Unit> {
        return try {
            close()
            currentDevice = device
            UsbTraceLogger.log(TAG, "Opening connection to ${device.deviceName} (VID: 0x${String.format("%04X", device.vendorId)}, PID: 0x${String.format("%04X", device.productId)})")
            
            val conn = usbManager.openDevice(device)
                ?: return Result.failure(IllegalStateException("UsbManager.openDevice returned null. Permission might be missing or device busy."))
            connection = conn
            UsbTraceLogger.log(TAG, "UsbDeviceConnection established successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Exception in openDevice", e)
            Result.failure(e)
        }
    }

    override fun claimInterface(interfaceIndex: Int): Result<Unit> {
        val device = currentDevice
            ?: return Result.failure(IllegalStateException("No device opened."))
        val conn = connection
            ?: return Result.failure(IllegalStateException("No connection established."))

        return try {
            var targetIf: UsbInterface? = null
            // Try to find the interface by id or by index
            for (i in 0 until device.interfaceCount) {
                val uif = device.getInterface(i)
                if (uif.id == interfaceIndex) {
                    targetIf = uif
                    break
                }
            }
            if (targetIf == null && interfaceIndex < device.interfaceCount) {
                targetIf = device.getInterface(interfaceIndex)
            }
            if (targetIf == null) {
                // Fallback: locate printer class interface
                for (i in 0 until device.interfaceCount) {
                    val uif = device.getInterface(i)
                    if (uif.interfaceClass == 7) {
                        targetIf = uif
                        break
                    }
                }
            }
            if (targetIf == null) {
                targetIf = device.getInterface(0)
            }

            UsbTraceLogger.log(TAG, "Claiming USB interface #${targetIf.id} (Class: ${targetIf.interfaceClass}, Subclass: ${targetIf.interfaceSubclass})")
            val claimed = conn.claimInterface(targetIf, true)
            if (!claimed) {
                UsbTraceLogger.log(TAG, "Failed to claim interface #${targetIf.id}", isError = true)
                return Result.failure(IllegalStateException("Could not claim USB interface #${targetIf.id}"))
            }
            claimedInterface = targetIf

            // Discover bulk endpoints
            bulkOutEndpoint = null
            bulkInEndpoint = null
            for (e in 0 until targetIf.endpointCount) {
                val ep = targetIf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOutEndpoint == null) {
                        bulkOutEndpoint = ep
                        UsbTraceLogger.log(TAG, "Found bulk OUT endpoint: addr=0x${String.format("%02X", ep.address)}, maxPacket=${ep.maxPacketSize}")
                    } else if (ep.direction == UsbConstants.USB_DIR_IN && bulkInEndpoint == null) {
                        bulkInEndpoint = ep
                        UsbTraceLogger.log(TAG, "Found bulk IN endpoint: addr=0x${String.format("%02X", ep.address)}, maxPacket=${ep.maxPacketSize}")
                    }
                }
            }

            if (bulkOutEndpoint == null) {
                UsbTraceLogger.log(TAG, "No bulk OUT endpoint found on interface #${targetIf.id}", isError = true)
                return Result.failure(IllegalStateException("No bulk OUT endpoint on interface #${targetIf.id}"))
            }

            UsbTraceLogger.log(TAG, "Interface #${targetIf.id} claimed and bulk endpoints configured successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Exception in claimInterface", e)
            Result.failure(e)
        }
    }

    override fun releaseInterface() {
        try {
            val uif = claimedInterface
            val conn = connection
            if (conn != null && uif != null) {
                UsbTraceLogger.log(TAG, "Releasing interface #${uif.id}")
                conn.releaseInterface(uif)
            }
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Error in releaseInterface", e)
        } finally {
            claimedInterface = null
            bulkOutEndpoint = null
            bulkInEndpoint = null
        }
    }

    override fun close() {
        releaseInterface()
        try {
            connection?.close()
            UsbTraceLogger.log(TAG, "Closed USB device connection.")
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Error closing connection", e)
        } finally {
            connection = null
            currentDevice = null
        }
    }

    override suspend fun writeBulk(
        data: ByteArray,
        timeoutMs: Int,
        chunkSize: Int,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        val conn = connection
            ?: return@withContext Result.failure(IllegalStateException("No USB connection"))
        val epOut = bulkOutEndpoint
            ?: return@withContext Result.failure(IllegalStateException("No bulk OUT endpoint"))

        val totalBytes = data.size.toLong()
        var bytesWrittenTotal = 0L
        var offset = 0

        UsbTraceLogger.log(TAG, "Starting bulk write: $totalBytes bytes to ep 0x${String.format("%02X", epOut.address)} in $chunkSize byte chunks")
        val startTime = System.currentTimeMillis()

        while (offset < data.size) {
            val chunkLength = (data.size - offset).coerceAtMost(chunkSize)
            val chunkBuffer = ByteArray(chunkLength)
            System.arraycopy(data, offset, chunkBuffer, 0, chunkLength)

            val written = conn.bulkTransfer(epOut, chunkBuffer, chunkLength, timeoutMs)
            if (written < 0) {
                val err = "USB bulk write failed at offset $offset with error code $written"
                UsbTraceLogger.log(TAG, err, isError = true)
                return@withContext Result.failure(IllegalStateException(err))
            }
            if (written < chunkLength) {
                val err = "USB short write: sent $written of requested $chunkLength bytes at offset $offset"
                UsbTraceLogger.log(TAG, err, isError = true)
                return@withContext Result.failure(IllegalStateException(err))
            }

            offset += written
            bytesWrittenTotal += written
            onProgress(bytesWrittenTotal, totalBytes)
        }

        val duration = System.currentTimeMillis() - startTime
        val speedKBs = if (duration > 0) (bytesWrittenTotal / duration) else 0
        UsbTraceLogger.log(TAG, "Bulk write complete: $bytesWrittenTotal bytes in ${duration}ms (~$speedKBs KB/s)")
        Result.success(bytesWrittenTotal)
    }

    override suspend fun readBulk(
        buffer: ByteArray,
        timeoutMs: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        val conn = connection
            ?: return@withContext Result.failure(IllegalStateException("No USB connection"))
        val epIn = bulkInEndpoint
            ?: return@withContext Result.failure(IllegalStateException("No bulk IN endpoint"))

        val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, timeoutMs)
        if (bytesRead < 0) {
            return@withContext Result.failure(IllegalStateException("Bulk read failed with code $bytesRead"))
        }
        UsbTraceLogger.log(TAG, "Bulk read: $bytesRead bytes received from ep 0x${String.format("%02X", epIn.address)}")
        Result.success(bytesRead)
    }

    override suspend fun queryPortStatus(interfaceIndex: Int): Result<UsbPrinterPortStatus> = withContext(Dispatchers.IO) {
        val conn = connection
            ?: return@withContext Result.failure(IllegalStateException("No USB connection"))
        val buf = ByteArray(1)
        val read = conn.controlTransfer(0xA1, 1, 0, interfaceIndex, buf, buf.size, 2000)
        if (read == 1) {
            val status = UsbPrinterPortStatus.fromByte(buf[0])
            Result.success(status)
        } else {
            Result.failure(IllegalStateException("GET_PORT_STATUS returned $read bytes"))
        }
    }

    override suspend fun queryIeee1284DeviceId(interfaceIndex: Int): Result<Ieee1284DeviceId> = withContext(Dispatchers.IO) {
        val conn = connection
            ?: return@withContext Result.failure(IllegalStateException("No USB connection"))
        val buf = ByteArray(1024)
        val read = conn.controlTransfer(0xA1, 0, 0, interfaceIndex, buf, buf.size, 2000)
        if (read > 0) {
            val devId = Ieee1284Parser.parse(buf.copyOf(read))
            Result.success(devId)
        } else {
            Result.failure(IllegalStateException("GET_DEVICE_ID returned $read bytes"))
        }
    }

    override suspend fun softReset(interfaceIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connection
            ?: return@withContext Result.failure(IllegalStateException("No USB connection"))
        val res = conn.controlTransfer(0x21, 2, 0, interfaceIndex, null, 0, 2000)
        if (res >= 0) {
            UsbTraceLogger.log(TAG, "SOFT_RESET executed successfully.")
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("SOFT_RESET failed with code $res"))
        }
    }
}
