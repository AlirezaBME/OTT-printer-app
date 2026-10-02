package com.example.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.usb.UsbDeviceRepository
import java.io.File
import java.io.FileOutputStream

class DiagnosticsRepository(
    private val context: Context,
    private val usbRepository: UsbDeviceRepository
) {
    fun copyReportToClipboard(): Boolean {
        return try {
            val text = DiagnosticReport.generatePlainText(
                usbRepository.activeDeviceInfo.value,
                usbRepository.allDevices.value
            )
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("LBP OTG Diagnostics", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createReportFile(asJson: Boolean = false): File {
        val extension = if (asJson) "json" else "txt"
        val content = if (asJson) {
            DiagnosticReport.generateJson(
                usbRepository.activeDeviceInfo.value,
                usbRepository.allDevices.value
            )
        } else {
            DiagnosticReport.generatePlainText(
                usbRepository.activeDeviceInfo.value,
                usbRepository.allDevices.value
            )
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "lbp_diagnostics.$extension")
        FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return file
    }

    fun shareReport(context: Context, asJson: Boolean = false) {
        val file = createReportFile(asJson)
        shareFile(context, file, if (asJson) "application/json" else "text/plain", "Export Diagnostic Report")
    }

    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
