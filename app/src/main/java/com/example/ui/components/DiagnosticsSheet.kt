package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.usb.UsbDeviceInfo
import com.example.usb.UsbTraceEvent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsSheet(
    deviceInfo: UsbDeviceInfo?,
    traceLogs: List<UsbTraceEvent>,
    isSafeProbing: Boolean,
    isPersian: Boolean,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onReconnect: () -> Unit,
    onSafeProbe: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onExportReport: (asJson: Boolean) -> Unit,
    onClearLogs: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.testTag("diagnostics_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppText.t(isPersian, "عیب‌یابی تخصصی USB چاپگر", "USB Hardware Diagnostics"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSafeProbe,
                    enabled = !isSafeProbing,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.testTag("safe_probe_button")
                ) {
                    if (isSafeProbing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(AppText.t(isPersian, "پویش ایمن USB", "Run Safe USB Probe"), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCopyDiagnostics,
                    modifier = Modifier.testTag("copy_diagnostics_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppText.t(isPersian, "کپی مشخصات", "Copy Diagnostics"), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onExportReport(false) },
                    modifier = Modifier.testTag("export_text_report_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppText.t(isPersian, "گزارش متنی (TXT)", "Export TXT Report"), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onExportReport(true) },
                    modifier = Modifier.testTag("export_json_report_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("JSON Report", fontSize = 12.sp)
                }

                OutlinedButton(onClick = onReconnect) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppText.t(isPersian, "اتصال مجدد", "Reconnect"), fontSize = 12.sp)
                }

                OutlinedButton(onClick = onClearLogs) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppText.t(isPersian, "پاک‌سازی وقایع", "Clear Logs"), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Details
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // USB Hardware Descriptor Card
                item {
                    Text(
                        text = AppText.t(isPersian, "مشخصات سخت‌افزاری چاپگر:", "Hardware USB Descriptors:"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (deviceInfo != null) {
                                Text("Device Name: ${deviceInfo.deviceName}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Manufacturer: ${deviceInfo.manufacturerName ?: "Unknown"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Product: ${deviceInfo.productName ?: "Unknown"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Vendor ID: ${deviceInfo.vendorIdHex} (${deviceInfo.vendorId})", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Product ID: ${deviceInfo.productIdHex} (${deviceInfo.productId})", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Serial: ${deviceInfo.serialNumber ?: "N/A"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Device Class: ${deviceInfo.deviceClass} | Subclass: ${deviceInfo.deviceSubclass} | Protocol: ${deviceInfo.deviceProtocol}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Permission Granted: ${if (deviceInfo.permissionGranted) "YES" else "NO"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Interface Claimed: ${if (deviceInfo.interfaceClaimed) "YES" else "NO"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)

                                if (deviceInfo.portStatus != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Port Status: ${deviceInfo.portStatus.toDisplayString()} (Byte: 0x${String.format("%02X", deviceInfo.portStatus.rawByte)})", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = StatusGreen)
                                }

                                if (deviceInfo.ieee1284 != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("IEEE-1284 Device ID:", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("  MFG: ${deviceInfo.ieee1284.manufacturer}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("  MDL: ${deviceInfo.ieee1284.model}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("  CMD: ${deviceInfo.ieee1284.commandSet.joinToString(", ")}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("  RAW: ${deviceInfo.ieee1284.rawString}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Interfaces & Endpoints (${deviceInfo.interfaces.size}):", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                for (uif in deviceInfo.interfaces) {
                                    Text("  Interface #${uif.id} [Class: ${uif.interfaceClass}, Subclass: ${uif.interfaceSubclass}, Protocol: ${uif.interfaceProtocol}]", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    for (ep in uif.endpoints) {
                                        Text("    • 0x${String.format("%02X", ep.address)}: ${ep.direction} ${ep.type} (maxPacketSize: ${ep.maxPacketSize})", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            } else {
                                Text(AppText.t(isPersian, "هیچ دستگاه USB متصل نیست.", "No USB device connected."), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = AppText.t(isPersian, "گزارش رویدادهای زنده USB (${traceLogs.size}):", "Real-time USB Trace Events:"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Trace logs list
                items(traceLogs.reversed()) { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (event.isError) MaterialTheme.colorScheme.errorContainer else Slate100)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = event.toFormattedString(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (event.isError) StatusRed else Slate800
                        )
                    }
                }
            }
        }
    }
}
