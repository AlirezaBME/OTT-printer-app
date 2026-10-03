package com.example.service

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.*
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.example.LbpOtgApplication
import com.example.core.model.*
import com.example.document.PdfDocumentSource
import com.example.driver.DriverRegistry
import com.example.jobs.PrintJobManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File

class LbpPrintService : PrintService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as LbpOtgApplication).usbRepository
    private lateinit var manager: PrintJobManager
    private val queue = Channel<PrintJob>(Channel.UNLIMITED)
    private var activeId: PrintJobId? = null
    private var activeExecution: Job? = null
    override fun onCreate() {
        super.onCreate()
        manager = PrintJobManager(this, repository, scope)
        scope.launch {
            for (job in queue) {
                if (!job.isQueued || !job.start()) continue
                activeId = job.id
                activeExecution = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var source: PdfDocumentSource? = null
                var temporary: File? = null
                try {
                    val descriptor = job.document.data ?: error("No document received")
                    withContext(Dispatchers.IO) {
                        temporary = File.createTempFile("service_", ".pdf", cacheDir)
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> temporary!!.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            var count = 0L
                            while (true) {
                                ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                count += size
                                require(count <= 64L * 1024 * 1024) { "Document exceeds 64 MB" }
                                output.write(buffer, 0, size)
                            }
                        } }
                        source = PdfDocumentSource(this@LbpPrintService, Uri.fromFile(temporary), job.document.info.name ?: "Document.pdf")
                    }
                    val attributes = job.info.attributes ?: error("Missing print attributes")
                    val media = attributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
                    val paper = when (media.id) {
                        PrintAttributes.MediaSize.ISO_A4.id -> PaperSize.A4
                        PrintAttributes.MediaSize.ISO_A5.id -> PaperSize.A5
                        PrintAttributes.MediaSize.NA_LETTER.id -> PaperSize.LETTER
                        else -> error("Unsupported paper size")
                    }
                    val settings = PrintSettings(paperSize = paper, copies = job.info.copies,
                        orientation = if (media.isPortrait) PrintOrientation.PORTRAIT else PrintOrientation.LANDSCAPE,
                        quality = if ((attributes.resolution?.horizontalDpi ?: 300) >= 600) PrintQuality.NORMAL_600DPI else PrintQuality.DRAFT_300DPI,
                        marginMm = 0f)
                    val done = CompletableDeferred<PrintJobState>()
                    manager.startPrintJob(source!!, settings) { done.complete(it) }
                    when (val result = done.await()) {
                        is PrintJobState.Completed -> job.fail("Unexpected file-export result")
                        is PrintJobState.TransferComplete -> job.block("USB transfer complete; printer acceptance unconfirmed. Check the printer before resubmitting.")
                        is PrintJobState.Cancelled -> job.cancel()
                        is PrintJobState.Failed -> job.fail(result.reason)
                        else -> job.fail("Unexpected job state")
                    }
                } catch (e: CancellationException) { job.cancel(); throw e }
                catch (e: Exception) { job.fail(e.message ?: "Print failed") }
                finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        manager.cancelAndJoin()
                        source?.close()
                        temporary?.delete()
                    }
                    activeId = null
                }
                }
                activeExecution?.join()
            }
        }
    }
    override fun onPrintJobQueued(printJob: PrintJob) { if (queue.trySend(printJob).isFailure) printJob.fail("Print service stopped") }
    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        if (activeId == printJob.id) { activeExecution?.cancel(); manager.cancelCurrentJob() }
        printJob.cancel()
    }
    override fun onDestroy() { queue.close(); scope.cancel(); super.onDestroy() }
    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = object : PrinterDiscoverySession() {
        private var monitor: Job? = null
        private var probed: String? = null
        private val id = generatePrinterId("usb_pcl5")
        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            monitor?.cancel()
            repository.refreshDevices()
            monitor = scope.launch {
                repository.activeDeviceInfo.collect { device ->
                    if (device == null) { removePrinters(listOf(id)); probed = null; return@collect }
                    if (device.permissionGranted && device.ieee1284 == null && probed != device.deviceName) {
                        probed = device.deviceName
                        repository.safeProbe()
                    }
                    if (device.ieee1284?.supportsPcl5 != true && !DriverRegistry.supportsCanon(device)) { removePrinters(listOf(id)); return@collect }
                    val capabilities = PrinterCapabilitiesInfo.Builder(id)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
                        .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                        .addResolution(PrintAttributes.Resolution("300", "300 DPI", 300, 300), true)
                        .addResolution(PrintAttributes.Resolution("600", "600 DPI", 600, 600), false)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(PrintAttributes.Margins(200, 200, 200, 200)).build()
                    val ready = device.permissionGranted && device.portStatus?.isReady != false
                    addPrinters(listOf(PrinterInfo.Builder(id, device.productName ?: "USB printer",
                        if (ready) PrinterInfo.STATUS_IDLE else PrinterInfo.STATUS_UNAVAILABLE)
                        .setCapabilities(capabilities).setDescription(if (DriverRegistry.supportsCanon(device)) "USB OTG • Canon UFRII LT" else "USB OTG • PCL 5 monochrome").build()))
                }
            }
        }
        override fun onStopPrinterDiscovery() { monitor?.cancel() }
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) { repository.refreshDevices() }
        override fun onStartPrinterStateTracking(printerId: PrinterId) { scope.launch { repository.safeProbe() } }
        override fun onStopPrinterStateTracking(printerId: PrinterId) {}
        override fun onDestroy() { monitor?.cancel() }
    }
}
