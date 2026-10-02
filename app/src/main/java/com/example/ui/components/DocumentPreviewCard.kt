package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.document.DocumentSource
import com.example.ui.strings.AppText
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate600

@Composable
fun DocumentPreviewCard(
    documentSource: DocumentSource?,
    previewBitmap: Bitmap?,
    currentPageIndex: Int,
    isPersian: Boolean,
    pageRangeText: String,
    onPageRangeChange: (String) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_preview_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Document title & page count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Document",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = documentSource?.title ?: AppText.t(isPersian, "سندی انتخاب نشده است", "No document selected"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                if (documentSource != null) {
                    Text(
                        text = "${documentSource.totalPages} ${AppText.t(isPersian, "صفحه", "pages")}",
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sheet Paper Canvas Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .shadow(6.dp, RoundedCornerShape(2.dp))
                            .border(1.dp, Slate200, RoundedCornerShape(2.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Page Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .aspectRatio(1f / 1.414f) // Standard A4 ratio
                                .testTag("preview_image")
                        )
                    }
                } else if (documentSource != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = PrimaryBlue,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = AppText.t(isPersian, "برای پیش‌نمایش، سند PDF یا عکس انتخاب کنید", "Select a PDF or Photo to preview"),
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Page Navigation Controls (if multi-page)
            if (documentSource != null && documentSource.totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousPage,
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.testTag("prev_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Page"
                        )
                    }

                    Text(
                        text = "${AppText.t(isPersian, "صفحه", "Page")} ${currentPageIndex + 1} / ${documentSource.totalPages}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = onNextPage,
                        enabled = currentPageIndex < documentSource.totalPages - 1,
                        modifier = Modifier.testTag("next_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Page"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Page Range text field
                OutlinedTextField(
                    value = pageRangeText,
                    onValueChange = onPageRangeChange,
                    label = { Text(AppText.t(isPersian, "محدوده صفحات (مانند ALL یا 1-3,5)", "Pages range (e.g. ALL or 1-3,5)"), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("page_range_input")
                )
            }
        }
    }
}
