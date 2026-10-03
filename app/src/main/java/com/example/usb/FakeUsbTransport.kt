package com.example.usb

import android.hardware.usb.UsbDevice
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay

class FakeUsbTransport(
    var simulatedPortStatus: UsbPrinterPortStatus = UsbPrinterPortStatus(
        rawByte = 0x18, // Bit 4 (Selected=1), Bit 3 (NotError=1), Bit 5 (PaperEmpty=0)
        paperEmpty = false,
        selected = true,
        notError = true
    ),
    var simulateFailAtByteOffset: Long? = null,
    var simulateTimeout: Boolean = false
) : UsbTransport {

    private var connected = false
    private var interfaceClaimed = false
    private val outputStream = ByteArrayOutputStream()

    override fun isConnected(): Boolean = connected && interfaceClaimed

    fun openMock(deviceName: String = "Canon_LBP6030_Mock"): Result<Unit> {
        connected = true
        UsbTraceLogger.log("FakeUsbTransport", "Mock opened device: $deviceName")
        return Result.success(Unit)
    }

    override fun open(device: UsbDevice): Result<Unit> {
        return openMock(device.deviceName)
    }

    override fun claimInterface(interfaceIndex: Int): Result<Unit> {
        if (!connected) return Result.failure(IllegalStateException("Device not opened"))
        interfaceClaimed = true
        UsbTraceLogger.log("FakeUsbTransport", "Mock claimed interface #$interfaceIndex")
        return Result.success(Unit)
    }

    override fun releaseInterface() {
        interfaceClaimed = false
        UsbTraceLogger.log("FakeUsbTransport", "Mock released interface")
    }

    override fun close() {
        releaseInterface()
        connected = false
        UsbTraceLogger.log("FakeUsbTransport", "Mock closed connection")
    }

    override suspend fun writeBulk(
        data: ByteArray,
        timeoutMs: Int,
        chunkSize: Int,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<Long> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("FakeUsbTransport not connected"))
        }
        if (simulateTimeout) {
            delay(100)
            return Result.failure(IllegalStateException("Simulated USB transfer timeout"))
        }

        val total = data.size.toLong()
        var written = 0L
        var offset = 0
        while (offset < data.size) {
            val chunkLen = (data.size - offset).coerceAtMost(chunkSize)
            val failOffset = simulateFailAtByteOffset
            if (failOffset != null && (written + chunkLen) > failOffset) {
                return Result.failure(IllegalStateException("Simulated write failure at offset $failOffset"))
            }
            outputStream.write(data, offset, chunkLen)
            offset += chunkLen
            written += chunkLen
            onProgress(written, total)
        }
        UsbTraceLogger.log("FakeUsbTransport", "Mock wrote $written bytes successfully")
        return Result.success(written)
    }

    override suspend fun readBulk(buffer: ByteArray, timeoutMs: Int): Result<Int> {
        return Result.success(0)
    }

    override suspend fun queryPortStatus(interfaceIndex: Int): Result<UsbPrinterPortStatus> {
        return Result.success(simulatedPortStatus)
    }

    override suspend fun queryIeee1284DeviceId(interfaceIndex: Int): Result<Ieee1284DeviceId> {
        val mockRaw = "MFG:Canon;CMD:LIPSLX,CPCA;CID:CA_UFRIILT_OIP;MDL:LBP6030/6040/6018L;CLS:PRINTER;DES:Canon LBP6030/6040/6018L;"
        return Result.success(Ieee1284Parser.parseString(mockRaw))
    }

    override suspend fun softReset(interfaceIndex: Int): Result<Unit> {
        outputStream.reset()
        return Result.success(Unit)
    }

    fun getCapturedBytes(): ByteArray = outputStream.toByteArray()

    fun clearCapturedBytes() {
        outputStream.reset()
    }
}
