package com.example.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.PrintJob
import com.example.core.model.PrintJobState
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.Slate600
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun PrintProgressDialog(
    job: PrintJob?,
    isPersian: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    if (job == null || job.state is PrintJobState.Idle) return

    val state = job.state
    val isFinished = state is PrintJobState.Completed || state is PrintJobState.Failed || state is PrintJobState.Cancelled

    AlertDialog(
        onDismissRequest = {
            if (isFinished) onDismiss()
        },
        confirmButton = {
            if (isFinished) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_job_dialog_button")
                ) {
                    Text(AppText.t(isPersian, "بستن", "Close"))
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    modifier = Modifier.testTag("cancel_job_button")
                ) {
                    Text(AppText.t(isPersian, "لغو چاپ", "Cancel Print"))
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (state) {
                    is PrintJobState.Completed -> Icons.Default.CheckCircle to StatusGreen
                    is PrintJobState.Failed -> Icons.Default.Error to StatusRed
                    else -> Icons.Default.Print to PrimaryBlue
                }
                Icon(
                    imageVector = icon.first,
                    contentDescription = null,
                    tint = icon.second,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (state) {
                        is PrintJobState.Completed -> AppText.t(isPersian, "چاپ با موفقیت انجام شد", "Print Job Completed")
                        is PrintJobState.Failed -> AppText.t(isPersian, "خطا در چاپ", "Print Job Failed")
                        is PrintJobState.Cancelled -> AppText.t(isPersian, "چاپ لغو شد", "Print Job Cancelled")
                        else -> AppText.t(isPersian, "در حال ارسال به چاپگر", "Printing Document")
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                when (state) {
                    is PrintJobState.Preparing -> {
                        Text(AppText.t(isPersian, "در حال آماده‌سازی سند...", "Preparing document..."))
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.Rendering -> {
                        Text(
                            text = "${AppText.t(isPersian, "در حال رندر صفحه", "Rendering page")} ${state.currentPage} / ${state.totalPages} (${state.message})",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = state.currentPage.toFloat() / state.totalPages.toFloat()
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.Encoding -> {
                        Text(
                            text = "${AppText.t(isPersian, "کدگذاری داده‌های رستر به فرمت", "Encoding raster into")} ${state.driverName} (${state.currentPage}/${state.totalPages})",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.WaitingForPrinter -> {
                        Text(
                            text = "${AppText.t(isPersian, "اتصال و پیکربندی چاپگر", "Connecting and probing printer")}: ${state.message}",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    is PrintJobState.Sending -> {
                        Text(
                            text = "${AppText.t(isPersian, "در حال ارسال داده به اندپوینت USB OUT", "Transmitting to USB bulk OUT")}: ${state.bytesSent / 1024} KB / ${state.totalBytes / 1024} KB (${state.percent}%)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = if (state.totalBytes > 0) state.bytesSent.toFloat() / state.totalBytes.toFloat() else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.DataSent -> {
                        Text(
                            text = AppText.t(isPersian, "تمام بایت‌ها به بافر چاپگر منتقل شدند. در حال دریافت تاییدیه...", "All bytes transferred to printer buffer. Processing..."),
                            fontSize = 14.sp,
                            color = StatusGreen
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.Finishing -> {
                        Text(
                            text = "${AppText.t(isPersian, "اتمام چاپ و خروج کاغذ", "Finishing and paper eject")}: ${state.message}",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is PrintJobState.Completed -> {
                        Text(
                            text = "${AppText.t(isPersian, "تعداد صفحه چاپ‌شده:", "Pages printed:")} ${state.pagesPrinted}\n" +
                                   "${AppText.t(isPersian, "حجم جریان داده:", "Stream size:")} ${state.totalBytes / 1024} KB\n" +
                                   "${AppText.t(isPersian, "مدت زمان:", "Duration:")} ${state.durationMs / 1000}s",
                            fontSize = 14.sp,
                            color = Slate600
                        )
                    }

                    is PrintJobState.Failed -> {
                        Text(
                            text = state.reason,
                            fontSize = 14.sp,
                            color = StatusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.technicalDetail.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.technicalDetail,
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    }

                    is PrintJobState.Cancelled -> {
                        Text(
                            text = state.reason,
                            fontSize = 14.sp,
                            color = Slate600
                        )
                    }

                    is PrintJobState.Idle -> {}
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
