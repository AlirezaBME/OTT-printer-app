package com.example.usb

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.IOException

/** Canon USB MLP channel layer, below CPCA. Derived from libcomm_usbmlportr v5.00.
 * Every packet consumes one channel credit; a reply restores it. Keep one packet
 * outstanding, as Canon's library does with its initial credit of one. USB writes
 * are never retried after an ambiguous failure. This does not confirm paper output.
 */
class CanonMlpSession(private val transport: UsbTransport, private val replyTimeoutMs: Int = 10000) {
    private var pending = ByteArray(0)
    private val incomingLimits = IntArray(4) { 64 }
    private var outgoingLimit = 0
    private var opened = false
    private var packets = 0L
    private var replies = 0L
    private var lastReply = "none"
    private val tag = "CanonMlpSession"

    suspend fun open() {
        check(!opened) { "Canon USB channel is already open." }
        send(INIT)
        val response = nextFrame("initialization")
        requireReply(response, 0, byteArrayOf(0x80.toByte(), 0, 8), "initialization")
        UsbTraceLogger.log(tag, "MLP initialization acknowledged", hexDump = UsbTraceLogger.bytesToHex(response))
        for (channel in 1..3) {
            send(frame(0, openRequest(channel)))
            val reply = receiveOn(0, "opening channel $channel")
            val body = reply.copyOfRange(6, reply.size)
            if (body.size != 12 || body[0] != 0x81.toByte() || body[1] != 0.toByte() ||
                body[2].u() != channel || body[3].u() != channel * 16) {
                fail("Canon rejected channel $channel", reply)
            }
            val outSize = be16(body, 4)
            val inSize = be16(body, 6)
            if (outSize <= 6 || inSize <= 6) fail("Invalid Canon channel packet sizes", reply)
            incomingLimits[channel] = inSize
            if (channel == 1) outgoingLimit = outSize
            UsbTraceLogger.log(tag, "MLP channel $channel/${channel * 16} open: OUT=$outSize IN=$inSize", hexDump = UsbTraceLogger.bytesToHex(reply))
        }
        opened = true
    }

    suspend fun transmit(input: InputStream, total: Long, onProgress: (Long) -> Unit) {
        check(opened) { "Canon USB channel is not open." }
        val buffer = ByteArray((outgoingLimit - 6).coerceAtMost(16378))
        var sent = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) break
            if (size == 0) continue
            send(frame(1, buffer.copyOf(size)))
            packets++
            // USB accepting bytes is insufficient: require the printer's MLP reply.
            val reply = receiveOn(1, "acknowledging data packet $packets ($sent/$total bytes)")
            replies++
            lastReply = UsbTraceLogger.bytesToHex(reply, 32)
            if (packets == 1L || packets % 64L == 0L) {
                UsbTraceLogger.log(tag, "MLP data acknowledged: packets=$packets bytes=${sent + size}/$total", hexDump = lastReply)
            }
            sent += size
            onProgress(sent)
        }
        if (sent != total) throw IOException("Canon spool changed while sending: $sent/$total bytes.")
        UsbTraceLogger.log(tag, "MLP transfer acknowledged: $sent bytes, $packets packets, $replies replies. Last reply: $lastReply")
    }

    suspend fun finish() {
        check(opened)
        // Canon jobEnd closes all three channels in ascending order.
        for (channel in 1..3) {
            send(frame(0, byteArrayOf(2, channel.toByte(), (channel * 16).toByte())))
            val reply = receiveOn(0, "closing channel $channel")
            requireReply(reply, 0, byteArrayOf(0x82.toByte(), 0, channel.toByte(), (channel * 16).toByte()), "closing channel $channel")
            UsbTraceLogger.log(tag, "MLP channel $channel close acknowledged", hexDump = UsbTraceLogger.bytesToHex(reply))
        }
        opened = false
        UsbTraceLogger.log(tag, "Canon MLP session complete: packets=$packets replies=$replies. Physical output unconfirmed.")
    }

    private suspend fun send(bytes: ByteArray) {
        currentCoroutineContext().ensureActive()
        if (bytes.contentEquals(INIT)) {
            write(bytes)
        } else {
            // Canon SendSub2 makes two WritePort calls: a short six-byte header
            // transfer, then the payload. Preserve these observed USB boundaries.
            write(bytes.copyOfRange(0, 6))
            if (bytes.size > 6) write(bytes.copyOfRange(6, bytes.size))
        }
    }

    private suspend fun write(bytes: ByteArray) {
        currentCoroutineContext().ensureActive()
        val written = transport.writeBulk(bytes, timeoutMs = 15000, chunkSize = 16384).getOrThrow()
        if (written != bytes.size.toLong()) throw IOException("Incomplete Canon MLP write: $written/${bytes.size} bytes. Reconnect the printer before retrying.")
    }

    private suspend fun receiveOn(channel: Int, stage: String): ByteArray {
        val deadline = System.nanoTime() + replyTimeoutMs * 1_000_000L
        repeat(128) {
            val response = nextFrame(stage, deadline)
            val id = response[0].u()
            if (id == channel) return response
            // The official library drains asynchronous status from the other channels.
            if (response.size > 6) {
                UsbTraceLogger.log(tag, "MLP asynchronous reply on channel $id during $stage", hexDump = UsbTraceLogger.bytesToHex(response, 32))
            }
        }
        throw IOException("Too many unrelated Canon replies during $stage.")
    }

    private suspend fun nextFrame(stage: String, deadline: Long = System.nanoTime() + replyTimeoutMs * 1_000_000L): ByteArray {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (pending.size >= 6) {
                val size = be16(pending, 2)
                val channel = pending[0].u()
                if (channel !in 0..3 || pending[1].u() != channel * 16 || size < 6 || size > incomingLimits[channel]) {
                    fail("Malformed Canon MLP reply during $stage", pending)
                }
                if (pending.size >= size) {
                    val packet = pending.copyOfRange(0, size)
                    pending = pending.copyOfRange(size, pending.size)
                    return packet
                }
            }
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtMost(replyTimeoutMs.toLong()).toInt()
            if (remainingMs <= 0) fail("Canon did not acknowledge $stage before the timeout. Power-cycle the printer and reconnect OTG", pending)
            // Read whole USB packets. A six-byte bulk read can truncate a larger reply.
            val buffer = ByteArray(16384)
            val result = transport.readBulk(buffer, remainingMs)
            val count = result.getOrElse {
                throw IOException("Canon MLP reply failed during $stage: ${it.message}. Last reply: $lastReply", it)
            }
            if (count <= 0 || count > buffer.size) fail("No Canon MLP reply during $stage. Power-cycle the printer and reconnect OTG", pending)
            if (pending.size + count > 81919) fail("Canon reply buffer exceeded its limit during $stage", pending)
            pending += buffer.copyOf(count)
        }
    }

    private fun requireReply(packet: ByteArray, channel: Int, body: ByteArray, stage: String) {
        if (packet[0].u() != channel || !packet.copyOfRange(6, packet.size).contentEquals(body)) {
            fail("Unexpected Canon acknowledgement during $stage", packet)
        }
    }
    private fun fail(message: String, bytes: ByteArray): Nothing {
        val hex = UsbTraceLogger.bytesToHex(bytes, 48)
        UsbTraceLogger.log(tag, message, isError = true, hexDump = hex)
        throw IOException("$message. Reply: ${hex.ifEmpty { "none" }}")
    }

    companion object {
        val INIT: ByteArray get() = byteArrayOf(0, 0, 0, 8, 1, 0, 0, 8)
        fun openRequest(channel: Int): ByteArray {
            require(channel in 1..3)
            return byteArrayOf(1, channel.toByte(), (channel * 16).toByte(), -1, -1, -1, -1, -1, -1)
        }
        fun frame(channel: Int, payload: ByteArray): ByteArray {
            require(channel in 0..3 && payload.size <= 65529)
            val length = payload.size + 6
            return byteArrayOf(channel.toByte(), (channel * 16).toByte(), (length ushr 8).toByte(), length.toByte(), 1, 0) + payload
        }
        private fun Byte.u() = toInt() and 255
        private fun be16(bytes: ByteArray, at: Int) = (bytes[at].u() shl 8) or bytes[at + 1].u()
    }
}
