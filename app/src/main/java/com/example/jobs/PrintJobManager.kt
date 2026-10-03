package com.example.jobs

import android.app.ActivityManager
import android.content.Context
import com.example.core.model.*
import com.example.document.DocumentSource
import com.example.document.RawPrnPrintSource
import com.example.diagnostics.ProtocolCapture
import com.example.protocol.CanonPrintJobTransport
import com.example.driver.DriverRegistry
import com.example.raster.DitherEngine
import com.example.usb.UsbDeviceRepository
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One job per manager; USB access is serialized across UI, service, and probes. */
class PrintJobManager(private val context: Context, private val usbRepository: UsbDeviceRepository,
                      private val scope: CoroutineScope, private val observationMs: Long = 15000) {
    var captureBinary = false
    private val _lastProtocolCapture = MutableStateFlow<ProtocolCapture?>(null)
    val lastProtocolCapture = _lastProtocolCapture.asStateFlow()
    val jobQueue = LocalJobQueue()
    private val _currentJob = MutableStateFlow<PrintJob?>(null)
    val currentJob = _currentJob.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    private val _lastCapturedStreamFile = MutableStateFlow<File?>(null)
    val lastCapturedStreamFile = _lastCapturedStreamFile.asStateFlow()
    private var execution: Job? = null

    fun startPrintJob(source: DocumentSource, settings: PrintSettings, onComplete: ((PrintJobState) -> Unit)? = null): Boolean =
        start(source, null, settings, onComplete)
    fun startRawPrinterJob(source: RawPrnPrintSource, onComplete: ((PrintJobState) -> Unit)? = null): Boolean =
        start(null, source, PrintSettings(driverType=DriverType.AUTO), onComplete)
    private fun start(source: DocumentSource?, raw: RawPrnPrintSource?, settings: PrintSettings, onComplete: ((PrintJobState)->Unit)?): Boolean {
        val title=raw?.name ?: source!!.title
        val sourcePages=source?.totalPages ?: 0
        if (_isBusy.value) return false
        val indices = try {
            settings.validate()
            if (settings.driverType == DriverType.CARPS2) DriverRegistry.getEncoder(settings.driverType)
            if(raw!=null) emptyList() else settings.parsePageIndices(sourcePages)
        } catch (e: IllegalArgumentException) {
            val failed = PrintJobState.Failed(e.message ?: "Invalid print settings.")
            _currentJob.value = PrintJob(title = title, totalPages = sourcePages, pageIndices = emptyList(), settings = settings, state = failed)
            onComplete?.invoke(failed)
            return false
        }
        _isBusy.value = true
        var job = PrintJob(title = title, totalPages = sourcePages, pageIndices = indices, settings = settings, state = PrintJobState.Preparing(title))
        _currentJob.value = job
        jobQueue.addJob(job)
        execution = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var locked = false
            var spool: File? = null
            var submissionStarted = false
            var capture: ProtocolCapture? = null
            val sourceType=if(raw!=null) "RAW_PRN" else "GENERATED"
            var deviceInfo: com.example.usb.UsbDeviceInfo? = null
            var printerInterfaceId = 0
            var terminal: PrintJobState = PrintJobState.Cancelled()
            val start = System.currentTimeMillis()
            fun state(value: PrintJobState) {
                job = job.copy(state = value)
                _currentJob.value = job
                jobQueue.updateJob(job)
            }
            try {
                withContext(Dispatchers.IO) {
                    val traceDir = File(context.cacheDir, "protocol-captures/${job.id}").apply { mkdirs() }
                    capture = ProtocolCapture(traceDir, captureBinary, sourceType, null)
                    _lastProtocolCapture.value?.directory?.deleteRecursively()
                    _lastProtocolCapture.value = capture
                    check(usbRepository.operationMutex.tryLock()) { "Printer is busy. Try again after the current job." }
                    locked = true
                    var resolvedDriver = settings.driverType
                    if (settings.driverType != DriverType.FILE_STREAM_DUMP) {
                        state(PrintJobState.WaitingForPrinter("Checking compatibility…"))
                        val info = usbRepository.probeConnectedDevice()
                        deviceInfo = info
                        capture!!.recordDevice(info)
                        resolvedDriver = DriverRegistry.resolve(info, settings.driverType)
                        check(raw==null || resolvedDriver==DriverType.UFRII_LT) { "Raw Canon PRN requires the verified LBP6030 USB identity." }
                        printerInterfaceId = info.primaryPrinterInterface?.id ?: 0
                        info.portStatus?.let { check(it.isReady) { it.toDisplayString() } }
                    }
                    if(raw==null) {
                        val memory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
                        check(settings.quality.dpi <= 300 || memory >= 256) { "Use 300 DPI on this device to avoid running out of memory." }
                    }
                    val maxPacket=deviceInfo?.primaryPrinterInterface?.endpoints?.firstOrNull { it.direction=="OUT" }?.maxPacketSize ?: 512
                    val transport=capture!!.transport(usbRepository.transport,maxPacket)
                    val canonSession=if(resolvedDriver==DriverType.UFRII_LT) CanonPrintJobTransport(transport,capture!!,maxPacket,observationMs) else null
                    if(canonSession!=null) {
                        state(PrintJobState.WaitingForPrinter("Opening Canon channels; job acceptance unknown…"))
                        submissionStarted=true
                        canonSession.open()
                    }
                    val encoder=if(raw==null) DriverRegistry.getEncoder(resolvedDriver) else null
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    spool = File.createTempFile("print_", if (resolvedDriver == DriverType.UFRII_LT) ".prn" else ".pcl", directory)
                    val file = spool!!
                    val totalSteps = indices.size * settings.copies
                    var step = 0
                    if(raw!=null) {
                        state(PrintJobState.Preparing("Verifying untouched raw PRN snapshot"))
                        raw.copyVerifiedTo(file)
                    } else {
                        file.outputStream().buffered().use { output ->
                            output.write(encoder!!.encodeJobStart(settings.copy(copies = 1), totalSteps))
                            repeat(settings.copies) {
                                for (index in indices) {
                                    ensureActive()
                                    step++
                                    state(PrintJobState.Rendering(step, totalSteps, "Page ${index + 1}"))
                                    val bitmap = source!!.renderForPrint(index, settings)
                                    val raster = try {
                                        withContext(Dispatchers.Default) {
                                            DitherEngine.convertToRaster(bitmap, settings.quality.dpi, settings.ditherAlgorithm, settings.contentMode, settings.contrastBoost, settings.brightnessOffset)
                                        }
                                    } finally { bitmap.recycle() }
                                    ensureActive()
                                    state(PrintJobState.Encoding(step, totalSteps, encoder.displayName))
                                    output.write(encoder.encodePage(raster, step, totalSteps, settings.copy(copies = 1)))
                                    check(file.length() <= 256L * 1024 * 1024) { "Print job exceeds the 256 MB limit. Select fewer pages or copies." }
                                }
                            }
                            output.write(encoder.encodeJobEnd())
                        }
                    }
                    capture!!.source(file,sourceType)
                    if (settings.driverType != DriverType.FILE_STREAM_DUMP) {
                        var sent = 0L
                        val total = file.length()
                        if(canonSession!=null) {
                            canonSession.send(file,printerInterfaceId,{ acknowledged ->
                                sent=acknowledged
                                state(PrintJobState.Sending(sent,total,(sent*100/total).toInt()))
                            },{ remaining,message -> state(PrintJobState.ObservingPrinter(message,remaining)) })
                        } else {
                            file.inputStream().use { input ->
                                val buffer=ByteArray(16384)
                                while(true) {
                                    ensureActive();val size=input.read(buffer);if(size<0) break
                                    submissionStarted=true
                                    transport.writeBulk(buffer.copyOf(size),timeoutMs=3000,chunkSize=16384).getOrThrow()
                                    sent+=size;state(PrintJobState.Sending(sent,total,(sent*100/total).toInt()))
                                }
                            }
                        }
                        UsbTraceLogger.log("PrintJobManager", "Job submitted: driver=${encoder?.driverId ?: "RAW_PRN"}, bytes=$sent, pages=$totalSteps. Physical output unconfirmed.")
                    }
                    terminal = if(settings.driverType==DriverType.FILE_STREAM_DUMP) PrintJobState.Completed(file.length(),totalSteps,System.currentTimeMillis()-start)
                    else PrintJobState.TransferComplete(file.length(),totalSteps,System.currentTimeMillis()-start,
                        if(canonSession!=null) "CONFIRMATION_TIMEOUT" else "UNKNOWN")
                    _lastCapturedStreamFile.value?.delete()
                    _lastCapturedStreamFile.value = file
                    spool = null
                }
            } catch (e: CancellationException) {
                terminal = PrintJobState.Cancelled("Cancelled. Pages already sent may still print.")
            } catch (e: Exception) {
                terminal = PrintJobState.Failed(e.message ?: "Print job failed.", technicalDetail=if(e is com.example.protocol.CanonProtocolException) "stage=${e.stage} code=${e.code}" else e.javaClass.simpleName, canRetry=!submissionStarted)
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        spool?.delete()
                        if (locked) {
                            // A partial job may end inside a length-delimited packet. Reset the
                            // USB input buffer before another job; never retry ambiguous writes.
                            try {
                                if (submissionStarted && terminal !is PrintJobState.Completed && terminal !is PrintJobState.TransferComplete) {
                                    usbRepository.transport.softReset(printerInterfaceId).exceptionOrNull()?.let {
                                        UsbTraceLogger.logError("PrintJobManager", "USB reset failed during cleanup", it)
                                    }
                                }
                            } catch (e: Exception) {
                                UsbTraceLogger.logError("PrintJobManager", "USB reset failed during cleanup", e)
                            }
                            try { usbRepository.transport.close() }
                            catch (e: Exception) { UsbTraceLogger.logError("PrintJobManager", "USB close failed during cleanup", e) }
                            finally { usbRepository.operationMutex.unlock() }
                        }
                    }
                    withContext(Dispatchers.IO) { capture?.let { trace ->
                        try {
                            // Explicit wire labels stay stable in optimized release builds.
                            val terminalLabel = when (terminal) {
                                is PrintJobState.Completed -> "Completed"
                                is PrintJobState.TransferComplete -> "TransferComplete"
                                is PrintJobState.Failed -> "Failed"
                                is PrintJobState.Cancelled -> "Cancelled"
                                else -> "Unknown"
                            }
                            trace.summary.put("terminalState", terminalLabel).put("result", terminal.toString())
                            trace.event("state",mapOf("state" to "INTERFACE_RELEASED","printerAcceptance" to "UNKNOWN"))
                        } catch(e: Exception) { UsbTraceLogger.logError("ProtocolCapture","Trace event finalization failed",e) }
                        finally { try { trace.close() } catch(e: Exception) { UsbTraceLogger.logError("ProtocolCapture","Trace export unavailable",e) } }
                    }
                    }
                    state(terminal)
                    _isBusy.value = false
                    onComplete?.invoke(terminal)
                }
            }
        }
        return true
    }
    fun cancelCurrentJob() { execution?.cancel() }
    suspend fun cancelAndJoin() { execution?.cancelAndJoin() }
    fun dismissJob() { if (!_isBusy.value) _currentJob.value = null }
}
