package com.example.protocol

import com.example.diagnostics.ProtocolCapture
import com.example.usb.UsbTraceLogger

/** Passive CPCA observation only. Job acceptance/processing/completion commands
 * have not been validated against hardware or an official USB capture. Keep them
 * UNKNOWN, even when a correctly shaped CPCA envelope arrives on a control socket.
 */
class CanonCpcaSession(private val capture: ProtocolCapture?) {
    private val pending=Array(4) { ByteArray(0) }
    var responseCount=0
        private set
    fun observe(frame: MlpFrame) {
        if(frame.isFlowControl) return
        val channel=frame.channel
        val bytes=pending[channel]+frame.payload
        if(bytes.size>131070) throw CanonProtocolException("CPCA","RESPONSE_LIMIT","CPCA response buffer exceeded")
        var offset=0
        while(bytes.size-offset>=20) {
            if(bytes[offset]!=0xcd.toByte() || bytes[offset+1]!=0xca.toByte() || bytes[offset+2]!=0x10.toByte()) {
                capture?.event("unclassifiedChannelPayload",mapOf("channel" to channel,"hex" to UsbTraceLogger.bytesToHex(bytes.copyOfRange(offset,bytes.size),64),"semantics" to "UNVERIFIED"))
                pending[channel]=ByteArray(0);return
            }
            val length=20+CanonMlpProtocol.be16(bytes,offset+8)
            if(bytes.size-offset<length) break
            responseCount++
            capture?.event("cpcaResponse",mapOf("channel" to channel,"command" to CanonMlpProtocol.be16(bytes,offset+4),
                "flags" to (bytes[offset+3].toInt() and 255),"sequence" to CanonMlpProtocol.be16(bytes,offset+6),"length" to length,
                "hex" to UsbTraceLogger.bytesToHex(bytes.copyOfRange(offset,offset+length),64),"jobStatus" to "UNKNOWN","semantics" to "UNVERIFIED"))
            UsbTraceLogger.log("CanonCpcaSession","CPCA-shaped response on channel $channel; command semantics unverified. Not a print confirmation.")
            offset+=length
        }
        pending[channel]=bytes.copyOfRange(offset,bytes.size)
    }
}
