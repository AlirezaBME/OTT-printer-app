package com.example.jobs

import android.content.Context
import com.example.core.model.DriverType
import com.example.core.model.PrintJob
import com.example.core.model.PrintJobState
import com.example.core.model.PrintSettings
import com.example.document.DocumentSource
import com.example.driver.DriverRegistry
import com.example.raster.RasterPipeline
import com.example.usb.UsbDeviceRepository
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class PrintJobManager(
    private val context: Context,
    private val usbRepository: UsbDeviceRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "PrintJobManager"
    }

    val jobQueue = LocalJobQueue()
    private val rasterPipeline = RasterPipeline()

    private val _currentJob = MutableStateFlow<PrintJob?>(null)
    val currentJob: StateFlow<PrintJob?> = _currentJob.asStateFlow()

    private val _lastCapturedStreamFile = MutableStateFlow<File?>(null)
    val lastCapturedStreamFile: StateFlow<File?> = _lastCapturedStreamFile.asStateFlow()

    private var activeExecutionJob: Job? = null

    fun startPrintJob(
        documentSource: DocumentSource,
        settings: PrintSettings,
        onComplete: ((PrintJobState) -> Unit)? = null
    ) {
        val totalPages = documentSource.totalPages
        if (totalPages <= 0) {
            val failedJob = PrintJob(
                title = documentSource.title,
                totalPages = 0,
                pageIndices = emptyList(),
                settings = settings,
                state = PrintJobState.Failed("Document has 0 pages.")
            )
            _currentJob.value = failedJob
            onComplete?.invoke(failedJob.state)
            return
        }

        val pageIndices = settings.parsePageIndices(totalPages)
        val job = PrintJob(
            title = documentSource.title,
            totalPages = totalPages,
            pageIndices = pageIndices,
            settings = settings,
            state = PrintJobState.Preparing(documentSource.title)
        )

        jobQueue.addJob(job)
        _currentJob.value = job

        activeExecutionJob?.cancel()
        activeExecutionJob = scope.launch {
            executeJob(job, documentSource, onComplete)
        }
    }

    fun cancelCurrentJob() {
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        val cur = _currentJob.value
        if (cur != null && cur.state !is PrintJobState.Completed && cur.state !is PrintJobState.Failed) {
            val cancelledJob = cur.copy(state = PrintJobState.Cancelled())
            _currentJob.value = cancelledJob
            jobQueue.updateJob(cancelledJob)
            UsbTraceLogger.log(TAG, "Current print job cancelled by user.")
        }
    }

    private suspend fun executeJob(
        initialJob: PrintJob,
        documentSource: DocumentSource,
        onComplete: ((PrintJobState) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        var job = initialJob
        val startTime = System.currentTimeMillis()
        val settings = job.settings
        val encoder = DriverRegistry.getEncoder(settings.driverType)

        fun updateState(newState: PrintJobState) {
            job = job.copy(state = newState)
            _currentJob.value = job
            jobQueue.updateJob(job)
        }

        try {
            UsbTraceLogger.log(TAG, "Executing job '${job.title}' with driver ${encoder.displayName} on ${job.pageIndices.size} page(s)")

            // 1. Rendering and Encoding
            val streamBuffer = ByteArrayOutputStream()
            val jobStartBytes = encoder.encodeJobStart(settings, job.pageIndices.size)
            streamBuffer.write(jobStartBytes)

            for ((stepIndex, pageIndex) in job.pageIndices.withIndex()) {
                val displayPageNum = stepIndex + 1
                val totalSteps = job.pageIndices.size

                updateState(PrintJobState.Rendering(displayPageNum, totalSteps, "Page ${pageIndex + 1}"))
                val targetDpi = settings.quality.dpi
                val targetW = settings.paperSize.getPixelWidth(targetDpi)
                val targetH = settings.paperSize.getPixelHeight(targetDpi)

                val pageBitmap = documentSource.renderPage(pageIndex, targetW, targetH)
                val rasterPage = rasterPipeline.processBitmapToRaster(pageBitmap, settings)
                pageBitmap.recycle()

                updateState(PrintJobState.Encoding(displayPageNum, totalSteps, encoder.displayName))
                val pageBytes = encoder.encodePage(rasterPage, displayPageNum, totalSteps, settings)
                streamBuffer.write(pageBytes)
            }

            val jobEndBytes = encoder.encodeJobEnd()
            streamBuffer.write(jobEndBytes)

            val fullEncodedStream = streamBuffer.toByteArray()
            UsbTraceLogger.log(TAG, "Job stream encoded completely: ${fullEncodedStream.size} bytes generated.")

            // Always save last captured stream to cache for debugging / export
            val dumpFile = File(context.cacheDir, "last_job_${System.currentTimeMillis()}_${encoder.driverId}.bin")
            FileOutputStream(dumpFile).use { it.write(fullEncodedStream) }
            _lastCapturedStreamFile.value = dumpFile

            if (settings.driverType == DriverType.FILE_STREAM_DUMP) {
                // File dump mode selected: finish without USB transfer
                updateState(PrintJobState.Completed(
                    totalBytes = fullEncodedStream.size.toLong(),
                    pagesPrinted = job.pageIndices.size,
                    durationMs = System.currentTimeMillis() - startTime
                ))
                onComplete?.invoke(job.state)
                return@withContext
            }

            // 2. Hardware USB Communication
            val usbDevice = usbRepository.activeDevice.value
            if (usbDevice == null) {
                updateState(PrintJobState.Failed("No printer connected via USB OTG."))
                onComplete?.invoke(job.state)
                return@withContext
            }

            if (!usbRepository.permissionManager.hasPermission(usbDevice)) {
                updateState(PrintJobState.Failed("USB permission required for connected printer."))
                onComplete?.invoke(job.state)
                return@withContext
            }

            updateState(PrintJobState.WaitingForPrinter("Connecting to printer..."))
            val transport = usbRepository.transport
            val openRes = transport.open(usbDevice)
            if (openRes.isFailure) {
                val err = openRes.exceptionOrNull()?.message ?: "Failed to open USB device"
                updateState(PrintJobState.Failed(err))
                onComplete?.invoke(job.state)
                return@withContext
            }

            // Claim printer interface
            var printerIfId = 0
            for (i in 0 until usbDevice.interfaceCount) {
                val uif = usbDevice.getInterface(i)
                if (uif.interfaceClass == 7) {
                    printerIfId = uif.id
                    break
                }
            }

            val claimRes = transport.claimInterface(printerIfId)
            if (claimRes.isFailure) {
                transport.close()
                val err = claimRes.exceptionOrNull()?.message ?: "Could not claim printer interface"
                updateState(PrintJobState.Failed(err))
                onComplete?.invoke(job.state)
                return@withContext
            }

            // Check hardware port status
            val portStatusRes = transport.queryPortStatus(printerIfId)
            if (portStatusRes.isSuccess) {
                val portStatus = portStatusRes.getOrThrow()
                if (portStatus.paperEmpty) {
                    transport.close()
                    updateState(PrintJobState.Failed("Printer reported Paper Out / Tray Empty."))
                    onComplete?.invoke(job.state)
                    return@withContext
                }
            }

            // 3. Sending Data in Chunks
            val totalBytes = fullEncodedStream.size.toLong()
            updateState(PrintJobState.Sending(0L, totalBytes, 0))

            val writeRes = transport.writeBulk(
                data = fullEncodedStream,
                timeoutMs = 15000,
                chunkSize = 16384
            ) { bytesWritten, total ->
                val percent = if (total > 0) ((bytesWritten * 100) / total).toInt() else 0
                updateState(PrintJobState.Sending(bytesWritten, total, percent))
            }

            if (writeRes.isFailure) {
                transport.close()
                val err = writeRes.exceptionOrNull()?.message ?: "USB bulk transfer error"
                updateState(PrintJobState.Failed(err))
                onComplete?.invoke(job.state)
                return@withContext
            }

            updateState(PrintJobState.DataSent(totalBytes, job.pageIndices.size))
            updateState(PrintJobState.Finishing("Printer processing page ejection…"))

            // Short settling pause for paper feed
            kotlinx.coroutines.delay(2000)

            transport.close()

            val duration = System.currentTimeMillis() - startTime
            val completedState = PrintJobState.Completed(
                totalBytes = totalBytes,
                pagesPrinted = job.pageIndices.size,
                durationMs = duration
            )
            updateState(completedState)
            onComplete?.invoke(completedState)

        } catch (ce: CancellationException) {
            updateState(PrintJobState.Cancelled("Print job was cancelled."))
            usbRepository.transport.close()
            onComplete?.invoke(job.state)
        } catch (e: Exception) {
            UsbTraceLogger.logError(TAG, "Unhandled error during print job execution", e)
            val failedState = PrintJobState.Failed("Error: ${e.message ?: "Unknown error"}")
            updateState(failedState)
            usbRepository.transport.close()
            onComplete?.invoke(failedState)
        }
    }
}
