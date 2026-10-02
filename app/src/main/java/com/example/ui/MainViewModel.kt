package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.PrintJob
import com.example.core.model.PrintJobState
import com.example.core.model.PrintSettings
import com.example.diagnostics.DiagnosticsRepository
import com.example.document.DocumentSource
import com.example.document.ImageDocumentSource
import com.example.document.PdfDocumentSource
import com.example.document.TestPageDocumentSource
import com.example.jobs.PrintJobManager
import com.example.usb.UsbDeviceInfo
import com.example.usb.UsbDeviceRepository
import com.example.usb.UsbTraceEvent
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val usbRepository = UsbDeviceRepository(application, viewModelScope)
    val printJobManager = PrintJobManager(application, usbRepository, viewModelScope)
    val diagnosticsRepository = DiagnosticsRepository(application, usbRepository)

    val activeDeviceInfo: StateFlow<UsbDeviceInfo?> = usbRepository.activeDeviceInfo
    val allDevices: StateFlow<List<UsbDeviceInfo>> = usbRepository.allDevices
    val currentJob: StateFlow<PrintJob?> = printJobManager.currentJob
    val traceLogs: StateFlow<List<UsbTraceEvent>> = UsbTraceLogger.eventsFlow

    private val _isPersian = MutableStateFlow(true) // Default Persian as requested
    val isPersian: StateFlow<Boolean> = _isPersian.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentSource?>(null)
    val currentDocument: StateFlow<DocumentSource?> = _currentDocument.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

    private val _showDiagnosticsSheet = MutableStateFlow(false)
    val showDiagnosticsSheet: StateFlow<Boolean> = _showDiagnosticsSheet.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSafeProbing = MutableStateFlow(false)
    val isSafeProbing: StateFlow<Boolean> = _isSafeProbing.asStateFlow()

    init {
        // Automatically load test page initially so the user immediately has an operable document ready to print
        loadTestPage()
    }

    fun toggleLanguage() {
        _isPersian.value = !_isPersian.value
    }

    fun setDiagnosticsSheetVisible(visible: Boolean) {
        _showDiagnosticsSheet.value = visible
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun requestUsbPermission() {
        usbRepository.requestPermissionForActiveDevice { granted ->
            _statusMessage.value = if (granted) {
                if (_isPersian.value) "مجوز دسترسی به USB با موفقیت داده شد." else "USB permission granted successfully."
            } else {
                if (_isPersian.value) "مجوز دسترسی به USB رد شد." else "USB permission denied."
            }
        }
    }

    fun refreshUsbDevices() {
        usbRepository.refreshDevices()
    }

    fun loadTestPage() {
        viewModelScope.launch {
            _currentDocument.value?.close()
            val source = TestPageDocumentSource(activeDeviceInfo.value)
            _currentDocument.value = source
            _selectedPageIndex.value = 0
            updatePreview(source, 0)
        }
    }

    fun loadPdfUri(uri: Uri) {
        viewModelScope.launch {
            try {
                _currentDocument.value?.close()
                val source = PdfDocumentSource(getApplication(), uri, uri.lastPathSegment ?: "document.pdf")
                _currentDocument.value = source
                _selectedPageIndex.value = 0
                updatePreview(source, 0)
                _statusMessage.value = if (_isPersian.value) "سند PDF بارگذاری شد (${source.totalPages} صفحه)" else "PDF loaded (${source.totalPages} pages)"
            } catch (e: Exception) {
                UsbTraceLogger.logError("MainViewModel", "Failed to open PDF", e)
                _statusMessage.value = if (_isPersian.value) "خطا در باز کردن فایل PDF: ${e.message}" else "Failed to open PDF: ${e.message}"
            }
        }
    }

    fun loadImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                _currentDocument.value?.close()
                val source = ImageDocumentSource(getApplication(), uri, uri.lastPathSegment ?: "image.png")
                _currentDocument.value = source
                _selectedPageIndex.value = 0
                updatePreview(source, 0)
                _statusMessage.value = if (_isPersian.value) "تصویر با موفقیت بارگذاری شد" else "Image loaded successfully"
            } catch (e: Exception) {
                UsbTraceLogger.logError("MainViewModel", "Failed to open image", e)
                _statusMessage.value = if (_isPersian.value) "خطا در باز کردن تصویر: ${e.message}" else "Failed to open image: ${e.message}"
            }
        }
    }

    fun selectPage(index: Int) {
        val doc = _currentDocument.value ?: return
        if (index in 0 until doc.totalPages) {
            _selectedPageIndex.value = index
            updatePreview(doc, index)
        }
    }

    private fun updatePreview(doc: DocumentSource, pageIndex: Int) {
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    doc.renderPage(pageIndex, 1000, 1414)
                }
                _previewBitmap.value = preview
            } catch (e: Exception) {
                UsbTraceLogger.logError("MainViewModel", "Failed to render preview", e)
            }
        }
    }

    fun updateSettings(newSettings: PrintSettings) {
        _printSettings.value = newSettings
    }

    fun startPrint() {
        val doc = _currentDocument.value
        if (doc == null) {
            _statusMessage.value = if (_isPersian.value) "ابتدا یک سند برای چاپ انتخاب کنید." else "Please select a document first."
            return
        }

        printJobManager.startPrintJob(doc, _printSettings.value) { finalState ->
            when (finalState) {
                is PrintJobState.Completed -> {
                    _statusMessage.value = if (_isPersian.value) "چاپ با موفقیت انجام شد!" else "Print job completed successfully!"
                }
                is PrintJobState.Failed -> {
                    _statusMessage.value = if (_isPersian.value) "خطا در چاپ: ${finalState.reason}" else "Print failed: ${finalState.reason}"
                }
                is PrintJobState.Cancelled -> {
                    _statusMessage.value = if (_isPersian.value) "چاپ لغو گردید." else "Print job cancelled."
                }
                else -> {}
            }
        }
    }

    fun cancelPrint() {
        printJobManager.cancelCurrentJob()
    }

    fun runSafeProbe() {
        viewModelScope.launch {
            _isSafeProbing.value = true
            val res = usbRepository.safeProbe()
            _isSafeProbing.value = false
            if (res.isSuccess) {
                val dev = res.getOrThrow()
                val msg = if (_isPersian.value) {
                    "پویش موفق: ${dev.productName ?: "چاپگر"} | وضعیت درگاه: ${dev.portStatus?.toDisplayString() ?: "تایید شد"}"
                } else {
                    "Probe Success: ${dev.productName} | Port: ${dev.portStatus?.toDisplayString() ?: "OK"}"
                }
                _statusMessage.value = msg
            } else {
                val err = res.exceptionOrNull()?.message ?: "خطای ناشناخته"
                _statusMessage.value = if (_isPersian.value) "خطا در پویش چاپگر: $err" else "Probe failed: $err"
            }
        }
    }

    fun copyDiagnostics() {
        val ok = diagnosticsRepository.copyReportToClipboard()
        _statusMessage.value = if (ok) {
            if (_isPersian.value) "مشخصات عیب‌یابی در حافظه کپی شد." else "Diagnostics copied to clipboard."
        } else {
            if (_isPersian.value) "خطا در کپی مشخصات." else "Failed to copy."
        }
    }

    fun shareDiagnosticsReport(asJson: Boolean) {
        diagnosticsRepository.shareReport(getApplication(), asJson)
    }

    fun clearTraceLogs() {
        UsbTraceLogger.clear()
        _statusMessage.value = if (_isPersian.value) "گزارش وقایع پاک شد." else "Logs cleared."
    }

    override fun onCleared() {
        super.onCleared()
        _currentDocument.value?.close()
    }
}
