package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DitherAlgorithm
import com.example.core.model.DriverType
import com.example.core.model.PaperSize
import com.example.core.model.PrintContentMode
import com.example.core.model.PrintOrientation
import com.example.core.model.PrintQuality
import com.example.core.model.PrintScaling
import com.example.core.model.PrintSettings
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsPanel(
    settings: PrintSettings,
    isPersian: Boolean,
    onSettingsChanged: (PrintSettings) -> Unit,
    allowActualSize: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("print_settings_panel"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppText.t(isPersian, "تنظیمات چاپ", "Print Settings"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Copies Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppText.t(isPersian, "تعداد نسخه:", "Copies:"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (settings.copies > 1) {
                                onSettingsChanged(settings.copy(copies = settings.copies - 1))
                            }
                        },
                        enabled = settings.copies > 1,
                        modifier = Modifier.testTag("copies_minus_button")
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease copies")
                    }

                    Text(
                        text = "${settings.copies}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    IconButton(
                        onClick = {
                            if (settings.copies < 99) {
                                onSettingsChanged(settings.copy(copies = settings.copies + 1))
                            }
                        },
                        enabled = settings.copies < 99,
                        modifier = Modifier.testTag("copies_plus_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase copies")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Paper Size Selection
            Text(
                text = AppText.t(isPersian, "اندازه کاغذ:", "Paper Size:"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            val paperSizes = listOf(PaperSize.A4, PaperSize.A5, PaperSize.LETTER)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                paperSizes.forEachIndexed { index, paper ->
                    SegmentedButton(
                        selected = settings.paperSize == paper,
                        onClick = { onSettingsChanged(settings.copy(paperSize = paper)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = paperSizes.size)
                    ) {
                        Text(paper.name, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Orientation Selection
            Text(
                text = AppText.t(isPersian, "جهت صفحه:", "Orientation:"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            val orientations = listOf(PrintOrientation.AUTO, PrintOrientation.PORTRAIT, PrintOrientation.LANDSCAPE)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                orientations.forEachIndexed { index, orient ->
                    val label = when (orient) {
                        PrintOrientation.AUTO -> AppText.t(isPersian, "خودکار", "Auto")
                        PrintOrientation.PORTRAIT -> AppText.t(isPersian, "عمودی", "Portrait")
                        PrintOrientation.LANDSCAPE -> AppText.t(isPersian, "افقی", "Landscape")
                    }
                    SegmentedButton(
                        selected = settings.orientation == orient,
                        onClick = { onSettingsChanged(settings.copy(orientation = orient)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = orientations.size)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scaling Selection
            Text(
                text = AppText.t(isPersian, "مقیاس‌بندی:", "Scaling:"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            val scalings = if (allowActualSize) listOf(PrintScaling.FIT_PAGE, PrintScaling.FILL_PAGE, PrintScaling.ACTUAL_SIZE) else listOf(PrintScaling.FIT_PAGE, PrintScaling.FILL_PAGE)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                scalings.forEachIndexed { index, scale ->
                    val label = when (scale) {
                        PrintScaling.FIT_PAGE -> AppText.t(isPersian, "تناسب", "Fit")
                        PrintScaling.FILL_PAGE -> AppText.t(isPersian, "تمام صفحه", "Fill")
                        PrintScaling.ACTUAL_SIZE -> AppText.t(isPersian, "۱۰۰٪", "100%")
                    }
                    SegmentedButton(
                        selected = settings.scaling == scale,
                        onClick = { onSettingsChanged(settings.copy(scaling = scale)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scalings.size)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Mode (Document vs Photo)
            Text(
                text = AppText.t(isPersian, "نوع محتوا:", "Content Mode:"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            val modes = listOf(PrintContentMode.DOCUMENT_TEXT, PrintContentMode.PHOTO)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    val label = when (mode) {
                        PrintContentMode.DOCUMENT_TEXT -> AppText.t(isPersian, "متن / سند", "Document / Text")
                        PrintContentMode.PHOTO -> AppText.t(isPersian, "عکس / تصویر", "Photo")
                    }
                    SegmentedButton(
                        selected = settings.contentMode == mode,
                        onClick = { onSettingsChanged(settings.copy(contentMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(AppText.t(isPersian, "کیفیت چاپ USB:", "USB print quality:"), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(PrintQuality.DRAFT_300DPI, PrintQuality.NORMAL_600DPI).forEachIndexed { index, quality ->
                    SegmentedButton(selected = settings.quality == quality,
                        onClick = { onSettingsChanged(settings.copy(quality = quality)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text("${quality.dpi} DPI") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Driver Engine Dropdown
            var driverExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = driverExpanded,
                onExpandedChange = { driverExpanded = !driverExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = settings.driverType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppText.t(isPersian, "موتور درایور چاپگر", "Printer Driver Engine"), fontSize = 12.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = driverExpanded,
                    onDismissRequest = { driverExpanded = false }
                ) {
                    listOf(DriverType.RAW_PCL, DriverType.FILE_STREAM_DUMP).forEach { dt ->
                        DropdownMenuItem(
                            text = { Text(dt.displayName, fontSize = 13.sp) },
                            onClick = {
                                onSettingsChanged(settings.copy(driverType = dt))
                                driverExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dithering Algorithm Dropdown
            var ditherExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = ditherExpanded,
                onExpandedChange = { if (settings.contentMode == PrintContentMode.PHOTO) ditherExpanded = !ditherExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (settings.contentMode == PrintContentMode.DOCUMENT_TEXT) AppText.t(isPersian, "آستانه برای متن", "Threshold for text") else settings.ditherAlgorithm.displayName,
                    enabled = settings.contentMode == PrintContentMode.PHOTO,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppText.t(isPersian, "الگوریتم ترام (Dither)", "Dithering Algorithm"), fontSize = 12.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ditherExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = ditherExpanded,
                    onDismissRequest = { ditherExpanded = false }
                ) {
                    DitherAlgorithm.entries.forEach { da ->
                        DropdownMenuItem(
                            text = { Text(da.displayName, fontSize = 13.sp) },
                            onClick = {
                                onSettingsChanged(settings.copy(ditherAlgorithm = da))
                                ditherExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
