package com.example

import com.example.usb.FakeUsbTransport
import com.example.usb.UsbTransport
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/** Device-side fixture: parses USB wire packets and acknowledges one at a time.
 * The command/response bytes come from the official v5.00 transport oracle.
 */
class CanonMlpPeer(val raw: FakeUsbTransport = FakeUsbTransport(), val packetSize: Int = 16384) : UsbTransport by raw {
    val wire = mutableListOf<ByteArray>()
    val transfers = mutableListOf<ByteArray>()
    private var header: ByteArray? = null
    val cpca = ByteArrayOutputStream()
    val replies = ArrayDeque<Byte>()
    var readFragment = Int.MAX_VALUE
    var missingReplies = false
    var rejectOpen = false
    var corruptChannel = false
    var rejectClose = false
    var reads = 0
    var failWrite = false
    override suspend fun writeBulk(transfer: ByteArray, timeoutMs: Int, chunkSize: Int, onProgress: (Long,Long)->Unit): Result<Long> {
        if (failWrite) return Result.failure(java.io.IOException("Ambiguous USB write"))
        if (replies.isNotEmpty()) throw AssertionError("A second packet was sent before the previous reply was read")
        transfers.add(transfer.copyOf())
        val data = if (header != null) {
            (header!! + transfer).also { header = null }
        } else if (transfer.size == 6) {
            header = transfer.copyOf()
            return Result.success(6)
        } else {
            if (transfer.size != 8) throw AssertionError("Header and payload must be separate USB transfers")
            transfer
        }
        wire.add(data.copyOf())
        val response = when {
            data.contentEquals(byteArrayOf(0,0,0,8,1,0,0,8)) -> byteArrayOf(0,0,0,9,1,0,0x80.toByte(),0,8)
            data[0] == 0.toByte() && data[6] == 1.toByte() -> {
                if (!data.copyOfRange(9,15).all { it == (-1).toByte() }) throw AssertionError("Invalid channel open request")
                val channel = data[7]
                byteArrayOf(0,0,0,18,1,0,0x81.toByte(),if(rejectOpen) 1 else 0,channel,data[8],(packetSize ushr 8).toByte(),packetSize.toByte(),0x40,0,-1,-1,0,1)
            }
            data[0] == 0.toByte() && data[6] == 2.toByte() -> byteArrayOf(0,0,0,10,1,0,0x82.toByte(),if(rejectClose) 1 else 0,data[7],data[8])
            data[0] == 0.toByte() && data[6] == 0x0a.toByte() -> {
                val name="CANON_SOCKET_${data[7].toInt() and 255}".toByteArray()
                byteArrayOf(0,0,0,(9+name.size).toByte(),1,0,0x8a.toByte(),0,data[7])+name
            }
            data[0] == 1.toByte() && data[1] == 16.toByte() -> {
                if (data.size > packetSize) throw AssertionError("Exceeded negotiated packet size")
                cpca.write(data,6,data.size-6)
                // Native RecvSub restores one credit for this packet, even with byte 4 = 0.
                byteArrayOf(1,16,0,6,0,0)
            }
            else -> throw AssertionError("Unexpected Canon packet")
        }
        if (((data[2].toInt() and 255) shl 8 or (data[3].toInt() and 255)) != data.size) throw AssertionError("Bad MLP packet length")
        if (!missingReplies) {
            if (corruptChannel) response[1]=0x7f
            response.forEach { replies.add(it) }
        }
        onProgress(transfer.size.toLong(),transfer.size.toLong())
        return Result.success(transfer.size.toLong())
    }
    override suspend fun readBulk(buffer: ByteArray, timeoutMs: Int): Result<Int> {
        reads++
        val count=minOf(buffer.size, readFragment, replies.size)
        repeat(count) { buffer[it]=replies.removeFirst() }
        return Result.success(count)
    }
    override suspend fun softReset(interfaceIndex: Int): Result<Unit> {
        replies.clear()
        return raw.softReset(interfaceIndex)
    }
}
