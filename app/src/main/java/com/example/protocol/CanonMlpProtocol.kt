package com.example.protocol

import java.io.IOException

/** v5.00 native library evidence, not a claim of device acceptance. */
data class CanonChannel(val hostSocket: Int, val printerSocket: Int, val nativeRole: String, val service: String = "UNKNOWN")
data class MlpFrame(val channel: Int, val printerSocket: Int, val credit: Int, val flags: Int, val payload: ByteArray) {
    val isFlowControl get() = payload.isEmpty()
}
class CanonProtocolException(val stage: String, val code: String, message: String) : IOException("$code [$stage]: $message")
object CanonMlpProtocol {
    val channels = listOf(CanonChannel(1,16,"PRINT_STREAM"), CanonChannel(2,32,"CPCA_CONTEXT_1"), CanonChannel(3,48,"CPCA_CONTEXT_2"))
    fun parse(bytes: ByteArray, maximum: Int = 65535): MlpFrame {
        if (bytes.size < 6) throw CanonProtocolException("MLP", "INVALID_FRAME", "Header is incomplete")
        val channel=bytes[0].u(); val socket=bytes[1].u(); val length=be16(bytes,2)
        if (channel !in 0..3 || socket!=channel*16 || length!=bytes.size || length>maximum) {
            throw CanonProtocolException("MLP", "INVALID_FRAME", "Socket or length mismatch")
        }
        return MlpFrame(channel,socket,bytes[4].u(),bytes[5].u(),bytes.copyOfRange(6,bytes.size))
    }
    fun openSizes(body: ByteArray, channel: Int): Pair<Int,Int> {
        if (body.size!=12 || body[0].u()!=0x81 || body[1].u()!=0 || body[2].u()!=channel || body[3].u()!=channel*16) {
            throw CanonProtocolException("OPEN", "CHANNEL_REJECTED", "Channel $channel reply is invalid")
        }
        val out=be16(body,4);val input=be16(body,6)
        if(out<=6 || input<=6) throw CanonProtocolException("OPEN","INVALID_PACKET_SIZE","OUT=$out IN=$input")
        return out to input
    }
    fun serviceRequest(socket: Int)=byteArrayOf(0x0a,socket.toByte())
    fun serviceName(body: ByteArray, socket: Int): String {
        if(body.size<3 || body[0].u()!=0x8a || body[2].u()!=socket) throw CanonProtocolException("SERVICE", "INVALID_SERVICE_REPLY", "Unexpected service reply")
        if(body[1].u()!=0) return "UNKNOWN (device status ${body[1].u()})"
        return body.copyOfRange(3,body.size).toString(Charsets.US_ASCII).trimEnd('\u0000').take(64).ifEmpty { "UNKNOWN" }
    }
    /** Match observed separate native WritePort calls. A final aligned payload is
     * shortened by one byte so every actual payload write terminates short. This
     * changes segmentation only, not the concatenated printer job. No blind ZLP.
     */
    fun payloadCount(remaining: Int, maximum: Int, maxPacketSize: Int): Int {
        require(remaining>0 && maximum in 7..65535 && maxPacketSize>0)
        val count=minOf(remaining, maximum-6,16378)
        return if(count>1 && count%maxPacketSize==0) count-1 else count
    }
    fun be16(bytes: ByteArray,at: Int)=(bytes[at].u() shl 8) or bytes[at+1].u()
    private fun Byte.u()=toInt() and 255
}
