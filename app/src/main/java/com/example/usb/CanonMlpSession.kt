package com.example.usb

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.IOException

/** Canon USB MLP channel layer, below CPCA. Derived from libcomm_usbmlportr v5.00.
 * Every packet consumes one channel credit; a reply restores it. Keep one packet
 * outstanding, as Canon's library does with its initial credit of one. USB writes
 * are never retried after an ambiguous failure.
 *
 * MLP replies prove transport flow control only. They do not prove CPCA/NCAP job
 * acceptance or physical paper output. After the final data packet rc6 can keep the
 * channels open briefly and record asynchronous traffic before closing the session.
 */
class CanonMlpSession(
    private val transport: UsbTransport,
    private val replyTimeoutMs: Int = 10000,
    private val postJobObservationMs: Int = 0
) {
    private var pending = ByteArray(0)
    private val incomingLimits = IntArray(4) { 64 }
    private var outgoingLimit = 0
    private var opened = false
    private var packets = 0L
    private var replies = 0L
    private var asynchronousFrames = 0L
    private var asynchronousPayloadBytes = 0L
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
        val maxPayload = (outgoingLimit - 6).coerceAtMost(16378)
        check(maxPayload > 0) { "Canon negotiated an invalid MLP payload size." }

        var sent = 0L
        var cpcaPackets = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val cpca = readCpcaPacket(input) ?: break
            cpcaPackets++

            // Canon's cnpkmodulencapr calls Info_commJobWrite once per complete
            // CPCA packet. Preserve that boundary on MLP: never pack the tail of
            // one CPCA packet together with the head of the next. Large CPCA
            // packets are fragmented only as required by the negotiated MLP size.
            var offset = 0
            while (offset < cpca.size) {
                currentCoroutineContext().ensureActive()
                val remaining = cpca.size - offset
                val size = nativeFragmentSize(remaining, maxPayload)
                send(frame(1, cpca.copyOfRange(offset, offset + size)))
                packets++

                // This is MLP flow control, not a printer-level "page accepted" response.
                val reply = receiveOn(1, "restoring flow credit for MLP packet $packets / CPCA packet $cpcaPackets")
                replies++
                lastReply = UsbTraceLogger.bytesToHex(reply, 32)
                if (reply.size > 6) {
                    noteApplicationFrame(reply, "channel-1 reply while restoring flow credit")
                }

                offset += size
                sent += size
                if (packets == 1L || packets % 64L == 0L) {
                    UsbTraceLogger.log(
                        tag,
                        "MLP flow credit received: mlpPackets=$packets cpcaPackets=$cpcaPackets bytes=$sent/$total",
                        hexDump = lastReply
                    )
                }
                onProgress(sent)
            }
        }

        if (sent != total) throw IOException("Canon spool changed while sending: $sent/$total bytes.")
        UsbTraceLogger.log(
            tag,
            "MLP transport transfer complete: $sent bytes, $cpcaPackets CPCA packets, $packets MLP packets, " +
                "$replies flow replies. CPCA/NCAP acceptance and physical output remain unconfirmed. Last reply: $lastReply"
        )
    }

    private fun readCpcaPacket(input: InputStream): ByteArray? {
        val header = ByteArray(CPCA_HEADER_BYTES)
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            if (count < 0) {
                if (offset == 0) return null
                throw IOException("Truncated Canon CPCA header: $offset/${header.size} bytes.")
            }
            if (count == 0) continue
            offset += count
        }

        if (!header.copyOfRange(0, 4).contentEquals(CPCA_MAGIC)) {
            fail("Invalid Canon CPCA packet boundary", header)
        }
        val payloadBytes = be16(header, 8)
        val packet = ByteArray(CPCA_HEADER_BYTES + payloadBytes)
        header.copyInto(packet)
        offset = CPCA_HEADER_BYTES
        while (offset < packet.size) {
            val count = input.read(packet, offset, packet.size - offset)
            if (count < 0) throw IOException("Truncated Canon CPCA packet: $offset/${packet.size} bytes.")
            if (count == 0) continue
            offset += count
        }
        return packet
    }

    suspend fun finish() {
        check(opened)
        observePostJobTraffic()

        // Canon jobEnd closes all three channels in ascending order.
        for (channel in 1..3) {
            send(frame(0, byteArrayOf(2, channel.toByte(), (channel * 16).toByte())))
            val reply = receiveOn(0, "closing channel $channel")
            requireReply(reply, 0, byteArrayOf(0x82.toByte(), 0, channel.toByte(), (channel * 16).toByte()), "closing channel $channel")
            UsbTraceLogger.log(tag, "MLP channel $channel close acknowledged", hexDump = UsbTraceLogger.bytesToHex(reply))
        }
        opened = false
        UsbTraceLogger.log(
            tag,
            "Canon MLP session complete: packets=$packets flowReplies=$replies " +
                "asyncFrames=$asynchronousFrames asyncPayloadBytes=$asynchronousPayloadBytes. Physical output unconfirmed."
        )
    }

    private suspend fun observePostJobTraffic() {
        if (postJobObservationMs <= 0) return
        val deadline = System.nanoTime() + postJobObservationMs * 1_000_000L
        var observed = 0L
        var payloadFrames = 0L

        UsbTraceLogger.log(
            tag,
            "Observing Canon channels for ${postJobObservationMs}ms after the final CPCA byte before MLP close"
        )

        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val buffered = takePendingFrame("post-job observation")
            if (buffered != null) {
                observed++
                if (buffered.size > 6) payloadFrames++
                noteApplicationFrame(buffered, "post-job observation")
                continue
            }

            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
            if (remainingMs <= 0) break
            val timeout = remainingMs.coerceAtMost(150).coerceAtLeast(1)
            val buffer = ByteArray(16384)
            val readResult = transport.readBulk(buffer, timeout)
            val count = readResult.getOrNull()
            if (count == null || count <= 0) {
                // Android bulkTransfer reports timeout and several USB errors as -1.
                // During this bounded observation window a quiet timeout is expected.
                delay(10)
                continue
            }
            if (count > buffer.size) fail("Invalid Canon post-job read size", pending)
            if (pending.size + count > 81919) fail("Canon reply buffer exceeded its limit during post-job observation", pending)
            pending += buffer.copyOf(count)
        }

        UsbTraceLogger.log(
            tag,
            "Post-job Canon observation complete: frames=$observed payloadFrames=$payloadFrames " +
                "totalAsyncFrames=$asynchronousFrames totalAsyncPayloadBytes=$asynchronousPayloadBytes"
        )
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
            noteApplicationFrame(response, "asynchronous frame during $stage")
        }
        throw IOException("Too many unrelated Canon replies during $stage.")
    }

    private suspend fun nextFrame(stage: String, deadline: Long = System.nanoTime() + replyTimeoutMs * 1_000_000L): ByteArray {
        while (true) {
            currentCoroutineContext().ensureActive()
            takePendingFrame(stage)?.let { return it }

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

    private fun takePendingFrame(stage: String): ByteArray? {
        if (pending.size < 6) return null
        val size = be16(pending, 2)
        val channel = pending[0].u()
        if (channel !in 0..3 || pending[1].u() != channel * 16 || size < 6 || size > incomingLimits[channel]) {
            fail("Malformed Canon MLP reply during $stage", pending)
        }
        if (pending.size < size) return null
        val packet = pending.copyOfRange(0, size)
        pending = pending.copyOfRange(size, pending.size)
        return packet
    }

    private fun noteApplicationFrame(packet: ByteArray, stage: String) {
        asynchronousFrames++
        val payload = (packet.size - 6).coerceAtLeast(0)
        asynchronousPayloadBytes += payload
        val channel = packet.getOrNull(0)?.u() ?: -1
        val peer = packet.getOrNull(1)?.u() ?: -1
        val credit = packet.getOrNull(4)?.u() ?: -1
        val flags = packet.getOrNull(5)?.u() ?: -1
        UsbTraceLogger.log(
            tag,
            "MLP frame observed during $stage: channel=$channel/$peer bytes=${packet.size} " +
                "payload=$payload credit=$credit flags=0x${flags.toString(16).padStart(2, '0')}",
            hexDump = UsbTraceLogger.bytesToHex(packet, 96)
        )
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
        private const val CPCA_HEADER_BYTES = 20
        private const val MLP_HEADER_BYTES = 6
        private val CPCA_MAGIC = byteArrayOf(0xcd.toByte(), 0xca.toByte(), 0x10, 0)

        /**
         * Mirrors Canon v5.00 SendSub2 segmentation observed with an 8192-byte
         * negotiated packet size. Full-sized frames are used while more than two
         * payload frames remain. The native library balances the final two payloads
         * with the six-byte MLP header accounted for in the first split.
         */
        internal fun nativeFragmentSize(remaining: Int, maxPayload: Int): Int {
            require(remaining > 0 && maxPayload > MLP_HEADER_BYTES)
            if (remaining <= maxPayload) return remaining
            if (remaining <= maxPayload * 2) {
                return ((remaining - MLP_HEADER_BYTES) / 2).coerceAtLeast(1)
            }
            return maxPayload
        }

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
