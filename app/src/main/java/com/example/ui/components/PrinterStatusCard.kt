package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.usb.UsbDeviceInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterStatusCard(
    deviceInfo: UsbDeviceInfo?,
    isPersian: Boolean,
    onRequestPermission: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("printer_status_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Printer Icon + Device Name + Connection Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Printer",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = when {
                        deviceInfo?.isLbp6030Family == true -> AppText.targetPrinter()
                        deviceInfo != null -> deviceInfo.productName ?: "USB Printer"
                        else -> AppText.t(isPersian, "چاپگر USB", "USB Printer")
                    }

                    Text(
                        text = displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Status line with colored indicator dot
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dotColor, statusText) = when {
                            deviceInfo == null -> StatusRed to AppText.t(isPersian, "هیچ چاپگری متصل نیست", "No printer connected")
                            !deviceInfo.permissionGranted -> StatusAmber to AppText.t(isPersian, "مجوز دسترسی به USB لازم است", "USB permission required")
                            else -> StatusGreen to AppText.t(isPersian, "● متصل از طریق USB OTG", "● Connected via USB OTG")
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (deviceInfo != null && deviceInfo.permissionGranted) StatusGreen else Slate600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware details chips
            if (deviceInfo != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("VID: ${deviceInfo.vendorIdHex}", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("PID: ${deviceInfo.productIdHex}", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    if (deviceInfo.portStatus != null) {
                        val portLabel = if (deviceInfo.portStatus.paperEmpty) {
                            AppText.t(isPersian, "اتمام کاغذ!", "Paper Empty!")
                        } else {
                            AppText.t(isPersian, "کاغذ: آماده", "Paper: Ready")
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(portLabel, fontSize = 11.sp, color = if (deviceInfo.portStatus.paperEmpty) StatusRed else StatusGreen) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    if (deviceInfo.ieee1284 != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("CMD: ${deviceInfo.ieee1284.commandSet.take(2).joinToString(",")}", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (deviceInfo?.permissionGranted == true && deviceInfo.ieee1284?.supportsPcl5 != true) {
                Text(AppText.t(isPersian, "برای بررسی سازگاری پویش USB را اجرا کنید. تشخیص دستگاه به معنی پشتیبانی چاپ نیست.",
                    "Run the USB probe to check compatibility. Detection alone does not establish print support."),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Action row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (deviceInfo != null && !deviceInfo.permissionGranted) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.testTag("grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(AppText.t(isPersian, "اعطای مجوز USB", "Grant USB Permission"), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Diagnostics",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppText.t(isPersian, "عیب‌یابی USB", "USB Diagnostics"), fontSize = 13.sp)
                }
            }
        }
    }
}
