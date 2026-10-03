package com.example.jobs

import android.app.ActivityManager
import android.content.Context
import com.example.core.model.*
import com.example.document.DocumentSource
import com.example.driver.DriverRegistry
import com.example.raster.DitherEngine
import com.example.usb.UsbDeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One job per manager; USB access is serialized across UI, service, and probes. */
class PrintJobManager(private val context: Context, private val usbRepository: UsbDeviceRepository,
                      private val scope: CoroutineScope) {
    val jobQueue = LocalJobQueue()
    private val _currentJob = MutableStateFlow<PrintJob?>(null)
    val currentJob = _currentJob.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    private val _lastCapturedStreamFile = MutableStateFlow<File?>(null)
    val lastCapturedStreamFile = _lastCapturedStreamFile.asStateFlow()
    private var execution: Job? = null

    fun startPrintJob(source: DocumentSource, settings: PrintSettings, onComplete: ((PrintJobState) -> Unit)? = null): Boolean {
        if (_isBusy.value) return false
        val indices = try {
            settings.validate()
            if (settings.driverType == DriverType.CARPS2) DriverRegistry.getEncoder(settings.driverType)
            settings.parsePageIndices(source.totalPages)
        } catch (e: IllegalArgumentException) {
            val failed = PrintJobState.Failed(e.message ?: "Invalid print settings.")
            _currentJob.value = PrintJob(title = source.title, totalPages = source.totalPages, pageIndices = emptyList(), settings = settings, state = failed)
            onComplete?.invoke(failed)
            return false
        }
        _isBusy.value = true
        var job = PrintJob(title = source.title, totalPages = source.totalPages, pageIndices = indices, settings = settings, state = PrintJobState.Preparing(source.title))
        _currentJob.value = job
        jobQueue.addJob(job)
        execution = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var locked = false
            var spool: File? = null
            var submissionStarted = false
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
                    check(usbRepository.operationMutex.tryLock()) { "Printer is busy. Try again after the current job." }
                    locked = true
                    var resolvedDriver = settings.driverType
                    if (settings.driverType != DriverType.FILE_STREAM_DUMP) {
                        state(PrintJobState.WaitingForPrinter("Checking compatibility…"))
                        val info = usbRepository.probeConnectedDevice()
                        resolvedDriver = DriverRegistry.resolve(info, settings.driverType)
                        printerInterfaceId = info.primaryPrinterInterface?.id ?: 0
                        info.portStatus?.let { check(it.isReady) { it.toDisplayString() } }
                    }
                    val memory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
                    check(settings.quality.dpi <= 300 || memory >= 256) { "Use 300 DPI on this device to avoid running out of memory." }
                    val encoder = DriverRegistry.getEncoder(resolvedDriver)
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    spool = File.createTempFile("print_", if (resolvedDriver == DriverType.UFRII_LT) ".prn" else ".pcl", directory)
                    val file = spool!!
                    val totalSteps = indices.size * settings.copies
                    var step = 0
                    file.outputStream().buffered().use { output ->
                        output.write(encoder.encodeJobStart(settings.copy(copies = 1), totalSteps))
                        repeat(settings.copies) {
                            for (index in indices) {
                                ensureActive()
                                step++
                                state(PrintJobState.Rendering(step, totalSteps, "Page ${index + 1}"))
                                val bitmap = source.renderForPrint(index, settings)
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
                    if (settings.driverType != DriverType.FILE_STREAM_DUMP) {
                        var sent = 0L
                        val total = file.length()
                        file.inputStream().use { input ->
                            val buffer = ByteArray(16384)
                            while (true) {
                                ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                submissionStarted = true
                                usbRepository.transport.writeBulk(buffer.copyOf(size), timeoutMs = if (resolvedDriver == DriverType.UFRII_LT) 15000 else 3000, chunkSize = 16384).getOrThrow()
                                sent += size
                                state(PrintJobState.Sending(sent, total, (sent * 100 / total).toInt()))
                            }
                        }
                    }
                    terminal = PrintJobState.Completed(file.length(), totalSteps, System.currentTimeMillis() - start)
                    _lastCapturedStreamFile.value?.delete()
                    _lastCapturedStreamFile.value = file
                    spool = null
                }
            } catch (e: CancellationException) {
                terminal = PrintJobState.Cancelled("Cancelled. Pages already sent may still print.")
            } catch (e: Exception) {
                terminal = PrintJobState.Failed(e.message ?: "Print job failed.")
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        spool?.delete()
                        if (locked) {
                            // A partial job may end inside a length-delimited packet. Reset the
                            // USB input buffer before another job; never retry ambiguous writes.
                            try {
                                if (submissionStarted && terminal !is PrintJobState.Completed) {
                                    usbRepository.transport.softReset(printerInterfaceId)
                                }
                            } finally {
                                usbRepository.transport.close(); usbRepository.operationMutex.unlock()
                            }
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
