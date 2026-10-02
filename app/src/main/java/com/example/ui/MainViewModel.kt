package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LbpOtgApplication
import com.example.core.model.*
import com.example.diagnostics.DiagnosticsRepository
import com.example.document.*
import com.example.jobs.PrintJobManager
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LbpOtgApplication
    val usbRepository = app.usbRepository
    val printJobManager = PrintJobManager(app, usbRepository, viewModelScope)
    val diagnosticsRepository = DiagnosticsRepository(app, usbRepository)
    val activeDeviceInfo = usbRepository.activeDeviceInfo
    val allDevices = usbRepository.allDevices
    val currentJob = printJobManager.currentJob
    val isPrinting = printJobManager.isBusy
    val traceLogs = UsbTraceLogger.eventsFlow
    private val preferences = app.getSharedPreferences("preferences", 0)
    private val _isPersian = MutableStateFlow(preferences.getBoolean("persian", java.util.Locale.getDefault().language == "fa"))
    val isPersian = _isPersian.asStateFlow()
    private val _currentDocument = MutableStateFlow<DocumentSource?>(null)
    val currentDocument = _currentDocument.asStateFlow()
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap = _previewBitmap.asStateFlow()
    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex = _selectedPageIndex.asStateFlow()
    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings = _printSettings.asStateFlow()
    private val _showDiagnosticsSheet = MutableStateFlow(false)
    val showDiagnosticsSheet = _showDiagnosticsSheet.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()
    private val _isSafeProbing = MutableStateFlow(false)
    val isSafeProbing = _isSafeProbing.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val documentMutex = Mutex()
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    init { loadTestPage() }
    fun toggleLanguage() {
        _isPersian.value = !_isPersian.value
        preferences.edit().putBoolean("persian", _isPersian.value).apply()
    }
    fun setDiagnosticsSheetVisible(visible: Boolean) { _showDiagnosticsSheet.value = visible }
    fun clearStatusMessage() { _statusMessage.value = null }
    fun reportError(message: String) { _statusMessage.value = message }
    fun requestUsbPermission() {
        usbRepository.requestPermissionForActiveDevice { granted ->
            _statusMessage.value = if (granted) text("مجوز USB داده شد.", "USB permission granted.") else text("مجوز USB رد شد.", "USB permission denied.")
            if (granted) runSafeProbe()
        }
    }
    fun refreshUsbDevices() = usbRepository.refreshDevices()
    private fun displayName(uri: Uri): String = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.take(200) else null
        } ?: "Document"
    } catch (_: Exception) { "Document" }
    fun loadTestPage() = loadDocument { TestPageDocumentSource(activeDeviceInfo.value) }
    fun loadPdfUri(uri: Uri) = loadDocument { PdfDocumentSource(app, uri, displayName(uri)) }
    fun loadImageUri(uri: Uri) = loadDocument { ImageDocumentSource(app, uri, displayName(uri)) }
    private fun loadDocument(factory: () -> DocumentSource) {
        if (isPrinting.value) { reportError(text("تا پایان چاپ صبر کنید.", "Wait for the current job to finish.")); return }
        val generation = ++loadGeneration
        loadJob?.cancel()
        previewJob?.cancel()
        loadJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _isLoading.value = true
            try {
                documentMutex.withLock {
                    // Keep the previous document when the new file is invalid. Clean up cancelled loads.
                    var newSource: DocumentSource? = null
                    var newPreview: Bitmap? = null
                    var adopted = false
                    try {
                        withContext(NonCancellable + Dispatchers.IO) {
                            newSource = factory()
                            newPreview = newSource!!.renderPage(0, 800, 1132)
                        }
                        ensureActive()
                        _currentDocument.value?.close()
                        _currentDocument.value = newSource
                        _previewBitmap.value = newPreview
                        _selectedPageIndex.value = 0
                        _printSettings.value = _printSettings.value.copy(pageRangeText = "ALL", scaling = if (newSource is PdfDocumentSource) _printSettings.value.scaling else PrintScaling.FIT_PAGE)
                        adopted = true
                    } finally {
                        if (!adopted) { newSource?.close(); newPreview?.recycle() }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError(text("باز کردن سند ناموفق بود: ", "Cannot open document: ") + e.message) }
            finally { if (loadGeneration == generation) _isLoading.value = false }
        }
    }
    fun selectPage(index: Int) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            documentMutex.withLock {
                val doc = _currentDocument.value ?: return@withLock
                if (index !in 0 until doc.totalPages) return@withLock
                try {
                    val preview = withContext(Dispatchers.IO) { doc.renderPage(index, 800, 1132) }
                    _previewBitmap.value = preview
                    _selectedPageIndex.value = index
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { reportError(e.message ?: "Preview failed") }
            }
        }
    }
    fun updateSettings(settings: PrintSettings) { if (!isPrinting.value) _printSettings.value = settings }
    fun startPrint() {
        if (_isLoading.value) return
        val doc = _currentDocument.value ?: return
        printJobManager.startPrintJob(doc, _printSettings.value) { state ->
            if (state is PrintJobState.Completed) reportError(
                if (_printSettings.value.driverType == DriverType.FILE_STREAM_DUMP) text("فایل PCL آماده اشتراک‌گذاری است.", "PCL file ready to share.")
                else text("داده‌ها ارسال شدند. خروجی چاپگر را بررسی کنید.", "Data sent. Check the printer for the printed pages.")
            )
        }
    }
    fun prepareSystemPrint(onReady: (DocumentSource, PrintSettings) -> Unit) {
        if (_isLoading.value || isPrinting.value) return
        viewModelScope.launch {
            try {
                _printSettings.value.validate()
                val settings = _printSettings.value
                var copy: DocumentSource? = null
                var handedOff = false
                try {
                    documentMutex.withLock {
                        val doc = _currentDocument.value ?: error("Select a document first")
                        settings.parsePageIndices(doc.totalPages)
                        withContext(NonCancellable + Dispatchers.IO) { copy = doc.duplicate() }
                    }
                    ensureActive()
                    onReady(copy!!, settings)
                    handedOff = true
                } finally { if (!handedOff) copy?.close() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError(e.message ?: "Cannot start system printing") }
        }
    }
    fun cancelPrint() = printJobManager.cancelCurrentJob()
    fun runSafeProbe() {
        if (_isSafeProbing.value) return
        viewModelScope.launch {
            _isSafeProbing.value = true
            try {
                val result = usbRepository.safeProbe()
                reportError(result.fold({ text("پویش انجام شد: ", "Probe finished: ") + (it.portStatus?.toDisplayString() ?: "Status unavailable") }, { it.message ?: "Probe failed" }))
            } finally { _isSafeProbing.value = false }
        }
    }
    fun copyDiagnostics() { diagnosticsRepository.copyReportToClipboard(); reportError(text("گزارش کپی شد.", "Report copied.")) }
    fun shareDiagnosticsReport(asJson: Boolean) {
        try { diagnosticsRepository.shareReport(app, asJson) } catch (e: Exception) { reportError(e.message ?: "Cannot share report") }
    }
    fun shareStream() {
        val file = printJobManager.lastCapturedStreamFile.value ?: return
        try { diagnosticsRepository.shareFile(app, file, "application/octet-stream", "Export PCL file") }
        catch (e: Exception) { reportError(e.message ?: "Cannot share file") }
    }
    fun clearTraceLogs() = UsbTraceLogger.clear()
    private fun text(fa: String, en: String) = if (_isPersian.value) fa else en
    override fun onCleared() {
        val doc = _currentDocument.value
        app.applicationScope.launch {
            printJobManager.cancelAndJoin()
            documentMutex.withLock { withContext(Dispatchers.IO) { doc?.close() } }
        }
        super.onCleared()
    }
}
