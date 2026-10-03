package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.DocumentPreviewCard
import com.example.ui.components.PrintProgressDialog
import com.example.ui.components.PrinterStatusCard
import com.example.ui.components.PrintSettingsPanel
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onSystemPrint: (com.example.document.DocumentSource, com.example.core.model.PrintSettings) -> Unit) {
    val isLoading by viewModel.isLoading.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    val streamFile by viewModel.printJobManager.lastCapturedStreamFile.collectAsState()
    val devices by viewModel.allDevices.collectAsState()
    val isPersian by viewModel.isPersian.collectAsState()
    val activeDevice by viewModel.activeDeviceInfo.collectAsState()
    val currentDoc by viewModel.currentDocument.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val pageIndex by viewModel.selectedPageIndex.collectAsState()
    val settings by viewModel.printSettings.collectAsState()
    val currentJob by viewModel.currentJob.collectAsState()
    val showDiagnostics by viewModel.showDiagnosticsSheet.collectAsState()
    val traceLogs by viewModel.traceLogs.collectAsState()
    val isSafeProbing by viewModel.isSafeProbing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Document Pickers
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadPdfUri(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImageUri(uri)
        }
    }

    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = AppText.appName(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    },
                    actions = {
                        // Language Toggle: فا / EN
                        OutlinedButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("language_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "EN" else "فا", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Diagnostics icon
                        IconButton(
                            onClick = { viewModel.setDiagnosticsSheetVisible(true) },
                            modifier = Modifier.testTag("appbar_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Diagnostics"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Primary Print Action Bar
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { viewModel.prepareSystemPrint(onSystemPrint) },
                            enabled = currentDoc != null && !isLoading && !isPrinting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp)
                                .heightIn(min = 52.dp)
                                .testTag("print_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppText.t(isPersian, "چاپ با اندروید / ذخیره PDF", "Print / Save PDF"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth() // Maintain pleasant ergonomics on tablets and foldables
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 1. Printer Status Card
                    PrinterStatusCard(
                        deviceInfo = activeDevice,
                        isPersian = isPersian,
                        onRequestPermission = { viewModel.requestUsbPermission() },
                        onOpenDiagnostics = { viewModel.setDiagnosticsSheetVisible(true) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(AppText.t(isPersian,
                        "چاپ USB مستقیم فقط برای PCL 5 است. درایور Canon LBP6030 در این نسخه موجود نیست. چاپ اندروید به سرویس سازگار با چاپگر نیاز دارد.",
                        "Direct USB printing supports PCL 5 only. Canon LBP6030 requires a driver that is not included. Android printing requires a service compatible with your printer."),
                        style = MaterialTheme.typography.bodyMedium)
                    if (devices.count { it.primaryPrinterInterface != null } > 1) {
                        devices.filter { it.primaryPrinterInterface != null }.forEach { device ->
                            OutlinedButton(onClick = { viewModel.usbRepository.selectDeviceByName(device.deviceName) }, enabled = !isPrinting) {
                                Text((if (activeDevice?.deviceName == device.deviceName) "✓ " else "") + (device.productName ?: device.deviceName))
                            }
                        }
                    }
                    if (isLoading) {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Document Selection Buttons
                    Text(
                        text = AppText.t(isPersian, "انتخاب سند برای چاپ:", "Select Document to Print:"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            enabled = !isPrinting,
                            modifier = Modifier
                                .widthIn(min = 140.dp)
                                .testTag("select_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppText.t(isPersian, "انتخاب PDF", "Select PDF"), fontSize = 13.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .widthIn(min = 140.dp)
                                .testTag("select_image_button"),
                            enabled = !isPrinting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppText.t(isPersian, "انتخاب عکس", "Select Photo"), fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadTestPage() },
                            enabled = !isPrinting,
                            modifier = Modifier
                                .widthIn(min = 140.dp)
                                .testTag("test_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppText.t(isPersian, "صفحه آزمایش", "Test Page"), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Document Preview Card
                    DocumentPreviewCard(
                        documentSource = currentDoc,
                        previewBitmap = previewBitmap,
                        currentPageIndex = pageIndex,
                        isPersian = isPersian,
                        pageRangeText = settings.pageRangeText,
                        onPageRangeChange = { viewModel.updateSettings(settings.copy(pageRangeText = it)) },
                        onPreviousPage = { viewModel.selectPage(pageIndex - 1) },
                        onNextPage = { viewModel.selectPage(pageIndex + 1) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Print Settings Panel
                    PrintSettingsPanel(
                        settings = settings,
                        isPersian = isPersian,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        allowActualSize = currentDoc is com.example.document.PdfDocumentSource
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.startPrint() },
                        enabled = currentDoc != null && !isLoading && !isPrinting &&
                            (settings.driverType == com.example.core.model.DriverType.FILE_STREAM_DUMP || activeDevice?.permissionGranted == true),
                        modifier = Modifier.fillMaxWidth().testTag("usb_print_button")) {
                        Text(AppText.t(isPersian,
                            if (settings.driverType == com.example.core.model.DriverType.FILE_STREAM_DUMP) "ساخت فایل PCL" else "ارسال با USB (PCL 5)",
                            if (settings.driverType == com.example.core.model.DriverType.FILE_STREAM_DUMP) "Create PCL file" else "Send via USB (PCL 5)"))
                    }
                    if (streamFile != null) {
                        OutlinedButton(onClick = { viewModel.shareStream() }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppText.t(isPersian, "اشتراک فایل PCL", "Share PCL file"))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp)) // Clearance for bottom bar
                }
            }

            // Diagnostics Modal Sheet
            if (showDiagnostics) {
                DiagnosticsSheet(
                    deviceInfo = activeDevice,
                    traceLogs = traceLogs,
                    isSafeProbing = isSafeProbing,
                    isPersian = isPersian,
                    onDismiss = { viewModel.setDiagnosticsSheetVisible(false) },
                    onRequestPermission = { viewModel.requestUsbPermission() },
                    onReconnect = { viewModel.refreshUsbDevices() },
                    onSafeProbe = { viewModel.runSafeProbe() },
                    onCopyDiagnostics = { viewModel.copyDiagnostics() },
                    onExportReport = { asJson -> viewModel.shareDiagnosticsReport(asJson) },
                    onClearLogs = { viewModel.clearTraceLogs() }
                )
            }

            // Real-time Print Progress Dialog
            PrintProgressDialog(
                job = currentJob,
                isPersian = isPersian,
                onCancel = { viewModel.cancelPrint() },
                onDismiss = { viewModel.printJobManager.dismissJob() }
            )
        }
    }
}
