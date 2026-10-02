package com.example.usb

import android.hardware.usb.UsbDevice

interface UsbTransport {
    fun isConnected(): Boolean
    
    fun open(device: UsbDevice): Result<Unit>
    
    fun claimInterface(interfaceIndex: Int): Result<Unit>
    
    fun releaseInterface()
    
    fun close()
    
    /**
     * Chunked bulk transfer with progress callback and verification.
     */
    suspend fun writeBulk(
        data: ByteArray,
        timeoutMs: Int = 10000,
        chunkSize: Int = 16384,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Long>
    
    suspend fun readBulk(
        buffer: ByteArray,
        timeoutMs: Int = 5000
    ): Result<Int>
    
    suspend fun queryPortStatus(interfaceIndex: Int = 0): Result<UsbPrinterPortStatus>
    
    suspend fun queryIeee1284DeviceId(interfaceIndex: Int = 0): Result<Ieee1284DeviceId>
    
    suspend fun softReset(interfaceIndex: Int = 0): Result<Unit>
}
