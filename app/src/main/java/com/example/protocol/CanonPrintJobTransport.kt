package com.example.protocol

import com.example.diagnostics.ProtocolCapture
import com.example.usb.CanonMlpSession
import com.example.usb.UsbTransport
import java.io.File

/** Printer language bytes are opaque here. Raw PRNs use exactly this same path. */
class CanonPrintJobTransport(transport: UsbTransport, private val capture: ProtocolCapture, maxPacketSize: Int, private val observationMs: Long) {
    private val mlp=CanonMlpSession(transport,capture=capture,maxPacketSize=maxPacketSize)
    suspend fun open() { mlp.open();capture.event("state",mapOf("state" to "MLP_ESTABLISHED","cpcaStatusCommands" to "UNVERIFIED; passive capture only")) }
    suspend fun send(file: File, interfaceId: Int, onProgress: (Long)->Unit, onObservation: (Long,String)->Unit) {
        file.inputStream().use { mlp.transmit(it,file.length(),onProgress) }
        check(capture.summary.optString("transmittedJobSha256")==capture.summary.getString("jobSha256")) { "Transmitted job SHA-256 differs from prepared bytes." }
        capture.event("state",mapOf("state" to "TRANSFER_COMPLETE","jobAccepted" to "UNKNOWN"))
        mlp.observePrinter(observationMs,interfaceId,onObservation)
        mlp.finish()
        capture.event("state",mapOf("state" to "CHANNELS_CLOSED"))
    }
}
