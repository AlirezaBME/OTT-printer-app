package com.example.usb

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.IOException
import java.io.PushbackInputStream
import com.example.protocol.*
import com.example.diagnostics.ProtocolCapture

/** Canon USB MLP channel layer, below CPCA. Derived from libcomm_usbmlportr v5.00.
 * Every packet consumes one channel credit; a reply restores it. Keep one packet
 * outstanding, as Canon's library does with its initial credit of one. USB writes
 * are never retried after an ambiguous failure. This does not confirm paper output.
 */
class CanonMlpSession(private val transport: UsbTransport, private val replyTimeoutMs: Int = 10000, private val capture: ProtocolCapture? = null, private val maxPacketSize: Int = 512, private val discoverServices: Boolean = true, private val nanoTime: () -> Long = System::nanoTime) {
    private val cpca = CanonCpcaSession(capture)
    private var pending = ByteArray(0)
    private val incomingLimits = IntArray(4) { 64 }
    private var outgoingLimit = 0
    private var opened = false
    private var packets = 0L
    private var replies = 0L
    private var flowReplies = 0L
    private var wireCredits = 0L
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
            val (outSize,inSize) = CanonMlpProtocol.openSizes(body,channel)
            incomingLimits[channel] = inSize
            capture?.recordOpenedChannel(CanonMlpProtocol.channels[channel - 1], outSize, inSize)
            if (channel == 1) outgoingLimit = outSize
            UsbTraceLogger.log(tag, "MLP channel $channel/${channel * 16} open: OUT=$outSize IN=$inSize", hexDump = UsbTraceLogger.bytesToHex(reply))
        }
        if(discoverServices) {
            for(channel in CanonMlpProtocol.channels) {
                send(frame(0,CanonMlpProtocol.serviceRequest(channel.printerSocket)))
                val response=receiveOn(0,"service lookup ${channel.printerSocket}")
                val service=CanonMlpProtocol.serviceName(response.copyOfRange(6,response.size),channel.printerSocket)
                capture?.recordServiceName(channel.hostSocket, service)
                capture?.event("channelMapping",mapOf("hostSocket" to channel.hostSocket,"printerSocket" to channel.printerSocket,"service" to service,"nativeRole" to channel.nativeRole,"roleEvidence" to "VERIFIED_NATIVE_DRIVER","devicePrintAcceptance" to "UNVERIFIED"))
                UsbTraceLogger.log(tag,"MLP channel ${channel.hostSocket} service=$service host=${channel.hostSocket} printer=${channel.printerSocket} purpose=${channel.nativeRole} (native driver mapping; job acceptance unknown)")
            }
        }
        opened = true
    }

    suspend fun transmit(input: InputStream, total: Long, onProgress: (Long) -> Unit) {
        check(opened) { "Canon USB channel is not open." }
        val stream=PushbackInputStream(input,1)
        val buffer = ByteArray((outgoingLimit - 6).coerceAtMost(16378))
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        var sent = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            var size = stream.read(buffer,0,CanonMlpProtocol.payloadCount((total-sent).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),outgoingLimit,maxPacketSize))
            if (size < 0) break
            if (size == 0) continue
            if(size>1 && size%maxPacketSize==0) { stream.unread(buffer[size-1].toInt() and 255);size-- }
            digest.update(buffer,0,size)
            send(frame(1, buffer.copyOf(size)))
            packets++
            // USB accepting bytes is insufficient: require the printer's MLP reply.
            val reply = receiveOn(1, "acknowledging data packet $packets ($sent/$total bytes)")
            replies++
            if(reply.size==6) flowReplies++
            wireCredits+=(reply[4].toInt() and 255)
            lastReply = UsbTraceLogger.bytesToHex(reply, 32)
            if (packets == 1L || packets % 64L == 0L) {
                UsbTraceLogger.log(tag, "MLP flow-control replies: packets=$packets bytes=${sent + size}/$total", hexDump = lastReply)
            }
            sent += size
            capture?.summary?.put("bytesTransmitted", sent)
            onProgress(sent)
        }
        if (sent != total) throw IOException("Canon spool changed while sending: $sent/$total bytes.")
        capture?.summary?.put("transmittedJobSha256",digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        capture?.summary?.put("dataPackets",packets)?.put("flowControlReplies",flowReplies)?.put("nativeFlowTokens",replies)?.put("wireCreditFieldTotal",wireCredits)
        UsbTraceLogger.log(tag, "Transport complete — printer acceptance UNKNOWN: $sent bytes, $packets packets, $replies replies. Last reply: $lastReply")
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
        capture?.event("mlpOut",mapOf("direction" to "OUT","channel" to (bytes[0].toInt() and 255),"printerSocket" to (bytes[1].toInt() and 255),
            "credit" to (bytes[4].toInt() and 255), "flags" to (bytes[5].toInt() and 255),
            "headerHex" to UsbTraceLogger.bytesToHex(bytes.copyOfRange(0,6)),"payloadLength" to (bytes.size-6),"frameLength" to bytes.size,
            "command" to (if(bytes[0]==0.toByte()) bytes.getOrNull(6)?.toInt()?.and(255) else null),
            "usbWriteCount" to (if(bytes.contentEquals(INIT)) 1 else if(bytes.size>6) 2 else 1),
            "usbWriteSizes" to (if(bytes.contentEquals(INIT)) listOf(8) else if(bytes.size>6) listOf(6, bytes.size-6) else listOf(6)),"zlpEmitted" to false,
            "finalTransferAligned" to ((if(bytes.contentEquals(INIT)) 8 else bytes.size-6)%maxPacketSize==0)))
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
        val deadline = nanoTime() + replyTimeoutMs * 1_000_000L
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

    private suspend fun nextFrame(stage: String, deadline: Long = nanoTime() + replyTimeoutMs * 1_000_000L): ByteArray {
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
                    val parsed=CanonMlpProtocol.parse(packet,incomingLimits[channel])
                    capture?.event("mlpIn",mapOf("direction" to "IN","channel" to parsed.channel,"printerSocket" to parsed.printerSocket,"credit" to parsed.credit,
                        "flags" to parsed.flags,"headerHex" to UsbTraceLogger.bytesToHex(packet.copyOfRange(0,6)),"responseLength" to packet.size,"frameLength" to packet.size,
                        "payloadLength" to parsed.payload.size,"responseHex" to UsbTraceLogger.bytesToHex(packet,64),
                        "kind" to if(parsed.isFlowControl) "FLOW_CONTROL" else "PAYLOAD","printerAcceptance" to "UNKNOWN"))
                    if(channel!=0) cpca.observe(parsed)
                    return packet
                }
            }
            val remainingMs = ((deadline - nanoTime()) / 1_000_000L).coerceAtMost(replyTimeoutMs.toLong()).toInt()
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

    /** Poll only documented USB port status and passive channel replies. A basic
     * READY byte or MLP credit never establishes CPCA job acceptance/completion.
     */
    suspend fun observePrinter(windowMs: Long, interfaceId: Int, onSample: (Long,String)->Unit) {
        val deadline=nanoTime()+windowMs*1_000_000L
        require(windowMs in 0..60000)
        var samples=0
        while(nanoTime()<deadline) {
            currentCoroutineContext().ensureActive()
            if(++samples>128) throw CanonProtocolException("OBSERVING","RESPONSE_LIMIT","Observation sample limit exceeded before the deadline")
            val remaining=((deadline-nanoTime())/1_000_000).coerceAtLeast(1)
            onSample(remaining,"Transport complete. Waiting for printer evidence; CPCA job state UNKNOWN.")
            val status=transport.queryPortStatus(interfaceId).getOrThrow()
            capture?.event("portStatus",mapOf("rawByte" to status.rawByte,"ready" to status.isReady,"printerJobState" to "UNKNOWN"))
            if(!status.isReady) throw CanonProtocolException("OBSERVING","PRINTER_PORT_ERROR",status.toDisplayString())
            if(pending.isEmpty()) {
                val buffer=ByteArray(16384)
                val readRemaining=((deadline-nanoTime())/1_000_000).toInt()
                if (readRemaining <= 0) break
                val read=transport.readBulk(buffer,minOf(1000,readRemaining))
                val count=read.getOrNull() ?: 0
                if(count>0) pending+=buffer.copyOf(count)
                else {
                    capture?.event("observationIdle",mapOf("error" to read.exceptionOrNull()?.message,"meaning" to "No job evidence; timeout or I/O remains unclassified until next port query"))
                    kotlinx.coroutines.yield()
                    continue
                }
            }
            // Use existing bounded parser and passive CPCA observer. Never infer
            // acceptance from payload presence without validated command semantics.
            nextFrame("observing printer",deadline)
        }
        capture?.summary?.put("cpcaResponses",cpca.responseCount)?.put("observationOutcome","CONFIRMATION_TIMEOUT")
        capture?.event("state",mapOf("state" to "CONFIRMATION_TIMEOUT","printerAcceptance" to "UNKNOWN","physicalPrintConfirmed" to false))
        UsbTraceLogger.log(tag,"Observation ended: CPCA responses=${cpca.responseCount}; printer acceptance, processing and physical completion UNKNOWN.")
    }

    private fun requireReply(packet: ByteArray, channel: Int, body: ByteArray, stage: String) {
        if (packet[0].u() != channel || !packet.copyOfRange(6, packet.size).contentEquals(body)) {
            fail("Unexpected Canon acknowledgement during $stage", packet)
        }
    }
    private fun fail(message: String, bytes: ByteArray): Nothing {
        val hex = UsbTraceLogger.bytesToHex(bytes, 48)
        UsbTraceLogger.log(tag, message, isError = true, hexDump = hex)
        val code = if (message.contains("timeout", ignoreCase = true) || message.startsWith("No Canon")) "REPLY_TIMEOUT" else "INVALID_RESPONSE"
        throw CanonProtocolException("MLP", code, "$message. Reply: ${hex.ifEmpty { "none" }}")
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
