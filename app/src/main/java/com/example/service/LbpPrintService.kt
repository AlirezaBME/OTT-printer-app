package com.example.service

import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.example.LbpOtgApplication
import com.example.core.model.PrintJobState
import com.example.core.model.PrintSettings
import com.example.document.PdfDocumentSource
import com.example.jobs.PrintJobManager
import com.example.usb.UsbDeviceRepository
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LbpPrintService : PrintService() {

    companion object {
        private const val TAG = "LbpPrintService"
        const val PRINTER_LOCAL_ID = "canon_lbp6030_usb"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var usbRepository: UsbDeviceRepository
    private lateinit var printJobManager: PrintJobManager

    override fun onCreate() {
        super.onCreate()
        usbRepository = UsbDeviceRepository(this)
        printJobManager = PrintJobManager(this, usbRepository)
        UsbTraceLogger.log(TAG, "LbpPrintService initialized.")
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                val printerId = generatePrinterId(PRINTER_LOCAL_ID)
                val activeDev = usbRepository.activeDeviceInfo.value
                val printerName = if (activeDev?.isLbp6030Family == true) {
                    "Canon LBP6030/6040/6018L (USB OTG)"
                } else if (activeDev != null) {
                    "${activeDev.productName ?: "Canon Laser"} (USB OTG)"
                } else {
                    "Canon LBP6030 Series (USB OTG)"
                }

                val status = if (activeDev?.permissionGranted == true) {
                    PrinterInfo.STATUS_IDLE
                } else {
                    PrinterInfo.STATUS_UNAVAILABLE
                }

                val capabilities = PrinterCapabilitiesInfo.Builder(printerId)
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
                    .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                    .addResolution(PrintAttributes.Resolution("600dpi", "600 DPI", 600, 600), true)
                    .addResolution(PrintAttributes.Resolution("300dpi", "300 DPI", 300, 300), false)
                    .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                    .setMinMargins(PrintAttributes.Margins(200, 200, 200, 200)) // ~5mm
                    .build()

                val info = PrinterInfo.Builder(printerId, printerName, status)
                    .setCapabilities(capabilities)
                    .setDescription("Direct USB OTG Canon LBP Laser Printer")
                    .build()

                addPrinters(listOf(info))
            }

            override fun onStopPrinterDiscovery() {}
            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onDestroy() {}
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJobManager.cancelCurrentJob()
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        if (!printJob.isQueued) return
        printJob.start()

        val doc = printJob.document
        val pfd: ParcelFileDescriptor? = doc.data
        if (pfd == null) {
            printJob.fail("No document data received")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Copy stream to cache file for PdfDocumentSource
                val cacheFile = File(cacheDir, "service_job_${System.currentTimeMillis()}.pdf")
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val uri = android.net.Uri.fromFile(cacheFile)
                val docSource = PdfDocumentSource(this@LbpPrintService, uri, printJob.document.info.name ?: "PrintJob.pdf")
                val settings = PrintSettings()

                printJobManager.startPrintJob(docSource, settings) { finalState ->
                    when (finalState) {
                        is PrintJobState.Completed -> printJob.complete()
                        is PrintJobState.Cancelled -> printJob.cancel()
                        is PrintJobState.Failed -> printJob.fail(finalState.reason)
                        else -> {}
                    }
                    docSource.close()
                }
            } catch (e: Exception) {
                UsbTraceLogger.logError(TAG, "PrintService job execution failed", e)
                printJob.fail(e.message ?: "Job failed")
            }
        }
    }
}
