package com.example.diagnostics

import com.example.document.RawPrnPrintSource
import com.example.usb.UsbDeviceInfo
import com.example.usb.UsbTraceLogger
import com.example.usb.UsbTransport
import org.json.JSONObject
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.Closeable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Metadata is always captured. Binary capture is explicit and bounded. Private
 * documents stay in app cache until the user invokes Export binary session trace.
 */
class ProtocolCapture(val directory: File, private val binary: Boolean, sourceType: String, device: UsbDeviceInfo?) : Closeable {
    private val events=File(directory,"events.jsonl").bufferedWriter()
    private val out=if(binary) File(directory,"usb-out.bin").outputStream() else null
    private val input=if(binary) File(directory,"usb-in.bin").outputStream() else null
    private var closed=false
    private var eventCount=0
    val summary=JSONObject().put("schemaVersion",1).put("sourceType",sourceType).put("binaryCapture",binary)
        .put("printerAcceptance","UNKNOWN").put("physicalPrintConfirmed",false)
        .put("cpcaJobStatusSemantics","UNVERIFIED; no fabricated status commands")
        .put("usb",JSONObject(DiagnosticReport.generateJson(device, listOfNotNull(device))))
    private val _snapshot=kotlinx.coroutines.flow.MutableStateFlow(summary.toString(2))
    val snapshot=_snapshot.asStateFlow()
    init { event("state",mapOf("state" to "PREPARING","interfaceClaimed" to device?.interfaceClaimed)) }
    fun event(type: String, values: Map<String,Any?> = emptyMap()) {
        check(!closed)
        check(++eventCount<=100000) { "Protocol trace event limit reached; job stopped to preserve trace integrity." }
        val value=JSONObject().put("timestampMs",System.currentTimeMillis()).put("monotonicNs",System.nanoTime()).put("type",type)
        values.forEach { (k,v) -> value.put(k, when (v) { is Collection<*> -> org.json.JSONArray(v); null -> JSONObject.NULL; else -> v }) }
        if (type == "state") summary.put("transportState", values["state"] ?: JSONObject.NULL)
        events.append(value.toString()).append('\n')
        _snapshot.value=summary.toString(2)
    }
    fun recordOpenedChannel(channel: com.example.protocol.CanonChannel, outSize: Int, inSize: Int) {
        val channels = summary.optJSONArray("channels") ?: org.json.JSONArray().also { summary.put("channels", it) }
        channels.put(JSONObject().put("hostSocket", channel.hostSocket).put("printerSocket", channel.printerSocket)
            .put("nativeRole", channel.nativeRole).put("service", "UNKNOWN").put("outFrameMaximum", outSize).put("inFrameMaximum", inSize))
        summary.put("printChannel", 1)
        event("channelOpened", mapOf("hostSocket" to channel.hostSocket, "printerSocket" to channel.printerSocket,
            "outFrameMaximum" to outSize, "inFrameMaximum" to inSize, "nativeRole" to channel.nativeRole))
    }
    fun recordServiceName(hostSocket: Int, service: String) {
        val channels = summary.getJSONArray("channels")
        for (i in 0 until channels.length()) {
            val channel = channels.getJSONObject(i)
            if (channel.getInt("hostSocket") == hostSocket) channel.put("service", service)
        }
    }
    fun recordDevice(device: UsbDeviceInfo) {
        summary.put("usb", JSONObject(DiagnosticReport.generateJson(device, listOf(device))))
        event("state", mapOf("state" to "INTERFACE_CLAIMED", "interfaceClaimed" to device.interfaceClaimed))
    }
    fun source(file: File, sourceType: String) {
        val fingerprint=RawPrnPrintSource.fingerprint(file)
        summary.put("jobBytes",fingerprint.size).put("jobSha256",fingerprint.sha256)
            .put("first64Hex",fingerprint.first64Hex).put("last64Hex",fingerprint.last64Hex)
        event("sourceVerified",mapOf("sourceType" to sourceType,"bytes" to fingerprint.size,"sha256" to fingerprint.sha256))
        if(binary) {
            require(file.length()<=RawPrnPrintSource.MAX_BYTES) { "Binary trace supports jobs up to 64 MB. Disable binary capture for larger generated jobs." }
            file.copyTo(File(directory,"print-job.bin"))
            if(sourceType=="GENERATED") summary.put("encoderOutputIdenticalToPrintJob",true)
        }
    }
    fun transport(delegate: UsbTransport, maxPacketSize: Int): UsbTransport = object : UsbTransport by delegate {
        override suspend fun writeBulk(data: ByteArray,timeoutMs: Int,chunkSize: Int,onProgress: (Long,Long)->Unit): Result<Long> {
            val start=System.nanoTime();var recorded=0;var calls=0
            event("usbWriteRequested",mapOf("length" to data.size,"chunkSize" to chunkSize,"timeoutMs" to timeoutMs,"aligned" to (data.size%maxPacketSize==0),"zlpEmitted" to false))
            val result=delegate.writeBulk(data,timeoutMs,chunkSize) { written,total ->
                val delta=written.toInt()-recorded
                if(delta>0) { out?.write(data,recorded,delta);recorded+=delta;calls++ }
                event("usbOut",mapOf("acceptedBytes" to delta,"offset" to (recorded-delta),"elapsedNs" to (System.nanoTime()-start),"aligned" to (delta%maxPacketSize==0),"zlpEmitted" to false))
                onProgress(written,total)
            }
            // A test/custom transport may report a complete result without callbacks.
            if(result.isSuccess && result.getOrThrow()>recorded) {
                val n=result.getOrThrow().toInt()-recorded;out?.write(data,recorded,n);recorded+=n
            }
            event("usbWriteResult",mapOf("requestedBytes" to data.size,"knownAcceptedBytes" to recorded,"usbProgressCallbacks" to calls,
                "durationNs" to (System.nanoTime()-start),"error" to result.exceptionOrNull()?.message,
                "failedTransferBytesMayBeAmbiguous" to result.isFailure))
            return result
        }
        override suspend fun readBulk(buffer: ByteArray,timeoutMs: Int): Result<Int> {
            val start=System.nanoTime();val result=delegate.readBulk(buffer,timeoutMs)
            val n=result.getOrNull()?.coerceAtLeast(0) ?: 0
            if(n>0) input?.write(buffer,0,n)
            event("usbIn",mapOf("length" to n,"timeoutMs" to timeoutMs,"durationNs" to (System.nanoTime()-start),
                "hex" to UsbTraceLogger.bytesToHex(buffer.copyOf(n),64),"error" to result.exceptionOrNull()?.message))
            return result
        }
    }
    override fun close() {
        if(closed) return
        var closeFailure: Throwable? = null
        listOfNotNull<Closeable>(events, out, input).forEach { stream ->
            try { stream.close() } catch (error: Throwable) { if (closeFailure == null) closeFailure = error }
        }
        closeFailure?.let { throw it }
        File(directory,"session.json").bufferedWriter().use { writer ->
            val header=summary.toString();writer.write(header.dropLast(1));writer.write(",\"events\":[")
            File(directory,"events.jsonl").useLines { lines ->
                var first=true
                lines.forEach { line -> if(!first) writer.write(",");writer.write(line);first=false }
            }
            writer.write("]}")
        }
        closed=true
    }
    fun export(target: File) {
        check(closed) { "Wait for the active job to finish before exporting its trace." }
        ZipOutputStream(target.outputStream()).use { zip ->
            for(name in listOf("session.json","usb-out.bin","usb-in.bin","print-job.bin")) {
                val file=File(directory,name);if(!file.exists()) continue
                zip.putNextEntry(ZipEntry(name));file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
            }
            if(summary.optBoolean("encoderOutputIdenticalToPrintJob")) {
                zip.putNextEntry(ZipEntry("encoder-output.bin"));File(directory,"print-job.bin").inputStream().use { it.copyTo(zip) };zip.closeEntry()
            }
        }
    }
}
